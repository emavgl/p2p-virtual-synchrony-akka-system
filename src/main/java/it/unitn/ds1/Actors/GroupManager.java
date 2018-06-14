package it.unitn.ds1.Actors;

import akka.actor.Props;

public class GroupManager extends Actor {
    public GroupManager(int id, String remotePath) {
        super(remotePath);
        this.id = id;
    }

    /**
     Akka - Build from constructor
     */
    static public Props props(int id, String remotePath) {
        return Props.create(GroupManager.class, () -> new GroupManager(id, remotePath));
    }
}
