package latis.writer

import org.junit._
import Assert._
import latis.reader.tsml.TsmlReader
import java.io.File
import java.io.FileOutputStream

class TestImageWriter {

  //@Test
  def testPlot{
    val file = new File("/tmp/latis_image_writer_test.png")
    val fos = new FileOutputStream(file)
    val ds = TsmlReader("datasets/test/international_sunspot_number.tsml").getDataset
    Writer(fos, "png").write(ds)
    fos.close()
  }

}