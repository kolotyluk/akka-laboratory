package net.kolotyluk.akka.example

import akka.typed.{ActorRef, Behavior}
import akka.typed.scaladsl.Actor

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import grizzled.slf4j.Logger

object Brat extends Runnable {

  val logger = Logger[this.type]

  sealed trait Command

  final case class Count(count: Int) extends Command

  final case class Start(replyTo: ActorRef[Guardian.Message]) extends Command

  override def run = {
    val random = math.random()
    logger.info(random)
    if (random > 0.9) 10 / 0
  }

  val behavior: Behavior[Command] =
    Actor.immutable[Command] { (actorCell, command) ⇒

      command match {
        case message@Count(count) ⇒
          val random = math.random()
          logger.info(random)
          if (random > 0.9) 10 / 0
          if (random < 0.1)
            Actor.stopped
          else {
            val cancelable = actorCell.schedule(1 second, actorCell.self, Count(0))
            Actor.same
          }

        case message@Start(sender) ⇒
          //println(s"Brat starting with $actorCell")

          logger.info(s"brat received $message")

          val cancelable = actorCell.schedule(1 second, actorCell.self, Count(0))
          Actor.same
      }

    }

}