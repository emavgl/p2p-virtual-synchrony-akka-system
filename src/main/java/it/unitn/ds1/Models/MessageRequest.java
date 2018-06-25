package it.unitn.ds1.Models;

import akka.actor.ActorRef;
import it.unitn.ds1.Messages.Message;

import java.util.Map;

public class MessageRequest {
    public Message m;
    public Map<Integer, ActorRef> receivers;
    public int milliseconds;
    public int maxTime = 15000;
    public boolean shoudLog;
    public boolean shouldCrash;

    public MessageRequest(Message m, Map<Integer, ActorRef> receivers, int milliseconds, boolean shoudLog){
        this.m = m;
        this.receivers = receivers;
        this.milliseconds = milliseconds;
        this.shoudLog = shoudLog;
        this.shouldCrash = false;
        if (this.milliseconds == -1){
            this.milliseconds = maxTime;
        }
    }
}
