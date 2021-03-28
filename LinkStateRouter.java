/***************
 * LinkStateRouter
 * Author: Christian Duncan
 * Modified by: 
 * Represents a router that uses a Distance Vector Routing algorithm.
 ***************/


import java.sql.Time;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

// testing123
public class LinkStateRouter extends Router {
    Map<Integer, Long> RouterTable;
    Map<Integer, Long> DijTab;//dikja alg
    Time StartTime;
    long StrTime = 0;
    long DijTime = 0;
    int DijIncome = 0;
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
//        System.out.printf("this is current node %d \n", nic.getNSAP());
//        System.out.println("The out going links are "+ nic.getOutgoingLinks().toString());
        for( int i : nic.getOutgoingLinks()){
            //send ping to get ping length to use as the key
            RouterTable.put(i, (long) -1);//puts in all outgoing routes
            DijTab.put(i,(long)-1);
        }


        StartTime = new Time(System.currentTimeMillis());
        StrTime = StartTime.getTime();

        PrintTableStats();

    }
    public synchronized void PrintTableStats(){
        for(int i : nic.getOutgoingLinks()){
            System.out.println("Table "+nic.getNSAP()+" Getting results for "+ i+" "+ RouterTable.get(i));
        }
    }

    public static class DijPack {
        // This is how we will store our Packet Header information
        int source;
        int dest;
        long cost;  //data for time




        public DijPack(int source, int dest, long cost ) {
            this.source = source;
            this.dest = dest;
            this.cost = cost;
            //change
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

    public void run() {
        while (true) {

            // See if there is anything to process
            boolean process = false;
            NetworkInterface.TransmitPair toSend = nic.getTransmit();
            if (toSend != null) {
                if (toSend.data instanceof LinkStateRouter.PingPacket) {
                    LinkStateRouter.PingPacket p = (LinkStateRouter.PingPacket) toSend.data;
                    System.out.println(p.sendBack);
                }

                // There is something to send out
                process = true;
                debug.println(3, "(LinkStateRouter.run): I am being asked to transmit: " + toSend.data + " to the destination: " + toSend.destination);
            }


            NetworkInterface.ReceivePair toRoute = nic.getReceived();
            if (toRoute != null) {
              //  System.out.println("not null");
                // There is something to route through - or it might have arrived at destination
                process = true;
                if (toRoute.data instanceof FloodRouter.Packet) {
                    System.out.println("this is a FloodRouter");
                    FloodRouter.Packet p = (FloodRouter.Packet) toRoute.data;
                    if (p.dest == nsap) {
                        // It made it!  Inform the "network" for statistics tracking purposes

                        debug.println(4, "(FloodRouter.run): Packet has arrived!  Reporting to the NIC - for accounting purposes!");
                        debug.println(6, "(FloodRouter.run): Payload: " + p.payload);
                        nic.trackArrivals(p.payload);
                    } else if (p.hopCount > 0) {
                        // Still more routing to do
                        p.hopCount--;
                       System.err.println("not the destination");
                    } else {
                        debug.println(5, "Packet has too many hops.  Dropping packet from " + p.source + " to " + p.dest + " by router " + nsap);
                    }
                }
                else if (toRoute.data instanceof LinkStateRouter.PingPacket) {
                    //System.out.println("Reaches 100");
                    LinkStateRouter.PingPacket p = (LinkStateRouter.PingPacket) toRoute.data;
                    if (p.dest == nsap) {
                        if (p.sendBack== false) {
                            p.sendBack =true;
                            int src = p.source;
                            p.source = p.dest ;
                            p.dest = src;
                            PingTest(src,p);

                        }
                        else{
                            long len =   System.currentTimeMillis() - p.time;
                            long onelen = len/2;
                            RouterTable.put(p.source, onelen);

                        }
                    }
                    else{
                        System.out.println("the to route data is ");
                        System.out.println(toRoute.data);
                    }
                }
                    else if(toRoute.data instanceof DijPack){
                    LinkStateRouter.DijPack p = (LinkStateRouter.DijPack) toRoute.data;
                    //to send off either
                    if(DijTab.get(nic.getNSAP())== -1){
                        //send off packet to offspring
                        DijTime = System.currentTimeMillis();
                        DijTab.put(p.source,p.cost);
                    }
                    else{

                    }
                    if(DijIncome == nic.getIncomingLinks().size()|| System.currentTimeMillis()-DijTime >400){
                        //all incoming links have arrived proceed to send

                    }
                }
                {
                    debug.println(0, "Error.  The packet being tranmitted is not a recognized Flood Packet.  Not processing");
                }
                //debug.println(3, "(LinkStateRouter.run): I am being asked to transmit: " + toSend.data + " to the destination: " + toSend.destination);
            }
            else{

            }

            if (!process) {
                // Didn't do anything, so sleep a bit
                try { Thread.sleep(1); } catch (InterruptedException e) { }
            }

            if(3000 < System.currentTimeMillis()- StrTime){//every three seconds ping is sent out
                //refresh the ping values

                StrTime = System.currentTimeMillis();
                for(int i: nic.getOutgoingLinks()){
                    PingTest(i,new PingPacket(nic.getNSAP(),i,System.currentTimeMillis(),false));
                }
                PrintTableStats();
            }
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
  public void runDik(){
        //send off the values to the children starting with the source
    for( int i : nic.getOutgoingLinks()){

    }
  }
}
