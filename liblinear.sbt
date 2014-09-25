
name := "spark-liblinear"

version := "1.95"
 
scalaVersion := "2.10.4"

libraryDependencies += "org.apache.spark" %% "spark-core" % "1.1.0"

libraryDependencies += "org.jblas" % "jblas" % "1.2.3"

resolvers += "Akka Repository" at "http://repo.akka.io/releases/"

 
ivyXML :=
<dependency org="org.eclipse.jetty.orbit" name="javax.servlet"
rev="2.5.0.v201103041518">
<artifact name="javax.servlet" type="orbit" ext="jar"/>
</dependency>
