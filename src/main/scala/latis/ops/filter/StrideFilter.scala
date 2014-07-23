package latis.ops.filter

import latis.dm.Function
import latis.dm.Sample
import latis.metadata.Metadata
import latis.ops.OperationFactory
import latis.util.MappingIterator


/**
 * Take every "stride-th" element of a Function in the Dataset.
 */
class StrideFilter(val stride: Int) extends Filter {
  
  override def applyToFunction(function: Function) = {
    val it = new MappingIterator(function.iterator.grouped(stride).map(_(0)), (s: Sample) => this.applyToSample(s))
//    val nlen = function.getLength match {
//      case -1 => throw new Exception("Function has undefined length")
//      case 0 => 0
//      case n => ((n - 1) / stride) + 1
//    }
//    val md = Metadata(function.getMetadata.getProperties + ("length" -> nlen.toString))

    //TODO: change 'length' metadata
    Some(Function(function.getDomain, function.getRange, it, function.getMetadata))
  }
  
}

object StrideFilter extends OperationFactory {
  
  override def apply(args: Seq[String]): StrideFilter = {
    if (args.length > 1) throw new UnsupportedOperationException("The StrideFilter accepts only one argument")
    try {
      StrideFilter(args.head.toInt)
    } catch {
      case e: NumberFormatException => throw new UnsupportedOperationException("The StrideFilter requires an integer argument")
    }
  }
    
  def apply(stride: Int): StrideFilter = new StrideFilter(stride)
}
