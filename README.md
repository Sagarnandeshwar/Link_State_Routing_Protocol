# COMP535: Computer Networks Course Project 
I have implemented a Link State Routing Protocol using Java Socket Programming. Addtionally, I have used Apache Maven to build the project. 

## Link State Routing 
In Link State Routing, every router maintains its own description of the connectivity of the complete network. As a result, each router can calculate the best next hop for all possible destinations independently.  

## Socket Programming 
A socket is the interface between the application layer and the transmission layer. 
<br>
![Socket Description](https://github.com/Sagarnandeshwar/Link_State_Routing_COMP535/blob/main/images/socket.png)
<br>
When the processes communicate via socket, they usually play two categories of roles, Server and client. 
In a connection-oriented client-to-server model, the socket on the server process waits for requests from a client. 
To do this, the server first establishes (binds) an address that clients can use to find the server. 
When the address is established, the server waits for clients to request a service. 
The client-to-server data exchange takes place when a client connects to the server through a socket. 
The server performs the client's request and sends the reply back to the client.
<br>
![Server Client Methods](https://github.com/Sagarnandeshwar/Link_State_Routing_COMP535/blob/main/images/server_client.png)
<br>
I use Java Socket Programming to build Link State Routing. 
In addition, I have used a multi-threaded for server to handle concurrent socket requests/messages from the client. 

## Simulation
To simulate the real-world network environment, I have to start multiple instances of the program, each of which connects with others via socket. Each program instance represents a router or host in the simulated network space. Correspondingly, the links connecting the routers/hosts and the IP addresses identifying the routers/hosts are simulated by the in-memory data structures. 

Each socket-based program has its own IP address and port, which are the identifiers used to communicate with other processes. For the purpose of this project, I have assigned a "simulated IP address" to each router. This IP address is only used to identify the router program instance network space but is not used to communicate via sockets. I, then, map between this “simulated IP address” and the "Process IP" and "Process Port" to simulate the link state routing protocol.

## Data Structures in Routers 
Each router maintains its own Link State Database, which is essentially a map from the router's IP address to the link state description originated by the corresponding router. The shortest path algorithm runs over this database. 
 
## Synchronize Link State Database 
The synchronization of Link State Database happens when the link state of a router changes. The router where the link state changes broadcasts which contains the latest information of link state to all neighbors. The routers which receive the message will in turn broadcast to their own neighbors except the one which sends the LSAUPDATE . 
<br>
LSAUPDATE contains one or more LSA(Link State Advertisement) structures which summarize the latest link state information of the router. To update the local link state database with the latest information, I have distinguished the LSAUPDATE information generated from the same router with a monotonically increasing sequence number. The router receiving the LSAUPDATE only updates its Link State Database when the LSAUPDATE 's sequence number is larger than maximum sequence number from the same router it just received. 
<br>

## Shortest Path Finding: 
Based on LSA entries saved in Link State Database, I built the weighted graph representing the topology of network. With the weighted graph, I can find the shortest path from the router to all the other ones with the Dijkstra algorithm.  
 
## Essential Components: 
- **port** is the array of Link which stands for the 4 ports of the router;  
- **re** is an instance of RouterDescription, which is a wrapper of several describing fields of the router: processIPAddress and processPortNumber are where the server socket of the router program binds at; SIMULATEDIPADDRESS is the simulated IP address and the identifier of this router; STATUS is the instance of RouterStatus describing the stage of the database synchronization. 
- **lsd** is the instance of LinkStateDatabase. LSD contains a HashMap which maps the linkStateId to the Link State Advertisement (LSA). LSA is the data structure storing the LinkDecription of in the router which advertised this LSA. 
 
## RouterStatus:  
NA -> Not applicable (given during "attach" and before "start")  
INIT  -> used during router link establishment 
<br>
TWO_WAY -> once the two routers are connected 
 
## Messages 
The messages are distinguished by the field of sospfType, where 
0 -> Hello  
1 -> LSAUpdate  
2 -> Acknowledgement  
3 -> Connect  
4 -> Disconnect  
5 -> Quit  
6 -> UpdateWeight 
 
 
## Command-line Console 
 
- **attach [Process IP] [Process Port] [IP Address] [Link Weight]**: this command establishes a local link to the remote router which is identified by [IP Address]. This command does not trigger database synchronization. 
 
- **start**: start this router and initialize the database synchronization process. After we establish the local links by running ATTACH, we will run the start command to send messages and connect to all routers to which we have established a local link. This command does trigger database synchronization. 
 
 
- **connect [Process IP] [Process Port] [IP Address] [Link Weight]**: similar to start command, but directly connect the current router with remote router without the need to establish local connection. This command does trigger database synchronization. 
 
 
- **disconnect [Port Number]**: remove the connection between the current router and the remote one which is connected at port [Port Number]. This command does trigger database synchronization. 
 
 
- **detect [IP Address]**: output the routing path from this router to the destination router which is identified by [IP Address]. 
 
- **neighbors**: will output IP Addresses of all neighbors of the current router 
- **quit**: quit the current router. Before quitting, the current router will inform all of its neighbor, hence this will trigger the database synchronization. 
 
- **update [processIP] [processPort] [simulatedIP] [new weight]**: update the weight of link between current router and remote router. This command does trigger database synchronization. 
 
 
## Startup Instruction (from the main folder):  
To Compile the program, run: mvn compile assembly:single  
To start a router, run: java -cp target/COMP535-1.0-SNAPSHOT-jar-with-dependencies.jar socs.network.Main conf/router1.conf 
<br> (please choose the router’s number from [1,5]) 

