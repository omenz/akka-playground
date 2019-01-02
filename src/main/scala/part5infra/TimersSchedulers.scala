package part5infra

import akka.actor.{Actor, ActorLogging, ActorSystem, Cancellable, PoisonPill, Props, Timers}

import scala.concurrent.duration._
import scala.language.postfixOps

object TimersSchedulers extends App {

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(message.toString)
    }
  }

  val system = ActorSystem("SchedulersTimersDemo")
  val simpleActor = system.actorOf(Props[SimpleActor])

  system.log.info("Scheduling reminder for simpleActor")

//  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  import system.dispatcher //same as implicit val
//  system.scheduler.scheduleOnce(1 second) {
//    simpleActor ! "reminder"
//  }

//  val routine: Cancellable = system.scheduler.schedule(1 second, 2 seconds) {
//    simpleActor ! "heartbeat"
//  }

//  system.scheduler.scheduleOnce(5 seconds) {
//    routine.cancel()
//  }

  /**
    * Exercise: implement a self-closing actor
    * - if the actor receives a message (anything), you have 1 second to send it another message
    * - if the time window expires, the actor will stop itself
    * - if you send anther message, the time window is reset
    */

  class ExpiringActor extends Actor with ActorLogging {

    override def receive: Receive = {
      case _ =>
        log.info("received initial message, starting expiration")
        context.become(expiring(stopInOneSecond))
    }

    def expiring(cancellable: Cancellable): Receive = {
      case msg =>
        log.info(s"received message $msg, resetting expiration")
        cancellable.cancel()
        context.become(expiring(stopInOneSecond))
    }

    private def stopInOneSecond: Cancellable = context.system.scheduler.scheduleOnce(1 second) {
      self ! PoisonPill
    }
  }

  val expiringActor = system.actorOf(Props[ExpiringActor], "expiringActor")
  expiringActor ! "initialize"
  Thread.sleep(500)
  expiringActor ! "pls don't stop"
  Thread.sleep(700)
  expiringActor ! "wait a little more"
  Thread.sleep(1200)
  expiringActor ! "actor should have stopped by this time"

  /**
    * Timer
    */

  case object TimerKey
  case object Start
  case object Reminder
  case object Stop
  class TimerBasedHeartbeatActor extends Actor with ActorLogging with Timers {
    timers.startSingleTimer(key = TimerKey, msg = Start, timeout = 500 millis)

    override def receive: Receive = {
      case Start =>
        log.info("Bootstrapping")
        timers.startPeriodicTimer(key = TimerKey, msg = Reminder, interval = 1 second)
      case Reminder =>
        log.info("I am alive")
      case Stop =>
        log.warning("Stopping!")
        timers.cancel(TimerKey)
        context.stop(self)
    }
  }

  val timerHeartBeatActor = system.actorOf(Props[TimerBasedHeartbeatActor], "timerActor")
  system.scheduler.scheduleOnce(5 seconds) {
    timerHeartBeatActor ! Stop
  }
}
