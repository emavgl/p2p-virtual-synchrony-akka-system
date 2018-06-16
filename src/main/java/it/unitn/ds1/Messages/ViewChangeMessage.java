package it.unitn.ds1.Messages;

import it.unitn.ds1.Models.View;
import java.io.Serializable;

public class ViewChangeMessage extends Message {
    public View view;
    public ViewChangeMessage(int senderId, View view) {
        super(senderId);
        this.view = view;
    }
}
