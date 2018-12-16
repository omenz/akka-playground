package part2actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import part2actors.ChangingActorBehavior.Mom.MomStart

object ChangingActorBehavior extends App {

  object FussyKid {
    case object KidAccept
    case object KidReject
    val HAPPY = "HAPPY"
    val SAD = "sad"
  }
  class FussyKid extends Actor {
    import FussyKid._
    import Mom._
    var state = HAPPY
    override def receive: Receive = {
      case Food(VEGETABLE) => state = SAD
      case Food(CHOCOLATE) => state = HAPPY
      case Ask(_) =>
        if (state == HAPPY) sender() ! KidAccept
        else sender() ! KidReject
    }
  }

  class StatelessFussyKid extends Actor {
    import FussyKid._
    import Mom._

    override def receive: Receive = happyReceive

    def happyReceive: Receive = {
      case Food(VEGETABLE) => context.become(sadReceive)
      case Food(CHOCOLATE) =>
      case Ask(_) => sender() ! KidAccept
    }
    def sadReceive: Receive = {
      case Food(VEGETABLE) => // stay sad
      case Food(CHOCOLATE) => context.become(happyReceive)// change my receive handler to happyReceive
      case Ask(_) => sender() ! KidReject
    }
  }

  class StatelessFussyKid2 extends Actor {
    import FussyKid._
    import Mom._

    override def receive: Receive = happyReceive

    def happyReceive: Receive = {
      case Food(VEGETABLE) => context.become(sadReceive, false)
      case Food(CHOCOLATE) =>
      case Ask(_) => sender() ! KidAccept
    }
    def sadReceive: Receive = {
      case Food(VEGETABLE) => context.become(sadReceive, false)
      case Food(CHOCOLATE) => context.unbecome()// change my receive handler to happyReceive
      case Ask(_) => sender() ! KidReject
    }
  }

  object Mom {
    case class MomStart(kidRef: ActorRef)
    case class Food(food: String)
    case class Ask(message: String)// do you want to play?
    val VEGETABLE = "veggies"
    val CHOCOLATE = "chocolate"
  }
  class Mom extends Actor {
    import FussyKid._
    import Mom._
    override def receive: Receive = {
      case MomStart(kidRef) =>
        // tes our interaction
        kidRef ! Food(VEGETABLE)
        kidRef ! Ask("do you want to play?")
      case KidAccept => println("Yay, my kid is happy!")
      case KidReject => println("My kid is sad, but at least he's healthy!")
    }
  }

  val system = ActorSystem("changingActorBehaviorDemo")
  val fussyKid = system.actorOf(Props[FussyKid])
  val mom = system.actorOf(Props[Mom])

//  mom ! MomStart(fussyKid)

  val statelessFussyKid = system.actorOf(Props[StatelessFussyKid])
  mom ! MomStart(statelessFussyKid)

  /*
    mom receives MomStart
      kid receives Food(veg) -> kid will change the handler to sadReceive
      kid receives Ask("play?") -> kid replies with a sadReceive handler
    mom receives KidReject
   */

  /*
     StatelessFussyKid2 uses a behavior stack
     new behavior
     Food(veg)
     Food(veg)
     Food(chocolate)

     Stack on new behavior:
     1. sadReceive
     2. sadReceive
     3. happyReceive

     Stack after new behavior:
     1. sadReceive, one sad receive was popped off the stack
     2. happyReceive
   */

  /**
    * Exercises:
    * 1 - recreate the Counter Actor with context.become without a mutable state, already done in ActorCapabilities
    * 2 - simplified voting system
    */
  case class Vote(candidate: String)
  case object VoteStatusRequest
  case class VoteStatusReply(candidate: Option[String])
  class Citizen extends Actor {
    override def receive: Receive = canVote

    private def canVote: Receive = {
      case vote: Vote => context.become(hasVoted(vote))
      case VoteStatusRequest => sender() ! VoteStatusReply(None)
    }

    private def hasVoted(vote: Vote): Receive = {
      case _: Vote => println("already voted")
      case VoteStatusRequest => sender() ! VoteStatusReply(Some(vote.candidate))
    }
  }

  case class AggregateVotes(citizens: Set[ActorRef])
  class VoteAggregator extends Actor {
    override def receive: Receive = readyToAggregate

    private def readyToAggregate: Receive = {
      case AggregateVotes(citizens) =>
        context.become(aggregating(Map(), citizens))
        citizens.foreach(_ ! VoteStatusRequest)
    }

    private def aggregating(report: Map[String, Int], stillWaitingCitizens: Set[ActorRef]): Receive = {
      case AggregateVotes(_) => println("[vote aggregator] Aggregation in progress, please wait!")
      case VoteStatusReply(Some(vote)) =>
        println(s"[vote aggregator] Vote accepted $vote")
        val existingVotes = report.getOrElse(vote, 0)
        val updatedReport = report ++ Map(vote -> (existingVotes + 1))
        val newStillWaitingCitizens = stillWaitingCitizens - sender()
        if (newStillWaitingCitizens.isEmpty) {
          println(s"[vote aggregator] Vote result: $updatedReport")
          context.become(readyToAggregate)
        } else {
          context.become(aggregating(updatedReport, newStillWaitingCitizens))
        }
      case VoteStatusReply(None) => sender() ! VoteStatusRequest // potential infinite loop
    }
  }

  val alice = system.actorOf(Props[Citizen])
  val bob = system.actorOf(Props[Citizen])
  val charlie = system.actorOf(Props[Citizen])
  val daniel = system.actorOf(Props[Citizen])

  alice ! Vote("Martin")
  bob ! Vote("Jonas")
  charlie ! Vote("Roland")
  daniel ! Vote("Roland")

  val voteAggregator = system.actorOf(Props[VoteAggregator])
  voteAggregator ! AggregateVotes(Set(alice, bob, charlie, daniel))
  voteAggregator ! AggregateVotes(Set(alice, bob, charlie, daniel))
  voteAggregator ! AggregateVotes(Set(alice, bob, charlie, daniel))

  /*
    Print the status of the votes:
    Martin -> 1
    Jonas -> 1
    Roland -> 2
   */
  Thread.sleep(1000)
  System.exit(0)
}
