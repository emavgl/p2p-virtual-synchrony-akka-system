package it.unitn.ds1.Actors;
import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.HashMap;

import akka.actor.ActorRef;
import akka.actor.AbstractActor;
import akka.actor.Props;

import it.unitn.ds1.Messages.*;

/**
    Actor class
 */
public class Actor extends AbstractActor {

		// The table of all nodes in the system id->ref
		protected Map<Integer, ActorRef> nodes = new HashMap<>();
		protected String groupManagerHostPath = null;
		protected int id = -1;
		protected int contentCounter = 0;

		/* -- Actor constructor --------------------------------------------------- */
		public Actor(String groupManagerHostPath) {
			this.groupManagerHostPath = groupManagerHostPath;
		}

		/**
			Akka - Build from constructor
		 */
		static public Props props(String remotePath) {
			return Props.create(Actor.class, () -> new Actor(remotePath));
		}

		/**
			Ask to itself for the NodeList and add itself as a new node in its local list
		 */
		@Override
		public void preStart() {
			if (this.id == -1 ) {
				System.out.println("Id not assigned");
				getContext().actorSelection(groupManagerHostPath).tell(new RequestIDMessage(), getSelf());
			} else if (this.id > 0) {
				this.nodes.put(this.id, getSelf());
				System.out.println("Id assigned" + this.id);
				getContext().actorSelection(this.groupManagerHostPath).tell(new RequestNodelist(), getSelf());
			}
		}

		/**
			Callback on receiving of RequestNodeList
			returns the this.nodes
		 */
		protected void onRequestNodelist(RequestNodelist message) {
			getSender().tell(new Nodelist(nodes), getSelf());
		}

		/**
			Callback on receiving of NodeList Message
			Update its local node list with the receiving one
			Then, sends a Join message to everyone in that list
		 */
		protected void onNodelist(Nodelist message) {
			nodes.putAll(message.nodes);
			for (ActorRef n: nodes.values()) {
				n.tell(new Join(this.id), getSelf());
			}
		}

		/**
			Callback on receiving of the Join message
			Add the joining node to its local list of nodes
		 */
		protected void onJoin(Join message) {
			int id = message.id;
			System.out.println("Node " + id + " joined");
			nodes.put(id, getSender());
			// Here ends the start configuration of each new node who is joining in the group
            startChat();
		}

		protected void startChat(){
		    // Create a new unique content for this.id
            ChatMessage m = new ChatMessage(contentCounter, this.id);
            this.sendMulticastMessage(m, this.nodes.values());
            System.out.println("I (" + this.id + ") have sent a new message with the content " + contentCounter);
			contentCounter++;
        }

        protected void onChatMessage(ChatMessage msg){
            System.out.println("I (" + this.id + ") have RECEIVED " +  msg.content  + " from " + msg.senderId );
        }

        protected void sendMulticastMessage(Serializable m, Collection<ActorRef> participants) {
			for (ActorRef p: participants) {
				p.tell(m, getSelf());
				randomSleep();
			}
        }

		protected void randomSleep() {
			long randomSleepTime = (long)(Math.random()*1000);
			try {
				Thread.sleep(randomSleepTime);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		private void onNewIDMessage(NewIDMessage message) {
			this.id = message.id;
			// Recall preStart
			this.preStart();
		}

	/**
			Registers callbacks
		 */
		@Override
		public Receive createReceive() {
		return receiveBuilder()
			.match(NewIDMessage.class, this::onNewIDMessage)
			.match(RequestNodelist.class, this::onRequestNodelist)
			.match(Nodelist.class, this::onNodelist)
            .match(ChatMessage.class, this::onChatMessage)
			.match(Join.class, this::onJoin)
			.build();
		}


}