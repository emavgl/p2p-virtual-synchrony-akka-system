package it.unitn.ds1.Messages;

import java.io.Serializable;

public class Message implements Serializable {
    public final int senderId;
    public Integer viewId;
    public Message(int senderId){
        this.senderId = senderId;
    }
}
