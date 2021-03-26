package se.kth.id2203.BallotLeaderElection;

import se.kth.id2203.networking.{NetAddress, NetMessage}
import se.kth.id2203.overlay.{Topology, TopologyProvider}
import se.sics.kompics.network.{Network}
import se.sics.kompics.sl.{ComponentDefinition, Init, KompicsEvent, Port}
import se.sics.kompics.timer.{ScheduleTimeout, Timeout, Timer}

import scala.collection.mutable


case class CheckTimeout(timeout: ScheduleTimeout) extends Timeout(timeout);

case class HeartbeatReq(round: Long, highestBallot: Long) extends KompicsEvent;

case class HeartbeatResp(round: Long, ballot: Long) extends KompicsEvent;


class BallotLeaderElection extends Port {
  indication[BLE_Leader];
}
case class BLE_Leader(leader: NetAddress, ballot: Long) extends KompicsEvent;


class GossipLeaderElection extends ComponentDefinition {
  val ble = provides[BallotLeaderElection];
  val net = requires[Network];
  val timer = requires[Timer];
  val topo = requires[TopologyProvider];

  val self = cfg.getValue[NetAddress]("id2203.project.address");
  var topology: Set[NetAddress] = Set(self);
  val delta = 1;
  var majority: Int = (topology.size / 2) + 1;

  private var period = cfg.getValue[Long]("id2203.project.delay");
  private var ballots = mutable.Map.empty[NetAddress, Long];

  private var round = 0l;
  private var ballot = ballotFromNAddress(0, self);

  private var leader: Option[(Long, NetAddress)] = None;
  private var highestBallot: Long = ballot;

  private val ballotOne = 0x0100000000l;

  def ballotFromNAddress(n: Int, adr: NetAddress): Long = {
    val nBytes = com.google.common.primitives.Ints.toByteArray(n);
    val addrBytes = com.google.common.primitives.Ints.toByteArray(adr.hashCode());
    val bytes = nBytes ++ addrBytes;
    val r = com.google.common.primitives.Longs.fromByteArray(bytes);
    assert(r > 0);
    return r;
  }

  def incrementBallotBy(ballot: Long, inc: Int): Long = {
    ballot + inc.toLong * ballotOne
  }

  private def incrementBallot(ballot: Long): Long = {
    ballot + ballotOne
  }

  private def startTimer(delay: Long): Unit = {
    val scheduledTimeout = new ScheduleTimeout(period);
    scheduledTimeout.setTimeoutEvent(CheckTimeout(scheduledTimeout));
    trigger(scheduledTimeout -> timer);
  }

  private def checkLeader() {

    var tempBallots = ballots + ( self -> ballot );
    var (topProcess, topBallot) = tempBallots.maxBy{
      case (key, value) => value
    }

    if( topBallot < highestBallot ){
      while( ballot <= highestBallot ){
        ballot = incrementBallotBy(ballot, 1);
      }
      leader = None;
    }
    else{
      if( leader.isEmpty || ((topBallot, topProcess) != leader.get) ){
        highestBallot = topBallot;
        leader = Some((topBallot, topProcess));
        trigger( BLE_Leader(topProcess, topBallot) -> ble );
      }

    }
  }

  timer uponEvent {
    case CheckTimeout(_) => {

      if( ballots.size + 1 >= majority ){
        checkLeader();
      }

      ballots.clear;
      round += 1;

      for(p <- topology) {
        if( p != self  ){
          trigger( NetMessage(self, p, HeartbeatReq(round, highestBallot)) -> net )
        }
      }

      startTimer(period);
    }
  }

  net uponEvent {
    case NetMessage( header, HeartbeatReq(r, hb) ) => {

      if( hb > highestBallot ){
        highestBallot = hb;
      }

      trigger( NetMessage(self, header.src, HeartbeatResp(r, ballot)) -> net );

    }
    case NetMessage( header, HeartbeatResp(r, b) ) => {

      if( r == round ){
        ballots += (header.src -> b);
      }
      else{
        period += delta;
      }

    }
  }

  topo uponEvent {

    case Topology(nodes: Set[NetAddress]) => {
      topology = nodes;
      majority = (topology.size / 2) + 1;
      startTimer(period);
    }

  }
}