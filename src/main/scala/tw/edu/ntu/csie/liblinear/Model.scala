package tw.edu.ntu.csie.liblinear

import java.io.FileOutputStream
import java.io.FileInputStream
import java.io.ObjectOutputStream
import java.io.ObjectInputStream
import scala.Predef
import tw.edu.ntu.csie.liblinear.SolverType._
import org.jblas.DoubleMatrix

/** 
 * A linear model stores weights and other information.
 *
 *@param param user-specified parameters
 *@param w the weights of features
 *@param nrClass the number of classes
 *@param bias the value of user-specified bias
 */
class Model(val param : Parameter, labelSet : Array[Double]) extends Serializable
{
	var label : Array[Double] = labelSet.sortWith(_ < _)
	val nrClass : Int = label.size
	var w : Array[DoubleMatrix] = new Array(nrClass)
	var bias : Double = -1.0

	def setBias(b : Double) : this.type = 
	{
		this.bias = b
		this
	}

	def predictValues(index : Array[Int], value : Array[Double]) : Array[Double] = 
	{
		var decValues = Array.fill(nrClass)(0.0)
		val lastIndex = w(0).length - 1
		if(nrClass == 2)
		{
			var i = 0
			while(i < index.length)
			{
				if(index(i) <= lastIndex)
				{
					decValues(0) += value(i) * w(0).get(index(i))
				}
				i += 1
			}
			if(bias >= 0)
			{
				decValues(0) += bias*w(0).get(lastIndex)
			}
		}
		else
		{
			var i = 0
			var j = 0
			while(i < nrClass)
			{
				j = 0
				while(j < index.length)
				{
					if(index(j) <= lastIndex) 
					{
						decValues(i) += value(j) * w(i).get(index(j))
					}
					j += 1
				}
				if(bias >= 0) 
				{
					decValues(i) += bias*w(i).get(lastIndex)
				}
				i += 1
			}
		}
		decValues
	}

	/** 
	 *Predict a label given a DataPoint.
	 *@param point a DataPoint
	 *@return a label
	 */
	def predict(point : DataPoint) : Double = 
	{
		val decValues = predictValues(point.index, point.value)
		var labelIndex = 0
		if(nrClass == 2) 
		{
			if(decValues(0) < 0) 
			{
				labelIndex = 1
			}
		}
		else
		{
			var i = 1
			while(i < nrClass)
			{
				if(decValues(i) > decValues(labelIndex))
				{
					labelIndex = i
				}
				i += 1
			}
		}
		label(labelIndex)
	}

	/** 
	 *Predict probabilities given a DataPoint.
	 *@param point a DataPoint
	 *@return probabilities which follow the order of label
	 */
	def predictProbability(point : DataPoint) : Array[Double] =
	{
		Predef.require(param.solverType == L2_LR, "predictProbability only supports for logistic regression.")
		var probEstimates = predictValues(point.index, point.value)
		probEstimates = probEstimates.map(value => 1.0/(1.0+Math.exp(-value)))
		if(nrClass == 2)
		{
			probEstimates(1) = 1.0 - probEstimates(0)
		}
		else
		{
			var sum = probEstimates.sum
			probEstimates = probEstimates.map(value => value/sum)
		}
		probEstimates
	}

	/** 
	 * Save Model to the local file system.
	 *
	 * @param fileName path to the output file
	 */
	def saveModel(fileName : String) =
	{
		val fos = new FileOutputStream(fileName)
		val oos = new ObjectOutputStream(fos)
		oos.writeObject(this)
		oos.close
	}
}

object Model
{

	/** 
	 * load Model from the local file system.
	 *
	 * @param fileName path to the input file
	 */
	def loadModel(fileName : String) : Model =
	{
		val fis = new FileInputStream(fileName)
		val ois = new ObjectInputStream(fis)
		val model : Model = ois.readObject.asInstanceOf[Model]
		ois.close
		model
	}
}

	
