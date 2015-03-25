package akka.rtcweb

import java.net.NetworkInterface

import akka.actor.Actor
import scala.collection.JavaConverters._

import scala.concurrent.duration._
import scala.util.Try


object InterfaceMonitor {

  //def props(interval:FiniteDuration)

  private case object Tick

}

class InterfaceMonitor(updateInterval:FiniteDuration) extends Actor {

  import InterfaceMonitor._



  override def receive: Receive = {
    case Tick =>
      val ifs = NetworkInterface.getNetworkInterfaces.asScala.toList
      val allInterfaces = ifs.flatMap(interface => interface :: interface.getSubInterfaces.asScala.toList)
      val onlineInterfaces = allInterfaces.filter(_.isUp)
      val allAddresses = ifs.flatMap(_.getInetAddresses.asScala.toList)



  }

  private def scheduleNextTick() = context.system.scheduler.scheduleOnce(updateInterval, self, Tick)



}
