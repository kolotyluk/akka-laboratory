package net.kolotyluk.akka.example

import akka.typed.{ActorSystem, Terminated}
import net.kolotyluk.akka.net.kolotyluk.scala.Configuration

import scala.util.Failure
import scala.util.Success

object Main extends App with Configuration {
  println(s"${this.getClass.getName} starting...")

  //println(Configuration.toString)

  val systemName = "Example"
  val system = ActorSystem(Guardian.behavior, systemName)

  system.log.info(s"Actor System Started")

  implicit val executor = system.executionContext

  system.whenTerminated.onComplete {
    case Success(terminated) =>
      println(s"Actor System Terminated Normally")
      system.log.info(s"Actor System Terminated Normally")
    case Failure(cause) =>
      println(s"Actor System Termination Failure", cause)
      system.log.error(s"Actor System Termination Failure", cause)
  }
}
