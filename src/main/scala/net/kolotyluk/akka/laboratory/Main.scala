package net.kolotyluk.akka.laboratory

import akka.typed._
import akka.typed.scaladsl.Actor
import akka.typed.scaladsl.AskPattern._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.Await

/** =Akka Typed Behaviors Laboratory=
  * Example App for Demonstrating Akka Typed Behaviors
  * <p>
  * This demonstration of '''Akka Typed''' is inspired by my difficulty in understanding
  * the main documenation at
  * [[https://doc.akka.io/docs/akka/2.5/scala/typed.html Akka Typed]].
  * For some more up-to-date explanations, checkout
  * [[https://akka.io/blog/2017/05/05/typed-intro Akka Typed Intro]]
  * on the blog.
  * <p>
  * Generally I find in understanding concurrent programming, it's best to experiment with
  * real code, and lots of logging to show what is actually happening.
  *
  * ==Principles of Operation==
  * I appologise if I don't get this right, but I am still learning...
  * <p>
  * In Akka Typed, the classic Actor.Recieve message function is replaced with the new
  * akka.typed.Behavior function.
  * That is, the PartialFunction[Any, Unit] is replaced by (ActorContext[T], T) => Behavior[T].
  * More precisely, the message handling function that takes Any type of message, without
  * producing a result, is replaced by a function which handles only specific types of
  * messages, and always returns a ''new'' behavior. For example
  * {{{
  * override def receive = {
  *   case WhoToGreet(who) =>
  *     greeting = s"hello, $who"
  *   case Greet =>
  *     println(greeting)
  * }
  * }}}
  * is replaced by
  * {{{
  * override def onMessage(message: Command): Behavior[Command] = {
  *   message match {
  *     case WhoToGreet(who) =>
  *       greeting = s"hello, $who"
  *     case Greet =>
  *       println(greeting)
  *     }
  *   this // from the actor definition
  * }
  * }}}
  * As we will see later, this is an over simplification, but it's a start. In essence,
  * a Behavior is a function which takes a specific ''type'' of message, and returns a new
  * Behavior to process the next message from the actor's mailbox. This solves a couple of
  * problems:
  * <p>
  * (1) if the base of your message type is a sealed class/trait, then the Scala compiler
  * can make sure you handle all message types, and (2) no-one can send you an unhandled
  * message type, or no more dead letters.
  * <p>
  * Another important aspect of behaviors is that they embody the classic Akka `context.become`
  * mechanism in that we explicitly return the behavior to handle the next message. As we will
  * see later, it is good practice to change state handling not by changing internal state,
  * but by creating a new Behavior with new immutable data; or a more functional approach.
  * @author eric@kolotyluk.net
  */
object Main extends App {

  /** =Actor base Behavior=
    * Example of using a Typed Actor
    * <p>
    * The Gabbler basically handles SessionEvent messages
    * <p>
    * Actor.immutable means: Construct an actor behavior that can react to both incoming messages
    * and lifecycle signals. After spawning this actor from another actor (or as the guardian of
    * an akka.typed.ActorSystem) it will be executed within an ActorContext that allows access to
    * the system, spawning and watching other actors, etc. This constructor is called immutable
    * because the behavior instance doesn't have or close over any mutable state. Processing the
    * next message results in a new behavior that can potentially be different from this one.
    * State is updated by returning a new behavior that holds the new immutable state.
    * <p>
    * Note that by returning a new behavior, the behavior can effectively change state similar to
    * the 'context.become' mechanism used to implement state machines.
    */
  val gabbleActor = Actor.immutable[SessionEvent] {
    (actorContext, sessionEvent) ⇒
      println(s"gabbleActor: actorContext = $actorContext")
      println(s"gabbleActor: sessionEvent = $sessionEvent")
      sessionEvent match {
        case MessagePosted(screenName, message) ⇒
          println(s"gabbleActor: message has been posted by '$screenName': $message")
          // Handle one message and we're done???
          Actor.stopped
        case SessionDenied(reason) ⇒
          // Return this behavior from message processing to signal that this actor shall terminate
          // voluntarily. If this actor has created child actors then these will be stopped as part
          // of the shutdown procedure. The PostStop signal that results from stopping this actor
          // will be passed to the current behavior. All other messages and signals will effectively
          // be ignored.
          Actor.stopped
        case SessionGranted(handle) ⇒
          handle ! PostMessage("Hello World!")
          // Return this behavior from message processing in order to advise the system to reuse
          // the previous behavior. This is provided in order to avoid the allocation overhead of
          // recreating the current behavior where that is not necessary.
          Actor.same
      }
    }

  println(s"Main: gabbleActor = $gabbleActor")

  /** =Top Level Behavior=
    * Top level user defined Behavior created for this Actor System.
    * <p>
    * Behaviors are the new abstraction of Actors in the akka.typed model. This behavior does not
    * handle any messages [akka.NotUsed] but does handle signals from the Actor System.
    * <p>
    * The behavior of an actor defines how it reacts to the messages that it receives. The message
    * may either be of the type that the Actor declares and which is part of the ActorRef signature,
    * or it may be a system Signal that expresses a lifecycle event of either this actor or one of
    * its child actors. Behaviors can be formulated in a number of different ways, either by using
    * the DSLs in akka.typed.scaladsl.Actor and akka.typed.javadsl.Actor or extending the abstract
    * ExtensibleBehavior class. Closing over ActorContext makes a Behavior immobile: it cannot be
    * moved to another context and executed there, and therefore it cannot be replicated or forked
    * either. This base class is not meant to be extended by user code. If you do so, you may lose
    * binary compatibility.
    * <p>
    * akka.NotUsed is used in generic type signatures wherever the actual value is of no importance.
    * It is a combination of Scala’s Unit and Java’s Void, which both have different issues when used
    * from the other language. An example use-case is the materialized value of an Akka Stream for
    * cases where no result shall be returned from materialization.
    * <p>
    * Actor.deferred is a factory for a behavior. Creation of the behavior instance is deferred
    * untilthe actor is started, as opposed to Actor.immutable that creates the behavior instance
    * immediately before the actor is running. The factory function pass the ActorContext as
    * parameter and that can for example be used for spawning child actors. deferred is typically
    * used as the outer most behavior when spawning an actor, but it can also be returned as the
    * next behavior when processing a message or signal. In that case it will be "undeferred"
    * immediately after it is returned, i.e. next message will be processed by the undeferred
    * behavior.
    */
  val mainBehavior: Behavior[akka.NotUsed] = Actor.deferred { actorContext ⇒

    println(s"mainBehavior: actorContext.self = ${actorContext.self}")

    // Spawn the chatRoom actor to handle Command messages
    // Create a child Actor from ChatRoom.behavior with the name "chatroom".
    val chatRoomActor = actorContext.spawn(ChatRoom.behavior, "chatroom")
    println(s"mainBehavior: chatRoomActor = $chatRoomActor")

    // Spawn the gabbler actoer to handle SessionEvent messages
    // Create a child Actor from gabbler with the name "gabbler".
    val gabblerActor = actorContext.spawn(gabbleActor, "gabbler")
    println(s"mainBehavior: gabblerActor = $gabblerActor")

    // Keep an eye on the gabbler and signal us if it stops
    actorContext.watch(gabblerActor)

    // Start a session in the chatRoom on for our gabbler
    chatRoomActor ! GetSession("ol’ Gabbler", gabblerActor)

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
        println(s"mainBehavior: Terminated(actorRef) = $actorRef")
        // Since the gabblerActor is the only one we are watching, we assume the gabblerActor stopped.
        // If we were watching more actors, we could actually check the actorRef to see which one stopped.

        // Return this behavior from message processing to signal that this actor shall terminate voluntarily.
        // If this actor has created child actors then these will be stopped as part of the shutdown procedure.
        // The PostStop signal that results from stopping this actor will be passed to the current behavior.
        // All other messages and signals will effectively be ignored.
        Actor.stopped
    }
  }

  println(s"mainBehavior = $mainBehavior")

  // Start the whole ball of wax rolling by creating an ActorSystem with the 'main' behavior as
  // the root of the system, or in old school terms, the root actor. For example, this behavior
  // will start after Actor[akka://ChatRoomDemo/user#0] starts.
  val system = ActorSystem(mainBehavior, "ChatRoomDemo")

  // system.whenTerminated means: Returns a Future which will be completed after the ActorSystem
  // has been terminated and termination hooks have been executed.
  //
  // Note: in a real world system we would typically wait Duration.Inf for unbounded waiting,
  // but this is a laboratory demo, so we limit our wait.
  Await.result(system.whenTerminated, 3 seconds)
}

/** =Command Protocol=
  *
  */
sealed trait Command
final case class GetSession(screenName: String, replyTo: ActorRef[SessionEvent]) extends Command

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

  println(s"ChatRoom: behavior = $behavior")

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

          case GetSession(screenName, gabbler) ⇒
            // Note how we refer to the replyTo argument as the gabbler. By convention, if a
            // message infers a reply, then it includes a replyTo: parameter, so we define
            // the protocol that way. When matching the case, and renaming the parameter, we are
            // implying we know more about who we are replying to. If we wanted to make this
            // more explicit, then we might have defined the message like
            // type Gabbler = ActorRef[SessionEvent]
            // final case class GetSession(screenName: String, replyTo: Gabbler) extends Command
            //
            // In Akka Typed they have done away with the sender reference, which was problematic.
            // In this case we are not going to reply to the actual sender of the message, but to a
            // proxy or redirect.

            // Create a new Actor instance to deal with this specific session.
            val sessionHandler = actorContext.spawnAdapter {
              initialMessage: PostMessage ⇒
                PostSessionMessage(screenName, initialMessage.message)
            }
            // Tell the client which sessionHandler to use for this session
            gabbler ! SessionGranted(sessionHandler)

            // Very clever functional style, we return a new chatRoom behavior recursively
            // with the new session prepended to the session list. Remember, prepending
            // is more performant with immutable data structures than appending. Basically
            // we are saying 'the state has changed' and needs a new behavior to deal with
            // that state.
            //
            // Because the Actor's state is immutable, we change state by creating a new
            // immutable data structure, and passing that into a new instance of the behavior.
            // Same actor, new behavior.
            chatRoom(gabbler :: sessions)

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