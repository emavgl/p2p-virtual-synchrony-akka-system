package it.unitn.ds1.Actors;
import java.io.Serializable;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import akka.actor.ActorRef;
import akka.actor.AbstractActor;
import akka.actor.ActorSystem;
import akka.actor.Props;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.Config;

import it.unitn.ds1.Messages.*;
	/**
		Actor class
	 */
public class Actor extends AbstractActor {

		// The table of all nodes in the system id->ref
		private Map<Integer, ActorRef> nodes = new HashMap<>();
		private String remotePath = null;
		private int id;

		/* -- Actor constructor --------------------------------------------------- */
		public Actor(int id, String remotePath) {
			this.id = id;
			this.remotePath = remotePath;
		}

		/**
			Akka - Build from constructor
		 */
		static public Props props(int id, String remotePath) {
			return Props.create(Actor.class, () -> new Actor(id, remotePath));
		}

		/**
			Ask to itself for the NodeList and add itself as a new node in its local list
		 */
		public void preStart() {
			if (this.remotePath != null) {
				getContext().actorSelection(remotePath).tell(new RequestNodelist(), getSelf());
		}
			nodes.put(this.id, getSelf());
		}

		/**
			Callback on receiving of RequestNodeList
			returns the this.nodes
		 */
		private void onRequestNodelist(RequestNodelist message) {
			getSender().tell(new Nodelist(nodes), getSelf());
		}

		/**
			Callback on receiving of NodeList Message
			Update its local node list with the receiving one
			Then, sends a Join message to everyone in that list
		 */
		private void onNodelist(Nodelist message) {
			nodes.putAll(message.nodes);
			for (ActorRef n: nodes.values()) {
				n.tell(new Join(this.id), getSelf());
			}
		}

		/**
			Callback on receiving of the Join message
			Add the joining node to its local list of nodes
		 */
		private void onJoin(Join message) {
			int id = message.id;
			System.out.println("Node " + id + " joined");
			nodes.put(id, getSender());
		}

		/**
			Registers callbacks
		 */
		@Override
		public Receive createReceive() {
		return receiveBuilder()
			.match(RequestNodelist.class, this::onRequestNodelist)
			.match(Nodelist.class, this::onNodelist)
			.match(Join.class, this::onJoin)
			.build();
		}
}