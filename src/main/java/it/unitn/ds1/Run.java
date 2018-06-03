package it.unitn.ds1;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import it.unitn.ds1.Actors.Actor;

public class Run {

    public static void main(String[] args) {
        // Load the configuration file
        Config config = ConfigFactory.load();
        int myId = config.getInt("nodeapp.id");
        String remotePath = null;

        if (config.hasPath("nodeapp.remote_ip")) {
            String remote_ip = config.getString("nodeapp.remote_ip");
            int remote_port = config.getInt("nodeapp.remote_port");
            // Starting with a bootstrapping node
            // The Akka path to the bootstrapping peer
            remotePath = "akka.tcp://mysystem@"+remote_ip+":"+remote_port+"/user/node";
            System.out.println("Starting node " + myId + "; bootstrapping node: " + remote_ip + ":"+ remote_port);
        }
        else {
            System.out.println("Start master node: " + myId);
        }

        // Create the actor system
        final ActorSystem system = ActorSystem.create("mysystem", config);

        // Create a single node actor locally
        final ActorRef receiver = system.actorOf(
                Actor.props(myId, remotePath),
                "node"      // actor name
        );
    }
}
