package net.kolotyluk.akka.example

import akka.typed.{ActorSystem, Terminated}
import grizzled.slf4j.Logger
import net.kolotyluk.akka.net.kolotyluk.scala.Configuration

import scala.util.Failure
import scala.util.Success

object Main extends App with Configuration {

  trait Message extends Serializable

  val logger = Logger[this.type]

  logger.info(s"starting...")

  //logger.info(Configuration.toString)

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

  Brat.run
}
