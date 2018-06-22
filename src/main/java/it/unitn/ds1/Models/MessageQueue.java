package it.unitn.ds1.Models;

import it.unitn.ds1.Messages.ChatMessage;
import it.unitn.ds1.Messages.Message;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class MessageQueue {
    private Queue<MessageRequest> queue = new LinkedList<MessageRequest>();

    public MessageQueue(){
    }

    public void add(MessageRequest m){
        queue.add(m);
    }

    public MessageRequest next(){
        return queue.poll();
    }
    public boolean isEmpty(){
        return queue.isEmpty();
    }
    public void removeChatMessages() {
        queue.removeIf(x -> x.m instanceof ChatMessage);
    }

    public int getSize() { return this.queue.size(); }
}
