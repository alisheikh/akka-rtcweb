package akka.rtcweb.protocol.jsep

import java.net.{ InetSocketAddress, InetAddress }

import akka.actor.{ Props, ActorRef, Actor }
import akka.rtcweb.protocol.jsep.RTCPeerConnection.{ ICECandidatePolicy, PeerConnectionConfiguration }
import akka.rtcweb.protocol.sdp._
import collection.immutable.Seq
import scala.concurrent.duration._

import scala.concurrent.duration.FiniteDuration
import scala.util.Random

object RTCPeerConnection {

  type Label = String

  /**
   * Creates Props for an actor of this type.
   * @param config ???
   * @return a Props for creating this actor, which can then be further configured
   *         (e.g. calling `.withDispatcher()` on it)
   */
  def props(config: PeerConnectionConfiguration) = Props(new RTCPeerConnection(config))

  /**
   * By specifying a policy from the list below, the application can control how aggressively it
   * will try to BUNDLE media streams together.  The set of available
   * policies is as follows:
   */
  sealed trait BundlePolicy

  /**
   *  Typically, when gathering ICE candidates, the browser will gather all
   * possible forms of initial candidates - host, server reflexive, and
   * relay.  However, in certain cases, applications may want to have more
   * specific control over the gathering process, due to privacy or
   * related concerns.  For example, one may want to suppress the use of
   * host candidates, to avoid exposing information about the local
   * network, or go as far as only using relay candidates, to leak as
   * little location information as possible (note that these choices come
   * with corresponding operational costs).  To accomplish this, the
   * browser MUST allow the application to restrict which ICE candidates
   * are used in a session.  In addition, administrators may also wish to
   * control the set of ICE candidates, and so the browser SHOULD also
   * allow control via local policy, with the most restrictive policy
   * prevailing.
   *
   * There may also be cases where the application wants to change which
   * types of candidates are used while the session is active.  A prime
   * example is where a callee may initially want to use only relay
   * candidates, to avoid leaking location information to an arbitrary
   * caller, but then change to use all candidates (for lower operational
   * cost) once the user has indicated they want to take the call.  For
   * this scenario, the browser MUST allow the candidate policy to be
   * changed in mid-session, subject to the aforementioned interactions
   * with local policy.
   *
   * To administer the ICE candidate policy, the browser will determine
   * the current setting at the start of each gathering phase.  Then,
   * during the gathering phase, the browser MUST NOT expose candidates
   * disallowed by the current policy to the application, use them as the
   * source of connectivity checks, or indirectly expose them via other
   * fields, such as the raddr/rport attributes for other ICE candidates.
   * Later, if a different policy is specified by the application, the
   * application can apply it by kicking off a new gathering phase via an
   * ICE restart.
   * @see [[http://tools.ietf.org/html/draft-ietf-rtcweb-jsep-08#section-3.4.3]]
   */
  sealed trait ICECandidatePolicy

  /**
   * Session description objects (RTCSessionDescription) may be of type
   * "offer", "pranswer", and "answer".  These types provide information
   * as to how the description parameter should be parsed, and how the
   * media state should be changed.
   */
  sealed trait RTCSessionDescription

  sealed trait HasSDPPayload extends RTCSessionDescription { def sessionDescription: SessionDescription }

  sealed trait PeerConnectionMessage

  final case class StunServerDescription(address: InetSocketAddress, credentials: Option[Nothing] = None)

  /**
   * @param stunServers TODO
   * @param iceCandidatePoolSize TODO
   * @param bundlePolicy   Lastly, the application can specify its preferred policy regarding
   * use of BUNDLE, the multiplexing mechanism defined in
   * [I-D.ietf-mmusic-sdp-bundle-negotiation].  By specifying a policy
   * from the list below, the application can control how aggressively it
   * will try to BUNDLE media streams together.  The set of available
   * policies is as follows:
   * @param iceCandidatePolicy If an ICE candidate policy is specified,
   * it functions as described in Section 3.4.3, causing the browser to only surface the permitted
   * candidates to the application, and only use those candidates for
   * connectivity checks.  The set of available policies is as follows:
   */
  final case class PeerConnectionConfiguration(
    stunServers: Seq[StunServerDescription],
    iceCandidatePoolSize: Int,
    bundlePolicy: BundlePolicy,
    iceCandidatePolicy: ICECandidatePolicy = ICECandidatePolicy.all)

  /**
   *  The createOffer method takes as a parameter an RTCOfferOptions
   * object.  Special processing is performed when generating a SDP
   * description if the following constraints are present.
   *
   * @param OfferToReceiveAudio If the "OfferToReceiveAudio" option is specified, with an integer
   * value of N, the offer MUST include N non-rejected m= sections with
   * media type "audio", even if fewer than N audio MediaStreamTracks have
   * been added to the PeerConnection.  This allows the offerer to receive
   * audio, including multiple independent streams, even when not sending
   * it; accordingly, the directional attribute on the audio m= sections
   * without associated MediaStreamTracks MUST be set to recvonly.  If
   * this option is specified in the case where at least N audio
   * MediaStreamTracks have already been added to the PeerConnection, or N
   * non-rejected m= sections with media type "audio" would otherwise be
   * generated, it has no effect.  For backwards compatibility, a value of
   * "true" is interpreted as equivalent to N=1.
   *
   * @param OfferToReceiveVideo If the "OfferToReceiveVideo" option is specified, with an integer
   * value of N, the offer MUST include N non-rejected m= sections with
   * media type "video", even if fewer than N video MediaStreamTracks have
   * been added to the PeerConnection.  This allows the offerer to receive
   * video, including multiple independent streams, even when not sending
   * it; accordingly, the directional attribute on the video m= sections
   * without associated MediaStreamTracks MUST be set to recvonly.  If
   * this option is specified in the case where at least N video
   * MediaStreamTracks have already been added to the PeerConnection, or N
   * non-rejected m= sections with media type "video" would otherwise be
   * generated, it has no effect.  For backwards compatibility, a value of
   * "true" is interpreted as equivalent to N=1.
   *
   * @param VoiceActivityDetection  If the "VoiceActivityDetection" option is specified, with a value of
   * "true", the offer MUST indicate support for silence suppression in
   * the audio it receives by including comfort noise ("CN") codecs for
   * each offered audio codec, as specified in [RFC3389], Section 5.1,
   * except for codecs that have their own internal silence suppression
   * support.  For codecs that have their own internal silence suppression
   * support, the appropriate fmtp parameters for that codec MUST be
   * specified to indicate that silence suppression for received audio is
   * desired.  For example, when using the Opus codec, the "usedtx=1"
   * parameter would be specified in the offer.  This option allows the
   * endpoint to significantly reduce the amount of audio bandwidth it
   * receives, at the cost of some fidelity, depending on the quality of
   * the remote VAD algorithm.
   *
   * @param IceRestart  If the "IceRestart" option is specified, with a value of "true", the
   * offer MUST indicate an ICE restart by generating new ICE ufrag and
   * pwd attributes, as specified in RFC5245, Section 9.1.1.1.  If this
   * option is specified on an initial offer, it has no effect (since a
   * new ICE ufrag and pwd are already generated).  This option is useful
   * for reestablishing connectivity in cases where failures are detected.
   *
   */
  final case class RTCOfferOptions(
    OfferToReceiveAudio: Option[Int] = None,
    OfferToReceiveVideo: Option[Int] = None,
    VoiceActivityDetection: Boolean = false,
    IceRestart: Boolean = false,
    DtlsSrtpKeyAgreement: Boolean,
    RtpDataChannels: Boolean)

  final case class CreateAnswerOptions()

  /**
   * Session descriptions generated by createOffer must be immediately
   * usable by setLocalDescription; if a system has limited resources
   * (e.g. a finite number of decoders), createOffer should return an
   * offer that reflects the current state of the system, so that
   * setLocalDescription will succeed when it attempts to acquire those
   * resources.  Because this method may need to inspect the system state
   * to determine the currently available resources, it may be implemented
   * as an async operation.
   *
   * Calling this method may do things such as generate new ICE
   * credentials, but does not result in candidate gathering, or cause
   * media to start or stop flowing.
   *
   * The createOffer method generates a blob of SDP that contains a
   * [RFC3264] offer with the supported configurations for the session,
   * including descriptions of the local MediaStreams attached to this
   * PeerConnection, the codec/RTP/RTCP options supported by this
   * implementation, and any candidates that have been gathered by the ICE
   * Agent.  An options parameter may be supplied to provide additional
   * control over the generated offer.  This options parameter should
   * allow for the following manipulations to be performed:
   *
   * - To indicate support for a media type even if no MediaStreamTracks
   * of that type have been added to the session (e.g., an audio call
   * that wants to receive video.)
   * - To trigger an ICE restart, for the purpose of reestablishing
   * connectivity.
   *
   * In the initial offer, the generated SDP will contain all desired
   * functionality for the session (functionality that is supported but
   * not desired by default may be omitted); for each SDP line, the
   * generation of the SDP will follow the process defined for generating
   * an initial offer from the document that specifies the given SDP line.
   * The exact handling of initial offer generation is detailed in
   * Section 5.2.1 below.
   *
   * In the event createOffer is called after the session is established,
   * createOffer will generate an offer to modify the current session
   * based on any changes that have been made to the session, e.g. adding
   * or removing MediaStreams, or requesting an ICE restart.  For each
   * existing stream, the generation of each SDP line must follow the
   * process defined for generating an updated offer from the RFC that
   * specifies the given SDP line.  For each new stream, the generation of
   * the SDP must follow the process of generating an initial offer, as
   * mentioned above.  If no changes have been made, or for SDP lines that
   * are unaffected by the requested changes, the offer will only contain
   * the parameters negotiated by the last offer-answer exchange.  The
   * exact handling of subsequent offer generation is detailed in
   * Section 5.2.2. below.
   *
   * Session descriptions generated by createOffer must be immediately
   * usable by setLocalDescription; if a system has limited resources
   * (e.g. a finite number of decoders), createOffer should return an
   * offer that reflects the current state of the system, so that
   * setLocalDescription will succeed when it attempts to acquire those
   * resources.  Because this method may need to inspect the system state
   * to determine the currently available resources, it may be implemented
   * as an async operation.
   *
   * Calling this method may do things such as generate new ICE
   * credentials, but does not result in candidate gathering, or cause
   * media to start or stop flowing.
   *
   */
  final case class CreateOffer(options: RTCOfferOptions) extends PeerConnectionMessage

  /**
   * The createAnswer method generates a blob of SDP that contains a
   * [RFC3264] SDP answer with the supported configuration for the session
   * that is compatible with the parameters supplied in the most recent
   * call to setRemoteDescription, which MUST have been called prior to
   * calling createAnswer.  Like createOffer, the returned blob contains
   * descriptions of the local MediaStreams attached to this
   * PeerConnection, the codec/RTP/RTCP options negotiated for this
   * session, and any candidates that have been gathered by the ICE Agent.
   * An options parameter may be supplied to provide additional control
   * over the generated answer.
   *
   * As an answer, the generated SDP will contain a specific configuration
   * that specifies how the media plane should be established; for each
   * SDP line, the generation of the SDP must follow the process defined
   * for generating an answer from the document that specifies the given
   * SDP line.  The exact handling of answer generation is detailed in
   * Section 5.3. below.
   *
   * Session descriptions generated by createAnswer must be immediately
   * usable by setLocalDescription; like createOffer, the returned
   * description should reflect the current state of the system.  Because
   * this method may need to inspect the system state to determine the
   * currently available resources, it may need to be implemented as an
   * async operation.
   *
   * Calling this method may do things such as generate new ICE
   * credentials, but does not trigger candidate gathering or change media
   * state.
   *
   */
  final case class CreateAnswer(options: CreateAnswerOptions) extends PeerConnectionMessage

  /**
   * The SetLocalDescription method instructs the PeerConnection to apply
   * the supplied SDP blob as its local configuration.  The type field
   * indicates whether the blob should be processed as an offer,
   * provisional answer, or final answer; offers and answers are checked
   * differently, using the various rules that exist for each SDP line.
   *
   * This API changes the local media state; among other things, it sets
   * up local resources for receiving and decoding media.  In order to
   * successfully handle scenarios where the application wants to offer to
   * change from one media format to a different, incompatible format, the
   * PeerConnection must be able to simultaneously support use of both the
   * old and new local descriptions (e.g. support codecs that exist in
   * both descriptions) until a final answer is received, at which point
   * the PeerConnection can fully adopt the new local description, or roll
   * back to the old description if the remote side denied the change.
   *
   * This API indirectly controls the candidate gathering process.  When a
   * local description is supplied, and the number of transports currently
   * in use does not match the number of transports needed by the local
   * description, the PeerConnection will create transports as needed and
   * begin gathering candidates for them.
   *
   * If setRemoteDescription was previous called with an offer, and
   * setLocalDescription is called with an answer (provisional or final),
   * and the media directions are compatible, and media are available to
   * send, this will result in the starting of media transmission.
   * @see [[http://tools.ietf.org/html/draft-ietf-rtcweb-jsep-08#section-4.1.5]]
   */
  final case class SetLocalDescription(rtcSessionDescription: RTCSessionDescription) extends PeerConnectionMessage

  /**
   * The setRemoteDescription method instructs the PeerConnection to apply
   * the supplied SDP blob as the desired remote configuration.  As in
   * setLocalDescription, the type field of the indicates how the blob
   * should be processed.
   *
   * This API changes the local media state; among other things, it sets
   * up local resources for sending and encoding media.
   *
   * If setLocalDescription was previously called with an offer, and
   * setRemoteDescription is called with an answer (provisional or final),
   * and the media directions are compatible, and media are available to
   * send, this will result in the starting of media transmission.
   * @see [[http://tools.ietf.org/html/draft-ietf-rtcweb-jsep-08#section-4.1.6]]
   */
  final case class SetRemoteDescription(rtcSessionDescription: RTCSessionDescription) extends PeerConnectionMessage

  /**
   * The setConfiguration method allows the global configuration of the
   * PeerConnection, which was initially set by constructor parameters, to
   * be changed during the session.  The effects of this method call
   * depend on when it is invoked, and differ depending on which specific
   * parameters are changed:
   *
   * o  Any changes to the STUN/TURN servers to use affect the next
   * gathering phase.  If gathering has already occurred, this will
   * cause the next call to createOffer to generate new ICE
   * credentials, for the purpose of forcing an ICE restart and kicking
   * off a new gathering phase, in which the new servers will be used.
   * If the ICE candidate pool has a nonzero size, any existing
   * candidates will be discarded, and new candidates will be gathered
   * from the new servers.
   *
   * o  Any changes to the ICE candidate policy also affect the next
   * gathering phase, in similar fashion to the server changes
   * described above.  Note though that changes to the policy have no
   * effect on the candidate pool, because pooled candidates are not
   * surfaced to the application until a gathering phase occurs, and so
   * any necessary filtering can still be done on any pooled
   * candidates.
   *
   * o  Any changes to the ICE candidate pool size take effect
   * immediately; if increased, additional candidates are pre-gathered;
   * if decreased, the now-superfluous candidates are discarded.
   *
   * o  Any changes to the BUNDLE policy take effect immediately, i.e.
   * any future tracks added to the PeerConnection will have their
   * bundle-only state marked accordingly.
   *
   * This call may result in a change to the state of the ICE Agent, and
   * may result in a change to media state if it results in connectivity
   * being established.
   * @see [[http://tools.ietf.org/html/draft-ietf-rtcweb-jsep-08#section-4.1.10]]
   */
  final case class SetConfiguration(configuration: RTCPeerConnection.PeerConnectionConfiguration) extends PeerConnectionMessage

  /**
   * Creates a new RTCDataChannel object with the given label. The RTCDataChannelInit dictionary can be
   * used to configure properties of the underlying channel such as data reliability.
   *
   * When the createDataChannel() method is invoked, the user agent must run the following steps.
   *
   * 1. If the RTCPeerConnection object’s RTCPeerConnection signalingState is closed, throw an InvalidStateError exception and abort these steps.
   * 2. Let channel be a newly created RTCDataChannel object.
   * 3. Initialize channel's label attribute to the value of the first argument.
   * 4. If the second (dictionary) argument is present, set channel's ordered, maxPacketLifeTime, maxRetransmits, protocol, negotiated and id attributes to the values of their corresponding dictionary members (if present in the dictionary).
   * 5. If both the maxPacketLifeTime and maxRetransmits attributes are set (not null), then throw a SyntaxError exception and abort these steps.
   * 6. If an attribute, either maxPacketLifeTime or maxRetransmits, has been set to indicate unreliable mode, and that value exceeds the maximum value supported by the user agent, the value must be set to the user agents maximum value.
   * 7. If id attribute is uninitialized (not set via the dictionary), initialize it to a value generated by the user agent, according to the WebRTC DataChannel Protocol specification, and skip to the next step. Otherwise, if the value of the id attribute is taken by an existing RTCDataChannel, throw a ResourceInUse exception and abort these steps.
   * 8. Return channel and continue the following steps in the background.
   * 9. Create channel's associated underlying data transport and configure it according to the relevant properties of channel.
   *
   */
  final case class CreateDataChannel(listener: ActorRef, label: String, dataChannelDict: RTCDataChannelInit = RTCDataChannelInit.DEFAULT) extends PeerConnectionMessage

  /**
   * This type represents a collection of object properties and does not have an explicit JavaScript representation.
   * @param ordered If set to false, data is allowed to be delivered out of order. The default value of true,
   *                guarantees that data will be delivered in order.
   * @param maxRetransmits Limits the number of times a channel will retransmit data if not successfully delivered.
   *                       This value may be clamped if it exceeds the maximum value supported by the user agent.
   * @param maxPacketLifeTime Limits the time during which the channel will retransmit data if not successfully delivered.
   *                          This value may be clamped if it exceeds the maximum value supported by the user agent.
   * @param protocol Subprotocol name used for this channel.
   * @param negotiated The default value of false tells the user agent to announce the channel in-band and instruct
   *                   the other peer to dispatch a corresponding RTCDataChannel object. If set to true, it is up to
   *                   the application to negotiate the channel and create a RTCDataChannel object with the same id
   *                   at the other peer.
   * @param id Overrides the default selection of id for this channel.
   */
  final case class RTCDataChannelInit(
    ordered: Boolean = true,
    maxRetransmits: Short,
    maxPacketLifeTime: FiniteDuration,
    protocol: String = "",
    negotiated: Boolean = false,
    id: Int)

  object ICECandidatePolicy {
    /** All candidates will be gathered and used. **/
    case object all extends ICECandidatePolicy
    /**
     * Candidates with private IP addresses [RFC1918] will be
     * filtered out.  This prevents exposure of internal network details,
     * at the cost of requiring relay usage even for intranet calls, if
     * the NAT does not allow hairpinning as described in [RFC4787],
     * section 6. *
     */
    case object public extends ICECandidatePolicy
    /**
     * All candidates except relay candidates will be filtered out.
     * This obfuscates the location information that might be ascertained
     * by the remote peer from the received candidates.  Depending on how
     * the application deploys its relay servers, this could obfuscate
     * location to a metro or possibly even global level. *
     */
    case object relay extends ICECandidatePolicy
  }

  /**
   * The localDescription method returns a copy of the current local
   * configuration, i.e. what was most recently passed to
   * setLocalDescription, plus any local candidates that have been
   * generated by the ICE Agent.
   *
   * A null object will be returned if the local description has not yet
   * been established, or if the PeerConnection has been closed.
   * @see [[http://tools.ietf.org/html/draft-ietf-rtcweb-jsep-08#section-4.1.7]]
   */
  case object GetLocalDescription extends PeerConnectionMessage

  object BundlePolicy {

    /**
     * The application will BUNDLE all media streams of the same
     * type together.  That is, if there are multiple audio and multiple
     * video MediaStreamTracks attached to a PeerConnection, all but the
     * first audio and video tracks will be marked as bundle-only, and
     * candidates will only be gathered for N media streams, where N is
     * the number of distinct media types.  When talking to a non-BUNDLE-
     * aware endpoint, only the non-bundle-only streams will be
     * negotiated.  This policy balances desire to multiplex with the
     * need to ensure basic audio and video still works in legacy cases.
     * Data channels will be in a separate bundle group.
     */
    case object balanced extends BundlePolicy

    /**
     *
     * max-bundle:  The application will BUNDLE all of its media streams,
     * including data channels, on a single transport.  All streams other
     * than the first will be marked as bundle-only.  This policy aims to
     * minimize candidate gathering and maximize multiplexing, at the
     * cost of less compatibility with legacy endpoints.
     */
    case object `max-bundle` extends BundlePolicy

    /**
     * The application will offer BUNDLE, but mark none of its
     * streams as bundle-only.  This policy will allow all streams to be
     * received by non-BUNDLE-aware endpoints, but require separate
     * candidates to be gathered for each media stream.
     */
    case object `max-compat` extends BundlePolicy

    /**
     * Similar to [[`max-bundle`]], but RTCP candidates
     * are not gathered.  This policy reduces the candidates that must be
     * gathered to the absolute minimum, but will not be compatible with
     * legacy endpoints that do not support RTCP mux.
     */
    case object `max-bundle-and-rtcp-mux` extends BundlePolicy
  }
  object RTCSessionDescription {

    /**
     *  "offer" indicates that a description should be parsed as an offer;
     * said description may include many possible media configurations.  A
     * description used as an "offer" may be applied anytime the
     * PeerConnection is in a stable state, or as an update to a previously
     * supplied but unanswered "offer".
     */
    final case class offer(sessionDescription: SessionDescription) extends RTCSessionDescription with HasSDPPayload

    /**
     * "pranswer" indicates that a description should be parsed as an
     * answer, but not a final answer, and so should not result in the
     * freeing of allocated resources.  It may result in the start of media
     * transmission, if the answer does not specify an inactive media
     * direction.  A description used as a "pranswer" may be applied as a
     * response to an "offer", or an update to a previously sent "pranswer".
     */
    final case class pranswer(sessionDescription: SessionDescription) extends RTCSessionDescription with HasSDPPayload

    /**
     *  "answer" indicates that a description should be parsed as an answer,
     * the offer-answer exchange should be considered complete, and any
     * resources (decoders, candidates) that are no longer needed can be
     * released.  A description used as an "answer" may be applied as a
     * response to a "offer", or an update to a previously sent "pranswer".
     *
     * The only difference between a provisional and final answer is that
     * the final answer results in the freeing of any unused resources that
     * were allocated as a result of the offer.  As such, the application
     * can use some discretion on whether an answer should be applied as
     * provisional or final, and can change the type of the session
     * description as needed.  For example, in a serial forking scenario, an
     * application may receive multiple "final" answers, one from each
     * remote endpoint.  The application could choose to accept the initial
     * answers as provisional answers, and only apply an answer as final
     * when it receives one that meets its criteria (e.g. a live user
     * instead of voicemail).
     */
    final case class answer(sessionDescription: SessionDescription) extends RTCSessionDescription with HasSDPPayload

    /**
     *  "rollback" is a special session description type implying that the
     * state machine should be rolled back to the previous state, as
     * described in Section 4.1.4.2.  The contents MUST be empty.
     */
    case object rollback extends RTCSessionDescription

  }

  object RTCDataChannelInit {
    val DEFAULT = RTCDataChannelInit(ordered = true, maxRetransmits = 10, maxPacketLifeTime = 5 seconds, id = 0)
  }

}

final class RTCPeerConnection private[jsep] (private val config: PeerConnectionConfiguration) extends Actor {

  require(config.iceCandidatePolicy == ICECandidatePolicy.all, "Only ICECandidatePolicy.all is supported yet.")

  import RTCPeerConnection._

  /** known dataChannels, id to ActorRef */
  private val dataChannels: collection.mutable.Map[Int, (RTCDataChannelInit, Label, ActorRef)] = collection.mutable.Map.empty
  private var channelIdCounter: Int = 0

  override def receive: Receive = {

    case CreateOffer(options) => {
      require(!options.RtpDataChannels, "Option RtpDataChannels is not yet supported")
      sender ! RTCSessionDescription.offer(createOffer)
    }
    case CreateDataChannel(listener, label, dataChannelInit) =>
      val validChannelId = if (dataChannels.contains(dataChannelInit.id) || dataChannelInit.id == 0) nextChannelId else dataChannelInit.id
      val config = dataChannelInit.copy(id = validChannelId)
      val dataChannelActorProps = RTCDataChannel.props(listener, config)
      val dataChannelActor = context.actorOf(dataChannelActorProps)
      dataChannels += validChannelId -> (config, label, dataChannelActor)
  }

  private def createOffer = {

    val mediaDescriptions: Seq[MediaDescription] = dataChannels.map {
      case (id, dc) =>
        val attributes: Seq[Attribute] = Nil

        MediaDescription(media = Media.application,
          mediaTitle = None,
          portRange = PortRange(9),
          protocol = MediaTransportProtocol.`DTLS/SCTP`,
          mediaAttributes = attributes,
          fmt = Nil,
          connectionInformation = Some(
            ConnectionData(NetworkType.IN, AddressType.IP6, InetSocketAddress.createUnresolved("::", 0))
          ),
          bandwidthInformation = Nil,
          encryptionKey = None
        )
    }.toList

    val sessionAttributes: Seq[Attribute] = Seq(ValueAttribute("msid-semantic", "WMS"))

    SessionDescription(
      protocolVersion = ProtocolVersion.`0`,
      origin = Origin(
        username = None,
        `sess-id` = createSessionId.toString,
        `sess-version` = 0L,
        addrtype = AddressType.IP4,
        `unicast-address` = InetSocketAddress.createUnresolved("0.0.0.0", 0)),
      sessionAttributes = sessionAttributes,
      mediaDescriptions = mediaDescriptions,
      sessionName = None,
      sessionInformation = None,
      descriptionUri = None,
      emailAddresses = Nil,
      phoneNumbers = Nil,
      connectionInformation = None,
      bandwidthInformation = None,
      encryptionKey = None,
      timings = Seq(Timing(Some(0L), Some(0L)))
    )
  }

  private def createSessionId = Random.nextLong()

  private def nextChannelId: Int = { channelIdCounter += 1; channelIdCounter }

}
