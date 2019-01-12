package part6patterns

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Cancellable, FSM, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, OneInstancePerTest, WordSpecLike}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

class FSMSpec extends TestKit(ActorSystem("FSMSpec"))
  with ImplicitSender with WordSpecLike with BeforeAndAfterAll
  with OneInstancePerTest {  // one instance per test helps to avoid preserving test kit state between tests

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import FSMSpec._

  def runTestSuite(props: Props): Unit = {
    "error when not initialized" in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! RequestProduct("coke")
      expectMsg(VendingError("MachineNotInitialized"))
    }
    "report a product not available" in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! Initialize(Map("coke" -> 10), Map("coke" -> 1))
      vendingMachine ! RequestProduct("sandwich")
      expectMsg(VendingError("ProductNotAvailable"))
    }
    "throw a timeout if I don't insert money" in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! Initialize(Map("coke" -> 10), Map("coke" -> 1))
      vendingMachine ! RequestProduct("coke")
      expectMsg(Instruction("Please insert 1 dollars"))

      within(1.5 seconds) {
        expectMsg(VendingError("RequestTimedOut"))
      }
    }
    "handle the reception of partial money" in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! Initialize(Map("coke" -> 10), Map("coke" -> 3))
      vendingMachine ! RequestProduct("coke")
      expectMsg(Instruction("Please insert 3 dollars"))

      vendingMachine ! ReceiveMoney(1)
      expectMsg(Instruction("Please insert 2 dollars"))

      within(1.5 seconds) {
        expectMsg(VendingError("RequestTimedOut"))
        expectMsg(GiveBackChange(1))
      }
    }
    "deliver the product if I insert all the money" in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! Initialize(Map("coke" -> 10), Map("coke" -> 3))
      vendingMachine ! RequestProduct("coke")
      expectMsg(Instruction("Please insert 3 dollars"))

      vendingMachine ! ReceiveMoney(3)
      expectMsg(Deliver("coke"))
    }
    "give back change and be able to request money for a new product" in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! Initialize(Map("coke" -> 10), Map("coke" -> 3))
      vendingMachine ! RequestProduct("coke")
      expectMsg(Instruction("Please insert 3 dollars"))

      vendingMachine ! ReceiveMoney(5)
      expectMsg(Deliver("coke"))
      expectMsg(GiveBackChange(2))

      vendingMachine ! RequestProduct("coke")
      expectMsg(Instruction("Please insert 3 dollars"))
    }
  }

  "A vending machine" should {
    runTestSuite(Props[VendingMachine])
  }

  "A vending machine FSM" should {
    runTestSuite(Props[VendingMachineFSM])
  }
}

object FSMSpec {

  /*
    Vending machine
   */
  case class Initialize(inventory: Map[String, Int], prices: Map[String, Int])
  case class RequestProduct(product: String)

  case class Instruction(instruction: String) // message the VM will show on its "screen"
  case class ReceiveMoney(amount: Int)
  case class Deliver(product: String)
  case class GiveBackChange(amount: Int)

  case class VendingError(reason: String)
  case object ReceiveMoneyTimeout

  class VendingMachine extends Actor with ActorLogging {
    implicit val ec: ExecutionContext = context.dispatcher

    override def receive: Receive = idle

    def idle: Receive = {
      case Initialize(inventory, prices) => context.become(operational(inventory, prices))
      case _ => sender() ! VendingError("MachineNotInitialized")
    }

    def operational(inventory: Map[String, Int], prices: Map[String, Int]): Receive = {
      case RequestProduct(product) => inventory.get(product) match {
        case None | Some(0) =>
          sender() ! VendingError("ProductNotAvailable")
        case Some(_) =>
          val price = prices(product)
          sender() ! Instruction(s"Please insert $price dollars")
          context.become(waitForMoney(
            inventory = inventory,
            prices = prices,
            product = product,
            receivedMoney = 0,
            moneyTimeoutSchedule = startReceiveMoneyTimeoutSchedule,
            requester = sender()))
      }
    }

    def waitForMoney(inventory: Map[String, Int],
                     prices: Map[String, Int],
                     product: String,
                     receivedMoney: Int,
                     moneyTimeoutSchedule: Cancellable,
                     requester: ActorRef,
                    ): Receive = {
      case ReceiveMoneyTimeout =>
        requester ! VendingError("RequestTimedOut")
        if (receivedMoney > 0) requester ! GiveBackChange(receivedMoney)
        context.become(operational(inventory, prices))
      case ReceiveMoney(amount) =>
        moneyTimeoutSchedule.cancel()
        val price = prices(product)
        if (receivedMoney + amount >= price) {
          // user buys product
          requester ! Deliver(product)
          // deliver the change
          if (receivedMoney + amount - price > 0) requester ! GiveBackChange(receivedMoney + amount - price)
          // updating inventory
          val newStock = inventory(product) - 1
          val newInventory = inventory + (product -> newStock)
          context.become(operational(newInventory, prices))
        } else {
          val remainingMoney = price - receivedMoney - amount
          requester ! Instruction(s"Please insert $remainingMoney dollars")
          context.become(waitForMoney(
            inventory = inventory, prices = prices, product = product, // don't change
            receivedMoney = receivedMoney + amount, // user has inserted some money
            moneyTimeoutSchedule = startReceiveMoneyTimeoutSchedule, // I need to set the timeout again
            requester = requester
          ))
        }
    }

    def startReceiveMoneyTimeoutSchedule: Cancellable = context.system.scheduler.scheduleOnce(1 second) {
      self ! ReceiveMoneyTimeout
    }
  }

  // step 1 - define the states and the date of the actor
  trait VendingState
  case object Idle extends VendingState
  case object Operational extends VendingState
  case object WaitForMoney extends VendingState

  trait VendingData
  case object Uninitialized extends VendingData
  case class Initialized(inventory: Map[String, Int], prices: Map[String, Int]) extends VendingData
  case class WaitingForMoneyData(inventory: Map[String, Int],
                                 prices: Map[String, Int],
                                 product: String,
                                 receivedMoney: Int,
                                 requester: ActorRef,
                                ) extends VendingData

  class VendingMachineFSM extends FSM[VendingState, VendingData] {
    // we don't have a receive handler

    // an EVENT(message, data)

    /*
      state, data

      event => state and data can be changed

      state = Idle
      data = Uninitialized

      event(Initialize(Map(coke -> 10), Map(coke -> 1)))
       => state Operational
       data = Initialized(inventory, prices)

      event(RequestProduct(Coke))
        =>
        state = WaitForMoney
        data = WaitForMoneyData(Map(coke -> 10), Map(coke -> 1)), coke, 0, R)

      event(ReceiveMoney(2))
        =>
        state = Operational
        data = Initialized(Map(coke -> 10), Map(coke -> 1))

     */

    startWith(Idle, Uninitialized)

    when(Idle) {
      case Event(Initialize(inventory, prices), Uninitialized) =>
        // equivalent with context.become(operational(inventory, prices))
        goto(Operational) using Initialized(inventory, prices)
      case _ =>
        sender() ! VendingError("MachineNotInitialized")
        stay()
    }

    when(Operational) {
      case Event(RequestProduct(product), Initialized(inventory, prices)) =>
        inventory.get(product) match {
          case None | Some(0) =>
            sender() ! VendingError("ProductNotAvailable")
            stay()
          case Some(_) =>
            val price = prices(product)
            sender() ! Instruction(s"Please insert $price dollars")
            goto(WaitForMoney) using WaitingForMoneyData(inventory = inventory,
              prices = prices,
              product = product,
              receivedMoney = 0,
              requester = sender())
        }
    }
    
    when(WaitForMoney, stateTimeout = 1 second) {
      case Event(StateTimeout, WaitingForMoneyData(inventory, prices, product, receivedMoney, requester)) =>
        requester ! VendingError("RequestTimedOut")
        if (receivedMoney > 0) requester ! GiveBackChange(receivedMoney)
        goto(Operational) using Initialized(inventory, prices)
      case Event(ReceiveMoney(amount), WaitingForMoneyData(inventory, prices, product, receivedMoney, requester)) =>
        val price = prices(product)
        if (receivedMoney + amount >= price) {
          // user buys product
          requester ! Deliver(product)
          // deliver the change
          if (receivedMoney + amount - price > 0) requester ! GiveBackChange(receivedMoney + amount - price)
          // updating inventory
          val newStock = inventory(product) - 1
          val newInventory = inventory + (product -> newStock)
          goto(Operational) using Initialized(newInventory, prices)
        } else {
          val remainingMoney = price - receivedMoney - amount
          requester ! Instruction(s"Please insert $remainingMoney dollars")

          stay() using WaitingForMoneyData(
            inventory = inventory, prices = prices, product = product, // don't change
            receivedMoney = receivedMoney + amount, // user has inserted some money
            requester = requester
          )
        }
    }

    whenUnhandled {
      case Event(_, _) =>
        sender() ! VendingError("CommandNotFound")
        stay()
    }

    onTransition {
      case stateA -> stateB => log.info(s"Transitioning from $stateA to $stateB")
    }

    initialize()
  }
}