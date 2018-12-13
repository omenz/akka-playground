package part2actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import part2actors.ActorCapabilities.Account.{InteractWithAccount, Deposit, Withdraw, Statement}
import part2actors.ActorCapabilities.Counter.{Decrement, Increment, Print}

object ActorCapabilities extends App {

  class SimpleActor extends Actor {
    override def receive: Receive = {
      case "Hi!" => sender() ! "Hello, there!" // or context.sender(), replying to a message
      case message: String => println(s"[${context.self.path}] I have received: $message")
      case number: Int => println(s"[simple actor] I have received a NUMBER: $number")
      case SpecialMessage(contents) => println(s"[simple actor] I have received something SPECIAL: $contents")
      case SendMessageToYourself(content) => self ! content
      case SayHiTo(ref) => ref ! "Hi!"
      case WirelessPhoneMessage(content, ref) => ref forward (content + "s") // I keep the original sender of WirelessPhoneMessage
    }
  }

  val system = ActorSystem("actorCapabilitiesDemo")
  val simpleActor = system.actorOf(Props[SimpleActor], "simpleActor")

  simpleActor ! "hello, actor"

  // 1 - messages can be of any type
  // a) messages must be IMMUTABLE
  // b) messages must be SERIALIZABLE
  // in practice use case classes and case objects
  simpleActor ! 42 // who is the sender?

  case class SpecialMessage(contents: String)
  simpleActor ! SpecialMessage("some special message")

  // 2 - actors have information about their context and them selves
  // context.self === `this` in OOP

  case class SendMessageToYourself(content: String)
  simpleActor ! SendMessageToYourself("I am an actor and I am proud of it!")

  // 3 - actors can REPLY to messages
  val alice = system.actorOf(Props[SimpleActor], "alice")
  val bob = system.actorOf(Props[SimpleActor], "bob")

  case class SayHiTo(ref: ActorRef)
  alice ! SayHiTo(bob)

  // 4 - dead letters
  alice ! "Hi!" // reply to "me", goes straight to dead letters

  // 5 - forwarding messages
  // D -> A -> B
  // forwarding = sending a message with the ORIGINAL sender

  case class WirelessPhoneMessage(content: String, ref: ActorRef)
  alice ! WirelessPhoneMessage("Hi", bob) //noSender


  /**
    * Exercises
    * 1. a Counter actor, holds an internal variable
    *  - Increment
    *  - Decrement
    *  - Print
    *
    *  2. a Bank account as an actor
    *  receives
    *  - Deposit an amount
    *  - Withdraw an amount
    *  - Statement
    *  replies with
    *  - Success
    *  - Failure
    *
    *  interact with some other kind of actor
    */

  // 1.
  class Counter extends Actor {
    private val counter = 0
    override def receive: Receive = onMessage(counter)

    private def onMessage(counter: Int): Receive = {
      case Increment => context.become(onMessage(counter + 1))
      case Decrement => context.become(onMessage(counter - 1))
      case Print => println(s"[counter actor] count is: $counter")
    }
  }

  object Counter {
    case object Increment
    case object Decrement
    case object Print
  }

  val counter = system.actorOf(Props(new Counter))
  counter ! Increment
  counter ! Increment
  counter ! Decrement
  counter ! Print

  // 2.
  object Account {
    sealed trait Operation
    final case class Deposit(amount: BigDecimal) extends Operation
    final case class Withdraw(amount: BigDecimal) extends Operation
    final case object Statement extends Operation

    case class Success(msg: String)
    case class Failure(msg: String)

    case class InteractWithAccount(ref: ActorRef, operation: Operation)
  }

  class Account extends Actor {
    import part2actors.ActorCapabilities.Account._
    private val balance: BigDecimal = 0
    override def receive: Receive = onMessage(balance)

    private def onMessage(balance: BigDecimal): Receive = {
      case Deposit(amount) =>
        context.become(onMessage(balance + amount))
        sender() ! Success(s"Deposit is successful, new balance: ${balance + amount}!")
      case Withdraw(amount) =>
        if (balance > amount) {
          context.become(onMessage(balance - amount))
          sender() ! Success(s"Withdrawal is successful, new balance: ${balance - amount}!")
        } else {
          sender() ! Failure(s"Your balance is too low $balance")
        }
      case Statement =>
        sender() ! Success(s"Your balance is: $balance")
    }
  }

  class BankClient extends Actor {
    import part2actors.ActorCapabilities.Account._
    override def receive: Receive = {
      case InteractWithAccount(ref, operation) => ref ! operation
      case Success(msg) => println(s"Success! A bank has replied with: $msg")
      case Failure(msg) => println(s"Failure! A bank has replied with: $msg")
    }
  }

  val account = system.actorOf(Props(new Account))
  val bankClient = system.actorOf(Props(new BankClient))

  bankClient ! InteractWithAccount(account, Deposit(35.99))
  bankClient ! InteractWithAccount(account, Withdraw(15.99))
  bankClient ! InteractWithAccount(account, Statement)
  bankClient ! InteractWithAccount(account, Withdraw(105.99))
  bankClient ! InteractWithAccount(account, Statement)
  bankClient ! InteractWithAccount(account, Deposit(99.99))

  Thread.sleep(1000)
  System.exit(0)
}
