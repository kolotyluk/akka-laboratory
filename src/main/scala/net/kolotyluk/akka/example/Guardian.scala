package net.kolotyluk.akka.example

import akka.typed._
import akka.typed.scaladsl.Actor
import akka.typed.scaladsl.AskPattern._
import grizzled.slf4j.Logger
import net.kolotyluk.akka.example.Main.Start

/** =Guardian Actors=
  * Top level of our actor hierarchy.
  */
object Guardian {

  val logger = Logger[this.type]

  sealed trait Message extends Main.Message
  case class Done(cause: String) extends Message
  case class Spawn(behavior: Behavior[Main.Message], name:String) extends Message

  /** =Outermost Behavior of ActorSystem=
    *
    * Defer creating the behavior instance so that we can pass its reference to
    * [[akka.typed.ActorSystem ActorSystem]]
    * so it can spawn the top level actors.
    */
  val behavior: Behavior[Message] =
    Actor.deferred[Message] { actorContext ⇒
      logger.info(s"Guardian.behavior: initializing with actorContext.self = ${actorContext.self}")

      // do initialization stuff...

      monitor
    }

  /**
    *
    */
  val monitor: Behavior[Message] =
    Actor.immutable[Message] { (actorCell, message) ⇒
      logger.info(s"Guardian.monitor: received $message")
      message match {
        case Done(cause) =>
          Actor.stopped
        case Spawn(behavior, name) ⇒
          logger.info(s"Guardian.monitor: spawning $name")
          val actorRef = actorCell.spawn(behavior, name)
          actorCell.watch(actorRef)
          actorRef ! Start()
          Actor.same
      }
    } onSignal {
      // There is no other information available with this signal.
      // While akka knows the reason for termination, we don't.
      case (actorCell, Terminated(actorRef)) ⇒
        logger.warn(s"Guardian.monitor: Received Terminated signal for $actorRef")
        Actor.stopped
    }

}
