package sample.cluster.simple

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ClusterEvent.{MemberEvent, MemberRemoved, MemberUp, ReachabilityEvent}
import akka.cluster.typed.{Cluster, Subscribe}
import akka.util.Timeout
import sample.cluster.CborSerializable

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object ClusterListener {

  sealed trait Event
  // internal adapted cluster events only
  private final case class ReachabilityChange(reachabilityEvent: ReachabilityEvent) extends Event
  private final case class MemberChange(event: MemberEvent) extends Event
  private final case class Add(newWorkers: Set[ActorRef[Wave]]) extends Event
  private final case class X(str: String) extends Event
  private final case class XX(str: String) extends Event
  final case class Wave(text: String, replyTo: ActorRef[Wave]) extends Event with CborSerializable
  final case class WaveBack(text: String) extends CborSerializable
  val WorkeroServiceKey: ServiceKey[Wave] = ServiceKey[Wave]("Workero")
  var actorSet: Set[ActorRef[Wave]] = Set.empty

  def apply(): Behavior[Event] = Behaviors.setup { ctx =>
    val memberEventAdapter: ActorRef[MemberEvent] = ctx.messageAdapter(MemberChange)
    Cluster(ctx.system).subscriptions ! Subscribe(memberEventAdapter, classOf[MemberEvent])

    val reachabilityAdapter = ctx.messageAdapter(ReachabilityChange)
    Cluster(ctx.system).subscriptions ! Subscribe(reachabilityAdapter, classOf[ReachabilityEvent])

    val subscriptionAdapter = ctx.messageAdapter[Receptionist.Listing] {
      case WorkeroServiceKey.Listing(workers) =>
        Add(workers)
    }
    ctx.system.receptionist ! Receptionist.Subscribe(WorkeroServiceKey, subscriptionAdapter)
    ctx.system.receptionist ! Receptionist.Register(WorkeroServiceKey, ctx.self)

    Behaviors.receiveMessage { message =>
      message match {

        case MemberChange(changeEvent) =>
          changeEvent match {
            case MemberUp(member) =>
              ctx.log.info("Member is Up: {}", member.address)
              //necessario per il ctx.ask, dice dopo quanto deve andare nel case Failure senza attendere
              implicit val timeout: Timeout = 5.seconds
              actorSet.foreach(w => ctx.ask(w, Wave("PROVA", _)) {
                        //non necessari per questa dimostrazione
                //case Success(resp) => X(resp.text)
                //case Failure(ex) => X("NOOOO")
                case _ => X("avvenuto")
              })
            case _: MemberEvent => // ignore
          }

        case Add(worker) => ctx.log.info("UN ALTRO")
          if(worker.nonEmpty)
          actorSet = worker
          ctx.log.info(worker.toString())

        case Wave(msg, from) => ctx.log.info(msg + " da " + from.toString)

        case X(str) => ctx.log.info(str)

        case _ => ctx.log.info("wow")
      }
      Behaviors.same
    }
  }
}