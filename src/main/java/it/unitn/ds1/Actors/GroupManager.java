package it.unitn.ds1.Actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import it.unitn.ds1.Messages.*;
import it.unitn.ds1.Models.View;

import java.util.HashMap;
import java.util.Map;

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
        View firstView = new View(0).addNode(this.id, getSelf());
        this.installView(firstView);
    }

    /**
     Akka - Build from constructor
     */
    static public Props props(int id, String remotePath) {
        return Props.create(GroupManager.class, () -> new GroupManager(id, remotePath));
    }


    private void onRequestJoinMessage(RequestJoinMessage message) {
        logger.info(String.format("[%d] - [<- %d] join request at view %d", this.id, message.senderId,
                this.view.getId()));

        ActorRef sender = getSender();
        int newId = memberIdCounter;

        logger.info(String.format("[%d] - [-> %d] new ID %d", this.id, message.senderId, newId));
        sender.tell(new NewIDMessage(this.id, memberIdCounter++), getSelf());

        View newView = this.view.addNode(newId, sender);

        logger.info(String.format("[%d] - [-> %s] request new view %d", this.id,
                newView.getMembers().keySet().toString(),
                newView.getId()
                ));
        multicast(new ViewChangeMessage(this.id, newView), newView.getMembers().values());
    }

    /**
     Registers callbacks
     */
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(RequestJoinMessage.class, this::onRequestJoinMessage)
                .match(ViewChangeMessage.class, this::onViewChangeMessage)
                .match(ChatMessage.class, this::onChatMessage)
                .match(FlushMessage.class, this::onFlushMessage)
                .build();
    }
}
