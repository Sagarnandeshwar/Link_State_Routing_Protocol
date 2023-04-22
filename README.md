(bonus): I have implemented the update weight function to update the weight of the link
->update [processIP] [processPort] [simulatedIP] [new weight]

Changes: 
For PA2 in the Router class's terminal() method, I have moved the "break;" statement from the "else:" condition into "quit." This makes it much easier to run the command because, now, it won't close the reader if there is a mistake ("invalid" command).

Precautions :
If there is/are any terminal window/s (associated with the program) that is loading (i.e. there is an active loading icon on the top), then please wait till the loading process is completed (i.e. loading icon is gone) before giving any other command to that terminal window or any other window associate to the program. This may take some time, usually 1-5 sec. I implemented several measures to prevent miscommunication amongst all the active router (especially during hello message exchange, lsd update or broadcasting). However, there are still some chances that an issue could occur if precaution isn’t taken.

sospfType:
	0 -> Hello 
	1 -> LSAUpdate 
	2 -> Acknowledgement 
	3 -> Connect
	4 -> Disconnect 
	5 -> Quit
	6 -> UpdateWeight

RouterStatus:
	NA -> Not applicable (given during "attach" and before "start")
	INIT
	TWO_WAY


LSD synchronization: 
LSD synchronization happens in two stages. After two routers say, Router A and Router B, have a complete “hello” message exchange, they
	(First: one-to-one) update and synchronize their LSD and LSA, and then
	(Second: Broadcast) broadcast the updated LSA to all of their neighbours

Now, the neighbours will update their LSD based on (SeqNum Condition) and will broadcast this (received) update to their neighbour (except the source router). This chain of forwarding update messages will stop based on (should_broadcast Condition).

Conditions:
	SeqNum Condition: Router will update its LSD only if the SeqNum of the received LSA is higher than the (local) SeqNum in their LSA
	should_broadcast Condition: Router will only broadcast if and only if there is any change in their LSD.

Methods description:

"attach" Function: This function creates a new (local) link (b\w current router and remote router) and adds it to the ports array. It creates a temporary socket with the remote router to check the validity of the given router address information; however, it does not exchange any message, and the remote router does not change/update its LSD.

"neighbour" Function: Loop through all (not null) links in ports and output all those neighbours with whom it has a TWO_WAY connection.

"start" Function: (this) router tries to attach itself to the remote Servers in the ports array. Once the connection is established, they start the “hello” messages exchange. After a successful "hello" message with the remote router, it starts the two-stage LSD synchronization with the remote router and its other neighbours.

"connect" Function: it is essentially attach() + start() in one command. It checks the validity of remote router and make a new link for router's port like attack() and conduct the connect request and LSD synchronization like "start"

"disconnect" Function: Remove the given router from ports and LSD and simultaneously perform LSD synchronization.

"quit": quit the router program after informing the neighbours

"update": update the weight of the link

Overall Router Design:
During the initialization of the Router instance, we start a Server_Service_Thread thread that listens (accept()) to all client requests. Then the Server_Service_Thread thread will create a new client_service_thread thread every time it receives a request from the client to handle requests from the client (i.e. the hello message communication and LSDUpdate communication)

Startup (from inside the main folder):
Compile the code using the following: 
	mvn compile assembly:single
Run the router using the following:
	java -cp target/COMP535-1.0-SNAPSHOT-jar-with-dependencies.jar socs.network.Main conf/router1.conf



