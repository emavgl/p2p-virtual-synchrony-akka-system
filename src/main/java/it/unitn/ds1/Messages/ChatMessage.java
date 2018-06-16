package it.unitn.ds1.Messages;

import java.io.Serializable;

public class ChatMessage extends Message {
    public final int content;
    public ChatMessage(int senderId, int content){
        super(senderId);
        this.content = content;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ChatMessage other = (ChatMessage) obj;
        if (senderId != other.senderId)
            return false;
        if (content != other.content)
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 7;
        result = prime * result + this.senderId;
        result = prime * result + this.content;
        return result;
    }
}
