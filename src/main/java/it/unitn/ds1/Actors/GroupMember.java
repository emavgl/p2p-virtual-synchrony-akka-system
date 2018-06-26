package it.unitn.ds1.Actors;

import akka.actor.Props;
import it.unitn.ds1.Messages.*;
import it.unitn.ds1.Models.State;

public class GroupMember extends Actor {
    public GroupMember(String remotePath) {
        super(remotePath);
    }
    private long latestHeartBeatReceived;
    private long maxMillisWithoutHeartBeat;

    @Override
    protected void init() {
        super.init();
        this.id = -(int)(Math.random() * 1000);
        latestHeartBeatReceived = 0;
        maxMillisWithoutHeartBeat = 30000;
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
    public void crash(int recoveryTime) {
        this.state = State.CRASHED;
        logger.info(String.format("[%d] - CRASH!!!", this.id));
        this.senderHelper.scheduleControlMessage(new RecoveryMessage(this.id), recoveryTime);
    }

    protected void onRecoveryMessage(RecoveryMessage message){
        if (state == State.CRASHED) {
            this.init();
            this.preStart();
        }
    }

    @Override
    protected void onSendNewChatMessage(SendNewChatMessage message) {
        super.onSendNewChatMessage(message);

        // Add a control, Am I talking alone?
        long timeElapsed = System.currentTimeMillis() - this.latestHeartBeatReceived;
        if (timeElapsed > this.maxMillisWithoutHeartBeat ) crash(30000);
    }

    @Override
    protected void onHeartBeatMessage(HeartBeatMessage message) {
        super.onHeartBeatMessage(message);
        this.latestHeartBeatReceived = System.currentTimeMillis();
    }

    /**
     Akka - Build from constructor
     */
    static public Props props(String remotePath) {
        return Props.create(GroupMember.class, () -> new GroupMember(remotePath));
    }

    /*
     * Set the ActorID
     * */
    protected void onNewIDMessage(NewIDMessage message) {
        if (this.state == State.CRASHED) return;
        logger.info(String.format("[%d] - [<- %d] new id %d", this.id, message.senderId, message.id));
        this.id = message.id;
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
                .match(UnstableMessage.class, this::onUnstableMessage)
                .match(InstalledViewMessage.class, this::onInstalledViewMessage)
                .build();
    }
}
