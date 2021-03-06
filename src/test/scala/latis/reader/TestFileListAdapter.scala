package latis.reader

import org.junit._
import Assert._
import latis.reader.tsml.TsmlReader
import latis.writer.AsciiWriter
import latis.ops.filter.Selection
import java.io.File
import latis.util.FileUtils
import java.nio.file.Files
import java.nio.file.Paths

class TestFileListAdapter extends AdapterTests {

  def datasetName = "files"
  
  @Test
  @Ignore // ignore this test by default, because it takes a lot of time & system resources to run
  def large_nested_dir {
    try {
      // Note: this test intentionally uses the same dir methods as TestStreamingFileListAdapter
      // (that one was written first and then the test was ported here)
      val numFiles = TestStreamingFileListAdapter.populateLargeNestedTmpDir()
      val ds = TsmlReader("datasets/test/files_large_nested.tsml").getDataset()
      val data = ds.toStringMap
      assertEquals(numFiles, data("index").length)
    }
    finally {
      val dir = TestStreamingFileListAdapter.largeNestedTmpDir
      if (dir.exists()) {
        FileUtils.delete(dir)
      }
    }
  }
  
  @Test
  @Ignore // ignore this test by default, because it takes a lot of time & system resources to run
  def large_flat_dir {
    try {
      // Note: this test intentionally uses the same dir methods as TestStreamingFileListAdapter
      // (that one was written first and then the test was ported here)
      val numFiles = TestStreamingFileListAdapter.populateLargeFlatTmpDir()
      val ds = TsmlReader("datasets/test/files_large_flat.tsml").getDataset()
      val data = ds.toStringMap
      assertEquals(numFiles, data("index").length)
    }
    finally {
      val dir = TestStreamingFileListAdapter.largeFlatTmpDir
      if (dir.exists()) {
        FileUtils.delete(dir)
      }
    }
  }
  
  //@Test
  def test = writeDataset
  /*
   * files: (myTime -> (myInt, myReal, myText, file, fileSize))
   * 1970001 -> (1, 1.1, A, Foo1970001bar1v1.1A.dat, 4)
   * 1970002 -> (2, 2.2, B, Foo1970002bar2v2.2B.dat, 0)
   * 1970003 -> (3, 3.3, C, Foo1970003bar3v3.3C.dat, 0)
   */
  
  @Test
  def size {
    val ds = getDataset
    val data = ds.toDoubleMap
    assertEquals(4, data("fileSize")(0), 0)
    assertEquals(0, data("fileSize")(1), 0)
  }
}

object TestFileListAdapter {
  
  val tmpDir: File = {
    //val dir = new File(System.getProperty("java.io.tmpdir") + File.separator + "latis_file_test")
    //TODO: java.io.tmpdir ends with "/" on Mac, off by one when removing dir in FileUtils.list
    //  /var/folders/Jl/JlPXu2FoEs0GXqwaPMPc2++++TY/-Tmp-//latis_file_test
    val dir = new File("/tmp/latis_file_test")
    dir.mkdir
    dir
  }
  
  @BeforeClass
  def makeTmpFiles {
    //make sure this remains consistent with shared AdapterTests
    (new File(tmpDir, "Foo1970001bar1v1.1A.dat")).createNewFile
    Files.write(Paths.get(tmpDir.getPath,"Foo1970001bar1v1.1A.dat"),List(1,2,3,4).map(_.toByte).toArray)
    (new File(tmpDir, "Foo1970002bar2v2.2B.dat")).createNewFile
    (new File(tmpDir, "Foo1970003bar3v3.3C.dat")).createNewFile
    //test with non-matching file
    (new File(tmpDir, "junk")).createNewFile
    //(new File(tmpDir, "subDir")).mkdir
    //(new File("/tmp/latis_file_test/subDir/Foo1970004bar4v4.4D.dat")).createNewFile
  }
  
  @AfterClass
  def removeTmpFiles {
    FileUtils.delete(tmpDir)
  }
}