package it.unitn.ds1.Actors;
import java.util.*;

import akka.actor.AbstractActor;

import it.unitn.ds1.Helpers.SenderHelper;
import it.unitn.ds1.Messages.*;
import it.unitn.ds1.Models.State;
import it.unitn.ds1.Models.View;
import org.apache.log4j.Logger;
import scala.util.control.Exception;

/**
    Actor class
 */
public abstract class Actor extends AbstractActor {

	protected static final Logger logger = Logger.getLogger("DS1");

	// The table of all nodes in the system id->ref
	public View view;
	public View proposedView;

	protected String groupManagerHostPath = null;
	protected int id;
	protected int contentCounter = 0;
	public State state;
	protected Set<ChatMessage> msgBuffer;
	protected Set<ChatMessage> msgDelivered;
	protected HashMap<Integer, Set<Integer>> flushes;
	protected Thread keyListenerThread;
	protected boolean chatMessageLoopStarted;
	protected SenderHelper senderHelper;

	public abstract void crash(int time);

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
		this.senderHelper = new SenderHelper(this, true);
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
						} else if ("s".equals(input)) {
							//Do something
							System.out.println("Key pressed, start");
							getSelf().tell(new RecoveryMessage(this.id), getSelf());
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
		} catch (java.lang.Exception e) {
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
			senderHelper.enqMulticast(new ChatMessage(this.id, this.contentCounter++), -1, true);

			// TODO: FOR CRASH TESTING
			//if (this.contentCounter == 2 && this.id == 2) senderHelper.enqMulticastWithCrash(new ChatMessage(this.id, this.contentCounter++), -1, true);
			//else senderHelper.enqMulticast(new ChatMessage(this.id, this.contentCounter++), -1, true);
		}
		senderHelper.scheduleControlMessage(message, 15000);
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
	 * Add message to buffer, deliver all the possible messages and remove stable messages from buffer
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
			else if (message.viewId < this.view.getId()){
				// Impossible, last msg should be a flush, if so, ignore
				logger.error("Received a message from a previous view");
				return;
			}
		}

		// TODO: FOR CRASH TESTING
		/*
		if (this.id == 1 && message.content == 2) {
			logger.info("Intentionally crash during deliver of the message " + message.content);
			this.crash(90000);
			return;
		}
		*/

		// If here, the viewId matches
		// Add to the msgBuffer
		this.msgBuffer.add(message);

		// Check and deliver previous messages
		for (ChatMessage m: this.msgBuffer) {
			if (this.view.getId() == m.viewId && !this.msgDelivered.contains(m)) {
				this.msgDelivered.add(m);
				logger.info(String.format("%d deliver multicast %d from %d within %d", this.id,
						m.content, m.senderId, this.view.getId()));
			}
		}

		// Remove all stable messages of senderId from buffer
		this.msgBuffer.removeIf(m -> m.senderId == message.senderId && m.content < message.content);
	}

	/*
	* Sending and Receiving Helper functions
	* */
	protected void sendUnstableMessages() {
		logger.info(String.format("%d send unstable messages #%d within view %d",
				this.id, this.msgBuffer.size(), this.view.getId()));

		UnstableMessage unstableMessage = new UnstableMessage(this.id, this.msgBuffer);
		this.senderHelper.enqMulticast(unstableMessage,  -1, true);
	}

	protected void installView(View v){
		this.proposedView = null;
		this.flushes.clear();
		this.msgBuffer.clear();

		// TODO: FOR CRASH TESTING
		/*
		if (this.id == 1) {
			logger.info("Intentionally crash during processing of the ViewChangeMessage");
			this.crash(1000000);
			return;
		}
		*/

		String viewListRepresentation =  v.getMembers().keySet().toString();
		String participants = viewListRepresentation.substring(1, viewListRepresentation.length() - 1);
		participants = participants.replace(" ", "");
		logger.info(String.format("%d install view %d %s", this.id, v.getId(), participants));

		this.view = new View(v.getId(), v.getMembers());

		this.state = State.NORMAL;
		this.startChatMessageLoop();
	}

	private void startChatMessageLoop() {
		if (!this.chatMessageLoopStarted){
			senderHelper.scheduleControlMessage(new SendNewChatMessage(this.id), 2000);
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
		if (this.proposedView != null && message.view.getId() < this.proposedView.getId()) return;

		logger.info(String.format("[%d <- 0] Received a request for new view %d", this.id,
				message.view.getId()));

		this.state = State.PAUSE;

		// TODO: FOR CRASH TESTING
		/*
		if (this.id == 1) {
			logger.info("Intentionally crash during processing of the ViewChangeMessage");
			this.crash(1000000);
			return;
		}
		*/

		// from now on, no new chatmessage are inserted in the queue
		// to ensure that the senderHelper will no enque and send other
		// chatmessage before a new view is installed, we remove all
		// chat messages previous enqueued in the send queue
		this.senderHelper.removeChatMessages();

		// Set the proposed view
		this.proposedView = message.view;

		// Init the flushes list for the proposedView
		if (this.flushes.get(this.proposedView.getId()) == null){
			this.flushes.put(this.proposedView.getId(), new HashSet<>());
		}


		if (this.msgBuffer.size() > 0){
			// Or we are a new node
			// Or we don't have msg in the buffer
			sendUnstableMessages();
		}

		// Send in multicast the flush message
		FlushMessage fm = new FlushMessage(this.id, this.proposedView.getId());
		this.senderHelper.enqMulticast(fm, -1, true);
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

	protected void onHeartBeatMessage(HeartBeatMessage message) {
		if (this.state == State.CRASHED) return;
		HeartBeatMessage hbm = new HeartBeatMessage(this.id);
		senderHelper.enqMessage(hbm, getSender(), 0, 3000, true);
	}

	protected void onUnstableMessage(UnstableMessage message){
		if (this.state == State.CRASHED) return;
		if (this.view == null) return; // Joining node - Ignores incoming unstable message

		logger.info("Received unstable messages");

		for (ChatMessage m : message.unstableMessages){
			this.canDeliverMessage(m);
		}
	}
}