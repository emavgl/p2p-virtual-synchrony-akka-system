package it.unitn.ds1.Actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import it.unitn.ds1.Messages.*;

public class GroupManager extends Actor {

    private int memberIdCounter;

    public GroupManager(int id, String remotePath) {
        super(remotePath);
        this.id = id;             // is always 0
        this.memberIdCounter = 1;
    }

    @Override
    public void preStart() {
        super.preStart();
        this.nodes.put(this.id, getSelf());
    }

    /**
     Akka - Build from constructor
     */
    static public Props props(int id, String remotePath) {
        return Props.create(GroupManager.class, () -> new GroupManager(id, remotePath));
    }


    private void onRequestIDMessage(RequestIDMessage message) {
        ActorRef sender = getSender();
        sender.tell(new NewIDMessage(memberIdCounter++), getSelf());
    }

    /**
     Registers callbacks
     */
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(RequestIDMessage.class, this::onRequestIDMessage)
                .match(RequestNodelist.class, this::onRequestNodelist)
                .match(Nodelist.class, this::onNodelist)
                .match(ChatMessage.class, this::onChatMessage)
                .match(Join.class, this::onJoin)
                .build();
    }
}
