package it.unitn.ds1.Actors;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import akka.actor.ActorRef;
import akka.actor.AbstractActor;
import akka.actor.Props;

import it.unitn.ds1.Helpers.SenderHelper;
import it.unitn.ds1.Messages.*;
import it.unitn.ds1.Models.MessageQueue;
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
		public State state;
		protected Set<ChatMessage> msgBuffer;
		protected Set<ChatMessage> msgDelivered;
		protected HashMap<Integer, Set<Integer>> flushes;
		protected Thread keyListenerThread;
		protected boolean chatMessageLoopStarted;
		protected SenderHelper senderHelperLog;

        abstract void crash(int time);

    /* -- Actor constructor --------------------------------------------------- */
		public Actor(String groupManagerHostPath) {
			this.groupManagerHostPath = groupManagerHostPath;
			this.init();
		}

		protected void init(){
			this.view = null;
			this.state = State.INIT;
			this.msgBuffer = new HashSet<>();
			this.msgDelivered = new HashSet<>();
			this.flushes = new HashMap<> ();
			this.chatMessageLoopStarted = false;
			this.senderHelperLog = new SenderHelper(this, true);
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
            try {
                super.preStart();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    /**
         * Callback: on receiving of a new SendNewChatMessage
         * It create and enque a new ChatMessage
         * @param message
         */
		protected void onSendNewChatMessage(SendNewChatMessage message){
            if (this.state == State.CRASHED) return;

            if (state == State.NORMAL && this.view.getMembers().size() > 1){
		        senderHelperLog.enqMulticast(new ChatMessage(this.id, this.contentCounter++), this.view.getMembers(), -1, true);
            }
            senderHelperLog.scheduleControlMessage(message, 15000);
        }

		/**
		 * Callback: on new ChatMessage Received
		 * Add the message into the buffer, deliver the message
		 * @param message
		 */
		protected void onChatMessage(ChatMessage message){
			if (this.state == State.CRASHED) return;
			if (this.view == null) return; // Joining node - Ignores incoming unstable message
			this.canDeliverMessage(message); // do not deliver the message twice
		}

		/**
		 * Add message to buffer, deliver all the possible messages and remove stable messages from sender
		 * Useful only to check if it is possible to deliver the received chatMessage
		 * Not useful for other messages, for example, flushes, not possibile to receive
		 * a flush message and be a view ahead.
		 * @param message
		 */
		protected void canDeliverMessage(ChatMessage message){
			// Should always enter inside the if
			// ViewID is set by the senderHelper
			if (message.viewId != null){
				if (message.viewId > this.view.getId()) {
					// Add but not deliver
					this.msgBuffer.add(message);
					return;
				}
				else if (message.viewId < this.view.getId()) return; // Impossible, last msg should be a flush, if so, ignore
			}

			// If here, the viewId matches
			// Add to the msgBuffer
			this.msgBuffer.add(message);

			// Check and deliver previous messages
			for (ChatMessage m: this.msgBuffer) {
				if (this.view.getId() == m.viewId && !this.msgDelivered.contains(m)) {
					this.msgDelivered.add(m);
					logger.info(String.format("[%d <- %d] deliver chatmsg %d within %d", this.id,
							m.senderId, m.content, this.view.getId()));
				}
			}

			// Remove all stable messages of senderId from buffer
			this.msgBuffer.removeIf(m -> m.senderId == message.senderId && m.content < message.content);
		}

		/*
		* Sending and Receiving Helper functions
		* */
		protected void sendUnstableMessages() {
			logger.info(String.format("[%d -> %s] unstable messages within view %d",
					this.id, this.view.getMembers().keySet().toString(), this.view.getId()));

			for (Message m : this.msgBuffer){
				this.senderHelperLog.enqMulticast(m, this.view.getMembers(),  -1, true);
			}
		}

		protected void installView(View v){
			this.proposedView = null;
			this.flushes.clear();

			logger.info(String.format("[%d] - install view %d %s", this.id, v.getId(),
					v.getMembers().keySet().toString()));

            this.view = new View(v.getId(), v.getMembers());

            this.state = State.NORMAL;
            this.startChatMessageLoop();
		}

        private void startChatMessageLoop() {
		    if (!this.chatMessageLoopStarted){
                senderHelperLog.scheduleControlMessage(new SendNewChatMessage(this.id), 2000);
		        this.chatMessageLoopStarted = true;
            }
        }

        /**
		 * Callback: on receiving a new ViewChangeMessage
		 * Set proposedView, send unstable messages and flushes
		 * @param message: ViewChangeMessage
		 */
		protected void onViewChangeMessage(ViewChangeMessage message) {
			if (this.state == State.CRASHED) return;

			logger.info(String.format("[%d <- 0] Received a request for new view %d", this.id,
					message.view.getId()));

			this.state = State.PAUSE;
			this.proposedView = message.view;

			if (this.msgBuffer.size() > 0){
				// Or we are a new node
				// Or we don't have msg in the buffer
				// sendUnstableMessages();
			}

			sendFlush();
		}


		/**
		 * Initialize flush list and schedule a SendFlushMessage
		 */
		protected void sendFlush(){

			// Init the flushes list for the proposedView
			if (this.flushes.get(this.proposedView.getId()) == null){
				this.flushes.put(this.proposedView.getId(), new HashSet<>());
			}

			// Send in multicast the flush message
            FlushMessage fm = new FlushMessage(this.id, this.proposedView.getId());
            this.senderHelperLog.enqMulticast(fm, this.proposedView.getMembers(), -1, true);
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
			if (this.proposedView != null && this.proposedView.getId() >= message.proposedViewId){
				Set<Integer> requestReceivedForProposedView = this.flushes.get(this.proposedView.getId());
				boolean complete = requestReceivedForProposedView.equals(this.proposedView.getMembers().keySet());
				if (complete) this.installView(this.proposedView);
			}
		}

		/*
		* Set the ActorID
		* */
		protected void onNewIDMessage(NewIDMessage message) {
			if (this.state == State.CRASHED) return;

			logger.info(String.format("[%d] - [<- %d] new id %d", this.id, message.senderId, message.id));
			this.id = message.id;
		}


		protected void onHeartBeatMessage(HeartBeatMessage message) {
			if (this.state == State.CRASHED) return;
            HeartBeatMessage hbm = new HeartBeatMessage(this.id);
            Map<Integer, ActorRef> receiver = new HashMap<>();
            receiver.put(0, getSender());
            this.senderHelperLog.enqMulticast(hbm, receiver, 3000, true);
		}
}