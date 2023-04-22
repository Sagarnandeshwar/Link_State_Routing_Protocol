package socs.network.node;

import socs.network.message.SOSPFPacket;
import socs.network.util.Configuration;

import socs.network.message.LSA;
import socs.network.message.LinkDescription;

import java.io.*;
import java.net.*;
import java.util.HashMap;

public class Router {

  protected LinkStateDatabase lsd;
  RouterDescription rd;
  //assuming that all routers are with 4 ports
  Link[] ports = new Link[4];

  public Router(Configuration config) throws Exception{

    if (config == null) {
      throw new Exception("Given configuration is invalid: null");
    }

    // Starting Server Socket
    ServerSocket server_socket = null;
    short process_port = 1000;
    short loop_port_no = 1000;

    while (server_socket == null){
      try{
        server_socket = new ServerSocket(loop_port_no);
        Server_Service_Thread server_service = new Server_Service_Thread(server_socket);
        Thread service_thread = new Thread(server_service);
        service_thread.start();
      } catch (Exception e){
        if (loop_port_no < Short.MAX_VALUE){
          loop_port_no += 1;
        } else{
          throw new Exception("Cannot find port to start router");
        }
      }
    }
    process_port = loop_port_no;

    // Router's RouterDescription
    this.rd = new RouterDescription(
            InetAddress.getLocalHost().getHostAddress(),
            process_port,
            config.getString("socs.network.router.ip"),
            RouterStatus.NA
            );

    // Router's LSA
    LSA router_link_list = new LSA();
    router_link_list.linkStateID = rd.simulatedIPAddress;
    router_link_list.lsaSeqNumber = 0;

    // Router's LSD
    this.lsd = new LinkStateDatabase(rd);
    lsd._store.put(rd.simulatedIPAddress,router_link_list);

    // Router's ports initialization to null
    for (int ind = 0; ind <= 3; ind++){
      this.ports[ind] = null;
    }

    // Print Router's information
    System.out.println("Router has been started");
    System.out.println("Simulated IP Address: " + this.rd.simulatedIPAddress);
    System.out.println("Port: " + this.rd.processPortNumber);
    System.out.println("process IP Address: "+ this.rd.processIPAddress);
  }


  /**
   * process request from the remote router.
   * For example: when router2 tries to attach router1. Router1 can decide whether it will accept this request.
   * The intuition is that if router2 is an unknown/anomaly router, it is always safe to reject the attached request from router2.
   */
  private void requestHandler() {}

  /**
   * attach the link to the remote router, which is identified by the given simulated ip;
   * to establish the connection via socket, you need to indentify the process IP and process Port;
   * additionally, weight is the cost to transmitting data through the link
   * <p/>
   * NOTE: this command should not trigger link database synchronization
   */
  private void processAttach(String processIP, short processPort, String simulatedIP, short weight){

    // check for null values, same values or already exits values
    if (RouterUtils.check_info(this.rd, processIP, simulatedIP, processPort, weight, ports)){
      System.out.println("Attachment Unsuccessful");
      return;
    }
    if (RouterUtils.in_port(simulatedIP, ports)){
      System.out.println("(Given) Simulated_IP is address already in this router");
      System.out.println("Attachment Unsuccessful");
      return;
    }
    if (RouterUtils.in_port(processPort, ports)){
      System.out.println("(Given) Port number is address already in this router");
      System.out.println("Attachment Unsuccessful");
      return;
    }

    // check if the remote router is running
    Socket client_Socket = null;
    OutputStream outToServer = null;
    InputStream inFromServer = null;

    // create the client socket
    try {
      client_Socket = new Socket(processIP, processPort);
      outToServer = client_Socket.getOutputStream();
      inFromServer = client_Socket.getInputStream();;
    } catch (IOException e) {
      System.out.println("Failed to locate the router " + simulatedIP);
      System.out.println("Attachment Unsuccessful");
      return;
    }
    // close the client socket
    try {
      inFromServer.close();
      outToServer.close();
      client_Socket.close();
    } catch (Exception e) {
      System.out.println("Could not close the test socket with " + simulatedIP);
      System.out.println("Attachment Unsuccessful");
      return;
    }

    // get available index value for the 'ports'
    int index = RouterUtils.get_Available_Port_Index(ports);
    if (index == -1){
      System.out.println("No port available for attach");
      System.out.println("Attachment Unsuccessful");
      return;
    }

    // creating new link
    RouterDescription remote_rd = new RouterDescription(processIP, processPort, simulatedIP, RouterStatus.NA);
    RouterDescription router_rd = new RouterDescription(this.rd.processIPAddress, this.rd.processPortNumber, this.rd.simulatedIPAddress, RouterStatus.NA);
    Link new_link = new Link(router_rd, remote_rd, weight);
    ports[index] = new_link;

    System.out.println("Attachment successful at index: " + index + " in router ports");
    return;
  }

  /**
   * broadcast Hello to neighbors
   */
  private void processStart() {
    Socket client_Socket = null;
    OutputStream outToServer = null;
    InputStream inFromServer = null;
    ObjectOutputStream out = null;
    ObjectInputStream in = null;

    SOSPFPacket hello_send_first = null;
    SOSPFPacket hello_reply = null;
    SOSPFPacket hello_send_second = null;
    SOSPFPacket receive_LSAUPDATE = null;


    for (Link a_link : this.ports) {
      if (a_link == null) {
        continue;
      }

      RouterDescription router_rd = a_link.router1;
      RouterDescription remote_rd = a_link.router2;

      System.out.println("Connecting with " + remote_rd.simulatedIPAddress);

      // If we have already established a two-way connection before
      if (remote_rd.status == RouterStatus.TWO_WAY) {
        System.out.println("Already established TWO-WAY with " + remote_rd.simulatedIPAddress);
        continue;
      }

      // Creating socket with the remote router
      try {
        client_Socket = new Socket(remote_rd.processIPAddress, remote_rd.processPortNumber);
        outToServer = client_Socket.getOutputStream();
        inFromServer = client_Socket.getInputStream();
        out = new ObjectOutputStream(outToServer);
        in = new ObjectInputStream(inFromServer);
      } catch (IOException e) {
        System.out.println("Failed To establish connection with " + remote_rd.simulatedIPAddress);
        continue;
      }

      // Starting "Hello" Message Exchange
      // Sending FIRST Hello Message
      try {
        hello_send_first = RouterUtils.make_Hello_Packet(router_rd, remote_rd, a_link.weight);
        out.writeObject(hello_send_first);
      } catch (Exception e) {
        System.out.println("Failed to send FIRST hello message to " + remote_rd.simulatedIPAddress);
        continue;
      }

      a_link.router1.status = RouterStatus.INIT;
      a_link.router2.status = RouterStatus.INIT;

      // Receive Hello Reply
      try {
        hello_reply = (SOSPFPacket) in.readObject();
      } catch (Exception e) {
        System.out.println("Failed to receive hello reply From" + remote_rd.simulatedIPAddress);
        continue;
      }

      System.out.println("received HELLO from " + remote_rd.simulatedIPAddress);

      // Sending SECOND Hello Message
      try {
        hello_send_second = RouterUtils.make_Hello_Packet(router_rd, remote_rd, a_link.weight);
        out.writeObject(hello_send_second);
      } catch (Exception e) {
        System.out.println("Failed to send SECOND hello message to " + remote_rd.simulatedIPAddress);
        continue;
      }

      a_link.router1.status = RouterStatus.TWO_WAY;
      a_link.router2.status = RouterStatus.TWO_WAY;
      System.out.println("set " + remote_rd.simulatedIPAddress + " STATE to TWO_WAY;");
      // "Hello" Message Exchange Finished

      // Now we start LSD synchronization
      System.out.println("");
      System.out.println("Starting one-to-one LSD synchronization with the remote router");

      // Create new LinkDescription
      LinkDescription new_LD = new LinkDescription();
      new_LD.linkID = remote_rd.simulatedIPAddress;
      new_LD.portNum = remote_rd.processPortNumber;
      new_LD.tosMetrics = a_link.weight;

      // Adding new LinkDescription to LSA in LSD
      lsd._store.get(router_rd.simulatedIPAddress).links.add(new_LD);
      lsd._store.get(rd.simulatedIPAddress).lsaSeqNumber += 1;

      // Sending Updated LSD to remote router
      try {
        SOSPFPacket send_LSAUPDATE_packet = RouterUtils.make_UpdateLSD_Packet(router_rd, remote_rd, lsd);
        out.writeObject(send_LSAUPDATE_packet);
      } catch (Exception e) {
        System.out.println("Failed to send LSD to " + remote_rd.simulatedIPAddress);
        continue;
      }

      System.out.println("send LSD to " + remote_rd.simulatedIPAddress);

      // Receive remote router LSD
      try {
        receive_LSAUPDATE = (SOSPFPacket) in.readObject();
      } catch (Exception e) {
        System.out.println("Failed to receive LSD from " + remote_rd.simulatedIPAddress);
        continue;
      }

      System.out.println("received LSD from " + remote_rd.simulatedIPAddress);

      // Updating LSD
      for (LSA a_lsa: receive_LSAUPDATE.lsaArray){
        lsd._store.put(a_lsa.linkStateID, a_lsa);
      }

      System.out.println("one-to-one LSD synchronization finished");
      // one-to-one LSD synchronization finished

      // Closed the socket
      try {
        inFromServer.close();
        outToServer.close();
        client_Socket.close();
      } catch (Exception e) {
        System.out.println("Could not close the socket with: " + remote_rd.simulatedIPAddress);
        continue;
      }

      // Broadcasting LSD to neighbours
      System.out.println("");
      System.out.println("Broadcasting LSD to neighbours");
      broadcast_lsd_update_to_all_except(remote_rd.simulatedIPAddress);
      System.out.println("Broadcasting Finished");
      System.out.println("");
    }
    return;
  }

  /**
   * attach the link to the remote router, which is identified by the given simulated ip;
   * to establish the connection via socket, you need to indentify the process IP and process Port;
   * additionally, weight is the cost to transmitting data through the link
   * <p/>
   * This command does trigger the link database synchronization
   */
  private void processConnect(String processIP, short processPort, String simulatedIP, short weight) {
    if (RouterUtils.check_info(this.rd, processIP, simulatedIP, processPort, weight, ports)){
      System.out.println("Connect Unsuccessful");
      return;
    }

    int index = -1;

    if (RouterUtils.in_port(simulatedIP, ports)){
      index = RouterUtils.get_index(simulatedIP, ports);
    } else{
      index = RouterUtils.get_Available_Port_Index(ports);
    }

    if (index == -1){
      System.out.println("No port available for Connect");
      System.out.println("Connect Unsuccessful");
      return;
    }

    RouterDescription remote_rd = new RouterDescription(processIP, processPort, simulatedIP, RouterStatus.NA);
    RouterDescription router_rd = new RouterDescription(rd.processIPAddress, rd.processPortNumber, rd.simulatedIPAddress, RouterStatus.NA);
    Link new_link = new Link(router_rd, remote_rd, weight);

    Socket client_Socket = null;
    OutputStream outToServer = null;
    InputStream inFromServer = null;
    ObjectOutputStream out = null;
    ObjectInputStream in = null;

    SOSPFPacket send_connect = null;
    SOSPFPacket receive_connect = null;
    SOSPFPacket send_LSAUPDATE_packet = null;
    SOSPFPacket receive_LSAUPDATE = null;

    // Create client socket with remote router
    try {
      client_Socket = new Socket(processIP, processPort);
      outToServer = client_Socket.getOutputStream();
      inFromServer = client_Socket.getInputStream();
      out = new ObjectOutputStream(outToServer);
      in = new ObjectInputStream(inFromServer);
    } catch (Exception e) {
      System.out.println("Failed To establish connection with " + simulatedIP);
      System.out.println("Connect Unsuccessful");
      return;
    }

    // Send CONNECT Request
    try {
      send_connect = RouterUtils.make_Connect_Packet(this.rd, remote_rd, new_link.weight);
      out.writeObject(send_connect);
    } catch (Exception e) {
      System.out.println("Failed to send Connect request to " + remote_rd.simulatedIPAddress);
      System.out.println("Connect Unsuccessful");
      return;
    }

    // Receive CONNECT Reply
    try {
      receive_connect = (SOSPFPacket) in.readObject();
    } catch (Exception e) {
      System.out.println("Failed to receive Connect reply from " + remote_rd.simulatedIPAddress);
      System.out.println("Connect Unsuccessful");
      System.out.println("");
      return;
    }

    if (!receive_connect.portAvailable) {
      System.out.println("No ports available at the remote router's end");
      System.out.println("Connect Unsuccessful");
      return;
    }

    // Now we start LSD synchronization
    System.out.println("Start one-to-one LSD synchronization with remote router");

    // updating port
    new_link.router1.status = RouterStatus.TWO_WAY;
    new_link.router2.status = RouterStatus.TWO_WAY;
    ports[index] = new_link;

    // Create new LinkDescription for remote router
    LinkDescription new_LD = new LinkDescription();
    new_LD.linkID = remote_rd.simulatedIPAddress;
    new_LD.portNum = remote_rd.processPortNumber;
    new_LD.tosMetrics = new_link.weight;

    // updating LSA in LSD
    lsd._store.get(rd.simulatedIPAddress).links.add(new_LD);
    lsd._store.get(rd.simulatedIPAddress).lsaSeqNumber += 1;

    System.out.println("send LSD to " + remote_rd.simulatedIPAddress);

    // Send Updated LSD to remote router
    try {
      send_LSAUPDATE_packet = RouterUtils.make_UpdateLSD_Packet(router_rd, remote_rd, lsd);
      out.writeObject(send_LSAUPDATE_packet);
    } catch (Exception e) {
      System.out.println("Failed to send LSD to " + remote_rd.simulatedIPAddress);
      System.out.println("Connect Unsuccessful");
      return;
    }

    // Receive update updated from remote router
    try {
      receive_LSAUPDATE = (SOSPFPacket) in.readObject();
    } catch (Exception e) {
      System.out.println("Failed to receive LSD " + remote_rd.simulatedIPAddress);
      System.out.println("Connect Unsuccessful");
      return;
    }

    System.out.println("received LSD from " + remote_rd.simulatedIPAddress);

    // Updating LSD
    for (LSA a_lsa: receive_LSAUPDATE.lsaArray){
      lsd._store.put(a_lsa.linkStateID, a_lsa);
    }

    System.out.println("one-to-one LSD synchronization finished");
    // one-to-one LSD synchronization finished

    // Closed the socket
    try {
      inFromServer.close();
      outToServer.close();
      client_Socket.close();
    } catch (Exception e) {
      System.out.println("Could not close the socket with: " + remote_rd.simulatedIPAddress);
      System.out.println("Connect Unsuccessful");
      return;
    }

    // Broadcasting LSD to neighbours
    System.out.println("");
    System.out.println("Broadcasting LSD to neighbours");
    broadcast_lsd_update_to_all_except(remote_rd.simulatedIPAddress);
    System.out.println("Broadcasting Finished");

    return;
  }

  /**
   * disconnect with the router identified by the given destination ip address
   * Notice: this command should trigger the synchronization of database
   *
   * @param portNumber the port number which the link attaches at
   */
  private void processDisconnect(Short portNumber) {
    // check for null value
    if (portNumber.equals(null)){
      System.out.println("Given port is null");
      System.out.println("Disconnect Unsuccessful");
      return;
    }

    // check if the router is in 'ports'
    if (!RouterUtils.in_port(portNumber, ports)){
      System.out.println("No router with given port found in the 'ports'");
      System.out.println("Disconnect Unsuccessful");
      return;
    }

    // get remote router index
    int index = RouterUtils.get_index(portNumber,ports);

    // get routers rd
    Link a_link = ports[index];
    RouterDescription remote_rd = ports[index].router2;
    RouterDescription router_rd = ports[index].router1;

    // check if router is in two_way connection
    if (ports[index].router2.status != RouterStatus.TWO_WAY){
      System.out.println("Given router is not in TWO_WAY connected");
      System.out.println("Disconnect Unsuccessful");
      ports[index] = null;
      return;
    }

    Socket client_Socket = null;
    OutputStream outToServer = null;
    InputStream inFromServer = null;
    ObjectOutputStream out = null;
    ObjectInputStream in = null;

    SOSPFPacket send_LSAUPDATE_packet = null;
    SOSPFPacket receive_LSAUPDATE = null;

    // Starting one-to-one LSD synchronization with remote router
    System.out.println("Start one-to-one LSD synchronization with remote router");

    // Create socket with remote router
    try {
      client_Socket = new Socket(remote_rd.processIPAddress, remote_rd.processPortNumber);
      outToServer = client_Socket.getOutputStream();
      inFromServer =client_Socket.getInputStream();
      out = new ObjectOutputStream(outToServer);
      in = new ObjectInputStream(inFromServer);
    } catch (IOException e) {
      System.out.println("Failed To establish connection with " + remote_rd.simulatedIPAddress);
      System.out.println("Disconnect Unsuccessful");
      return;
    }

    // removing remote router from ports and its LD from LSA in LSD
    for (LinkDescription ld : lsd._store.get(router_rd.simulatedIPAddress).links) {
      if (ld.linkID.equals(remote_rd.simulatedIPAddress)){
        ports[index] = null;
        lsd._store.get(router_rd.simulatedIPAddress).links.remove(ld);
        lsd._store.get(rd.simulatedIPAddress).lsaSeqNumber += 1;
        break;
      }
    }

    System.out.println("send LSD to " + remote_rd.simulatedIPAddress);

    // Sending LSD to remote router
    try {
      send_LSAUPDATE_packet = RouterUtils.make_DisConnect_Packet(router_rd, remote_rd, lsd);
      out.writeObject(send_LSAUPDATE_packet);
    } catch (Exception e) {
      System.out.println("Failed to send LSD to " + remote_rd.simulatedIPAddress);
      System.out.println("Disconnect Unsuccessful");
      return;
    }

    // Receiving LSD from remote router
    try {
      receive_LSAUPDATE = (SOSPFPacket) in.readObject();
    } catch (Exception e) {
      System.out.println("Failed to receive LSD " + remote_rd.simulatedIPAddress);
      System.out.println("Disconnect Unsuccessful");
      return;
    }

    System.out.println("received LSD from " + remote_rd.simulatedIPAddress);

    // updating LSD
    for (LSA a_lsa: receive_LSAUPDATE.lsaArray){
      lsd._store.put(a_lsa.linkStateID, a_lsa);
    }

    System.out.println("one-to-one LSD synchronization finished");
    // one-to-one LSD synchronization finished

    // Closed the socket
    try {
      inFromServer.close();
      outToServer.close();
      client_Socket.close();
    } catch (Exception e) {
      System.out.println("Could not close the socket with: " + remote_rd.simulatedIPAddress);
      System.out.println("Disconnect Unsuccessful");
      return;
    }

    // Broadcasting LSD to neighbours
    System.out.println("");
    System.out.println("Broadcasting LSD to neighbours");
    broadcast_lsd_update_to_all_except(remote_rd.simulatedIPAddress);
    System.out.println("Broadcasting Finished");

    return;
  }

  /**
   * disconnect with all neighbors and quit the program
   */
  private void processQuit() {
    Socket client_Socket = null;
    OutputStream outToServer = null;
    InputStream inFromServer = null;
    ObjectOutputStream out = null;
    ObjectInputStream in = null;

    SOSPFPacket send_quit = null;
    SOSPFPacket acknowledgement = null;

    for (Link a_link : ports){
      if (a_link == null){
        continue;
      }

      RouterDescription router_rd = a_link.router1;
      RouterDescription remote_rd = a_link.router2;

      // check if the remote router is in TWO_WAY connection
      if (remote_rd.status != RouterStatus.TWO_WAY){
        continue;
      }

      // Create Socket with remote router
      try{
        client_Socket = new Socket(remote_rd.processIPAddress, remote_rd.processPortNumber);
        outToServer = client_Socket.getOutputStream();
        out = new ObjectOutputStream(outToServer);
        inFromServer = client_Socket.getInputStream();
        in = new ObjectInputStream(inFromServer);
      } catch (Exception e){
        System.out.println("Failed to establish connection with " + remote_rd.simulatedIPAddress);
        continue;
      }

      System.out.println("send quit to " + remote_rd.simulatedIPAddress);
      // Sending Quit to remote router
      try{
        send_quit = RouterUtils.make_Quit_Packet(router_rd, remote_rd);
        out.writeObject(send_quit);
      } catch (Exception e){
        System.out.println("Failed send quit to " + remote_rd.simulatedIPAddress);
        continue;
      }

      // Receiving acknowledgement from remote router
      try{
        acknowledgement = (SOSPFPacket) in.readObject();
      } catch (Exception e){
        System.out.println("Failed to receive ack from " + remote_rd.simulatedIPAddress);
        continue;
      }

      System.out.println("received ack from " + remote_rd.simulatedIPAddress);

      // Close the socket
      try{
        outToServer.close();
        inFromServer.close();
        client_Socket.close();
      } catch (Exception e){
        System.out.println("Could not close the socket with: " + remote_rd.simulatedIPAddress);
        continue;
      }
    }
    // Shutting down the program
    System.exit(0);
  }

  /**
   * output the shortest path to the given destination ip
   * <p/>
   * format: source ip address  -> ip address -> ... -> destination ip
   *
   * @param destinationIP the ip adderss of the destination simulated router
   */
  private void processDetect(String destinationIP) {
    String path_to_dest = lsd.getShortestPath(destinationIP);
    if (path_to_dest == null){
      return;
    }
    System.out.println(path_to_dest);
    return;
  }

  /**
   * output the neighbors of the routers
   */
  private void processNeighbors() {
    int index = 0;
    for (Link cur_link: this.ports){
      if (cur_link == null){
        continue;
      }
      RouterDescription remote_rd = cur_link.router2;
      if (remote_rd.status == RouterStatus.TWO_WAY){
        System.out.println("IP Address of the neighbor" + index + ": " + remote_rd.simulatedIPAddress);
        index = index + 1;
      }
    }
  }

  /**
   * update the weight of an attached link
   */
  private void updateWeight(String processIP, short desPort, String simulatedIP, short new_weight){
    // check for null values, same values
    if (RouterUtils.check_info(this.rd, processIP, simulatedIP, desPort, new_weight, ports)){
      System.out.println("Update Unsuccessful");
      return;
    }

    // check if the router is in 'ports'
    if (!RouterUtils.in_port(desPort, ports)){
      System.out.println("No router with given port found in the 'ports'");
      System.out.println("Update Unsuccessful");
      return;
    }

    // get the index of remote router in 'port'
    int index = RouterUtils.get_index(desPort, ports);

    // get the routers rd
    Link a_link = ports[index];
    RouterDescription router_rd = a_link.router1;
    RouterDescription remote_rd = a_link.router2;

    // Updating weight in ports and in remote router's LD in LSA in LSD
    for (LinkDescription link : lsd._store.get(rd.simulatedIPAddress).links){
      if (link.linkID.equals(remote_rd.simulatedIPAddress)){
        LinkDescription new_link = new LinkDescription();
        new_link.tosMetrics = new_weight;
        new_link.linkID = link.linkID;
        new_link.portNum = link.portNum;

        ports[index].weight = new_weight;
        lsd._store.get(rd.simulatedIPAddress).links.remove(link);
        lsd._store.get(rd.simulatedIPAddress).links.add(new_link);
        lsd._store.get(rd.simulatedIPAddress).lsaSeqNumber +=1;
        break;
      }
    }

    Socket client_Socket = null;
    OutputStream outToServer = null;
    InputStream inFromServer = null;
    ObjectOutputStream out = null;
    ObjectInputStream in = null;

    SOSPFPacket lsd_update_receive = null;
    SOSPFPacket updateWeight = null;

    // Create socket with remote router
    try{
      client_Socket = new Socket(remote_rd.processIPAddress, remote_rd.processPortNumber);
      outToServer = client_Socket.getOutputStream();
      out = new ObjectOutputStream(outToServer);
      inFromServer = client_Socket.getInputStream();
      in = new ObjectInputStream(inFromServer);
    } catch (Exception e){
      System.out.println("Failed to establish connection with " + remote_rd.simulatedIPAddress);
      System.out.println("Update Unsuccessful");
      return;
    }

    System.out.println("send LSD to " + remote_rd.simulatedIPAddress);
    // Sending LSD to remote router
    try{
      updateWeight = RouterUtils.make_UpdateWeight_Packet(router_rd, remote_rd, lsd, new_weight);
      out.writeObject(updateWeight);
    } catch (Exception e){
      System.out.println("Failed send LSD to " + remote_rd.simulatedIPAddress);
      System.out.println("Update Unsuccessful");
      return;
    }

    // Receiving LSD from remote router
    try{
      lsd_update_receive = (SOSPFPacket) in.readObject();
    } catch (Exception e){
      System.out.println("Failed to receive LSD update from " + remote_rd.simulatedIPAddress);
      System.out.println("Update Unsuccessful");
      return;
    }
    System.out.println("received LSD from " + remote_rd.simulatedIPAddress);

    // Updating LSD
    for (LSA a_lsa: lsd_update_receive.lsaArray){
      lsd._store.put(a_lsa.linkStateID, a_lsa);
    }

    // close socket
    try {
      inFromServer.close();
      outToServer.close();
      client_Socket.close();
    } catch (Exception e) {
      System.out.println("Could not close the socket with: " + remote_rd.simulatedIPAddress);
      System.out.println("Update Unsuccessful");
      return;
    }

    // Broadcasting LSD to neighbours
    System.out.println("");
    System.out.println("Broadcasting LSA update to neighbours");
    broadcast_lsd_update_to_all_except(remote_rd.simulatedIPAddress);
    System.out.println("Broadcasting Finished");
  }


  public void broadcast_lsd_update_to_all_except(String ignore_simIP){
    Socket client_Socket = null;
    OutputStream outToServer = null;
    InputStream inFromServer = null;
    ObjectOutputStream out = null;
    ObjectInputStream in = null;

    SOSPFPacket lsd_update_direct = null;
    SOSPFPacket acknowledgement = null;


    for (Link a_link: ports){
      if (a_link == null){
        continue;
      }

      RouterDescription router_rd = a_link.router1;
      RouterDescription remote_rd = a_link.router2;

      // check if remote router is in TWO_WAY connection
      if (remote_rd.status != RouterStatus.TWO_WAY){
        continue;
      }

      // check if remote router is ignore_simIP
      if (remote_rd.simulatedIPAddress.equals(ignore_simIP)){
        continue;
      }

      System.out.println("broadcasting LSD to " + remote_rd.simulatedIPAddress);

      // create socket with remote router
      try{
        client_Socket = new Socket(remote_rd.processIPAddress, remote_rd.processPortNumber);
        outToServer = client_Socket.getOutputStream();
        out = new ObjectOutputStream(outToServer);
        inFromServer = client_Socket.getInputStream();
        in = new ObjectInputStream(inFromServer);
      } catch (Exception e){
        System.out.println("Failed to establish connection with " + remote_rd.simulatedIPAddress);
        continue;
      }

      System.out.println("send LSD update to " + remote_rd.simulatedIPAddress);
      // Sending LSD to remote router
      try{
        lsd_update_direct = RouterUtils.make_UpdateLSD_Packet(router_rd, remote_rd, lsd);
        out.writeObject(lsd_update_direct);
      } catch (Exception e){
        System.out.println("Failed send LSD to " + remote_rd.simulatedIPAddress);
        continue;
      }

      // Receiving acknowledgement from remote router
      try{
        acknowledgement = (SOSPFPacket) in.readObject();
      } catch (Exception e){
        System.out.println("Failed to receive LSA update from" + remote_rd.simulatedIPAddress);
        continue;
      }

      System.out.println("received ack from " + remote_rd.simulatedIPAddress);

      // close the socket
      try{
        outToServer.close();
        client_Socket.close();
      } catch (Exception e){
        System.out.println("Could not close the socket with: " + remote_rd.simulatedIPAddress);
        continue;
      }
    }
  }
  public void terminal() {
    try {
      InputStreamReader isReader = new InputStreamReader(System.in);

      BufferedReader br = new BufferedReader(isReader);

      System.out.print(">> ");
      String command = br.readLine();
      while (true) {
        if (command.startsWith("detect ")) {
          String[] cmdLine = command.split(" ");
          processDetect(cmdLine[1]);
        } else if (command.startsWith("disconnect ")) {
          String[] cmdLine = command.split(" ");
          processDisconnect(Short.parseShort(cmdLine[1]));
        } else if (command.startsWith("quit")) {
          processQuit();
          break;
        } else if (command.startsWith("attach ")) {
          String[] cmdLine = command.split(" ");
          processAttach(cmdLine[1], Short.parseShort(cmdLine[2]),
                  cmdLine[3], Short.parseShort(cmdLine[4]));
        } else if (command.equals("start")) {
          processStart();
        } else if (command.startsWith("connect ")) {
          String[] cmdLine = command.split(" ");
          processConnect(cmdLine[1], Short.parseShort(cmdLine[2]),
                  cmdLine[3], Short.parseShort(cmdLine[4]));
        } else if (command.equals("neighbors")) {
          //output neighbors
          processNeighbors();
        }
          else if (command.startsWith("update ")) {
          String[] cmdLine = command.split(" ");
          updateWeight(cmdLine[1], Short.parseShort(cmdLine[2]), cmdLine[3], Short.parseShort(cmdLine[4]));
        } else {
          //invalid command
          //break
          System.out.print("invalid Command \n");
        }
        System.out.print(">> ");
        command = br.readLine();
      }
      isReader.close();
      br.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public class Server_Service_Thread implements Runnable {
    private final ServerSocket server_socket;
    public Server_Service_Thread(ServerSocket s) {server_socket = s;}

    public void run() {
      try {
        while (true) {
          // wait for client request
          Socket service_socket = server_socket.accept();

          if (service_socket == null){
            continue;
          }

          // create and start a thread to handle client request
          Server_ClientServiceThread request_handler = new Server_ClientServiceThread(service_socket);
          Thread client_service_thread = new Thread(request_handler);
          client_service_thread.start();
        }
      } catch (Exception e) {
        System.out.println("Server crashed");
        System.out.println(">> ");
      }
    }
  }

    public class Server_ClientServiceThread implements Runnable {
      private final Socket client_Socket;
      Server_ClientServiceThread(Socket cs){
        client_Socket = cs;
      }

      public void handle_HELLO_packet(SOSPFPacket hello_received_first, ObjectInputStream in_client, ObjectOutputStream out_client){
        SOSPFPacket hello_send_first = null;
        SOSPFPacket hello_received_second = null;
        SOSPFPacket receive_LSAUPDATE = null;
        SOSPFPacket send_LSAUPDATE_packet = null;

        System.out.println("received HELLO from " + hello_received_first.srcIP);

        RouterDescription remote_rd = null;
        RouterDescription router_rd = null;
        short weights;
        int index = -1;
        Link new_link = null;

        // Checking if the router is already exits in the 'ports'
        if (RouterUtils.in_port(hello_received_first.srcIP, ports)){
          index = RouterUtils.get_index(hello_received_first.srcIP, ports);

          if (ports[index].router2.status == RouterStatus.TWO_WAY){
            System.out.println("Already have two-way communication with " + hello_received_first.srcIP);
            return;
          }
          router_rd = ports[index].router1;
          remote_rd = ports[index].router2;
          weights = ports[index].weight;
        } else{
          remote_rd = new RouterDescription(
                  hello_received_first.srcProcessIP,
                  hello_received_first.srcProcessPort,
                  hello_received_first.srcIP,
                  RouterStatus.INIT
                  );

          router_rd = new RouterDescription(
                  rd.processIPAddress,
                  rd.processPortNumber,
                  rd.simulatedIPAddress,
                  RouterStatus.INIT
          );

          weights = hello_received_first.weight;
          new_link = new Link(router_rd, remote_rd, weights);
          index = RouterUtils.get_Available_Port_Index(ports);
          if (index == -1){
            System.out.println("No ports available");
            return;
          }
        }
        // updating ports
        ports[index] = new_link;

        System.out.println("set " + hello_received_first.srcIP + " STATE to INIT;");
        // Sending Hello Reply remote router
        try{
          hello_send_first = RouterUtils.make_Hello_Packet(router_rd, remote_rd, weights);
          out_client.writeObject(hello_send_first);
        } catch (Exception e){
          System.out.print("Could not reply hello to " + remote_rd.simulatedIPAddress);
          return;
        }

        // Receiving SECOND hello message
        try{
          hello_received_second = (SOSPFPacket) in_client.readObject();
        } catch (Exception e){
          System.out.print("Could not receive hello from " + remote_rd.simulatedIPAddress);
          return;
        }
        System.out.println("received HELLO from " + hello_received_second.srcIP);

        // updating port
        ports[index].router2.status = RouterStatus.TWO_WAY;
        ports[index].router1.status = RouterStatus.TWO_WAY;
        System.out.println("set " + hello_received_first.srcIP + " STATE to TWO_WAY;");
        // TWO_WAY connection established

        // Starting one-to-one LSA synchronization with remote router
        System.out.println("");
        System.out.println("Start one-to-one LSD synchronization with remote router");

        // Receiving LSD update from remote router
        try{
          receive_LSAUPDATE = (SOSPFPacket) in_client.readObject();
        } catch (Exception e){
          System.out.println("Could not receive LSD from " + remote_rd.simulatedIPAddress);
          return;
        }
        System.out.println("received LSD from " + remote_rd.simulatedIPAddress);

        // Updating LSD
        for (LSA a_lsa: receive_LSAUPDATE.lsaArray){
          lsd._store.put(a_lsa.linkStateID,a_lsa);
        }

        // create new LinkDescription for remote router
        LinkDescription new_LD = new LinkDescription();
        new_LD.linkID = remote_rd.simulatedIPAddress;
        new_LD.portNum = remote_rd.processPortNumber;
        new_LD.tosMetrics = weights;

        // Updating LSD
        lsd._store.get(router_rd.simulatedIPAddress).links.add(new_LD);
        lsd._store.get(rd.simulatedIPAddress).lsaSeqNumber += 1;

        System.out.println("send LSD to " + remote_rd.simulatedIPAddress);
        try{
          send_LSAUPDATE_packet = RouterUtils.make_UpdateLSD_Packet(router_rd, remote_rd, lsd);
          out_client.writeObject(send_LSAUPDATE_packet);
        } catch (Exception e){
          System.out.println("Could not send LSD to " + remote_rd.simulatedIPAddress);
          return;
        }

        System.out.println("one-to-one LSD synchronization finished");
        // one-to-one LSA synchronization finished

        // Broadcasting LSD to neighbours
        System.out.println("");
        System.out.println("Broadcasting LSA update to neighbours");
        broadcast_lsd_update_to_all_except(remote_rd.simulatedIPAddress);
        System.out.println("Broadcasting finished");
        return;
      }


      public void handle_CONNECT_packet(SOSPFPacket connect_request, ObjectInputStream in_client, ObjectOutputStream out_client){
        SOSPFPacket receive_LSAUPDATE = null;
        SOSPFPacket send_LSAUPDATE_packet = null;
        SOSPFPacket connect_reply = null;

        System.out.println("received connect from " + connect_request.srcIP);

        // Checking if the Router is already there in the ports
        RouterDescription remote_rd = null;
        RouterDescription router_rd = null;
        short weights;
        int index = -1;
        Link new_link = null;

        // Checking if the router is already exits in the 'ports'
        if (RouterUtils.in_port(connect_request.srcIP, ports)){
          index = RouterUtils.get_index(connect_request.srcIP, ports);

          if (ports[index].router2.status == RouterStatus.TWO_WAY){
            System.out.println("Already have two-way communication with " + connect_request.srcIP);
            return;
          }
          router_rd = ports[index].router1;
          remote_rd = ports[index].router2;
          weights = ports[index].weight;
        } else{
          remote_rd = new RouterDescription(
                  connect_request.srcProcessIP,
                  connect_request.srcProcessPort,
                  connect_request.srcIP,
                  RouterStatus.TWO_WAY
          );

          router_rd = new RouterDescription(
                  rd.processIPAddress,
                  rd.processPortNumber,
                  rd.simulatedIPAddress,
                  RouterStatus.TWO_WAY
          );

          weights = connect_request.weight;
          new_link = new Link(router_rd, remote_rd, weights);
          index = RouterUtils.get_Available_Port_Index(ports);
        }

        if (index == -1){
          System.out.println("to ports available to connect");
          try{
            connect_reply = RouterUtils.make_Acknowledgement_packet(router_rd, remote_rd, false);
            out_client.writeObject(connect_reply);
          } catch (Exception e){
            System.out.println("Could not send ack to " + remote_rd.simulatedIPAddress);
            return;
          }
          return;
        }

        // Sending Acknowledgement Reply
        try{
          connect_reply = RouterUtils.make_Acknowledgement_packet(router_rd, remote_rd, true);
          out_client.writeObject(connect_reply);
        } catch (Exception e){
          System.out.println("Could not send ack to " + remote_rd.simulatedIPAddress);
          return;
        }


        // updating port
        ports[index] = new_link;

        // Start one-to-one LSD synchronization with remote router
        System.out.println("Start one-to-one LSD synchronization with remote router");

        // Receiving LSD from remote router
        try{
          receive_LSAUPDATE = (SOSPFPacket) in_client.readObject();
        } catch (Exception e){
          System.out.println("Could not receive LSD from " + remote_rd.simulatedIPAddress);
          return;
        }
        System.out.println("received LSD from " + remote_rd.simulatedIPAddress);

        // updating LSD
        for (LSA a_lsa: receive_LSAUPDATE.lsaArray){
          lsd._store.put(a_lsa.linkStateID,a_lsa);
        }

        // Create new LinkDescription for remote router
        LinkDescription new_LD = new LinkDescription();
        new_LD.linkID = remote_rd.simulatedIPAddress;
        new_LD.portNum = remote_rd.processPortNumber;
        new_LD.tosMetrics = weights;

        // updating LSA in LSD
        lsd._store.get(router_rd.simulatedIPAddress).links.add(new_LD);
        lsd._store.get(rd.simulatedIPAddress).lsaSeqNumber += 1;

        System.out.println("send LSD to " + remote_rd.simulatedIPAddress);
        // Sending LSD to remote router
        try{
          send_LSAUPDATE_packet = RouterUtils.make_UpdateLSD_Packet(router_rd, remote_rd, lsd);
          out_client.writeObject(send_LSAUPDATE_packet);
        } catch (Exception e){
          System.out.println("Could not send LSD to " + remote_rd.simulatedIPAddress);
          return;
        }
        System.out.println("one-to-one LSD synchronization finished");
        // one-to-one LSD synchronization finished

        // Broadcasting LSD to neighbours
        System.out.println("");
        System.out.println("Broadcasting LSA update to neighbours");
        broadcast_lsd_update_to_all_except(remote_rd.simulatedIPAddress);
        System.out.println("Broadcasting finished");
        return;
      }

      public void handle_DISCONNECT_packet(SOSPFPacket disconnect_request, ObjectInputStream in_client, ObjectOutputStream out_client){
        SOSPFPacket send_LSAUPDATE_packet = null;

        // get the index of remote router in 'ports'
        int index = RouterUtils.get_index(disconnect_request.srcIP, ports);

        Link a_link = ports[index];
        RouterDescription remote_rd = a_link.router2;
        RouterDescription router_rd = a_link.router1;

        System.out.println("received LSD from " + remote_rd.simulatedIPAddress);

        // Start one-to-one LSD synchronization with remote router
        System.out.println("Start one-to-one LSD synchronization with remote router");

        // updating LSD
        for (LSA a_lsa: disconnect_request.lsaArray){
          lsd._store.put(a_lsa.linkStateID,a_lsa);
        }

        // updating port
        ports[index] = null;

        // update the LSA in LSD
        for (LinkDescription link: lsd._store.get(rd.simulatedIPAddress).links){
          if (link.linkID.equals(remote_rd.simulatedIPAddress)){
            lsd._store.get(router_rd.simulatedIPAddress).links.remove(link);
            lsd._store.get(rd.simulatedIPAddress).lsaSeqNumber += 1;
            break;
          }
        }

        System.out.println("send LSD to " + remote_rd.simulatedIPAddress);
        try{
          send_LSAUPDATE_packet = RouterUtils.make_UpdateLSD_Packet(router_rd, remote_rd, lsd);
          out_client.writeObject(send_LSAUPDATE_packet);
        } catch (Exception e){
          System.out.println("Could not send LSD to " + remote_rd.simulatedIPAddress);
          return;
        }

        System.out.println("one-to-one LSD synchronization finished");
        // one-to-one LSD synchronization finished

        // Broadcasting LSD to neighbours
        System.out.println("");
        System.out.println("Broadcasting LSA update to neighbours");
        broadcast_lsd_update_to_all_except(remote_rd.simulatedIPAddress);
        System.out.println("Broadcasting finished");

        return;

      }

      public void handle_QUIT_packet(SOSPFPacket quit_received, ObjectInputStream in_client, ObjectOutputStream out_client){
        RouterDescription remote_rd = new RouterDescription(
                quit_received.srcProcessIP,
                quit_received.srcProcessPort,
                quit_received.srcIP,
                RouterStatus.TWO_WAY
        );

        System.out.println("received quit from "+ quit_received.srcIP);

        // updating port
        int index = RouterUtils.get_index(remote_rd.simulatedIPAddress, ports);
        ports[index] = null;

        // updating LSA
        for (LinkDescription link: lsd._store.get(rd.simulatedIPAddress).links){
          if (link.linkID.equals(remote_rd.simulatedIPAddress)){
            lsd._store.get(rd.simulatedIPAddress).links.remove(link);
            lsd._store.get(rd.simulatedIPAddress).lsaSeqNumber += 1;
            break;
          }
        }

        // Updating LSD
        lsd._store.remove(remote_rd.simulatedIPAddress);

        System.out.println("send ack to "+ remote_rd.simulatedIPAddress);

        // Sending acknowledgement to remote router
        try{
          SOSPFPacket acknowledgement = RouterUtils.make_Acknowledgement_packet(rd, remote_rd, true);
          out_client.writeObject(acknowledgement);
        } catch (Exception e){
          System.out.println("Could not send ack to " + remote_rd.simulatedIPAddress);
          return;
        }

        // Broadcasting LSD to neighbours
        System.out.println("");
        System.out.println("Broadcasting LSA update to neighbours");
        broadcast_lsd_update_to_all_except(remote_rd.simulatedIPAddress);
        System.out.println("Broadcasting finished");

        return;

      }

      public void handle_UPDATEWEIGHT_packet(SOSPFPacket updateWeight_received, ObjectInputStream in_client, ObjectOutputStream out_client) {
        RouterDescription remote_rd = new RouterDescription(
                updateWeight_received.srcProcessIP,
                updateWeight_received.srcProcessPort,
                updateWeight_received.srcIP,
                RouterStatus.TWO_WAY
        );

        System.out.println("received quit from "+ updateWeight_received.srcIP);

        // updating port
        int index = RouterUtils.get_index(remote_rd.simulatedIPAddress, ports);
        ports[index].weight = updateWeight_received.weight;

        // updating LSD
        for (LSA a_lsa: updateWeight_received.lsaArray){
          lsd._store.put(a_lsa.linkStateID, a_lsa);
        }

        // updating LSA in LSD
        for(LinkDescription link: lsd._store.get(rd.simulatedIPAddress).links){
          if (link.linkID.equals(remote_rd.simulatedIPAddress)){
            LinkDescription new_link = new LinkDescription();
            new_link.portNum = link.portNum;
            new_link.linkID = link.linkID;
            new_link.tosMetrics = updateWeight_received.weight;

            lsd._store.get(rd.simulatedIPAddress).links.remove(link);
            lsd._store.get(rd.simulatedIPAddress).links.add(new_link);
            lsd._store.get(rd.simulatedIPAddress).lsaSeqNumber += 1;
            break;
          }
        }

        System.out.println("send LSD to "+ remote_rd.simulatedIPAddress);
        // sending LSD to remote router
        try{
          SOSPFPacket send_LSAUPDATE_packet = RouterUtils.make_UpdateLSD_Packet(rd, remote_rd, lsd);
          out_client.writeObject(send_LSAUPDATE_packet);
        } catch (Exception e){
          System.out.println("Could not send LSD to " + remote_rd.simulatedIPAddress);
          return;
        }

        // Broadcasting LSD to neighbours
        System.out.println("");
        System.out.println("Broadcasting LSA update to neighbours");
        broadcast_lsd_update_to_all_except(remote_rd.simulatedIPAddress);
        System.out.println("Broadcasting finished");
        return;

      }


      public void handle_LSAUPDATE_packet(SOSPFPacket LSAUPDATE_received, ObjectInputStream in_client, ObjectOutputStream out_client){
        RouterDescription remote_rd = new RouterDescription(
                LSAUPDATE_received.srcProcessIP,
                LSAUPDATE_received.srcProcessPort,
                LSAUPDATE_received.srcIP,
                RouterStatus.TWO_WAY
        );


        System.out.println("received LSD from "+ LSAUPDATE_received.srcIP);

        // checking for any changes
        boolean should_broadcast = false;

        // the following Hash Map help to decide whether to remove Router from LSD or not
        HashMap<String, Boolean> router_seen = new HashMap<String, Boolean>();
        for (String routers: lsd._store.keySet()){
          if (routers.equals(rd.simulatedIPAddress)){
            router_seen.put(routers, true);
            continue;
          }
          router_seen.put(routers, false);
        }

        for (LSA a_lsa: LSAUPDATE_received.lsaArray){
          String router_name = a_lsa.linkStateID;
          // add if router not in LSD
          if (lsd._store.get(router_name) == null){
            lsd._store.put(a_lsa.linkStateID, a_lsa);
            should_broadcast = true;
          }
          else{
            int received_lad_update = a_lsa.lsaSeqNumber;
            // update only if received seq no. is greater than local seq no.
            if (received_lad_update > lsd._store.get(router_name).lsaSeqNumber){
              lsd._store.put(a_lsa.linkStateID, a_lsa);
              should_broadcast = true;
            }
          }
          router_seen.put(router_name,true);
        }

        // if not seen then remove the router from LSD
        for (String routers: router_seen.keySet()){
          if (!router_seen.get(routers)) {
            lsd._store.remove(routers);
            should_broadcast = true;
          }
        }

        System.out.println("send ack to " + remote_rd.simulatedIPAddress);
        // Sending acknowledgement to remote router
        try{
          SOSPFPacket acknowledgment = RouterUtils.make_Acknowledgement_packet(rd, remote_rd, true);
          out_client.writeObject(acknowledgment);
        } catch (Exception e){
          System.out.println("Could not send ack to " + remote_rd.simulatedIPAddress);
          return;
        }

        // Broadcasting LSD to neighbours
        if (should_broadcast){
          System.out.println("");
          System.out.println("Broadcasting LSA update to neighbours");
          broadcast_lsd_update_to_all_except(remote_rd.simulatedIPAddress);
          System.out.println("Broadcasting finished");
        }
        return;
      }

      public void run(){
        InputStream in_from_client = null;
        OutputStream out_to_client = null;
        ObjectInputStream in_client = null;
        ObjectOutputStream out_client = null;

        SOSPFPacket received_packet = null;

        // create a socket
        try{
          in_from_client = client_Socket.getInputStream();
          out_to_client = client_Socket.getOutputStream();
          in_client = new ObjectInputStream(in_from_client);
          out_client = new ObjectOutputStream(out_to_client);
        } catch (Exception e){
          System.out.println("Could not establish a connection");
          System.out.print(">> ");
          return;
        }

        // Receiving packet from client
        try{
          received_packet = (SOSPFPacket) in_client.readObject();
        } catch (Exception e){
          System.out.println("Could not receive packet");
          System.out.print(">> ");
          e.printStackTrace();
          return;
        }

        System.out.println("");

        // If the packet's type is "hello"
        if (received_packet.sospfType == 0){
          handle_HELLO_packet(received_packet, in_client, out_client);
        }

        // If the packet's type is "LSDUpdate"
        if (received_packet.sospfType == 1){
          handle_LSAUPDATE_packet(received_packet, in_client, out_client);
        }

        // If the packet's type is "Connect"
        if (received_packet.sospfType == 3){
          handle_CONNECT_packet(received_packet, in_client, out_client);
        }

        // If the packet's type is "Disconnect"
        if (received_packet.sospfType == 4){
          handle_DISCONNECT_packet(received_packet, in_client, out_client);
        }

        // If the packet's type is "Quit"
        if (received_packet.sospfType == 5){
          handle_QUIT_packet(received_packet, in_client, out_client);
        }

        // If the packet's type is "Update"
        if (received_packet.sospfType == 6){
          handle_UPDATEWEIGHT_packet(received_packet, in_client, out_client);
        }

        // Close socket
        try{
          in_from_client.close();
          out_to_client.close();
          client_Socket.close();
        } catch (Exception e) {
          System.out.println("Could not closed the socket");
          System.out.print(">> ");
          return;
        }
        // Back to command line
        System.out.print(">> ");

      }

  }
}
