package latis.data.seq

import latis.data.IterableData
import latis.data.value.StringValue
import latis.util.StringUtils

import java.nio.ByteBuffer

import scala.collection.Seq
import scala.collection.immutable

case class StringSeqData(ss: immutable.Seq[String], textLength: Int) extends IterableData {
  
  override def getByteBuffer: ByteBuffer = {
    val bb = ByteBuffer.allocate(size)
    //Fix length of strings
    val ss2 = ss.map(StringUtils.padOrTruncate(_, textLength))
    val cb = ss2.foldLeft(bb.asCharBuffer())(_.put(_)).rewind
    bb
  }
  
  override def length: Int = ss.length //number of samples

  def recordSize: Int = textLength * 2 //2 bytes per char
  
  def iterator: Iterator[StringValue] = ss.iterator.map(s => StringValue(StringUtils.padOrTruncate(s, textLength)))
  
  def apply(index: Int): StringValue = StringValue(ss(index))
}

object StringSeqData {
  def apply(ss: Seq[String]): StringSeqData = {
    val length = ss.map(_.length).max
    StringSeqData(ss.toIndexedSeq, length)
  }
}