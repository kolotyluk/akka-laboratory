name := "akka-laboratory"

version := "1.0"

scalaVersion := "2.12.2"

lazy val akkaVersion = "2.5.6"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-typed" % akkaVersion,
  "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  "org.clapper" %% "grizzled-slf4j" % "1.3.1",
  "ch.qos.logback" % "logback-classic" % "1.2.3" // needed for grizzled
)
