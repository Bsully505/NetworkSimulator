/***************
 * DistanceVectorRouter
 * Author: Christian Duncan
 * Modified by: 
 * Represents a router that uses a Distance Vector Routing algorithm.
 * buildTable function: (screenshot)
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
    HashMap<Integer,DVPair> routingMap;
    ArrayList<HashMap<Integer,DVPair>>neighborMaps;
    
    public DistanceVectorRouter(int nsap, NetworkInterface nic) {
        super(nsap, nic);
        debug = Debug.getInstance();  // For debugging
        routingMap= new HashMap<>();  //
        neighborMaps= new ArrayList<>(nic.getOutgoingLinks().size());
        for(int i=0;i<nic.getOutgoingLinks().size();i++){
            neighborMaps.add(null);
        }

    }
    //private class to create our own little tuple we need for our hashmap value
    

    public void run() {
        int delay=5000;//5s delay
        long currentTime= System.currentTimeMillis();
        long timeToRebuild = currentTime + delay;
        while (true) {
            currentTime=System.currentTimeMillis();
            if(currentTime > timeToRebuild){
                timeToRebuild = currentTime + delay;
                buildTable(nsap);
            } 
            // See if there is anything to process
            boolean process = false;
            NetworkInterface.TransmitPair toSend = nic.getTransmit();
            if (toSend != null) {
                // There is something to send out
                process = true;
                debug.println(3, "(DistanceVectorRouter.run): I am being asked to transmit: " + toSend.data + " to the destination: " + toSend.destination);
            }

            NetworkInterface.ReceivePair toRoute = nic.getReceived();
            if (toRoute != null) {
                // There is something to route through - or it might have arrived at destination
                process = true;
                if(toRoute.data instanceof DVPacket){
                    int linkIndex= getLinkIndex(toRoute.originator);
                    debug.println(3, nsap +":just a hashmap to neighbor: " +toRoute.originator +" "+linkIndex);
                    DVPacket packet=(DVPacket) toRoute.data;
                    neighborMaps.set(linkIndex,packet.mapToSend);
                }
                else{debug.println(3, "(DistanceVectorRouter.run): I am being asked to transmit: " + toRoute.originator + " data = " + toRoute.data);}
            }

            if (!process) {
                // Didn't do anything, so sleep a bit
                try { Thread.sleep(1); } catch (InterruptedException e) { }
            }
        }
    }
    
    public void buildTable(int nsap){//call this method in its own delay timer (thanks, Professor!)
        ArrayList<Integer>linkIndex=nic.getOutgoingLinks();//this gives us an arraylist of all outgoing links (their index and NSAP)
        //Use ping method Bryan and Henok made (on each outgoing link's NSAP) to get distance from this router
        HashMap<Integer,DVPair> tempMap=new HashMap<>();//initialize temp map and add itself to it (distance=0, index=-1)
        DVPair itself= new DVPair(0.0,-1);
        tempMap.put(nsap,itself);
        //now, look for the map of every direct link to this router (how to do that as well?) skip this for now
        if(nsap==14){
            debug.println(3, "we got here");
            for(int i=0; i<neighborMaps.size();i++){
                HashMap<Integer, DVPair> map= neighborMaps.get(i);
                debug.println(3, "linkIndex: " +i);
                if(map!=null)
                    map.forEach((n,dvp) -> debug.println(3,"   "+n+ " "+dvp.distance));
            }
        }
        else{
            debug.println(3, "we did not get here");
        }
        //now we transmit our temporary hashmap to all of this router's neighbors
        //right now, this loop just sends to the router's neighbors a hashmap with its own distance and link
        DVPacket mapPacket=new DVPacket(tempMap);//special packet which just contains the map needed to be sent to direct neighbor(s) of this router
        for(int i=0;i<linkIndex.size();i++){
            //for each index in LinkIndex, send the DVPacket to the NSAP with that index
            nic.sendOnLink(i, mapPacket);
        }
        //$$ My main concern is how to add to the current node's hashmap other than putting itself in there.
        
        
    }
    private int getLinkIndex(int nsap){
        ArrayList<Integer> outLinks= nic.getOutgoingLinks();
        
        return outLinks.indexOf(nsap);
    }
    
    //class to make our little tuple for the hashmap (thanks, Professor!)
    public class DVPair { 
        double distance;
        int linkIndex; 
        public DVPair(double distance, int linkIndex) {
                 this.distance = distance;
                 this.linkIndex = linkIndex;
        }
    }
    //Custom packet class that simply holds the hashmap that will be sent to a router's direct neighbor(s)
    public static class DVPacket {
        HashMap<Integer,DVPair> mapToSend;
        //the hashmap is the only thing that the neighbors need receive from the router
        public DVPacket(HashMap<Integer,DVPair> mapToSend) {
            this.mapToSend=mapToSend;
        }
    }
    
}
