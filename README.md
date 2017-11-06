# akka-laboratory
Akka Experiments and Playground

## Akka Typed

`net.kolotyluk.akka.laboratory.Main` takes the documentation from
[Akka Typed](https://doc.akka.io/docs/akka/2.5/scala/typed.html)
to a working example with explanations of what is going on via
scaladoc.

Akka Typed makes a number of safety and robustness improvements
in working with Actors. Typed Actors and typed Behaviors replace
the untyped Actors with mechanisms to better defined the
protocols each actor supports.

### Sender

The sender mechanism has been removed as it tends to create problems.
In Akka Typed, by convention, when you send a message to an actor, you
use the `replyTo:` field in the message in place of sender.

It is interesting to note that by doing this, replies do not necessarily
come to the actor sending the message, so you can forward change message
flows. This might be of use in practices such as the 'Saga Pattern.'

### Behavior

The 'behavior' of an actor defines how the actor processes messages
from the in-box.

In the simplest explanation, the receive function of actors has been
replaced by a behavior, or rather, the onMessage() function, which returns
a Behavior.

    override def onMessage(message: Server): Behavior[Server] = {
      message match {
        case Create(name) ⇒
        case Destroy(name) ⇒
        }
      this
    }

 Two immediate changes here are:

#### Typed Actors and Behaviors

Both actors and their behaviors are typed, for example

    sealed trait Server
    final case class Create(name: String,  replyTo: ActorRef[Client]) extends Server
    final case class Destroy(name: String, replyTo: ActorRef[Client]) extends Server

    sealed trait Client
    final case class Created(name: String) extends Client
    final case class Destroyed(name: String) extends Client

    val serverActor = Actor.immutable[Server] {
      (actorContext, command) ⇒ command match {
        case Create(name)  ⇒
          replyTo ! Created(name)
          Actor.same // next behavior
        case Destroy(name) ⇒
          replyTo ! Destroyed(name)
          Actor.same // next behavior
      }
    }

Using this pattern we can see that by using sealed traits, the
serverActor must implement all cases of Server commands, or the compiler
will complain. The reduces the chances of unhandled messages and dead
letters.

#### Becomes

In legacy Akka, you can use `becomes` to implement a state machine so
that the actor behaves differently on the next messages processed from
the in-box. In Akka Type this is more explicit in that actors/behaviors
always explictly return the next behavior, even if it is just the
`Actor.same` behavior.

One pattern this supports is that you can create an `immutable` actor
with no internal state of its own, but returns a new behavior which
takes an immutable object as a parameter, encapulating the new state.
Basically, there is changing state based on immutable objects which are
inherently thread safe. See `net.kolotyluk.akka.laboratory.Main` for
an example of this.

### Signals

Actors/Behaviors can also catch signals

    actorContext.watch(commandActor)

    Actor.immutable[akka.NotUsed] {
      case (actorContext, notUsed) ⇒
        Actor.unhandled // next behavior
    } onSignal {
      case (actorContext, Terminated(actorRef)) ⇒
        Actor.stopped   // next beheavior
    }

which is a useful capability for actors monitoring other actors.

### Akka Cluster

See [Akka Typed Intro](https://akka.io/blog/2017/05/05/typed-intro)
for more information on how Akka Typed can be used in an Akka Cluster
environment. Currenly it is possible to use both Akka Typed and legacy
Akka together in the same project, which makes migration to Akka Typed
easier. However, at some point in the future, it may no longer be
possible to use both 'types' ;-) of Akka together.


