package net.kolotyluk.akka.example

import akka.typed.{ActorRef, Behavior}
import akka.typed.scaladsl.Actor

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import grizzled.slf4j.Logger
import net.kolotyluk.akka.example.Guardian.Spawn

object Brat extends Runnable {

  sealed trait Message
  final case class Count(count: Int) extends Message
  final case class Start(replyTo: ActorRef[Guardian.Message]) extends Message

  val logger = Logger[this.type]

  override def run = {
    val random = math.random()
    logger.info(random)
    if (random > 0.9) 10 / 0
  }

  Main.system ! Spawn(behavior, "brat")

  val behavior: Behavior[Message] =
    Actor.immutable[Message] { (actorCell, command) ⇒

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