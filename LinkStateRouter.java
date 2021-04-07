/***************
 * LinkStateRouter
 * Author: Christian Duncan
 * Modified by:
 * Represents a router that uses a Distance Vector Routing algorithm.
 *
 *
 * to fix
 * being able to send a packet without using the pingTest or dijtest
 * line 176 //need to fix this it only decreases the number does not stop sending if lower than 0
 *
 * the problem for flooding the
 ***************/


import java.sql.Time;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// testing123
public class LinkStateRouter extends Router {
    Map<Integer, Long> RouterTable;
    Map<Integer, Long> DijTab;//dikja alg
    Map<Integer,List<Object>> WholeTable;//the list<object> consists of the sequence number in list index 0 and the router Table inedex 1
    List<Integer> Nodes = new ArrayList<Integer>();
    Time StartTime;
    long StrTime = 0;
    long timeDelay = 2000;
    long sequenceNum = 1;



    // A generator for the given LinkStateRouter class
    public static class Generator extends Router.Generator {
        public Router createRouter(int id, NetworkInterface nic) {
            return new LinkStateRouter(id, nic);
        }
    }
    Debug debug;

    public LinkStateRouter(int nsap, NetworkInterface nic) {
        super(nsap, nic);
        debug = Debug.getInstance();  // For debugging!
        RouterTable  = new HashMap<Integer,Long>();
        for( int i : nic.getOutgoingLinks()){
            //send ping to get ping length to use as the key
            RouterTable.put(i, (long) -1);//puts in all outgoing routes

        }
        WholeTable = new HashMap<Integer, List<Object>>();
        //flood the network with

        StartTime = new Time(System.currentTimeMillis());
        StrTime = StartTime.getTime();

        PrintTableStats();

    }
    public synchronized void PrintTableStats(){
        for(int i : nic.getOutgoingLinks()){
            System.out.println("Table "+nic.getNSAP()+" Getting results for "+ i+" "+ RouterTable.get(i));
        }
    }
    public synchronized void PrintWholeTable(){
        System.out.println(" Start of Key Set");
        for(int i : WholeTable.keySet()){
            System.out.println(i);
        }
        System.out.println(" END of KEY SET");
    }

    public static class DijPack {
        // This is how we will store our Packet Header information
        int sequence;
        int source;
        int hopcount;
        Map<Integer,Long> routerTableDJ;
        int from;


        public DijPack(int sequence, int source, int hopcount,  Map<Integer,Long> routerTableDJ, int from) {
            this.sequence = sequence;
            this.source = source;
            this.hopcount = hopcount;
            this.routerTableDJ =  routerTableDJ;
            this.from = from;


        }
    }

    public static class PingPacket {
        // This is how we will store our Packet Header information
        int source;
        int dest;
        long time;  //data for time
        boolean sendBack;



        public PingPacket(int source, int dest, long time ,boolean sendBack) {
            this.source = source;
            this.dest = dest;
            this.time = time;
            this.sendBack = sendBack;
            //change
        }
    }
    public void PrintNodes(){
        for( int i : Nodes){
            System.out.print(i +" ");
        }
        System.out.println();
    }

    public void run() {
        while (true) {

            // See if there is anything to process
            boolean process = false;
            NetworkInterface.TransmitPair toSend = nic.getTransmit();
            if (toSend != null) {

                // There is something to send out
                process = true;
                debug.println(3, "(LinkStateRouter.run): I am being asked to transmit: " + toSend.data + " to the destination: " + toSend.destination);
            }


            NetworkInterface.ReceivePair toRoute = nic.getReceived();
            if (toRoute != null) {
                // There is something to route through - or it might have arrived at destination
                process = true;
                if (toRoute.data instanceof LinkStateRouter.PingPacket) {
                    LinkStateRouter.PingPacket p = (LinkStateRouter.PingPacket) toRoute.data;
                    if (p.dest == nsap) {
                        if (p.sendBack == false) {
                            p.sendBack = true;
                            int src = p.source;
                            p.source = p.dest;
                            p.dest = src;
                            PingTest(src, p);

                        } else {
                            long len = System.currentTimeMillis() - p.time;
                            long onelen = len / 2;
                            RouterTable.put(p.source, onelen);

                        }
                    } else {
                        System.out.println("the to route data is ");
                        System.out.println(toRoute.data);
                    }
                } else if (toRoute.data instanceof DijPack) {

                    LinkStateRouter.DijPack p = (LinkStateRouter.DijPack) toRoute.data;
                    if (WholeTable.containsKey(p.source)) {//this means that it contains the val
                        int seq = Integer.parseInt(String.valueOf((WholeTable.get(p.source).get(0))));
                        if (seq != p.sequence) {//now add it and send it off to outs
                            List<Object> temp = new ArrayList<Object>();
                            temp.add(0, p.sequence);
                            temp.add(1, p.routerTableDJ);
                            WholeTable.put(p.source, temp);
                            //now send it off to the offspring
                            int og = p.from;
                            p.from = nic.getNSAP();
                            if (p.hopcount > 0) {//need to fix this it only decreases the number does not stop sending if lower than 0
                                p.hopcount--;
                                for (int i : nic.getOutgoingLinks()) {
                                    if (i != og) {
                                        DijTest(i, p, og);
                                    }
                                }
                            }
                        }

                    } else {
                        List<Object> temp = new ArrayList<Object>();
                        temp.add(0, 1);
                        temp.add(1, p.routerTableDJ);
                        WholeTable.put(p.source, temp);
                        Nodes.add(p.source);
                        int og = p.from;
                        p.from = nic.getNSAP();
                        for (int i : nic.getOutgoingLinks()) {
                            if (i != og) {
                                DijTest(i, p,og);
                            }
                        }
                    }

                    //debug.println(3, "(LinkStateRouter.run): I am being asked to transmit: " + toSend.data + " to the destination: " + toSend.destination);
                }
            }
            if (!process) {
                // Didn't do anything, so sleep a bit
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                }
            }
            //called time delay
            if (timeDelay < System.currentTimeMillis() - StrTime) {//every three seconds ping is sent out
                //refresh the ping values

                StrTime = System.currentTimeMillis();

                List<Object> temp = new ArrayList<Object>();
                temp.add(0, 1);
                temp.add(1, this.RouterTable);
                WholeTable.put(this.nsap, temp);
                if (nic.getNSAP() == 24) {
                    PrintNodes();
                    //PrintWholeTable();


                }

            }
            for (int i : nic.getOutgoingLinks()) {
                PingTest(i, new PingPacket(nic.getNSAP(), i, System.currentTimeMillis(), false));
                DijPack temp = new DijPack((int) sequenceNum, nic.getNSAP(), 5, RouterTable, nic.getNSAP());
                DijTest(i, temp, nsap);
                //nic.sendOnLink(i,temp );
            }

            sequenceNum++;
            if (sequenceNum > 1500){
                sequenceNum = 1;
            }

        //else{

        //}
        //PrintTableStats();

        }
    }

    public  void PingTest(int dest, PingPacket PP){
        ArrayList<Integer> outGo = nic.getOutgoingLinks();
        int size = outGo.size();
        for (int i = 0; i < size; i++) {
            if (outGo.get(i) == dest) {
                // Not the originator of this packet - so send it along!
                nic.sendOnLink(i, PP);
            }
        }
    }
    public  void DijTest(int dest, DijPack DP, int og){
        ArrayList<Integer> outGo = nic.getOutgoingLinks();
        int size = outGo.size();
        for (int i = 0; i < size; i++) {
            if (outGo.get(i) != og) {
                // Not the originator of this packet - so send it along!
                nic.sendOnLink(i, DP);
            }
        }
    }

}
