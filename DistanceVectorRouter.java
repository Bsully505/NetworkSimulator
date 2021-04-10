/***************
 * DistanceVectorRouter
 * Author: Christian Duncan
 * Modified by: Jack Zemlanicky, Dylan Irwin
 * Represents a router that uses a Distance Vector Routing algorithm.
 ***************/
import java.util.ArrayList;
import java.util.HashMap;

public class DistanceVectorRouter extends Router {
    // A generator for the given DistanceVectorRouter class
    public static class Generator extends Router.Generator {
        public Router createRouter(int id, NetworkInterface nic) {
            return new DistanceVectorRouter(id, nic);
        }
    }


    Debug debug;
    //hashmap for this nsap, contains key=nsap and value=distance to a nsap, link to take to get there
    HashMap<Integer,DVPair> routingMap;
    //arraylist containing each neighbor's routingmap
    ArrayList<HashMap<Integer,DVPair>>neighborMaps;
    //array to keep track of the distances from this router to every other router in the network
    long[] distanceOnLink;
    public DistanceVectorRouter(int nsap, NetworkInterface nic) {
        super(nsap, nic);
        //For debugging
        debug = Debug.getInstance();  
        routingMap= new HashMap<>();
        neighborMaps= new ArrayList<>(nic.getOutgoingLinks().size());
        distanceOnLink=new long[nic.getOutgoingLinks().size()];
        for(int i=0;i<nic.getOutgoingLinks().size();i++){
            debug.println(3, "Number of outgoing links for NSAP "+nsap+ ": " +nic.getOutgoingLinks().size());
            //initially set null value to each outgoing link's map
            neighborMaps.add(null);
            //initially set all distances on this link to a large number (since they are all unknown at first)
            distanceOnLink[i]=(long)Integer.MAX_VALUE;

        }
    }

    public void run() {
        //delay in ms
        int delay=5000;
        long currentTime= System.currentTimeMillis();
        long timeToRebuild = currentTime + delay;
        while (true) {
            currentTime=System.currentTimeMillis();
            //if the current time is past the time to rebuild, then rebuild!
            if(currentTime > timeToRebuild){
                timeToRebuild = currentTime + delay;
                buildTable();
            }
            // See if there is anything to process
            boolean process = false;
            NetworkInterface.TransmitPair toSend = nic.getTransmit();
            if (toSend != null) {
                //There is something to send out
                process = true;
                debug.println(3, "(DistanceVectorRouter.run): I am being asked to transmit: " + toSend.data + " to the destination: " + toSend.destination);
                //check if what we are sending is a ping packet
                if (toSend.data instanceof DistanceVectorRouter.PingPacket) {
                    DistanceVectorRouter.PingPacket p = (DistanceVectorRouter.PingPacket) toSend.data;
                    debug.println(4, "WARNING: we do not send ping packets");
                }
                //otherwise we make new packets to send!
                else{
                    route(new Packet(nsap, toSend.destination, toSend.data,10));
                    debug.println(5,"New packet created!");
                }
            }

            NetworkInterface.ReceivePair toRoute = nic.getReceived();
            if (toRoute != null) {
                //There is something to route through - or it might have arrived at destination
                process = true;
                //check if we are receiving a DVPacket (with table information)
                if(toRoute.data instanceof DVPacket){
                    int linkIndex= getLinkIndex(toRoute.originator);
                    debug.println(4,"(DistanceVectorRouter.run): NSAP " +nsap +" just a hashmap to neighbor: " +toRoute.originator +" on link: "+linkIndex);
                    DVPacket packet=(DVPacket) toRoute.data;
                    //this adds the DVPacket's routing map to the neighborMaps arraylist
                    neighborMaps.set(linkIndex,packet.mapToSend);
                }
                //check if we are receiving a ping packet
                else if (toRoute.data instanceof DistanceVectorRouter.PingPacket) {
                    DistanceVectorRouter.PingPacket p = (DistanceVectorRouter.PingPacket) toRoute.data;
                    //if sendback is false, then we send the ping packet back from whence it came
                    if (p.sendBack == false) {
                        p.sendBack=true;
                        PingTest(toRoute.originator,p);
                        debug.println(4,"(DistanceVectorRouter.run): NSAP "+toRoute.originator+ " sent NSAP " +nsap+ " a ping packet. Sending it back now.");
                    }
                    else {
                        // if sendback is true, calculate distance using current time and the timestamp on the ping packet (the time at which it was originally sent)
                        long startTime = p.time;
                        long endTime = System.currentTimeMillis();
                        long dist = (endTime - startTime)/2;
                        //the nsap of the router which sent the ping packet to us(in this case, the router that this nsap sent it to originally)
                        int pingedNSAP = toRoute.originator; 
                        //the link index of the nsap we pinged 
                        int linkIndex=nic.getOutgoingLinks().indexOf(pingedNSAP);
                        //update the distance from this router to the pinged router
                        distanceOnLink[linkIndex]=dist;
                        debug.println(4, "(DistanceVectorRouter.run): NSAP "+nsap+" received its ping packet back from "+pingedNSAP);
                    }
                }
                //check if we are receiving a normal packet!
                else if (toRoute.data instanceof DistanceVectorRouter.Packet) {
                    debug.println(3, "(DistanceVectorRouter.run): I am being asked to transmit: " + toRoute.originator + " data = " + toRoute.data);
                    Packet p = (Packet) toRoute.data;
                    //route it (or not, depending on its destination)
                    route(p);
                }
            }
            else {
              debug.println(4, "Error.  The packet being tranmitted is not a recognized DistanceVector Packet.  Not processing");
            }

            if (!process) {
                //Didn't do anything, so sleep a bit
                try { Thread.sleep(1); } catch (InterruptedException e) { }
            }
        }
    }
    //method that builds each nsap's routing map every so often
    public void buildTable(){
        //this gives us an arraylist of all outgoing links (their index and NSAP)
        ArrayList<Integer>linkIndex=nic.getOutgoingLinks();
        //send a ping packet to all outgoing links so we can get some distances calculated
        for(int i: nic.getOutgoingLinks()){
            PingTest(i,new PingPacket(System.currentTimeMillis(),false));
        }
        //initialize temp map and add itself to it (distance=0, index=-1)
        HashMap<Integer,DVPair> tempMap= new HashMap<>();
        DVPair itself= new DVPair(0,-1);
        tempMap.put(nsap,itself);
        for(int i=0; i<nic.getOutgoingLinks().size();i++){
              //grab the specific hashmap for link i
              HashMap<Integer,DVPair> neighborMap= neighborMaps.get(i);
              //check to see if this specific map is empty
              if(neighborMap!=null){
                for (Integer dest: neighborMap.keySet()){
                    //get the dvpair from nsap 'dest' in this nsap's neighborMap
                    DVPair dvp=neighborMap.get(dest);
                    //get the current best dvpair from nsap 'dest' in this nsap's routingMap
                    DVPair bestdvp=tempMap.get(dest);
                    //calc new distance from link i to this nsap
                    long distance=distanceOnLink[i]+dvp.distance;
                    //check if its better than current distance (or there is no distance)
                    if(bestdvp==null||distance<bestdvp.distance){
                        //add this nsap's map to the tempMap (and soon to the routingMap)
                        DVPair newdvp=new DVPair(distance,i);
                        tempMap.put(dest,newdvp);
                    }
                }
              }
        }
        //special packet which just contains the map needed to be sent to direct neighbor(s) of this router
        DVPacket mapPacket=new DVPacket(tempMap);
        for(int i=0;i<linkIndex.size();i++){
            //for each index in LinkIndex, send the DVPacket to the NSAP with that index
            nic.sendOnLink(i, mapPacket);
        }
        //finally, set this nsap's routingMap as the tempMap we just modified
        routingMap=tempMap;
        if(nsap==-1){
            synchronized (debug){debug.println(3,"Routing map for nsap 15:");
            routingMap.forEach((nsap,dvp)->{debug.println(3,"["+nsap+" "+dvp.distance+" "+dvp.linkIndex+"]" );});
            }
        }       
    }
    private int getLinkIndex(int nsap){
        ArrayList<Integer> outLinks= nic.getOutgoingLinks();

        return outLinks.indexOf(nsap);
    }
    //class to make our little tuple for the hashmap (thanks, Professor!)
    public class DVPair {
        long distance;
        int linkIndex;
        public DVPair(long distance, int linkIndex) {
                 this.distance = distance;
                 this.linkIndex = linkIndex;
        }
    }
    //custom packet class that simply holds the hashmap that will be sent to a router's direct neighbor(s)
    public static class DVPacket {
        HashMap<Integer,DVPair> mapToSend;
        //the hashmap is the only thing that the neighbors need receive from the router
        public DVPacket(HashMap<Integer,DVPair> mapToSend) {
            this.mapToSend=mapToSend;
        }
    }
    public static class Packet {
        //this is how we will store our Packet Header information
        int source;
        int dest;
        int hopCount;
        Object payload;

        public Packet(int source, int dest, Object payload, int hopCount) {
            this.source = source;
            this.dest = dest;
            this.payload = payload;
            this.hopCount=hopCount;
        }
    }
    public static class PingPacket {
        //this is how we will store our Packet Header information
        long time;
        boolean sendBack;

        public PingPacket(long time, boolean sendBack) {
            this.time = time;
            this.sendBack = sendBack;
        }
    }
    //route method to determine if a packet needs to be routed or not (because it either reached its destination or ran out of hops!)
    private void route(Packet p) {
        //if we reached our destination
        if(p.dest==nsap){
            nic.trackArrivals(p.payload);
        }
        //otherwise we have to route some more (unless the hop count is out)
        else if(p.hopCount>0){
        p.hopCount--;
        DVPair pair=routingMap.get(p.dest);
            if(pair!=null){
                debug.println(3, "Routing a packet from "+nsap+ " to " +p.dest+ " on link " +pair.linkIndex);
                nic.sendOnLink(pair.linkIndex,p);
             }
             else{debug.println(3,"the source " +nsap+ " does not know how to route to " +p.dest);}
        }   
    }
    //ping test method from Bryan and Henok's Link State Router
    public void PingTest(int dest, PingPacket PP) {
         ArrayList<Integer> outGo = nic.getOutgoingLinks();
         int size = outGo.size();
         for (int i = 0; i < size; i++) {
            if (outGo.get(i) == dest) {
                nic.sendOnLink(i, PP);
            }
        }
    }
}
