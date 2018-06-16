package it.unitn.ds1.Messages;

import java.io.Serializable;

public class NewIDMessage extends Message {
    public int id;
    public NewIDMessage(int senderId, int id){
        super(senderId);
        this.id = id;
    }
}


