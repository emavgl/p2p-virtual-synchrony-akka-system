package it.unitn.ds1.Actors;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;

import akka.actor.ActorRef;
import akka.actor.AbstractActor;
import akka.actor.Props;

import it.unitn.ds1.Messages.*;
import it.unitn.ds1.Models.State;
import it.unitn.ds1.Models.View;
import org.apache.log4j.Logger;
import scala.concurrent.duration.Duration;

/**
    Actor class
 */
public abstract class Actor extends AbstractActor {

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
		protected Thread keyListenerThread;
		protected Thread runThread;

		/* -- Actor constructor --------------------------------------------------- */
		public Actor(String groupManagerHostPath) {
			this.groupManagerHostPath = groupManagerHostPath;
			this.init();
		}

		protected void init(){
			this.view = null;
			this.state = State.INIT;
			this.msgBuffer = new HashSet<>();
			this.flushes = new HashMap<> ();
			this.keyListenerThread  = new Thread(() -> {
				while (true){
					Scanner keyboard = new Scanner(System.in);
					boolean exit = false;
					while (!exit) {
						String input = keyboard.next();
						if(input != null) {
							if ("c".equals(input)) {
								System.out.println("Key pressed, crash mode on");
								this.crash(100000);
							} else if ("x".equals(input)) {
								//Do something
							}
						}
					}
					keyboard.close();
				}
			});
		}

		@Override
		public void preStart() {
		}

	/*
		* RUN
		* */
		protected void run(){
			switch (this.state){
				case NORMAL:
					if (this.view.getMembers().size() > 1){
						sendMulticastMessage();
					}
					break;
				case PAUSE:
					break;
				case CRASHED:
					break;
				case INIT:
					break;
			}
			randomSleep();
		}

		/*
		* Sending and Receiving Helper functions
		* */
		protected void sendUnstableMessages() {
			for (ChatMessage m : this.msgBuffer){
				multicast(m, this.view.getMembers().values());
			}
		}
		protected void sendFlush(){
			logger.info(String.format("[%d] - [-> %s] flush for view %d",
					this.id, this.proposedView.getMembers().keySet().toString(), this.proposedView.getId()));

			this.flushes.put(this.proposedView.getId(), new HashSet<>());
			multicast(new FlushMessage(this.id, this.proposedView.getId()), this.proposedView.getMembers().values());
		}

		protected void installView(View v){
			this.state = State.PAUSE;
			this.view = new View(v.getId(), v.getMembers());
			this.proposedView = null;
			this.flushes.clear();
			logger.info(String.format("[%d] - install view %d %s", this.id, this.view.getId(),
					this.view.getMembers().keySet().toString()));
			this.state = State.NORMAL;
			this.run();
		}

		protected void sendMulticastMessage(){
			this.contentCounter++;
			logger.info(String.format("[%d] - [-> %s] send chatmsg %d within %d",
					this.id, this.view.getMembers().keySet().toString(),
					this.contentCounter, this.view.getId()));
			ChatMessage m = new ChatMessage(this.id, this.contentCounter);
			this.multicast(m, this.view.getMembers());
        }

        protected void deliverMessage(ChatMessage message){
			logger.info(String.format("[%d] - [<- %d] deliver chatmsg %d within %d", this.id,
					message.senderId, message.content, this.view.getId()));
			this.run();
		}

        protected void multicast(Serializable m, Collection<ActorRef> participants) {
			for (ActorRef p: participants) {
				p.tell(m, getSelf());
				randomSleep();
			}
        }

		protected void multicast(ChatMessage m, Map<Integer, ActorRef> participants) {
			for (Map.Entry<Integer, ActorRef> entry: participants.entrySet()){
				logger.info(String.format("[%d] - [-> %d] sending message %d", this.id, entry.getKey(), m.content));
				entry.getValue().tell(m, getSelf());
				//this.crash(10000);
				this.randomSleep();
			}
		}

		protected void randomSleep() {
			long randomSleepTime = (long)(Math.random()*3000);
			try {
				Thread.sleep(randomSleepTime);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}


		protected void crash(long recoveryTime) {
			// Cannot crash on class actor
		}

		/*
		* CALLBACKS
		* */
		protected void onNewIDMessage(NewIDMessage message) {
			if (this.state == State.CRASHED) return;

			logger.info(String.format("[%d] - [<- %d] new id %d", this.id, message.senderId, message.id));
			this.id = message.id;
		}
		protected void onViewChangeMessage(ViewChangeMessage message) {
			if (this.state == State.CRASHED) return;

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
		protected void onFlushMessage(FlushMessage message){
			if (this.state == State.CRASHED) return;

			logger.info(String.format("[%d] - [<- %d] flush for view %d",
						this.id, message.senderId, message.proposedViewId));

			Set<Integer> s = this.flushes.get(message.proposedViewId);
			if (s == null) this.flushes.put(message.proposedViewId, new HashSet<>());
			this.flushes.get(message.proposedViewId).add(message.senderId);

			if (this.proposedView != null){
				Set<Integer> requestReceivedForProposedView = this.flushes.get(this.proposedView.getId());
				boolean complete = requestReceivedForProposedView.equals(this.proposedView.getMembers().keySet());
				if (complete) this.installView(this.proposedView);
			}
		}
		protected void onHeartBeatMessage(HeartBeatMessage message) {
			if (this.state == State.CRASHED) return;

			logger.info(String.format("[%d] - [-> 0] heartbeat", this.id));

			HeartBeatMessage hbm = new HeartBeatMessage(this.id);
			getSender().tell(hbm, getSelf());
		}
		protected void onChatMessage(ChatMessage message){
			if (this.state == State.CRASHED) return;

			if (this.view == null) return; // Joining node - Ignores incoming unstable message
			boolean added = this.msgBuffer.add(message);
			if (added) this.deliverMessage(message);
		}



}