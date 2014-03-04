package latis.reader.tsml

import latis.dm._
import scala.io.Source
import scala.collection._
import latis.data.Data
import latis.util.Util
import java.nio.ByteBuffer
import latis.data.IterableData
import latis.time.Time
import latis.reader.tsml.ml.Tsml
import latis.util.PeekIterator

class IterativeAsciiAdapter(tsml: Tsml) extends IterativeAdapter(tsml) with AsciiAdapterHelper {
  
  def makeIterableData(sampleTemplate: Sample): Data = new IterableData {
    def recordSize = sampleTemplate.getSize
    
    def iterator = new PeekIterator[Data] {
      val it = getRecordIterator
      
      //keep going till we find a valid record or run out
      def getNext: Data = {
        if (it.hasNext) {
          val record = it.next
          val svals = parseRecord(record)
          if (svals.isEmpty) getNext
          else makeDataFromRecord(sampleTemplate, svals)
        } else null
      }
    }
  }
  
  /**
   * Counter in case we have an Index domain.
   */
  private var index = -1
  
  //TODO: compare to Util dataToVariable... move this there?
  def makeDataFromRecord(sampleTemplate: Sample, svals: Map[String, String]): Data = {
    //build a ByteBuffer
    val size = sampleTemplate.getSize
    val bb = ByteBuffer.allocate(size)
    
    //assume every Scalar in the template has a value in the Map, e.g. not stored by Tuple name
    //get Seq of Scalars from template
    val vars = sampleTemplate.toSeq 
    
    for (v <- vars) {
      //val s = svals(v.getName) //string value for the given scalar
      v match {
        case _: Index   => index += 1; bb.putInt(index) //deal with index domain (defined in tsml)
        case _: Real    => bb.putDouble(svals(v.getName).toDouble)
        case _: Integer => bb.putLong(svals(v.getName).toLong)
        case t: Text    => {
          val l = t.length
          val s = svals(v.getName)
          val padded = "%"+t.length+"s" format s //pad to the Text variable's defined length
          padded.foldLeft(bb)(_.putChar(_)) //fold each character into buffer
        }
      }
    }
    
    //rewind for use
    Data(bb.flip.asInstanceOf[ByteBuffer])
  }

}