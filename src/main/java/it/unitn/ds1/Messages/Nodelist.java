package it.unitn.ds1.Messages;

import akka.actor.ActorRef;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 Definition of the NodeList message
 */
public class Nodelist implements Serializable {
    public Map<Integer, ActorRef> nodes;
    public Nodelist(Map<Integer, ActorRef> nodes) {
        this.nodes = Collections.unmodifiableMap(new HashMap<Integer, ActorRef>(nodes));
    }
}
