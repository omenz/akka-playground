package part2actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import part2actors.ChildActorsExercise.WordCounterMaster.{WordCountReply, WordCountTask}

object ChildActorsExercise extends App {

  // Distributed word counting

  object WordCounterMaster {
    case class Initialize(nChildren: Int)
    case class WordCountTask(masterRef: ActorRef, text: String)
    case class WordCountReply(count: Int)
  }
  class WordCounterMaster extends Actor {
    import WordCounterMaster._
    override def receive: Receive = {
      case Initialize(nChildren) =>
        (1 to nChildren).foreach(num => context.actorOf(Props[WordCounterWorker], num.toString))
        println(context.children)
        context.become(handleWordCountTasks(1))
    }

    private def handleWordCountTasks(lastActorNum: Int): Receive = {
      case task: WordCountTask =>
       context.children.find(ref => ref.path.name == lastActorNum.toString) match {
         case Some(worker) =>
           println(s"[counter master] found worker: $worker")
           worker forward task
           val nextWorkerNumber = lastActorNum + 1
           if (context.children.size < nextWorkerNumber) context.become(handleWordCountTasks(1))
           else context.become(handleWordCountTasks(nextWorkerNumber))
         case None => println("could not find worker")
       }
      case WordCountReply(count) => println(s"[${sender()}] Word count is: $count")
    }
  }

  class WordCounterWorker extends Actor {
    override def receive: Receive = {
      case WordCountTask(masterRef, text) => masterRef ! WordCountReply(text.split(" ").length)
    }
  }

  /*
    create WordCounterMaster
    send Initialize(10) to wordCounterMaster
    send "Akka is awesome" to wordCounterMaster
      wcm will send a WordCountTask("...") to one of its children
        child replies with a WordCountReply(3) to the master
      master replies with 3 to the sender.

     requester -> wcm -> wcw
     requester <- wcm <-
   */

  // round robin logic (load balancing)
  // 1,2,3,4,5 and 7 tasks
  // 1,2,3,4,5,1,2

  import WordCounterMaster._
  val system = ActorSystem("wordCounters")
  val wordCounterMaster = system.actorOf(Props[WordCounterMaster])
  wordCounterMaster ! Initialize(3)
  wordCounterMaster ! WordCountTask(wordCounterMaster, "count this")
  wordCounterMaster ! WordCountTask(wordCounterMaster, "and this thing")
  wordCounterMaster ! WordCountTask(wordCounterMaster, "and one more thing")
  wordCounterMaster ! WordCountTask(wordCounterMaster, "now you should rotate")
  wordCounterMaster ! WordCountTask(wordCounterMaster, "and stop")

  Thread.sleep(1000)
  System.exit(0)
}
