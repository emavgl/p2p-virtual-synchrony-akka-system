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

    public void enqMulticast(Message m, Map<Integer, ActorRef> receivers, int milliseconds, boolean shoudLog) {
        this.messageQueue.add(new MessageRequest(m, receivers, milliseconds, shoudLog));
        if (this.state == State.NORMAL) this.sendNextMessage();
    }

    public void enqMulticast(Message m, Map<Integer, ActorRef> receivers) {
        this.enqMulticast(m, receivers, -1, true);
    }

    private void sendNextMessage(){
        if (this.messageQueue.isEmpty()){
            state = State.NORMAL;
        } else {
            // If it is busy, will schedule the next message by itself
            state = State.BUSY;
            scheduleMessages(this.messageQueue.next());
        }
    }

    private void scheduleMessages(MessageRequest mr){
        Message m = mr.m;
        Set<Map.Entry<Integer, ActorRef>> entries = mr.receivers.entrySet();
        Set<Integer> sent = new HashSet<>();
        String messageType = m.getClass().getSimpleName();;
        String contentStr = "";
        String viewIdStr = "";

        if (messageType.toLowerCase().contains("chat")) {
            int content = ((ChatMessage)(mr.m)).content;
            contentStr = String.valueOf(content);
        }

        if (actor.view != null){
            viewIdStr = String.valueOf(actor.view.getId());
        }

        final String messageContent = contentStr;
        final String viewId = viewIdStr;

        if (mr.shoudLog) {
            logger.info(String.format("[%d -> %s] scheduling %s (c: %s) within %s",
                    m.senderId, mr.receivers.keySet().toString(), messageType, messageContent, viewId));
        }

        for (Map.Entry<Integer, ActorRef> entry : entries) {
            int timeForTheNextMessage = new Random().nextInt(mr.milliseconds);
            this.system.scheduler().scheduleOnce(java.time.Duration.ofMillis(timeForTheNextMessage),
                    new Runnable() {
                        @Override
                        public void run() {

                            if (actor.state == State.CRASHED) return;

                            if (shouldLog && mr.shoudLog){
                                logger.info(String.format("[%d -> %s] send %s (c: %s) within %s",
                                        m.senderId, entry.getKey(), messageType,
                                        messageContent, viewId));
                            }

                            entry.getValue().tell(m, actor.getSelf());
                            sent.add(entry.getKey());

                            if (sent.size() == entries.size()){
                                // Sent all the messages
                                // Do something
                                sendNextMessage();
                            }
                        }
                    }, this.system.dispatcher());
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
}
