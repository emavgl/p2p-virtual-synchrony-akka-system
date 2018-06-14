package it.unitn.ds1.Messages;

import java.io.Serializable;

public class ChatMessage implements Serializable {

    public final int senderId;
    public final int content;

    public ChatMessage(int content, int originId){
        this.senderId = originId;
        this.content = content;
    }
}
