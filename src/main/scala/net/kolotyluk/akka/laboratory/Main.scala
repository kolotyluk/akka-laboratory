package net.kolotyluk.akka.laboratory

import akka.typed._
import akka.typed.scaladsl.Actor
import akka.typed.scaladsl.AskPattern._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.Await

/** =Akka Typed Behaviors Laboratory=
  * Exampe App for Demonstrating Akka Typed Behaviors
  * <p>
  * This demonstration of Akka Typed is inspired by Lightbend/Typesafe's ongoing
  * lack of ability and motivation to explain things clearly or even adequately.
  * Not that it's really that hard to explain things, it's just that they don't care.
  * <p>
  * It's such as shame that every now and then they keep coming up with some good
  * technology, or improvements on existing technology, but they are lousy communicators
  * and educators; which is why I would never pay their products or services.
  * @author eric@kolotyluk.net
  * @see [[https://doc.akka.io/docs/akka/2.5/scala/typed.html Akka Typed]]
  */
object Main extends App {

  /** =Actor base Behavior=
    * Example of using an Actor as a Behavior
    * <p>
    * Construct an actor behavior that can react to both incoming messages and lifecycle signals.
    * After spawning this actor from another actor (or as the guardian of an akka.typed.ActorSystem)
    * it will be executed within an ActorContext that allows access to the system, spawning and
    * watching other actors, etc. This constructor is called immutable because the behavior instance
    * doesn't have or close over any mutable state. Processing the next message results in a new
    * behavior that can potentially be different from this one. State is updated by returning a
    * new behavior that holds the new immutable state.
    */
  val gabbleActor = Actor.immutable[SessionEvent] {
    (actorContext, sessionEvent) ⇒
      println(s"gabbleActor: actorContext = $actorContext")
      println(s"gabbleActor: sessionEvent = $sessionEvent")
      sessionEvent match {
        case MessagePosted(screenName, message) ⇒
          println(s"gabbleActor: message has been posted by '$screenName': $message")
          Actor.stopped
        case SessionDenied(reason) ⇒
          Actor.stopped
        case SessionGranted(handle) ⇒
          handle ! PostMessage("Hello World!")
          Actor.same
      }
    }

  println(s"Main: gabbleActor = $gabbleActor")

  /** =Top Level Behavior=
    * Top level user defined Behavior created for this Actor System.
    * <p>
    * Behaviors replace Actors in the akka.typed model.
    * <p>
    * This behavior does not handle any messages [akka.NotUsed] but does handle
    * signals from the Actor System.
    *
    */
  val mainBehavior: Behavior[akka.NotUsed] = Actor.deferred { actorContext ⇒

    println(s"mainBehavior: actorContext.self = ${actorContext.self}")

    // Spawn the chatRoom actor to handle Command messages
    // Create a child Actor from ChatRoom.behavior with the name "chatroom".
    val chatRoom = actorContext.spawn(ChatRoom.behavior, "chatroom")
    println(s"mainBehavior: chatRoom = $chatRoom")

    // Spawn the gabbler actoer to handle SessionEvent messages
    // Create a child Actor from gabbler with the name "gabbler".
    val gabblerRef = actorContext.spawn(gabbleActor, "gabbler")
    println(s"mainBehavior: gabblerRef = $gabblerRef")

    // Keep an eye on the gabbler in case it stops
    actorContext.watch(gabblerRef)

    // Start a session in the chatRoom for the gabbler
    chatRoom ! GetSession("ol’ Gabbler", gabblerRef)

    // Construct an actor behavior that can react to both incoming messages and lifecycle signals.
    // After spawning this actor from another actor (or as the guardian of an akka.typed.ActorSystem)
    // it will be executed within an ActorContext that allows access to the system, spawning and
    // watching other actors, etc. This constructor is called immutable because the behavior instance
    // doesn't have or close over any mutable state. Processing the next message results in a new
    // behavior that can potentially be different from this one. State is updated by returning a new
    // behavior that holds the new immutable state.
    Actor.immutable[akka.NotUsed] {
      (actorCell, notUsed) ⇒
        println(s"mainBehavior: actorCell = $actorCell")
        println(s"mainBehavior: notUsed = $notUsed")

        // Return this behavior from message processing in order to advise the system to reuse the previous behavior,
        // including the hint that the message has not been handled. This hint may be used by composite behaviors
        // that delegate (partial) handling to other behaviors.
        Actor.unhandled
    } onSignal {
      case (actorCell, Terminated(actorRef)) ⇒
        println(s"mainBehavior: actorCell = $actorCell")
        println(s"mainBehavior: actorRef = $actorRef")

        // Return this behavior from message processing to signal that this actor shall terminate voluntarily.
        // If this actor has created child actors then these will be stopped as part of the shutdown procedure.
        // The PostStop signal that results from stopping this actor will be passed to the current behavior.
        // All other messages and signals will effectively be ignored.
        Actor.stopped
    }
  }

  println(s"mainBehavior = $mainBehavior")

  // Start the whole ball of wax rolling by creating an ActorSystem with the 'main' behavior as
  // the root of the system, or in old school terms, the root actor.
  val system = ActorSystem(mainBehavior, "ChatRoomDemo")
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
final case class SessionDenied(reason: String) extends SessionEvent
final case class SessionGranted(handle: ActorRef[PostMessage]) extends SessionEvent
final case class MessagePosted(screenName: String, message: String) extends SessionEvent

final case class PostMessage(message: String)

/** =Behavior Based Chat Room=
  * Example of using Akka Behaviors to implement a simple Chat Room
  *
  * ==Principles Of Operation==
  * The initial ChatRoom.behavior has no sessions. This parent Behavior (actor)
  * accepts incoming sessons, and spawns a new Behavior (actor) for each session,
  * then routes/broadcasts incoming messages to all the sessions.
  */
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

  println(s"ChatRoom behavior = $behavior")

  private def chatRoom(sessions: List[ActorRef[SessionEvent]]): Behavior[Command] =
    Actor.immutable[Command] {
      // Note how the old Akka receive block is now replaced by a function from
      // context/message to some other behavior, where we match on the type of
      // actor mailbox messages we support. This is a good thing because if our
      // messages are derived from a sealed trait, then the compiler will complain
      // if we don't have a case for all subtypes. A common defect is actors that
      // fail to implement all messages, where messages end up in dead-letters.
      (actorContext, command) ⇒
        println(s"chatRoom: actorContext.self = ${actorContext.self}")
        println(s"chatRoom: command = $command")
        command match {
          case GetSession(screenName, client) ⇒
            // Note how we refer to the replyTo argument as the client. In Akka Typed they have
            // done away with the sender reference, which was problematic. In this case we are
            // not going to reply to the actual sender of the message, but to a proxy or
            // redirect.

            // Create a new Actor instance to deal with this specific session.
            val sessionHandler = actorContext.spawnAdapter {
              initialMessage: PostMessage ⇒
                PostSessionMessage(screenName, initialMessage.message)
            }
            // Tell the client which sessionHandler to use for this session
            client ! SessionGranted(sessionHandler)
            // Very clever functional style, we return a new chatRoom behavior recursively
            // with the new session prepended to the session list. Remember, prepending
            // is more performant with immutable data structures than appending. Basically
            // we are saying 'the state has changed'
            chatRoom(client :: sessions)
          case PostSessionMessage(screenName, chatMessage) ⇒
            // Broadcast the message to all the other sessons
            sessions foreach (_ ! MessagePosted(screenName, chatMessage))
            // Return this behavior from message processing in order to advise the system to reuse
            // the previous behavior. This is provided in order to avoid the allocation overhead
            // of recreating the current behavior where that is not necessary.
            Actor.same
            // Basically we are saying 'the state has not changed'
        }
    }
}