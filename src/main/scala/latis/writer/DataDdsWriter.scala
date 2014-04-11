package latis.writer

import latis.dm._
import java.nio.ByteBuffer
import java.io.DataOutputStream
import java.io.PrintWriter

class DataDdsWriter extends BinaryWriter {
  
  private[this] lazy val writer = new DataOutputStream(getOutputStream)

  final val START_OF_INSTANCE: Array[Byte] = Array(0x5A.toByte, 0, 0, 0)

  final val END_OF_SEQUENCE: Array[Byte] = Array(0xA5.toByte, 0, 0, 0)

  override def write(dataset: Dataset) {
    writeHeader(dataset)
    dataset.getVariables.map(writeVariable(_))
    writer.flush()
  }
  
  def writeHeader(dataset: Dataset) = {
    val w = new DdsWriter()
    val s = w.makeHeader(dataset) + dataset.getVariables.map(w.varToString(_)).mkString("") + w.makeFooter(dataset) + "\nData:\n"
    writer.write(s.getBytes)    
  }

  override def writeVariable(variable: Variable) = variable match {
    case f: Function => {
      for (sample <- f.iterator){
        writer.write(START_OF_INSTANCE)
        writer.write(varToBytes(sample))
      }
      writer.write(END_OF_SEQUENCE)
    }
    case _ => {
      writer.write("\n".getBytes)
      writer.write(varToBytes(variable))
    }
  }

  override def buildFunction(function: Function, bb: ByteBuffer): ByteBuffer = {
     writeVariable(function)
     bb
  }

}