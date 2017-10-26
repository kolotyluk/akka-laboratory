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

  /** =Raw Actor=
    * Construct a raw actor to handle SessionEvent messages
    *
    * Construct an actor behavior that can react to both incoming messages and lifecycle signals.
    * After spawning this actor from another actor (or as the guardian of an akka.typed.ActorSystem)
    * it will be executed within an ActorContext that allows access to the system, spawning and
    * watching other actors, etc. This constructor is called immutable because the behavior instance
    * doesn't have or close over any mutable state. Processing the next message results in a new
    * behavior that can potentially be different from this one. State is updated by returning a
    * new behavior that holds the new immutable state.
    */
  val gabbler =
    Actor.immutable[SessionEvent] { (actorContext, sessionEvent) ⇒
      sessionEvent match {
        case SessionDenied(reason)  ⇒
          Actor.stopped
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
    // Create a child Actor from ChatRoom.behavior with the name "chatroom".
    val chatRoom = actorContext.spawn(ChatRoom.behavior, "chatroom")

    // Spawn the gabbler actoer to handle Session messages
    // Create a child Actor from gabbler with the name "gabbler".
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

/** =Command Protocol=
  *
  */
sealed trait Command
final case class GetSession(screenName: String, replyTo: ActorRef[SessionEvent])
  extends Command

/** =Session Protocol=
  *
  */
sealed trait SessionEvent
final case class SessionGranted(handle: ActorRef[PostMessage]) extends SessionEvent
final case class SessionDenied(reason: String) extends SessionEvent
final case class MessagePosted(screenName: String, message: String) extends SessionEvent

final case class PostMessage(message: String)

object ChatRoom {

  private final case class PostSessionMessage(screenName: String, message: String)
    extends Command

  /** =Command Behavior=
    * Handles Command Messages
    * <p>
    * A Behavior is akin to an Actor in that it is an Actor with defined message interactions.
    *
    */
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