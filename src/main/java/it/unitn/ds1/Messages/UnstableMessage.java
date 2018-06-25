package it.unitn.ds1.Messages;

import java.util.Collection;

public class UnstableMessage extends Message {
    public Collection<ChatMessage> unstableMessages;
    public UnstableMessage(int senderId, Collection<ChatMessage> unstableMessages){
        super(senderId);
        this.unstableMessages = unstableMessages;
    }
}
