package net.kolotyluk.akka.example

import akka.typed.{ActorRef, Behavior}
import akka.typed.scaladsl.Actor

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import grizzled.slf4j.Logger
import net.kolotyluk.akka.example.Guardian.Spawn

object Brat extends Runnable {

  trait Message extends Main.Message
  final case class Count(count: Int) extends Message

  val logger = Logger[this.type]

  logger.info("Brat: initializing")

  val behavior: Behavior[Message] = brat

  def brat: Behavior[Message] =
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

        case Main.Start() ⇒
          //println(s"Brat starting with $actorCell")

          //logger.info(s"brat received $message")

          val cancelable = actorCell.schedule(1 second, actorCell.self, Count(0))
          Actor.same
      }
    }

  override def run = {
    Main.system ! Spawn(brat.asInstanceOf[Behavior[Main.Message]], "brat")

    //    val random = math.random()
//    logger.info(random)
//    if (random > 0.9) 10 / 0
  }

}