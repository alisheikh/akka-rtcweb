package akka.rtcweb.protocol.sdp.parser

import java.net.InetSocketAddress

import akka.parboiled2.ParseError
import akka.parboiled2.ParserInput.StringBasedParserInput
import akka.rtcweb.protocol.sdp._
import org.scalatest.{ Matchers, WordSpecLike }

import scala.util.{ Failure, Success }

class SessionDescriptionParserSpec extends WordSpecLike with Matchers {

  "A SessionDescriptorParser" should {

    "parse the rfc example" in {

      val parser = new SessionDescriptionParserImpl(input(
        """v=0
          |o=jdoe 2890844526 2890842807 IN IP4 10.47.16.5
          |s=SDP Seminar
          |i=A Seminar on the session description protocol
          |u=http://www.example.com/seminars/sdp.pdf
          |e=j.doe@example.com (Jane Doe)
          |p=+4917624822132
          |c=IN IP4 224.2.17.12/127
          |b=AS:1024
          |t=2873397496 2873404696
          |r=604800 3600 0 90000m
        |""".stripMargin //
          .replace("\n", "\r\n")))

      val result = parser.parseSessionDescription().recover { case e @ ParseError(position, traces) => fail(s"parse failed at $position: ${e.formatTraces}", e) }.get

      result.origin should be(Origin(Some("jdoe"), 2890844526L, 2890842807L, NetworkType.IN, AddressType.IP4, InetSocketAddress.createUnresolved("10.47.16.5", 0)))
      result.protocolVersion should be(ProtocolVersion.`0`)
      result.sessionName should be(Some("SDP Seminar"))
      result.sessionInformation should be(Some("A Seminar on the session description protocol"))
      result.descriptionUri should be(Some("http://www.example.com/seminars/sdp.pdf"))
      result.emailAddresses should contain only "j.doe@example.com (Jane Doe)"
      result.phoneNumbers should contain only "+4917624822132"
      result.connectionInformation should be(Some(ConnectionData(NetworkType.IN, AddressType.IP4, InetSocketAddress.createUnresolved("224.2.17.12/127", 0))))
      result.bandwidthInformation should be(Some(BandwidthInformation(BandwidthType.AS, 1024)))
      result.timings should contain only Timing(Some(2873397496L), Some(2873404696L))
      result.repeatTimes should contain only (RepeatTimes(TimeSpan(604800L), TimeSpan(3600L)), TimeSpan.ZERO, TimeSpan(90000, TimeUnit.Minutes))

    }

  }

  def input(str: String) = new StringBasedParserInput(str)

}
