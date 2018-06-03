package it.unitn.ds1.Messages;

import java.io.Serializable;

/**
 Definition of the JOIN message
 contains the actor's ID that wants to join
 */
public class Join implements Serializable {
    public int id;
    public Join(int id) {
        this.id = id;
    }
}