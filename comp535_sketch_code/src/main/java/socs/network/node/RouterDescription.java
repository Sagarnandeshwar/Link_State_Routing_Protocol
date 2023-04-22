package socs.network.node;

public class RouterDescription {
  //used to socket communication
  String processIPAddress;
  short processPortNumber;
  //used to identify the router in the simulated network space
  String simulatedIPAddress;
  //status of the router
  RouterStatus status;

  public RouterDescription(String processIP, short processPort, String processSimIP, RouterStatus router_status){
    this.processIPAddress = processIP;
    this.processPortNumber = processPort;
    this.simulatedIPAddress = processSimIP;
    this.status = router_status;
  }
}
