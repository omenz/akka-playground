package part1recap

object AdvancedRecap extends App {

  // PartialFunction[Int, Int]
   //same as (x: Int) => x match {}
   val partialFunction: PartialFunction[Int, Int] = {
     case 1 => 33
     case 2 => 44
   }

  // lifting
  var lifted = partialFunction.lift // total function Int => Option[Int]

  lifted(2) // Some(65)
  lifted(5000) // None, without lifting will return a pattern match error

  // orElse
  val partialFunctionChain = partialFunction.orElse[Int, Int] {
    case 60 => 6000
  }

  partialFunctionChain(1) // 33
  partialFunctionChain(60) //6000
  partialFunctionChain(123) // throw a MatchError

  
}
