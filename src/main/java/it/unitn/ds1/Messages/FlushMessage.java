package it.unitn.ds1.Messages;

public class FlushMessage extends Message {
    public int proposedViewId;
    public FlushMessage(int senderId, int proposedViewId){
        super(senderId);
        this.proposedViewId = proposedViewId;
    }
}
