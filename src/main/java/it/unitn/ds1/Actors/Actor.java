package it.unitn.ds1.Actors;
import java.io.Serializable;
import java.util.*;

import akka.actor.ActorRef;
import akka.actor.AbstractActor;
import akka.actor.Props;

import it.unitn.ds1.Messages.*;
import it.unitn.ds1.Models.State;
import it.unitn.ds1.Models.View;
import org.apache.log4j.Logger;

/**
    Actor class
 */
public class Actor extends AbstractActor {

		protected static final Logger logger = Logger.getLogger("DS1");

		// The table of all nodes in the system id->ref
		protected View view;
		protected View proposedView;

		protected String groupManagerHostPath = null;
		protected int id;
		protected int contentCounter = 0;
		protected State state;
		protected Set<ChatMessage> msgBuffer;
		protected HashMap<Integer, Set<Integer>> flushes;

		/* -- Actor constructor --------------------------------------------------- */
		public Actor(String groupManagerHostPath) {
			this.groupManagerHostPath = groupManagerHostPath;
			this.view = null;
			this.msgBuffer = new HashSet<>();
			this.id = -(int)(Math.random() * 1000);
			this.state = State.PAUSE;
			this.flushes = new HashMap<> ();
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
			if (this.id < 0 ) {
				logger.info(String.format("[%d] - %s", this.id, "Node started"));
				logger.info(String.format("[%d] - [-> 0] join request ", this.id));
				getContext().actorSelection(groupManagerHostPath).tell(new RequestJoinMessage(this.id), getSelf());
			}
			run();
		}

		protected void onViewChangeMessage(ViewChangeMessage message) {
			logger.info(String.format("[%d] - [<- 0] request new view %d", this.id,
					message.view.getId()));
			this.state = State.PAUSE;
			this.proposedView = message.view;

			if (this.msgBuffer.size() > 0){
				// Or we are a new node
				// Or we don't have msg in the buffer
				sendUnstableMessages();
			}

			sendFlush();
		}


		protected void sendUnstableMessages() {
			for (ChatMessage m : this.msgBuffer){
				multicast(m, this.view.getMembers().values());
			}
		}

		protected void sendFlush(){
			logger.info(String.format("[%d] - [-> %s] flush for view %d",
					this.id, this.proposedView.getMembers().keySet().toString(), this.proposedView.getId()));
			multicast(new FlushMessage(this.id, this.proposedView.getId()), this.proposedView.getMembers().values());
		}

		protected void run(){
			int i = 0;
			while (i < 2){
				switch (this.state){
					case NORMAL:
						sendMulticastMessage();
						break;
					case PAUSE:
						break;
					case CRASHED:
						break;
				}
				randomSleep();
				i++;
			}
		}

		protected void installView(View v){
			this.view = new View(v.getId(), v.getMembers());
			this.proposedView = null;
			this.flushes.clear();
			logger.info(String.format("[%d] - install view %d %s", this.id, this.view.getId(),
					this.view.getMembers().keySet().toString()));
		}

		protected void sendMulticastMessage(){
			this.contentCounter++;
			logger.info(String.format("[%d] - [-> %s] send chatmsg %d within %d",
					this.id, this.view.getMembers().keySet().toString(),
					this.contentCounter, this.view.getId()));
			ChatMessage m = new ChatMessage(this.id, this.contentCounter);
			multicast(m, this.view.getMembers().values());
        }

        protected void onChatMessage(ChatMessage message){
			if (this.view == null) return; // Joining node - Ignores incoming unstable message
			logger.info(String.format("[%d] - [<- %d] ChatMessage %d", this.id, message.senderId, message.content));
			boolean added = this.msgBuffer.add(message);
			if (added) this.deliverMessage(message);
        }

        protected void deliverMessage(ChatMessage message){
			logger.info(String.format("[%d] - [<- %d] deliver chatmsg %d within %d", this.id,
					message.senderId, message.content, this.view.getId()));
		}

        protected void multicast(Serializable m, Collection<ActorRef> participants) {
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
			logger.info(String.format("[%d] - [<- %d] new id %d", this.id, message.senderId, message.id));
			this.id = message.id;
		}

		protected void onFlushMessage(FlushMessage message){
			logger.info(String.format("[%d] - [<- %d] flush for view %d",
					this.id, message.senderId, message.proposedViewId));
			Set<Integer> s = this.flushes.get(message.proposedViewId);
			if (s == null) this.flushes.put(message.proposedViewId, new HashSet<>());
			this.flushes.get(message.proposedViewId).add(message.senderId);
			if (this.proposedView != null){
				boolean complete = this.flushes.get(this.proposedView.getId()).equals(this.proposedView.getMembers().keySet());
				if (complete) this.installView(this.proposedView);
			}
		}

	/**
			Registers callbacks
		 */
		@Override
		public Receive createReceive() {
		return receiveBuilder()
			.match(NewIDMessage.class, this::onNewIDMessage)
			.match(ViewChangeMessage.class, this::onViewChangeMessage)
			.match(ChatMessage.class, this::onChatMessage)
			.match(FlushMessage.class, this::onFlushMessage)
			.build();
		}



}