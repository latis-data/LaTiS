package latis.ops.filter

import latis.dm.Function
import latis.ops.OperationFactory
import latis.dm.Sample
import latis.dm.Scalar
import latis.dm.Variable
import latis.dm.Tuple
import latis._
import latis.metadata.Metadata
import scala.collection.mutable.ArrayBuffer

/**
 * Return the sample containing the max scalar of any outer Function in the Dataset.
 */
class MaxFilter(name: String) extends Filter {
  
  var currentMax = Double.MinValue //Arbitrary assignment
  var keepSamples = ArrayBuffer[Sample]()
  
  override def applyToFunction(function: Function) = {
    //Apply Operation to every sample from the iterator
    function.iterator.foreach(applyToSample(_))
    
    //-----Dumb Testing-----------------------------------------------------------------------------
    
//    val testSamples = function.iterator.take(2)             //First two samples in DS
//    val scalar1 = testSamples.next.findVariableByName(name) //Named variable from 1st sample
//    val scalar2 = testSamples.next.findVariableByName(name) //Named variable from 2nd sample
//                                                               
//    scalar1 match {                                              
//      case Some(scalar1) => println("scalar1: " + scalar1.asInstanceOf[Scalar].getValue)
//      case None =>          println("Scalar " + name + " not found.")
//    }                                                             
//    scalar2 match {                                                 
//      case Some(scalar2) => println("scalar2: " + scalar2.asInstanceOf[Scalar].getValue)
//      case None =>          println("Scalar " + name + " not found.")
//    }                                                       
//                                                                   
//    if (scalar1.isDefined && scalar2.isDefined) {           //If they exist, compare Scalars
//      val compare = scalar1.get.asInstanceOf[Scalar].compare(scalar2.get.asInstanceOf[Scalar]) match {
//      case compare if compare > 0 => println("sample1 is greater than.")
//      case compare if compare < 0 => println("sample1 is less than.")
//      case 0 =>                      println("The two are equal.")
//      } 
//    }
     
    //----Normal Code Below--------------------------------------------------------------------------
    
    //change length of Function in metadata
    //mdSquash does nothing but squash a bug; if samples isn't used like this nothing works for some reason...
    //val mdSquash = Metadata(function.getMetadata.getProperties + ("length" -> samples.length.toString))
    val md = Metadata(function.getMetadata.getProperties + ("length" -> keepSamples.length.toString))
    
    //make the new function with the updated metadata
    keepSamples.length match {
      case 0 => Some(Function(function.getDomain, function.getRange, Iterator.empty, md)) //empty Function with type of original
      //case _ => Some(Function(function.getDomain, function.getRange, samples, md))
      case _ => Some(Function(keepSamples, md))
    }
  }
  
  /**
   * Apply Operation to a Sample
   * note to Shawn: if Scalar "name" is not in a sample, make that empty function.
   */
  override def applyToSample(sample: Sample): Option[Sample] = {
 println("Applying to Sample!")
      //val x = sample.getVariables.map(applyToVariable(_)) 
      val x = sample.getVariables.map(applyToVariable(_)) 
      x.find(_.isEmpty) match { //Watch this. Assuming it throws away bad variables, thus bad samples.
        case None => {          //FOUND A PROBLEM! Even when a Scalar has been discarded from the sample, this block of code is executed; Sample NOT discarded.
          val s = Sample(sample.domain, sample.range)
          keepSamples += s
          println("keepSamples: " + keepSamples)
          if (keepSamples(0).findVariableByName(name).isDefined)
            println("keepSamples[name]: " + keepSamples(0).findVariableByName(name).get.getData)
          Some(s)
        }
        case Some(_) => None    //found an invalid variable, exclude the entire sample
      }
  }
  
  /**
   * Apply Operation to a Variable, depending on type
   */
  override def applyToVariable(variable: Variable): Option[Variable] = variable match {
    case scalar: Scalar     =>  println("Applying to Scalar Variable!"); applyToScalar(scalar)
    case sample: Sample     =>  println("Applying to Sample Variable!"); applyToSample(sample)
    case tuple: Tuple       =>  println("Applying to Tuple!"); applyToTuple(tuple)
    case function: Function => applyToFunction(function)
  }
  
  /**
   * Apply Operation to a Tuple
   */
  override def applyToTuple(tuple: Tuple): Option[Tuple] = {
    val x = tuple.getVariables.map(applyToVariable(_))
    x.find(_.isEmpty) match{
      case Some(_) => None //found an invalid variable, exclude the entire tuple
      case None => Some(Tuple(x.map(_.get), tuple.getMetadata))
    }
  }

  /**
   * Apply Operation to a Scalar
   */
  override def applyToScalar(scalar: Scalar): Option[Scalar] = {
		//If the filtering causes an exception, log a warning and return None.
		try {
			scalar match {

			  case s: Scalar => if (scalar.hasName(name)) { println("scalar has name");
				  val scalarValue: Double = s.getValue.asInstanceOf[Double]
						if (scalarValue > currentMax) {
						 //Found a new max value, so initiate grand master plan...
						 println("scalarValue greater than currentMax!")
						 keepSamples.clear 
						 currentMax = scalarValue
						 println("currentMax: " + currentMax)
						 Some(scalar) 

					  }
						else if (scalarValue == currentMax) {
				      //Keep the sample
			        println("scalarValue equals currentMax!")
				      Some(scalar)
			      }
			      else {
				      //Trash this sample! 
			        println("scalarValue less than currentMax!") 
				      None
			      }
			    } else { println("scalar doesn't have name, its: " + scalar.getName); Some(scalar) //no-op
			      
			    }
		    }
		  } catch {
		  case e: Exception => {
		   //logger.warn("Selection filter threw an exception: " + e.getMessage)
			  None
		  }
	  }
  }
}

object MaxFilter extends OperationFactory {
  def apply(name: String): MaxFilter = new MaxFilter(name)
}
