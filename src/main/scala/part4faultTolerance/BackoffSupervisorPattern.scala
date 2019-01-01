package part4faultTolerance

import java.io.File

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorLogging, ActorSystem, OneForOneStrategy, Props}
import akka.pattern.{Backoff, BackoffSupervisor}

import scala.io.Source
import scala.concurrent.duration._
import scala.language.postfixOps

object BackoffSupervisorPattern extends App {

  case object ReadFile
  class FileBasedPersistentActor extends Actor with ActorLogging {
    var dataSource: Source = null

    override def preStart(): Unit =
      log.info("Persistent actor starting")

    override def postStop(): Unit =
      log.warning("Persistent actor has stopped")

    override def preRestart(reason: Throwable, message: Option[Any]): Unit =
      log.warning("Persistent actor restarting")

    override def receive: Receive = {
      case ReadFile =>
        if (dataSource == null)
          dataSource = Source.fromFile(new File("src/main/resources/testfiles/important_data.txt"))
        log.info("I've just read some IMPORTANT data: " + dataSource.getLines().toList)
    }
  }

  val system = ActorSystem("BackoffSupervisorDemo")
//  val simpleActor = system.actorOf(Props[FileBasedPersistentActor], "simpleActor")
//  simpleActor ! ReadFile

  val simpleSupervisorProps = BackoffSupervisor.props(
    Backoff.onFailure(
      childProps = Props[FileBasedPersistentActor],
      childName = "simpleBackoffActor",
      minBackoff = 3 seconds, // then 6s, 12s, 24s
      maxBackoff = 30 seconds,
      randomFactor = 0.2, // adds a little noise to avoid multiple actors starting at the same time
    )
  )

//  val simpleBackoffSupervisor = system.actorOf(simpleSupervisorProps, "simpleSupervisor")
//  simpleBackoffSupervisor ! ReadFile

  /*
    simpleSupervisor
     - child called simpleBackoffActor (props of type FileBasedPersistentActor)
     - supervision strategy is the default one (restarting on everything)
      - first attempt after 3 seconds
      - next attempt is 2x the previous attempt
   */

  val stopSupervisorProps = BackoffSupervisor.props(
    Backoff.onStop(
      childProps = Props[FileBasedPersistentActor],
      childName = "stopBackoffActor",
      minBackoff = 3 seconds,
      maxBackoff = 30 seconds,
      randomFactor = 0.2,
    ).withSupervisorStrategy(
      OneForOneStrategy() {
        case _ => Stop
      }
    )
  )

//  val stopSupport = system.actorOf(stopSupervisorProps, "stopSupervisor")
//  stopSupport ! ReadFile

  class EagerFBPActor extends FileBasedPersistentActor {
    override def preStart(): Unit = {
      log.info("Eager actor starting")
      dataSource = Source.fromFile(new File("src/main/resources/testfiles/important_data.txt"))
    }
  }

  val eagerActor = system.actorOf(Props[EagerFBPActor])
  // ActorInitializationException => default supervision strategy STOP

  val repeatedSupervisorProps = BackoffSupervisor.props(
    Backoff.onStop(
      childProps = Props[EagerFBPActor],
      childName = "eagerActor",
      minBackoff = 1 second,
      maxBackoff = 30 seconds,
      randomFactor = 0.1
    )
  )
  val repeatedSupervisor = system.actorOf(repeatedSupervisorProps, "eagerSupervisor")

  /*
    eagerSupervisor
      - child eagerActor
        - will die on start with ActorInitializationsException
        - trigger the supervision strategy in eagerSupervisor => STOP eagerActor
      - backoff will kick in after 1 second, 2s, 4, 8, 16
   */

}
