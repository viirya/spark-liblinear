
###Liblinear on Spark

This is the fork of [Liblinear on Spark](http://www.csie.ntu.edu.tw/~cjlin/libsvmtools/distributed-liblinear/) from Dr. C.-J. Lin. 

The implementation uses a modified Spark broadcast interface to cache intermediate information to overcome the problem mentioned in IV.D Caching Intermediate Information or not of Dr. Lin's [original paper] (http://www.csie.ntu.edu.tw/~cjlin/papers/spark-liblinear/spark-liblinear.pdf).

Since the modified interface is not accepted by Spark project yet, this codes can not be built on official Spark.
