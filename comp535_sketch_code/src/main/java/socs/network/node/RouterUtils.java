package socs.network.node;

import socs.network.message.SOSPFPacket;
import socs.network.util.Configuration;

import socs.network.message.LSA;
import socs.network.message.LinkDescription;

public class RouterUtils {

    /**
     * Helper method to
     * check the validity of (given) remote router's information
     * This method essentially check of null values, same values, already exits values
     * */
    public static boolean check_info(RouterDescription router_rd, String processIP, String sim_IP, Short port_n, Short weight, Link[] ports_p){
        // Check for null
        if (processIP.equals(null)){
            System.out.println("(Given) processIP is null");
            return true;
        }

        if (port_n.equals(null)){
            System.out.println("(Given) port number is null");
            return true;
        }

        if (sim_IP.equals(null)){
            System.out.println("(Given) Simulated_IP address numb is null");
            return true;
        }

        if (weight.equals(null)){
            System.out.println("(Given) weight is null");
            return true;
        }

        //check for same
        if (port_n == router_rd.processPortNumber){
            System.out.println("(Given) port number is same as this router");
            return true;
        }

        if (sim_IP.equals(router_rd.simulatedIPAddress)){
            System.out.println("(Given) SimulatedIP is same as this router");
            return true;
        }

        return false;
    }


    /**
     * Helper method to
     * check if the simulated IP address is present in the ports
     * */
    public static boolean in_port(String sim_IP, Link[] ports_p){
        for (Link a_link : ports_p){
            if (a_link == null){
                continue;
            }
            RouterDescription remote_rd = a_link.router2;
            if (remote_rd.simulatedIPAddress.equals(sim_IP)){
                return true;
            }
        }
        return false;
    }

    /**
     * Helper method to
     * check if the port number address is present in the ports
     * */
    public static boolean in_port(short port_n, Link[] ports_p){
        for (Link a_link : ports_p){
            if (a_link == null){
                continue;
            }
            RouterDescription remote_rd = a_link.router2;
            if (remote_rd.processPortNumber == port_n){
                return true;
            }
        }
        return false;
    }

    /**
     * Helper method to
     * get the index of given simulated IP address in ports
     * */
    public static int get_index(String simIP, Link[] ports){
        for (int index = 0; index <= 3; index++){
            if (ports[index] == null){
                continue;
            }
            Link l = ports[index];
            RouterDescription r = l.router2;
            if ((r.simulatedIPAddress).equals(simIP)){
                return index;
            }
        }
        return -1;
    }

    /**
     * Helper method to
     * get the index of given port number address in ports
     * */
    public static int get_index(short port, Link[] ports){
        for (int index = 0; index <= 3; index++){
            if (ports[index] == null){
                continue;
            }
            Link l = ports[index];
            RouterDescription r = l.router2;
            if (r.processPortNumber == port){
                return index;
            }
        }
        return -1;
    }

    /**
     * Helper method to
     * get the available index in ports
     * */
    public static int get_Available_Port_Index(Link[] ports_p){
        int index;
        for(index=0; index<=3; ++index)
        {
            Link cur_link = ports_p[index];
            if (cur_link == null) {
                break;
            }
        }
        if (index == 4){ return -1;}
        else{ return index;}
    }

    /**
     * Helper method to
     * make Acknowledgement Packet
     * */
    public static SOSPFPacket make_Acknowledgement_packet(RouterDescription src_rd, RouterDescription des_rd, boolean port_space){
        SOSPFPacket new_msg = new SOSPFPacket();

        new_msg.srcProcessIP = src_rd.processIPAddress;
        new_msg.srcProcessPort = src_rd.processPortNumber;
        new_msg.srcIP = src_rd.simulatedIPAddress;
        new_msg.routerID = src_rd.simulatedIPAddress;

        new_msg.dstIP = des_rd.simulatedIPAddress;
        new_msg.neighborID = des_rd.simulatedIPAddress;

        new_msg.sospfType = 2;
        new_msg.weight = 0;
        new_msg.portAvailable = port_space;

        return new_msg;
    }

    /**
     * Helper method to
     * make UpdateLSD Packet
     * */
    public static SOSPFPacket make_UpdateLSD_Packet(RouterDescription src_rd, RouterDescription des_rd, LinkStateDatabase lsd){
        SOSPFPacket new_msg = new SOSPFPacket();

        new_msg.srcProcessIP = src_rd.processIPAddress;
        new_msg.srcProcessPort = src_rd.processPortNumber;
        new_msg.srcIP = src_rd.simulatedIPAddress;
        new_msg.routerID = src_rd.simulatedIPAddress;

        new_msg.dstIP = des_rd.simulatedIPAddress;
        new_msg.neighborID = des_rd.simulatedIPAddress;

        new_msg.sospfType = 1;
        for (String routers: lsd._store.keySet()){
            new_msg.lsaArray.add(lsd._store.get(routers));
        }

        return new_msg;
    }

    /**
     * Helper method to
     * make Hello message Packet
     * */
    public static SOSPFPacket make_Hello_Packet(RouterDescription src_rd, RouterDescription des_rd, short weight){
        SOSPFPacket new_msg = new SOSPFPacket();

        new_msg.srcProcessIP = src_rd.processIPAddress;
        new_msg.srcProcessPort = src_rd.processPortNumber;
        new_msg.srcIP = src_rd.simulatedIPAddress;
        new_msg.routerID = src_rd.simulatedIPAddress;

        new_msg.dstIP = des_rd.simulatedIPAddress;
        new_msg.neighborID = des_rd.simulatedIPAddress;

        new_msg.sospfType = 0;
        new_msg.weight = weight;
        return new_msg;
    }

    /**
     * Helper method to
     * make Connect request Packet
     * */
    public static SOSPFPacket make_Connect_Packet(RouterDescription src_rd, RouterDescription des_rd, short weight){
        SOSPFPacket new_msg = new SOSPFPacket();

        new_msg.srcProcessIP = src_rd.processIPAddress;
        new_msg.srcProcessPort = src_rd.processPortNumber;
        new_msg.srcIP = src_rd.simulatedIPAddress;
        new_msg.routerID = src_rd.simulatedIPAddress;

        new_msg.dstIP = des_rd.simulatedIPAddress;
        new_msg.neighborID = des_rd.simulatedIPAddress;

        new_msg.sospfType = 3;
        new_msg.weight = weight;
        return new_msg;
    }

    /**
     * Helper method to
     * make Disconnect request Packet
     * */
    public static SOSPFPacket make_DisConnect_Packet(RouterDescription src_rd, RouterDescription des_rd, LinkStateDatabase lsd){
        SOSPFPacket new_msg = new SOSPFPacket();

        new_msg.srcProcessIP = src_rd.processIPAddress;
        new_msg.srcProcessPort = src_rd.processPortNumber;
        new_msg.srcIP = src_rd.simulatedIPAddress;
        new_msg.routerID = src_rd.simulatedIPAddress;

        new_msg.dstIP = des_rd.simulatedIPAddress;
        new_msg.neighborID = des_rd.simulatedIPAddress;

        new_msg.sospfType = 4;
        for (String routers: lsd._store.keySet()){
            new_msg.lsaArray.add(lsd._store.get(routers));
        }

        return new_msg;
    }

    /**
     * Helper method to
     * make Quit request Packet
     * */
    public static SOSPFPacket make_Quit_Packet(RouterDescription src_rd, RouterDescription des_rd){
        SOSPFPacket new_msg = new SOSPFPacket();

        new_msg.srcProcessIP = src_rd.processIPAddress;
        new_msg.srcProcessPort = src_rd.processPortNumber;
        new_msg.srcIP = src_rd.simulatedIPAddress;
        new_msg.routerID = src_rd.simulatedIPAddress;

        new_msg.dstIP = des_rd.simulatedIPAddress;
        new_msg.neighborID = des_rd.simulatedIPAddress;

        new_msg.sospfType = 5;
        new_msg.weight = 0;
        return new_msg;
    }

    /**
     * Helper method to
     * make update weight request Packet
     * */
    public static SOSPFPacket make_UpdateWeight_Packet(RouterDescription src_rd, RouterDescription des_rd, LinkStateDatabase lsd, short weight){
        SOSPFPacket new_msg = new SOSPFPacket();

        new_msg.srcProcessIP = src_rd.processIPAddress;
        new_msg.srcProcessPort = src_rd.processPortNumber;
        new_msg.srcIP = src_rd.simulatedIPAddress;
        new_msg.routerID = src_rd.simulatedIPAddress;
        //Destination Information
        new_msg.dstIP = des_rd.simulatedIPAddress;
        new_msg.neighborID = des_rd.simulatedIPAddress;

        new_msg.sospfType = 6;
        for (String routers: lsd._store.keySet()){
            new_msg.lsaArray.add(lsd._store.get(routers));
        }
        new_msg.weight = weight;

        return new_msg;
    }
}
