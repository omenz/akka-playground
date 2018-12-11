package part2actors

import akka.actor.{Actor, ActorSystem, Props}

object ActorsIntro extends App {

  // part1 - actor systems
  val actorSystem = ActorSystem("firstActorSystem")//ONLY ALPHANUMERIC NAMES!
  println(actorSystem.name)

  // part 2 - create actors
  // word count actor

  //it is fully encapsulated, you can't instantiate this class or access it in any way but sending the message
  class WordCountActor extends Actor {
    var totalWords = 0

    //return type: PartialFunction[Any, Unit]
    override def receive: Receive = {
      case message: String =>
        println(s"[word counter] I have received: $message")
        totalWords += message.split(" ").length
      case msg => println(s"[word counter] I cannot understand ${msg.toString}")
    }
  }

  // part3 - instantiate our actor
  val wordCounter = actorSystem.actorOf(Props[WordCountActor], "wordCounter")
  val anotherWordCounter = actorSystem.actorOf(Props[WordCountActor], "anotherWordCounter")// name must be unique

  // part4 - communicate!
  wordCounter ! "I am learning akka and it's pretty damn cool!"// "tell"
  anotherWordCounter ! "A different message"
  // asynchronous!

  //how to instantiate actor?
  //good practice: companion object with a factory method
  object Person {
    def props(name: String) = Props(new Person(name))
  }
  class Person(name: String) extends Actor {
    override def receive: Receive = {
      case "hi" => println(s"Hi, my name is $name")
      case _ =>
    }
  }
//  val person = actorSystem.actorOf(Props(new Person("Bob")))  also valid
  val person = actorSystem.actorOf(Person.props("Bob"))
  person ! "hi"
}
