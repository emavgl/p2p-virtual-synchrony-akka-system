package it.unitn.ds1.Messages;

import java.io.Serializable;

/**
 Definition of the RequestNodeList message
 */
public class RequestJoinMessage extends Message {
    public RequestJoinMessage(int senderId){
        super(senderId);
    }
}

