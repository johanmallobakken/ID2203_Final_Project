package se.kth.id2203.overlay

import se.kth.id2203.networking.NetAddress
import se.sics.kompics.sl._

class TopologyProvider extends Port {
  indication[Topology]
}

case class Topology(set: Set[NetAddress]) extends KompicsEvent;