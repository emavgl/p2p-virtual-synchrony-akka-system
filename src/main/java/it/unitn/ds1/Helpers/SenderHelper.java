package it.unitn.ds1.Helpers;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import it.unitn.ds1.Actors.Actor;
import it.unitn.ds1.Messages.ChatMessage;
import it.unitn.ds1.Messages.Message;
import it.unitn.ds1.Models.State;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class SenderHelper {


    protected static final Logger logger = Logger.getLogger("DS1");

    private ActorSystem system;
    private int td;
    private Actor actor;
    private boolean shouldLog;

    public SenderHelper(int td, Actor actor, boolean shouldLog) {
        this.td = td;
        this.actor = actor;
        this.system = this.actor.getContext().getSystem();
        this.shouldLog = shouldLog;
    }

    public void scheduleMulticast(Message m, Map<Integer, ActorRef> receivers, String messageType) {
        for (Map.Entry<Integer, ActorRef> entry : receivers.entrySet()) {
            int timeForTheNextMessage = new Random().nextInt(this.td - 2000) + 2000;
            this.system.scheduler().scheduleOnce(java.time.Duration.ofMillis(timeForTheNextMessage),
                    new Runnable() {
                        @Override
                        public void run() {

                            if (actor.state == State.CRASHED) return;

                            if (shouldLog){
                                logger.info(String.format("[%d -> %d] %s message", m.senderId,
                                        entry.getKey(), messageType));
                            }

                            entry.getValue().tell(m, actor.getSelf());
                        }
                    }, this.system.dispatcher());
        }
    }

    public void scheduleMulticast(Message m, Map<Integer, ActorRef> receivers, String messageType, int milliseconds) {
        for (Map.Entry<Integer, ActorRef> entry : receivers.entrySet()) {
            this.system.scheduler().scheduleOnce(java.time.Duration.ofMillis(milliseconds),
                    new Runnable() {
                        @Override
                        public void run() {

                            if (actor.state == State.CRASHED) return;

                            if (shouldLog){
                                logger.info(String.format("[%d -> %d] %s message", m.senderId,
                                        entry.getKey(), messageType));
                            }
                            entry.getValue().tell(m, actor.getSelf());
                        }
                    }, this.system.dispatcher());
        }
    }

    public void scheduleMulticast(ChatMessage m, Map<Integer, ActorRef> receivers) {
        for (Map.Entry<Integer, ActorRef> entry : receivers.entrySet()) {
            int timeForTheNextMessage = new Random().nextInt(this.td - 2000) + 2000;
            this.system.scheduler().scheduleOnce(java.time.Duration.ofMillis(timeForTheNextMessage),
                    new Runnable() {
                        @Override
                        public void run() {

                            if (actor.state == State.CRASHED) return;

                            if (shouldLog){
                                logger.info(String.format("[%d -> %s] send chatmsg %d within %d",
                                        m.senderId, receivers.keySet().toString(),
                                        m.content, actor.view.getId()));
                            }
                            entry.getValue().tell(m, actor.getSelf());
                        }
                    }, this.system.dispatcher());
        }
    }


    public void scheduleMulticast(ChatMessage m, Map<Integer, ActorRef> receivers, int milliseconds) {
        int timeForTheNextMessage = milliseconds;
        for (Map.Entry<Integer, ActorRef> entry : receivers.entrySet()) {
            this.system.scheduler().scheduleOnce(java.time.Duration.ofMillis(timeForTheNextMessage),
                    new Runnable() {
                        @Override
                        public void run() {

                            if (actor.state == State.CRASHED) return;

                            if (shouldLog){
                                logger.info(String.format("[%d -> %s] send chatmsg %d within %d",
                                        m.senderId, receivers.keySet().toString(),
                                        m.content, actor.view.getId()));
                            }
                            entry.getValue().tell(m, actor.getSelf());
                        }
                    }, this.system.dispatcher());
        }
    }

    public void scheduleMessage(Message m, ActorRef receiver, int receiverId, String messageType) {
        int timeForTheNextMessage = new Random().nextInt(this.td - 2000) + 2000;
        this.system.scheduler().scheduleOnce(java.time.Duration.ofMillis(timeForTheNextMessage),
                new Runnable() {
                    @Override
                    public void run() {
                        if (actor.state == State.CRASHED) return;
                        if (shouldLog){
                            logger.info(String.format("[%d -> %d] %s message", m.senderId,
                                    receiverId, messageType));
                        }
                        receiver.tell(m, actor.getSelf());
                    }
                }, this.system.dispatcher());
    }

    public void scheduleMessageReliable(Message m, ActorRef receiver, int receiverId, String messageType, int time) {
        this.system.scheduler().scheduleOnce(java.time.Duration.ofMillis(time),
                new Runnable() {
                    @Override
                    public void run() {
                        if (shouldLog){
                            logger.info(String.format("[%d -> %d] %s message", m.senderId,
                                    receiverId, messageType));
                        }
                        receiver.tell(m, actor.getSelf());
                    }
                }, this.system.dispatcher());
    }

    public void scheduleMessage(Message m, ActorRef receiver, int receiverId, String messageType, int milliseconds) {
        this.system.scheduler().scheduleOnce(java.time.Duration.ofMillis(milliseconds),
                new Runnable() {
                    @Override
                    public void run() {
                        if (actor.state == State.CRASHED) return;
                        if (shouldLog){
                            logger.info(String.format("[%d -> %d] %s message", m.senderId,
                                    receiverId, messageType));
                        }
                        receiver.tell(m, actor.getSelf());
                    }
                }, this.system.dispatcher());
    }

    public void multicast(Message m, Map<Integer, ActorRef> participants, String messageType) {
        for (Map.Entry<Integer, ActorRef> entry: participants.entrySet()){
            if (shouldLog){
                logger.info(String.format("[%d -> %d] %s message", m.senderId, entry.getKey(), messageType));
            }
            entry.getValue().tell(m, this.actor.getSelf());
        }
    }

    public void multicast(ChatMessage m, Map<Integer, ActorRef> participants) {
        for (Map.Entry<Integer, ActorRef> entry: participants.entrySet()){
            if (shouldLog){
                logger.info(String.format("[%d -> %d] chatmsg %d", m.senderId, entry.getKey(), m.content));
            }
            entry.getValue().tell(m, this.actor.getSelf());
        }
    }

    public void unicast(Message m, ActorRef receiver, int receiverId, String messageType){
        if (shouldLog){
            logger.info(String.format("[%d -> %d] %s message", m.senderId, receiverId, messageType));
        }
        receiver.tell(m, this.actor.getSelf());
    }

    public void unicast(ChatMessage m, ActorRef receiver, int receiverId){
        if (shouldLog){
            logger.info(String.format("[%d -> %d] chatmsg %d", m.senderId, receiverId, m.content));
        }
        receiver.tell(m, this.actor.getSelf());
    }
}
