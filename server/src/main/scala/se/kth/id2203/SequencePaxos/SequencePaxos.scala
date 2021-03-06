package se.kth.id2203.SequencePaxos

import se.kth.id2203.BallotLeaderElection.{BLE_Leader, BallotLeaderElection}
import se.kth.id2203.networking.{NetAddress, NetMessage}
import se.kth.id2203.overlay.{Topology, TopologyProvider}
import se.sics.kompics.network.Network
import se.sics.kompics.sl.{ComponentDefinition, KompicsEvent, Port}
import scala.collection.mutable

class SequenceConsensus extends Port {
  request[SC_Propose];
  indication[SC_Decide];
}

case class SC_Propose(value: RSM_Command) extends KompicsEvent;
case class SC_Decide(value: RSM_Command) extends KompicsEvent;

trait RSM_Command

case class Prepare(nL: Long, ld: Int, na: Long) extends KompicsEvent;
case class Promise(nL: Long, na: Long, suffix: List[RSM_Command], ld: Int) extends KompicsEvent;
case class AcceptSync(nL: Long, suffix: List[RSM_Command], ld: Int) extends KompicsEvent;
case class Accept(nL: Long, c: RSM_Command) extends KompicsEvent;
case class Accepted(nL: Long, m: Int) extends KompicsEvent;
case class Decide(ld: Int, nL: Long) extends KompicsEvent;

object State extends Enumeration {
  type State = Value;
  val PREPARE, ACCEPT, UNKNOWN = Value;
}

object Role extends Enumeration {
  type Role = Value;
  val LEADER, FOLLOWER = Value;
}

class SequencePaxos extends ComponentDefinition {
  def suffix(s: List[RSM_Command], l: Int): List[RSM_Command] = {
    s.drop(l)
  }

  def prefix(s: List[RSM_Command], l: Int): List[RSM_Command] = {
    s.take(l)
  }

  import Role._
  import State._

  val sc = provides[SequenceConsensus];
  val ble = requires[BallotLeaderElection];
  val net = requires[Network];
  val topo = requires[TopologyProvider]

  val self = cfg.getValue[NetAddress]("id2203.project.address");
  var topology: Set[NetAddress] = Set(self)
  var others: Set[NetAddress] = Set.empty
  var majority: Int = (topology.size / 2) + 1

  var state: (Role.Value, State.Value) = (FOLLOWER, UNKNOWN);
  var nL = 0l;
  var nProm = 0l;
  var leader: Option[NetAddress] = None;
  var na = 0l;
  var va = List.empty[RSM_Command];
  var ld = 0;

  var propCmds = List.empty[RSM_Command];
  val las = mutable.Map.empty[NetAddress, Int];
  val lds = mutable.Map.empty[NetAddress, Int];
  var lc = 0;
  val acks = mutable.Map.empty[NetAddress, (Long, List[RSM_Command])];

  topo uponEvent {
    case Topology(nodes: Set[NetAddress]) => {
      topology = nodes
      others = topology - self
      majority = (topology.size / 2) + 1
    }
  }

  ble uponEvent {
    case BLE_Leader(l, n) => {

      if(n > nL) {
        leader = Some(l);
        nL = n;

        if(self == leader.get && nL > nProm) {

          state = (LEADER, PREPARE);
          propCmds = List.empty;
          las.clear();

          for(p <- topology){
            las(p) = 0;
          }

          lds.clear();
          acks.clear();
          lc = 0;

          for(p <- others) {
            trigger(NetMessage(self, p, Prepare(nL, ld, na)) -> net)
          };

          acks += self -> (na, suffix(va, ld));
          lds += self -> ld;
          nProm = nL;

        } else {
          state = (FOLLOWER, state._2);
        }
      }
    }
  }

  net uponEvent {
    case NetMessage(header, Prepare(np, ldp, n)) => {

      val p = header.src

      if(nProm < np) {

        nProm = np;
        state = (FOLLOWER, PREPARE);
        var sfx = List.empty[RSM_Command];

        if(na >= n) {
          sfx = suffix(va, ldp);
        }

        trigger(NetMessage(self, p, Promise(np, na, sfx, ld)) -> net);
      }
    }

    case NetMessage(header, Promise(n, nap, sfxa, lda)) => {

      val a = header.src

      if ((n == nL) && (state == (LEADER, PREPARE))) {

        acks += a -> (nap, sfxa);
        lds += a -> lda;

        if(acks.size >= majority) {

          val k = acks.map(x => x._2._1).max;
          val sfx = acks.filter(x => x._2._1 == k).head._2._2;
          va = prefix(va,  ld) ++ sfx ++ propCmds;
          las(self) = va.size;
          propCmds = List.empty[RSM_Command];
          state = (LEADER, ACCEPT);

          for(p <- others){
            if(lds.exists(x => x._1 == p)) {
              val sfxp = suffix(va, lds(p));
              trigger(NetMessage(self, p, AcceptSync(nL, sfxp, lds(p))) -> net);
            }
          };

        }
      } else if ((n == nL) && (state == (LEADER, ACCEPT))) {

        lds += a -> lda;
        val sfx = suffix(va, lds(a));
        trigger(NetMessage(self, a, AcceptSync(nL, sfx, lds(a))) -> net);

        if(lc != 0) {
          trigger(NetMessage(self, a, Decide(ld, nL)) -> net);
        }

      }
    }

    case NetMessage(header, AcceptSync(xL, sfx, ldp)) => {

      val p = header.src
      if ((nProm == xL) && (state == (FOLLOWER, PREPARE))) {
        na = xL;
        va = prefix(va, ldp) ++ sfx;
        trigger(NetMessage(self, p, Accepted(xL, va.size)) -> net);
        state = (FOLLOWER, ACCEPT);
      }

    }

    case NetMessage(header, Accept(xL, c)) => {
      val p = header.src

      if ((nProm == xL) && (state == (FOLLOWER, ACCEPT))) {
        va = va ++ List(c);
        trigger(NetMessage(self, p, Accepted(xL, va.size)) -> net);
      }

    }

    case NetMessage(header, Decide(l, xL)) => {

      if(nProm == xL) {
        while(ld < l) {
          trigger(SC_Decide(va(ld)) -> sc);
          ld += 1;
        }
      }

    }

    case NetMessage(header, Accepted(n, m)) => {
      val a = header.src

      if ((n == nL) && (state == (LEADER, ACCEPT))) {
        las += a -> m;

        if(lc < m && las.count(x => x._2 >= m) >= majority) {
          lc = m;

          for(p <- topology){

            if(lds.exists(x => x._1 == p)) {
              trigger(NetMessage(self, p, Decide(lc, nL)) -> net);
            }

          };

        }

      }
    }
  }

  sc uponEvent {
    case SC_Propose(c) => {

      if (state == (LEADER, PREPARE)) {
        propCmds = propCmds ++ List(c);
      } else if (state == (LEADER, ACCEPT)) {

        va = va ++ List(c);
        las(self) = las(self) + 1;
        for(p <- others) {
          if(lds.exists(x => x._1 == p)) {
            trigger(NetMessage(self, p, Accept(nL, c)) -> net);
          }
        };

      }
    }
  }
}
