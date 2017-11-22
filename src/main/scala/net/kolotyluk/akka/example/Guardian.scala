package net.kolotyluk.akka.example

import akka.typed._
import akka.typed.scaladsl.Actor
import akka.typed.scaladsl.AskPattern._

import grizzled.slf4j.Logger

/** =Guardian Actors=
  * Top level of our actor hierarchy.
  */
object Guardian {

  val logger = Logger[this.type]

  sealed trait Message
  case class Done(cause: String) extends Message

  /** =Outermost Behavior of ActorSystem=
    *
    * Defer creating the behavior instance so that we can pass its reference to
    * [[akka.typed.ActorSystem ActorSystem]]
    * so it can spawn the top level actor.
    */
  val behavior: Behavior[Message] =
    Actor.deferred { actorContext ⇒
      logger.info(s"Guardian.behavior: initializing with actorContext.self = ${actorContext.self}")

      val brat = actorContext.spawn(Brat.behavior, "brat")
      actorContext.watch(brat)
      brat ! Brat.Start(actorContext.self)

      monitor
    }

  /**
    *
    */
  val monitor: Behavior[Message] =
    Actor.immutable[Message] { (actorCell, message) ⇒
      logger.info("monitor received $message")
      message match {
        case Done(cause) =>
          Actor.stopped
      }
    } onSignal {
      // There is no other information available with this signal.
      // While akka knows the reason for termination, we don't.
      case (actorCell, Terminated(actorRef)) ⇒
        logger.warn(s"Received Terminated signal for $actorRef")
        Actor.stopped
    }
}
