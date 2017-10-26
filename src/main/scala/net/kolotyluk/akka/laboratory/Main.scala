package net.kolotyluk.akka.laboratory

import akka.typed._
import akka.typed.scaladsl.Actor
import akka.typed.scaladsl.AskPattern._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.Await

/** =Akka Typed Behaviors Demo=
  * Sample App for Demonstrating Akka Typed Behaviors
  */
object Main extends App {

  // Construct an actor to handle SessionEvent messages
  val gabbler =
    Actor.immutable[SessionEvent] { (actorContext, sessionEvent) ⇒
      sessionEvent match {
        case SessionGranted(handle) ⇒
          handle ! PostMessage("Hello World!")
          Actor.same
        case MessagePosted(screenName, message) ⇒
          println(s"message has been posted by '$screenName': $message")
          Actor.stopped
      }
    }

  /** =Top Level Behavior=
    * First user defined Behavior created for this Actor System.
    * <p>
    * Behaviors replace Actors in the akka.typed model.
    *
    *
    */
  val main: Behavior[akka.NotUsed] = Actor.deferred { actorContext ⇒
    // Construct our top level actor

    // Spawn the chatRoom actor to handle Command messages
    val chatRoom = actorContext.spawn(ChatRoom.behavior, "chatroom")

    // Spawn the gabbler actoer to handle Session messages
    val gabblerRef = actorContext.spawn(gabbler, "gabbler")

    // Keep an eye on the gabbler in case it stops
    actorContext.watch(gabblerRef)

    // Start a session in the chatRoom for the gabbler
    chatRoom ! GetSession("ol’ Gabbler", gabblerRef)

    // Construct an actor to listen for signals
    Actor.immutable[akka.NotUsed] {
      (_, _) ⇒ Actor.unhandled
    } onSignal {
      case (actorContext, Terminated(ref)) ⇒
        Actor.stopped
    }
  }

  val system = ActorSystem(main, "ChatRoomDemo")
  Await.result(system.whenTerminated, 3 seconds)
}

sealed trait Command
final case class GetSession(screenName: String, replyTo: ActorRef[SessionEvent])
  extends Command

sealed trait SessionEvent
final case class SessionGranted(handle: ActorRef[PostMessage]) extends SessionEvent
final case class SessionDenied(reason: String) extends SessionEvent
final case class MessagePosted(screenName: String, message: String) extends SessionEvent

final case class PostMessage(message: String)

object ChatRoom {

  private final case class PostSessionMessage(screenName: String, message: String)
    extends Command

  // A Behavior is akin to an Actor in that it is an Actor with defined message interactions.
  val behavior: Behavior[Command] = chatRoom(List.empty)

  private def chatRoom(sessions: List[ActorRef[SessionEvent]]): Behavior[Command] =
    Actor.immutable[Command] { (actorContext, command) ⇒
      command match {
        case GetSession(screenName, client) ⇒
          val wrapper = actorContext.spawnAdapter {
            p: PostMessage ⇒ PostSessionMessage(screenName, p.message)
          }
          client ! SessionGranted(wrapper)
          chatRoom(client :: sessions)
        case PostSessionMessage(screenName, message) ⇒
          val mp = MessagePosted(screenName, message)
          sessions foreach (_ ! mp)
          Actor.same
      }
    }

}