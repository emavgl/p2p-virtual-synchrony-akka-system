package it.unitn.ds1.Messages;

public class InstalledViewMessage extends Message {
    public int viewId;
    public InstalledViewMessage(int senderId, int viewId){
        super(senderId);
        this.viewId = viewId;
    }
}
