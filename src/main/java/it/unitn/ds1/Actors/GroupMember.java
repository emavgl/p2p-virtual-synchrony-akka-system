package it.unitn.ds1.Actors;

import akka.actor.Props;
import it.unitn.ds1.Messages.RequestJoinMessage;
import it.unitn.ds1.Models.State;
import it.unitn.ds1.Models.View;

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
        logger.info(String.format("[%d] - %s", this.id, "group member started"));
        logger.info(String.format("[%d] - [-> 0] join request ", this.id));
        getContext().actorSelection(groupManagerHostPath).tell(new RequestJoinMessage(this.id), getSelf());
        this.run();
    }

    /*
     * Status Helper Functions
     * */
    @Override
    protected void crash(long recoveryTime) {
        this.state = State.CRASHED;
        logger.info(String.format("[%d] - CRASH!!!", this.id));

        /*
            getContext().system().scheduler().scheduleOnce(
                    Duration.create(recoveryTime, TimeUnit.MILLISECONDS),
                    getSelf(),
                    new Recovery(), // message sent to myself
                    getContext().system().dispatcher(), getSelf()
            );
        */

        try {
            Thread.sleep(recoveryTime);
            this.init();
            this.preStart();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     Akka - Build from constructor
     */
    static public Props props(String remotePath) {
        return Props.create(GroupMember.class, () -> new GroupMember(remotePath));
    }
}
