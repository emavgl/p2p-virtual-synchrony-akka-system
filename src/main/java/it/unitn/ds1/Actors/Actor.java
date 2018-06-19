package it.unitn.ds1.Actors;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;

import akka.actor.ActorRef;
import akka.actor.AbstractActor;
import akka.actor.Props;

import it.unitn.ds1.Helpers.SenderHelper;
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
		public View view;
		protected View proposedView;

		protected String groupManagerHostPath = null;
		protected int id;
		protected int contentCounter = 0;
		protected State state;
		protected Set<ChatMessage> msgBuffer;
		protected HashMap<Integer, Set<Integer>> flushes;
		protected Thread keyListenerThread;
		protected boolean chatMessageLoopStarted = false;
		protected final int Td = 10000;
		protected SenderHelper senderHelper;

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
			this.senderHelper = new SenderHelper(this.Td, this);
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
			if (!chatMessageLoopStarted){
				int timeForTheNextMessage = new Random().nextInt(this.Td - 2000) + 2000;
				scheduleMessage(new SendNewChatMessage(this.id), getSelf(), timeForTheNextMessage);
				this.chatMessageLoopStarted = true;
			}
		}

		/*
		* Sending and Receiving Helper functions
		* */

		protected void sendUnstableMessages() {
			logger.info(String.format("[%d -> %s] unstable messages within view %d",
					this.id, this.view.getMembers().keySet().toString(), this.view.getId()));

			for (ChatMessage m : this.msgBuffer){
				multicast(m, this.view.getMembers());
			}
		}

		/**
		 * Send a flush message for the proposedView in multicast
		 */
		protected void sendFlush(){
			// Init the flushes list for the proposedView
			this.flushes.put(this.proposedView.getId(), new HashSet<>());

			// Send in multicast the flush message
			int timeForTheNextMessage = new Random().nextInt(this.Td - 2000) + 2000;
			SendNewFlushMessage snfm = new SendNewFlushMessage(this.id);
			scheduleMessage(snfm, getSelf(), timeForTheNextMessage );
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

		protected void onSendNewChatMessage(SendNewChatMessage message){
			// Create and send a new ChatMessage in multicast when the state is NORMAL
			if (this.state == State.NORMAL && this.view.getMembers().size() > 1){
				this.contentCounter++;
				logger.info(String.format("[%d -> %s] send chatmsg %d within %d",
						this.id, this.view.getMembers().keySet().toString(),
						this.contentCounter, this.view.getId()));
				ChatMessage m = new ChatMessage(this.id, this.contentCounter);
				this.multicast(m, this.view.getMembers());
			}

			// Schedule new SendNewChatMessage
			// Do it in any case, even if the node crashed or is in pause
			// This methods is here only to simulate a loop
			int timeForTheNextMessage = new Random().nextInt(this.Td - 2000) + 2000;
			scheduleMessage(new SendNewChatMessage(this.id), getSelf(), timeForTheNextMessage);
        }

        protected void deliverMessage(ChatMessage message){
			logger.info(String.format("[%d <- %d] deliver chatmsg %d within %d", this.id,
					message.senderId, message.content, this.view.getId()));
		}


		protected void randomSleep() {
			long randomSleepTime = (long)(Math.random()*3000);
			try {
				Thread.sleep(randomSleepTime);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		protected void scheduleMessage(Message m,  ActorRef receiver, int time){
			getContext().system().scheduler().scheduleOnce(
					Duration.create(time, TimeUnit.MILLISECONDS),
					receiver,
					m,
					getContext().system().dispatcher(), getSelf()
			);
		}

		protected void scheduleMessageMulticast(Message m,  Collection<ActorRef> receivers, int time){
			for (ActorRef receiver: receivers) {
				getContext().system().scheduler().scheduleOnce(
						Duration.create(time, TimeUnit.MILLISECONDS),
						receiver,
						m,
						getContext().system().dispatcher(), getSelf()
				);
			}
		}

		protected void crash(int recoveryTime) {
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

		/**
		 * Callback: on receiving a new ViewChangeMessage
		 * Set proposedView, send unstable messages and flushes
		 * @param message: ViewChangeMessage
		 */
		protected void onViewChangeMessage(ViewChangeMessage message) {
			if (this.state == State.CRASHED) return;

			logger.info(String.format("[%d <- 0] request new view %d", this.id,
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

		/**
		 * Callback: when receiving a new FlushMessage
		 * @param message FlushMessage
		 */
		protected void onFlushMessage(FlushMessage message){
			if (this.state == State.CRASHED) return;

			logger.info(String.format("[%d <- %d] flush for view %d",
						this.id, message.senderId, message.proposedViewId));

			// Register the flush message on the flush list
			Set<Integer> s = this.flushes.get(message.proposedViewId);
			if (s == null) this.flushes.put(message.proposedViewId, new HashSet<>());
			this.flushes.get(message.proposedViewId).add(message.senderId);

			// Check if I received all the flush messages
			if (this.proposedView != null){
				Set<Integer> requestReceivedForProposedView = this.flushes.get(this.proposedView.getId());
				boolean complete = requestReceivedForProposedView.equals(this.proposedView.getMembers().keySet());
				if (complete) this.installView(this.proposedView);
			}
		}

		protected void onHeartBeatMessage(HeartBeatMessage message) {
			if (this.state == State.CRASHED) return;

			logger.info(String.format("[%d -> 0] heartbeat", this.id));

			HeartBeatMessage hbm = new HeartBeatMessage(this.id);
			getSender().tell(hbm, getSelf());
		}

		/**
		 * Callback: on new ChatMessage Received
		 * Add the message into the buffer, deliver the message
		* @param message
		*/
		protected void onChatMessage(ChatMessage message){
			if (this.state == State.CRASHED) return;

			if (this.view == null) return; // Joining node - Ignores incoming unstable message
			boolean added = this.msgBuffer.add(message);
			if (added) this.deliverMessage(message);
		}

		protected void onSendNewFlushMessage(SendNewFlushMessage message){
			
			logger.info(String.format("[%d -> %s] flush for view %d",
					this.id, this.proposedView.getMembers().keySet().toString(), this.proposedView.getId()));

			FlushMessage fm = new FlushMessage(this.id, this.proposedView.getId());
			this.multicast(fm, this.proposedView.getMembers(), "Flush");
		}
}