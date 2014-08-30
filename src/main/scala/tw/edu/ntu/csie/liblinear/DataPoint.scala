package tw.edu.ntu.csie.liblinear

/**
 * DataPoint represents a sparse data point with label.
 *
 * @param x features represented in an Array of Feature
 * @param y label
 */
class DataPoint(val index : Array[Int], val value : Array[Double], val y : Double) extends Serializable
{

	def getMaxIndex() : Int = 
	{
		if(this.index.isEmpty)
		{
			return 0
		}
		this.index.last
	}

	def genTrainingPoint(n : Int, b : Double, posLabel : Double) : DataPoint = 
	{
		var index : Array[Int] = null
		var value : Array[Double] = null
		var y = if(this.y == posLabel) 1.0 else -1.0
		if(b < 0)
		{
			index = this.index
			value = this.value
		}
		else
		{
			val length = this.index.length
			index = new Array[Int](length + 1)
			value = new Array[Double](length + 1)
			this.index.copyToArray(index, 0)
			this.value.copyToArray(value, 0)
			index(length) = n-1
			value(length) = b
		}
		new DataPoint(index, value, y)
	}
}
