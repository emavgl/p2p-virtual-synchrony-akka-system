package it.unitn.ds1.Models;

import akka.actor.ActorRef;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class View implements Serializable {
    private int id;
    private Map<Integer, ActorRef> members;

    public View(int id){
        this.id = id;
        members = new HashMap<>();
    }

    public View(int id, Map<Integer, ActorRef> members){
        this.id = id;
        this.members = new HashMap<>(members);
    }

    public int getId() {
        return id;
    }

    public Map<Integer, ActorRef> getMembers() {
        return members;
    }

    public View removeNodeById(int id){
        Map<Integer, ActorRef> newMembers = new HashMap<>(this.getMembers());
        newMembers.remove(id);
        return new View(this.getId()+1, newMembers);
    }

    public View addNode(int id, ActorRef sender){
        Map<Integer, ActorRef> newMembers = new HashMap<>(this.getMembers());
        newMembers.put(id, sender);
        return new View(this.getId()+1, newMembers);
    }
}
