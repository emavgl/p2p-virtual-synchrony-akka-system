package it.unitn.ds1.Actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import it.unitn.ds1.Messages.*;
import it.unitn.ds1.Models.State;
import it.unitn.ds1.Models.View;
import scala.util.control.Exception;

import java.util.HashMap;
import java.util.Map;

public class GroupManager extends Actor {

    private int memberIdCounter;
    private Map<Integer, Integer> heartBeatCounter;
    private int timeoutThreshold;

    private Thread heartBeatThread;


    public GroupManager(int id, String remotePath) {
        super(remotePath);
        this.id = id;             // is always 0

        this.init();
    }

    @Override
    protected void init() {
        super.init();
        this.memberIdCounter = 1;
        this.timeoutThreshold = 5;
        this.heartBeatThread  = new Thread(() -> {
            while (true){
                try {
                    // Check for a timeout, increment the counter
                    for (Map.Entry<Integer, Integer> entry: this.heartBeatCounter.entrySet()){
                        if (entry.getValue() == timeoutThreshold) this.onCrashDetected(entry.getKey());
                        this.heartBeatCounter.put(entry.getKey(), entry.getValue()+1);
                    }

                    HeartBeatMessage hbm = new HeartBeatMessage(this.id);
                    multicast(hbm, this.view.getMembers().values());
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void preStart() {
        View firstView = new View(0).addNode(this.id, getSelf());
        this.installView(firstView);
        this.runHeartBeatLoop();
        this.run();
    }

    private void runHeartBeatLoop(){
        this.heartBeatThread.start();
    }

    /**
     Akka - Build from constructor
     */
    static public Props props(int id, String remotePath) {
        return Props.create(GroupManager.class, () -> new GroupManager(id, remotePath));
    }

    @Override
    protected void installView(View v){
        super.installView(v);
        this.initializeHeartBeatCounter(v);
    }

    private void initializeHeartBeatCounter(View v) {
        this.heartBeatCounter = new HashMap<>();
        for (Integer nodeId : v.getMembers().keySet()){
            this.heartBeatCounter.put(nodeId, 0);
        }
    }

    private void onRequestJoinMessage(RequestJoinMessage message) {
        this.state = State.PAUSE;

        logger.info(String.format("[%d] - [<- %d] join request at view %d", this.id, message.senderId,
                this.view.getId()));

        ActorRef sender = getSender();
        int newId = memberIdCounter;
        logger.info(String.format("[%d] - [-> %d] new ID %d", this.id, message.senderId, newId));
        sender.tell(new NewIDMessage(this.id, memberIdCounter++), getSelf());

        View newView = this.view.addNode(newId, sender);
        this.newViewRequest(newView);
    }

    private void newViewRequest(View v){

        logger.info(String.format("[%d] - [-> %s] new view %d request", this.id,
                v.getMembers().keySet().toString(),
                v.getId()
        ));

        multicast(new ViewChangeMessage(this.id, v), v.getMembers().values());
    }

    private void onCrashDetected(int crashedNodeId){

        logger.info(String.format("[%d] - node %d crashed within view %d", this.id,
                crashedNodeId,
                this.view.getId()
        ));

        this.state = State.PAUSE;
        View newView = this.view.removeNodeById(crashedNodeId);
        this.newViewRequest(newView);
    }

    @Override
    protected void onHeartBeatMessage(HeartBeatMessage message){
        logger.info(String.format("[%d] - [<- %d] heartbeat", this.id, message.senderId));
        this.heartBeatCounter.put(message.senderId, 0);
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
                .match(HeartBeatMessage.class, this::onHeartBeatMessage)
                .build();
    }
}
