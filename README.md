# P2P Virtual Synchrony using Akka framework

## How-to-run
1. Run Group Manager as Node0 `gradle run -Dconfig=node0.conf`
2. Run a new node using the script `python3 run_node.py 10001 127.0.0.1`

Group Manager has an assigned ID = 0
All the other group member has an initial unassigned ID = -1

# Docs
Read the [report](https://github.com/emavgl/p2p-virtual-synchrony-akka-system/blob/master/report.pdf).

# Credits
Emanuele Viglianisi
Alessandro Torresani


