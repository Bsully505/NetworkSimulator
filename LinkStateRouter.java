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
    Map<String, Integer> RouterTable;
    Time StartTime;
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
        RouterTable  = new HashMap<String,Integer>();
//        System.out.printf("this is current node %d \n", nic.getNSAP());
//        System.out.println("The out going links are "+ nic.getOutgoingLinks().toString());
        for( int i : nic.getOutgoingLinks()){
            //send ping to get ping length to use as the key
            RouterTable.put(String.valueOf(i),-1);//puts in all outgoing routes
        }

        PingTest(121,new PingPacket(189,121,20000,System.currentTimeMillis()));
        StartTime = new Time(System.currentTimeMillis());

        PrintTableStats();

    }
    public void PrintTableStats(){
        for(int i : nic.getOutgoingLinks()){
            System.out.print("Table "+nic.getNSAP()+" Getting results for "+ i+" ");
            System.out.println(RouterTable.get(String.valueOf(i)));

        }
    }

    public static class PingPacket {
        // This is how we will store our Packet Header information
        int source;
        int dest;
        int hopCount;  // Maximum hops to get there
        Object payload;// The payload!


        public PingPacket(int source, int dest, int hopCount, Object payload) {
            this.source = source;
            this.dest = dest;
            this.hopCount = hopCount;
            this.payload = payload;
        }
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
                System.out.println("not null");
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
                    System.out.println("Reaches 100");
                    LinkStateRouter.PingPacket p = (LinkStateRouter.PingPacket) toRoute.data;
                    if (p.dest == nsap) {
                        nic.sendOnLink(toRoute.originator, toRoute);
                    }
                    else{
                        System.out.println("the to route data is ");
                        System.out.println(toRoute.data);
                    }
                }
                    else
                {
                    debug.println(0, "Error.  The packet being tranmitted is not a recognized Flood Packet.  Not processing");
                }
                debug.println(3, "(LinkStateRouter.run): I am being asked to transmit: " + toSend.data + " to the destination: " + toSend.destination);
            }
            else{
                //System.out.println("is null");
            }

            if (!process) {
                // Didn't do anything, so sleep a bit
                try { Thread.sleep(1); } catch (InterruptedException e) { }
            }
            if(300000 > StartTime.getTime()-System.currentTimeMillis()){
                //refresh the ping values
                StartTime.setTime(System.currentTimeMillis());
            }
        }
    }
   public  void PingTest(int dest, PingPacket PP){
        System.out.println("reaches here");
        //LinkStateRouter.PingPacket p = (LinkStateRouter.PingPacket) toRoute.data;
        ArrayList<Integer> outGo = nic.getOutgoingLinks();
       int size = outGo.size();
       for (int i = 0; i < size; i++) {
           if (outGo.get(i) == dest) {
               // Not the originator of this packet - so send it along!
               System.out.println("something reached here");

               nic.sendOnLink(i, PP);
           }
           //long PingTime = System.currentTimeMillis();

           //nic.sendOnLink(dest,new PingPacket(source,dest,20,System.currentTimeMillis()) );

       }
  }
}
