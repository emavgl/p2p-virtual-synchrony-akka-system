package it.unitn.ds1.Actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import it.unitn.ds1.Messages.*;
import it.unitn.ds1.Models.State;
import it.unitn.ds1.Models.View;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GroupManager extends Actor {

    private int memberIdCounter;
    private Map<Integer, Integer> heartBeatCounter;
    private int timeoutThreshold;
    private boolean heartBeatLoopStarted = false;
    protected HashMap<Integer, Set<Integer>> installConfirmations;

    public GroupManager(int id, String remotePath) {
        super(remotePath);
        this.id = id;             // is always 0
        this.init();
    }

    @Override
    protected void init() {
        super.init();
        this.memberIdCounter = 1;
        this.timeoutThreshold = 7;
        this.installConfirmations = new HashMap<>();
    }

    @Override
    public void preStart() {
        super.preStart();
        View firstView = new View(0).addNode(this.id, getSelf());
        this.installView(firstView);
    }

    @Override
    public void crash(int time) {
        // Nothing
        // Group manager cannot crash
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
        // Initialize the heartBeat vector
        // All the nodes will have an initial counter set to 0
        this.heartBeatCounter = new HashMap<>();
        for (Integer nodeId : v.getMembers().keySet()){
            if (this.heartBeatCounter.get(nodeId) == null){
                this.heartBeatCounter.put(nodeId, 0);
            }
        }

        if (!this.heartBeatLoopStarted){
            // Initialize the HeartBeat loop
            // Simulating the reception of a new HeatBeatMessage
            this.onHeartBeatMessage(new HeartBeatMessage(this.id));
            this.heartBeatLoopStarted = true;
        }
    }

    /**
     * Callback: new RequestJoinMessage received
     * Set state to pause; send a new ID to the new node and start a new view request
     * @param message: RequestJoinMessage
     */
    private void onRequestJoinMessage(RequestJoinMessage message) {
        // Set the state to pause
        this.state = State.PAUSE;
        this.senderHelper.removeAllMessages();

        logger.info(String.format("[%d] - [<- %d] join request at view %d", this.id, message.senderId,
                this.view.getId()));

        // Send a new ID
        ActorRef sender = getSender();
        int newId = memberIdCounter;
        logger.info(String.format("[%d] - [-> %d] new ID %d", this.id, message.senderId, newId));
        sender.tell(new NewIDMessage(this.id, memberIdCounter++), getSelf());

        // Start a new View request
        View newView;
        if (this.proposedView != null){
            newView = this.proposedView.addNode(newId, sender);
        } else {
            newView = this.view.addNode(newId, sender);
        }

        this.newViewRequest(newView);
    }

    /**
     * Send a ViewChangeMessage in multicast
     * @param v: View -- The new view to be proposed
     */
    private void newViewRequest(View v){
        // Initialize flushes for this view
        this.flushes.put(v.getId(), new HashSet<>());
        this.initializeHeartBeatCounter(v);

        this.proposedView = v;
        this.senderHelper.enqMulticast(new ViewChangeMessage(this.id, v), -1, true);
    }

    /**
     * Callback: on crashDetected message received
     * Create a new view without node crashNodeId
     * @param crashedNodeId: int
     */
    private void onCrashDetected(int crashedNodeId){
        this.state = State.PAUSE;
        this.senderHelper.removeAllMessages();

        logger.info(String.format("[%d] - node %d crashed within view %d", this.id,
                crashedNodeId,
                this.view.getId()
        ));

        View newView;
        if (this.proposedView != null){
            newView = this.proposedView.removeNodeById(crashedNodeId);
        } else {
            newView = this.view.removeNodeById(crashedNodeId);
        }
        this.newViewRequest(newView);
    }

    /**
     * Callback: new HeartBeatMessage received
     * This function is used to reschedule multicast of heatbeatmessage
     * and to detect, using a counter, whether the node is crashed
     * @param message: HeartBeatMessage
     */
    @Override
    protected void onHeartBeatMessage(HeartBeatMessage message){
        if (message.senderId > 0) {
            // Received a message from another member
            // Reset counter for message.senderId
            this.heartBeatCounter.put(message.senderId, 0);
            logger.info(String.format("[%d <- %d] Heartbeat", this.id, message.senderId));

        } else {

            // Received a message from myself
            // Check for a timeout and increment the counter
            Set<Map.Entry<Integer, Integer>> entries = this.heartBeatCounter.entrySet();
            for (Map.Entry<Integer, Integer> entry: entries){
                if (entry.getValue() == timeoutThreshold){
                    int toRemoveId = entry.getKey();
                    this.onCrashDetected(toRemoveId);
                }
                if (entry.getKey() > 0) this.heartBeatCounter.put(entry.getKey(), entry.getValue()+1);
            }

            // Schedule a new HeartBeat Multicast to be sent after 1000 seconds
            // logger.info(String.format("[0 -> %s] scheduling heartbeat", this.view.getMembers().keySet().toString()));
            this.senderHelper.enqMulticast(new HeartBeatMessage(this.id), 3000, false);
        }
    }

    protected void onInstalledViewMessage(InstalledViewMessage message){
        if (this.state == State.CRASHED) return;

        // Initialize installConfirmations
        if (this.installConfirmations.get(this.view.getId()) == null){
            this.installConfirmations.put(this.view.getId(), new HashSet<>());
        }

        int senderId = message.senderId;
        int viewId = message.viewId;

        logger.info(String.format("[%d <- %d] InstalledViewMessage for view %d",
                this.id, message.senderId, message.viewId));

        Set<Integer> s = this.installConfirmations.get(viewId);
        if (s == null) this.installConfirmations.put(viewId, new HashSet<>());
        this.installConfirmations.get(viewId).add(senderId);

        if (this.proposedView != null) return;

        if (this.view.getId() >= viewId){
            Set<Integer> requestInstalledViewConfirmation = this.installConfirmations.get(this.view.getId());
            boolean complete = requestInstalledViewConfirmation.equals(this.view.getMembers().keySet());
            if (complete) {
                logger.info(String.format("%d is sure that all the nodes have installed the view %d, send start!",
                        this.id, message.viewId));
                this.senderHelper.enqMulticast(new StartChatMessage(this.id), -1, true);
            }
        }
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
                .match(SendNewChatMessage.class, this::onSendNewChatMessage)
                .match(UnstableMessage.class, this::onUnstableMessage)
                .match(InstalledViewMessage.class, this::onInstalledViewMessage)
                .match(StartChatMessage.class, this::onStartChatMessage)
                .build();
    }
}
