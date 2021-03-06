package latis.ops.math

abstract class BinOp extends Function2[Double, Double, Double]

//TODO: see scala.math.Numeric.Ops

class Add      extends BinOp {def apply(a: Double, b: Double): Double = a + b}
class Subtract extends BinOp {def apply(a: Double, b: Double): Double = a - b}
class Multiply extends BinOp {def apply(a: Double, b: Double): Double = a * b}
class Divide   extends BinOp {def apply(a: Double, b: Double): Double = a / b}
class Modulo   extends BinOp {def apply(a: Double, b: Double): Double = a % b}
class Power    extends BinOp {def apply(a: Double, b: Double): Double = Math.pow(a, b)}
class Lt       extends BinOp {def apply(a: Double, b: Double): Double = (a < b) match {case true => 1; case false => 0}}
class And      extends BinOp {def apply(a: Double, b: Double): Double = (a > 0 && b > 0) match {case true => 1; case false => 0}}

object BinOp {
  val ADD      = new Add()
  val SUBTRACT = new Subtract()
  val MULTIPLY = new Multiply()
  val DIVIDE   = new Divide()
  val MODULO   = new Modulo()
  val POWER    = new Power()
  val LT       = new Lt()
  val AND      = new And()
}
