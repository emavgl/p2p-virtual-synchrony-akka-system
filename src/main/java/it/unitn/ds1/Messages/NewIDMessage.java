package it.unitn.ds1.Messages;

import java.io.Serializable;

public class NewIDMessage implements Serializable {
    public int id;
    public NewIDMessage(int id){
        this.id = id;
    }
}


