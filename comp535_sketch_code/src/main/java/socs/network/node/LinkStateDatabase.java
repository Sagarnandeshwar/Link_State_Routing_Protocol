package socs.network.node;

import socs.network.message.LSA;
import socs.network.message.LinkDescription;

import java.util.*;

public class LinkStateDatabase {

  //linkID => LSAInstance
  public HashMap<String, LSA> _store = new HashMap<String, LSA>();

  private RouterDescription rd = null;

  public LinkStateDatabase(RouterDescription routerDescription) {
    rd = routerDescription;
    LSA l = initLinkStateDatabase();
    _store.put(l.linkStateID, l);
  }

  /**
   * output the shortest path from this router to the destination with the given IP address
   */
  public String getShortestPath(String destinationIP) {

    // Check for null value, same value and existence
    if( destinationIP.equals(null)){
      System.out.println("(Given) IP address is null");
      return null;
    }

    if(!_store.containsKey(destinationIP)){
      System.out.println("(Given) IP address not in the network");
      return null;
    }

    if(destinationIP.equals(rd.simulatedIPAddress)){
      System.out.println("(Given) this router's IP address");
      return null;
    }

    // Dijkstra's Shortest Path Algorithm
    HashMap<String, Integer> distance = new HashMap<String, Integer>();
    HashMap<String, String> parents = new HashMap<String, String>();
    HashMap<String, Boolean> node_visited = new HashMap<String, Boolean>();
    
    for (String routers_IP: _store.keySet()){
      if (routers_IP.equals(rd.simulatedIPAddress)){
        distance.put(routers_IP,0);
        parents.put(routers_IP, null);
        node_visited.put(routers_IP, false);
      }
      else{
        distance.put(routers_IP,Integer.MAX_VALUE);
        parents.put(routers_IP, null);
        node_visited.put(routers_IP, false);
      }
    }

    String cur_node = null;
    while (true){
      String min_dist_node = get_min_node(distance, node_visited);
      if (min_dist_node == null){
        break;
      }

      node_visited.put(min_dist_node, true);
      cur_node = min_dist_node;

      LSA cur_lsa = _store.get(cur_node);
      for (LinkDescription neighbour: cur_lsa.links){
        String neighbour_node = neighbour.linkID;
        if (node_visited.get(neighbour_node)){
          continue;
        }
        int neighbour_weight = neighbour.tosMetrics;
        int path_to_neighbour = distance.get(cur_node) + neighbour_weight;
        if ( path_to_neighbour < distance.get(neighbour_node)){
          distance.put(neighbour_node,path_to_neighbour);
          parents.put(neighbour_node, cur_node);
        }

      }
    }

    // creating the path array
    List<String> spt = new ArrayList<String>();
    cur_node = destinationIP;
    String parent_node = parents.get(cur_node);
    while(parent_node != null){
      spt.add(0,cur_node);
      cur_node = parent_node;
      parent_node = parents.get(cur_node);
    }
    spt.add(0,cur_node);

    if( parents.get(destinationIP) == null){
      System.out.println("(Given) IP address is not reachable from this router");
      return null;
    }

    return get_path(spt);
  }

  /**
   * helper method to convert path array into string
   */
  private String get_path(List<String> path){
    StringBuffer sb = new StringBuffer("");
    Iterator iterator = path.iterator();
    String cur_node = (String) iterator.next();
    sb.append(cur_node);
    String next_node = null;
    while(iterator.hasNext()){
      next_node = (String) iterator.next();
      LSA cur_lsa = _store.get(cur_node);
      for (LinkDescription a_LD: cur_lsa.links){
        if (a_LD.linkID.equals(next_node)){
          sb.append(" ");
          sb.append("->(");
          sb.append(Integer.toString(a_LD.tosMetrics));
          sb.append(")");
          sb.append(" ");
          sb.append(next_node);
          break;
        }
      }
      cur_node = next_node;
    }

    return sb.toString();
  }

  /**
   * helper method to get neighbour node with minimum distance
   */
  private String get_min_node(HashMap<String, Integer> distance, HashMap<String, Boolean> nodes_visited){
    Integer min_value = Integer.MAX_VALUE;
    String result = null;

    for (String node: distance.keySet()){
      if (nodes_visited.get(node)){
        continue;
      }
      int cur_int_value = distance.get(node);
      if (cur_int_value < min_value){
        min_value = cur_int_value;
        result = node;
      }
    }
    return result;
  }

  //initialize the linkstate database by adding an entry about the router itself
  private LSA initLinkStateDatabase() {
    LSA lsa = new LSA();
    lsa.linkStateID = rd.simulatedIPAddress;
    lsa.lsaSeqNumber = Integer.MIN_VALUE;
    LinkDescription ld = new LinkDescription();
    ld.linkID = rd.simulatedIPAddress;
    ld.portNum = -1;
    ld.tosMetrics = 0;
    lsa.links.add(ld);
    return lsa;
  }


  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (LSA lsa: _store.values()) {
      sb.append(lsa.linkStateID).append("(" + lsa.lsaSeqNumber + ")").append(":\t");
      for (LinkDescription ld : lsa.links) {
        sb.append(ld.linkID).append(",").append(ld.portNum).append(",").
                append(ld.tosMetrics).append("\t");
      }
      sb.append("\n");
    }
    return sb.toString();
  }

}
