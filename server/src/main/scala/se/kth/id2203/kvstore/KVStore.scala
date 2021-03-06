/*
 * The MIT License
 *
 * Copyright 2017 Lars Kroll <lkroll@kth.se>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package se.kth.id2203.kvstore;

import se.kth.id2203.SequencePaxos.{SC_Decide, SequenceConsensus}
import se.kth.id2203.networking._
import se.kth.id2203.overlay.{CommandBroadcast, Routing}
import se.sics.kompics.sl._
import se.sics.kompics.network.Network;

class KVService extends ComponentDefinition {

  //******* Ports ******
  val net = requires[Network];
  val route = requires(Routing);
  val sc = requires[SequenceConsensus];
  //******* Fields ******
  val self = cfg.getValue[NetAddress]("id2203.project.address");
  var store:Map[String,String] = Map("hola" -> "hi", "coche" -> "car", "gato" -> "cat")
  //******* Handlers ******
  net uponEvent {
    case NetMessage(header, op @ Get(key, _)) => {
      log.info("Got operation {}! Now implement me please :)", op);
      store.get(key) match {
        case Some(value) => trigger(NetMessage(self, header.src, op.response(OpCode.Ok, Some(value))) -> net)
        case None => trigger(NetMessage(self, header.src, op.response(OpCode.NotFound, None)) -> net)
      }
    }
    case NetMessage(header, op @ Put(key, value, _)) => {
      log.info("Got operation {}! Now implement me please :)", op);
      store += (key -> value);
      trigger(NetMessage(self, header.src, op.response(OpCode.Ok, store.get(op.key))) -> net)
    }
  }

  sc uponEvent {
    case SC_Decide(CommandBroadcast(header, op @ Get(key, _))) => {
      log.info("Got operation {}! Now implement me please :)", op);
      store.get(key) match {
        case Some(value) => trigger(NetMessage(self, header.src, op.response(OpCode.Ok, Some(value))) -> net)
        case None => trigger(NetMessage(self, header.src, op.response(OpCode.NotFound, None)) -> net)
      }
    }
    case SC_Decide(CommandBroadcast(header, op @ Put(key, value, _))) => {
      log.info("Got operation {}! Now implement me please :)", op);
      store += (key -> value);
      trigger(NetMessage(self, header.src, op.response(OpCode.Ok, store.get(op.key))) -> net)
    }
  }
}
