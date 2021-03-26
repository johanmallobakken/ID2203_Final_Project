package se.kth.id2203.broadcast

import se.kth.id2203.networking.{NetAddress, NetMessage}
import se.kth.id2203.overlay.{Topology, TopologyProvider}
import se.sics.kompics.network.Network
import se.sics.kompics.sl.{ComponentDefinition, Init, KompicsEvent, Port}

class BestEffortBroadcast extends Port {
  indication[BEB_Deliver];
  request[BEB_Broadcast];
}

case class BEB_Deliver(source: NetAddress, payload: KompicsEvent) extends KompicsEvent;
case class BEB_Broadcast(payload: KompicsEvent) extends KompicsEvent;

class BasicBroadcast() extends ComponentDefinition {

  val pLink = requires[Network];
  val beb = provides[BestEffortBroadcast];
  val topo = requires[TopologyProvider];

  val self = cfg.getValue[NetAddress]("id2203.project.address");

  var topology = Set.empty[NetAddress]

  beb uponEvent {
    case x: BEB_Broadcast => {
      for(q <- topology)
        trigger(NetMessage(self, q, x) -> pLink);
    }
  }

  pLink uponEvent {
    case NetMessage(header, BEB_Broadcast(payload)) => {
      trigger(BEB_Deliver(header.src, payload) -> beb)
    }
  }

  topo uponEvent {
    case Topology(set) => {
      topology = set;
    }
  }
}