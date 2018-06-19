package it.unitn.ds1.Actors;

import akka.actor.Props;
import it.unitn.ds1.Messages.*;
import it.unitn.ds1.Models.State;
import it.unitn.ds1.Models.View;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;

public class GroupMember extends Actor {
    public GroupMember(String remotePath) {
        super(remotePath);
    }

    @Override
    protected void init() {
        super.init();
        this.id = -(int)(Math.random() * 1000);
    }

    @Override
    public void preStart() {
        super.preStart();
        logger.info(String.format("[%d] - %s", this.id, "group member started"));
        logger.info(String.format("[%d -> 0] join request ", this.id));
        getContext().actorSelection(groupManagerHostPath).tell(new RequestJoinMessage(this.id), getSelf());
        this.keyListenerThread.start();
    }

    /*
     * Status Helper Functions
     * */
    @Override
    protected void crash(int recoveryTime) {
        this.state = State.CRASHED;
        logger.info(String.format("[%d] - CRASH!!!", this.id));
        this.senderHelperNoLog.scheduleMessageReliable(new RecoveryMessage(this.id), getSelf(), this.id, "RecoveryMessage", recoveryTime);
    }

    protected void onRecoveryMessage(RecoveryMessage message){
        this.init();
        this.preStart();
    }

    /**
     Akka - Build from constructor
     */
    static public Props props(String remotePath) {
        return Props.create(GroupMember.class, () -> new GroupMember(remotePath));
    }


    /**
     Registers callbacks
     */
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(NewIDMessage.class, this::onNewIDMessage)
                .match(ViewChangeMessage.class, this::onViewChangeMessage)
                .match(ChatMessage.class, this::onChatMessage)
                .match(FlushMessage.class, this::onFlushMessage)
                .match(HeartBeatMessage.class, this::onHeartBeatMessage)
                .match(RecoveryMessage.class, this::onRecoveryMessage)
                .match(SendNewChatMessage.class, this::onSendNewChatMessage)
                .build();
    }
}
