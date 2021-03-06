package latis.reader.tsml

import java.nio.ByteBuffer
import scala.collection.mutable.ListBuffer
import scala.io.Source
import latis.data.Data
import latis.dm.Integer
import latis.dm.Real
import latis.dm.Scalar
import latis.dm.Text
import latis.reader.tsml.ml.Tsml
import latis.util.StringUtils
import java.nio.ByteOrder

/**
 * Read a binary file via scala's Source which is not very efficient
 * since it involved character encoding.
 */
class BinaryAdapter(tsml: Tsml) extends IterativeAdapter[ByteBuffer](tsml) {
  
  private var source: Source = null 

  def getDataSource: Source = {
    if (source == null) source = Source.fromURL(getUrl, "ISO-8859-1") //TODO: is this safe? what about UTF-8?
    source
  }
  
  override def close {
    if (source != null) source.close
  }

  val order = getProperty("byteOrder", "big-endian") match {
    case "big-endian" => ByteOrder.BIG_ENDIAN
    case "little-endian" => ByteOrder.LITTLE_ENDIAN
  }
  
  lazy val blockSize = getOrigScalars.map(_.getSize).sum
  
  def getBuffer: ByteBuffer = {
    val it = getDataSource.toIterator
    val a = it.duplicate
    val buffer = ByteBuffer.allocate(a._2.length)
    for(c <- a._1) {
      buffer.put(c.toByte)
    }
    buffer.rewind.asInstanceOf[ByteBuffer].order(order)
  }
  
  def getRecordIterator: Iterator[ByteBuffer] = {
    splitBuffer(getBuffer).iterator
  }
  
  def splitBuffer(buffer: ByteBuffer): Seq[ByteBuffer] = {
    val seq = ListBuffer[ByteBuffer]()
    while(buffer.hasRemaining) {
      val arr = Array.ofDim[Byte](blockSize)
      buffer.get(arr)
      seq += ByteBuffer.wrap(arr).order(order)
    }
    seq.toSeq
  }
  
  def parseRecord(record: ByteBuffer): Option[Map[String, Data]] = {
    val vars = getOrigScalars
    val builder = ListBuffer[(String, Data)]()
    for(scalar <- vars) builder += extractPair(record, scalar)
    Some(builder.toMap[String, Data])
  }
  
  def extractPair(buffer: ByteBuffer, scalar: Scalar): (String, Data) = scalar match {
    case _: Integer => (scalar.getName, Data(buffer.getLong))
    case _: Real => (scalar.getName, Data(buffer.getDouble))
    case t: Text => {
      val sb = new StringBuilder
      for(a <- 0 until t.getSize/2) sb += buffer.getChar
      (scalar.getName, Data(StringUtils.padOrTruncate(sb.toString, t.length)))
    }
  }

}