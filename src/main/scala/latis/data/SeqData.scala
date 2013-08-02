package latis.data

import scala.collection._
import java.nio.ByteBuffer

//abstract class SeqData(val seq: Seq[Data]) extends Data {
abstract class SeqData extends Data { //TODO: extends Seq[Data]?
  //def value = seq
//  override def iterator = seq.iterator
  //def isEmpty = length == 0
//  def length = seq.length
}

case class DoubleSeqData(ds: immutable.Seq[Double]) extends SeqData {
  
  def getByteBuffer: ByteBuffer = ds.foldLeft(ByteBuffer.allocate(size))(_.putDouble(_))
  
  def getDouble: Option[Double] = length match {
    case 0 => None
    case 1 => Some(ds(0))
    case _ => Some(Double.NaN)
  }
  
  def getString: Option[String] = ???

  
  def length = ds.length
  def recordSize = 8
  
  def iterator = ds.iterator.map(DoubleValue(_))
}

//inner, faster varying array is over Tuple elements
//assume doubles for now
//case class TupleSeqData(ts: Seq[Seq[Double]]) extends SeqData {
//  def doubleValue = ts(0)(0) //TODO: error?
//  def recordSize = 8 * ts(0).length
//  def length = ts.length
//  def iterator = ts.map(DoubleValue(_)).iterator
//}


object SeqData {
  import scala.collection.Seq //Allow mutable inputs, convert to immutable for construction.
  
  def apply(ds: Seq[Double]): SeqData = DoubleSeqData(ds.toIndexedSeq)
  
//  def apply(ds1: Seq[Double], ds2: Seq[Double]): SeqData = {
//    TupleSeqData((ds1 zip ds2).map(p => Vector(p._1, p._2)).toIndexedSeq)
//  }
  
  //TODO: def apply(ds1: Seq[Double], ds2: Seq[Double]*): SeqData 
}