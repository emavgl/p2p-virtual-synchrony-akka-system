package it.unitn.ds1.Helpers;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import it.unitn.ds1.Actors.Actor;
import it.unitn.ds1.Messages.ChatMessage;
import it.unitn.ds1.Messages.Message;
import it.unitn.ds1.Models.MessageQueue;
import it.unitn.ds1.Models.MessageRequest;
import it.unitn.ds1.Models.State;
import org.agrona.collections.IntHashSet;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class SenderHelper {


    protected static final Logger logger = Logger.getLogger("DS1");

    private ActorSystem system;
    private int td;
    private Actor actor;
    private boolean shouldLog;
    protected MessageQueue messageQueue;
    protected State state;

    public SenderHelper(Actor actor, boolean shouldLog) {
        this.actor = actor;
        this.system = this.actor.getContext().getSystem();
        this.shouldLog = shouldLog;
        this.messageQueue = new MessageQueue();
        this.state = State.NORMAL;
    }

    public void enqMulticast(Message m, int milliseconds, boolean shoudLog) {
        this.messageQueue.add(new MessageRequest(m, null, milliseconds, shoudLog));
        if (this.state == State.NORMAL) this.sendNextMessage();
    }

    public void enqMulticastWithCrash(Message m, int milliseconds, boolean shoudLog) {
        MessageRequest messageRequest = new MessageRequest(m, null, milliseconds, shoudLog);
        messageRequest.shouldCrash = true;
        this.messageQueue.add(messageRequest);
        if (this.state == State.NORMAL) this.sendNextMessage();
    }

    private void sendNextMessage(){
        if (this.messageQueue.isEmpty()){
            state = State.NORMAL;
        } else {
            // If it is busy, will schedule the next message by itself
            if (state != State.BUSY){
                state = State.BUSY;
                scheduleMessages(this.messageQueue.next());
            }
        }
    }

    private void scheduleMessages(MessageRequest mr){
        Set<Integer> sent = new HashSet<>();
        String messageType = mr.m.getClass().getSimpleName();
        String contentStr = "";
        String viewIdStr = "";
        Map<Integer, ActorRef> receivers = new HashMap<>();

        // Set content if it is in the message
        if (mr.m instanceof ChatMessage) {
            int content = ((ChatMessage)(mr.m)).content;
            contentStr = String.valueOf(content);
        }

        // Set view and receivers
        if (actor.view != null){
            final int currentViewId = actor.view.getId();
            mr.m.viewId = currentViewId;
            viewIdStr = String.valueOf(currentViewId);
        }

        // === Get the multicast receivers
        // If actor.proposedView is not null, we are in pause, we must send heartbeat and flushes
        // to all the nodes in the proposedview, to the view otherwise.
        // no chatmessage will be delivered to member of the proposedview, since
        // we will be in pause, until the next view installation

        if (mr.receivers == null) {
            // Multicast
            if (actor.proposedView != null){
                receivers.putAll(actor.proposedView.getMembers());
            } else {
                receivers.putAll(actor.view.getMembers());
            }
        } else {
            // Not multicast
            receivers.putAll(mr.receivers);
        }

        final String messageContent = contentStr;
        final String viewId = viewIdStr;
        Set<Map.Entry<Integer, ActorRef>> entries = receivers.entrySet();

        if (mr.shoudLog) {
            if (!contentStr.isEmpty()){
                // Log chatmessages
                logger.info(String.format("%d send multicast %s within %s",
                        mr.m.senderId, messageContent, viewId));
            } else {
                logger.info(String.format("[%d -> %s] sent %s within %s",
                        mr.m.senderId, receivers.keySet().toString(), messageType, viewId));
            }
        }

        int crashIndex = -1;
        if (mr.shouldCrash){
            crashIndex = new Random().nextInt(entries.size());
        }

        int i = 0;
        final int crashAt = crashIndex;
        for (Map.Entry<Integer, ActorRef> entry : entries) {

            int timeForTheNextMessage = new Random().nextInt(mr.milliseconds);
            this.system.scheduler().scheduleOnce(java.time.Duration.ofMillis(timeForTheNextMessage),
                    new Runnable() {
                        @Override
                        public void run() {

                            if (actor.state == State.CRASHED) return;

                            if (sent.size() == crashAt){
                                logger.info("Node " + mr.m.senderId  +  "  crashed intentionally after sending " + sent.size() + "nodes");
                                actor.crash(100000);
                            }

                            entry.getValue().tell(mr.m, actor.getSelf());
                            sent.add(entry.getKey());

                            if (sent.size() == entries.size()){
                                // Sent all the messages
                                // Do something
                                state = State.NORMAL;
                                sendNextMessage();
                            }

                        }
                    }, this.system.dispatcher());

            i++;
        }
    }

    public void scheduleControlMessage(Message m, int milliseconds){
        this.system.scheduler().scheduleOnce(java.time.Duration.ofMillis(milliseconds),
                new Runnable() {
                    @Override
                    public void run() {
                        actor.getSelf().tell(m, actor.getSelf());
                    }
                }, this.system.dispatcher());
    }

    public void enqMessage(Message m, ActorRef receiver, int receiverId, int milliseconds, boolean shoudLog) {
        Map<Integer, ActorRef> receivers = new HashMap<>();
        receivers.put(receiverId, receiver);
        this.messageQueue.add(new MessageRequest(m, receivers, milliseconds, shoudLog));
        if (this.state == State.NORMAL) this.sendNextMessage();
    }

    public void removeChatMessages(){
        this.messageQueue.removeChatMessages();
    }
}
