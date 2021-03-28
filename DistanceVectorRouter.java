/***************
 * DistanceVectorRouter
 * Author: Christian Duncan
 * Modified by: 
 * Represents a router that uses a Distance Vector Routing algorithm.
 * buildTable function: (screenshot)
 ***************/
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

public class DistanceVectorRouter extends Router {
    // A generator for the given DistanceVectorRouter class
    public static class Generator extends Router.Generator {
        public Router createRouter(int id, NetworkInterface nic) {
            return new DistanceVectorRouter(id, nic);
        }
    }
    

    Debug debug;
    
    public DistanceVectorRouter(int nsap, NetworkInterface nic) {
        super(nsap, nic);
        debug = Debug.getInstance();  // For debugging!
    }
    //private class to create our own little tuple we need for our hashmap value
    

    public void run() {
        int delay=5000;//5s delay
        long currentTime= System.currentTimeMillis();
        long timeToRebuild = currentTime + delay;
        while (true) {
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
                debug.println(3, "(DistanceVectorRouter.run): I am being asked to transmit: " + toSend.data + " to the destination: " + toSend.destination);
            }

            if (!process) {
                // Didn't do anything, so sleep a bit
                try { Thread.sleep(1); } catch (InterruptedException e) { }
            }
        }
    }
    
    public void buildTable(int nsap){//call this method in its own delay timer (thanks, Professor!)
        ArrayList<Integer>linkIndex=nic.getIncomingLinks();//this gives us an arraylist of all incoming links
        //$$ the above initialization does not help us with the distance from whichever router's map/table we are trying to build, what is next step here? or do we initialize differently?
        HashMap<Integer,DVPair> tempMap=new HashMap<>();//initialize temp map and add itself to it (distance=0, index=-1)
        DVPair itself= new DVPair(0.0,-1);
        tempMap.put(nsap,itself);
        //now, look for the map of every direct link to this router (how to do that as well?) skip this for now

        //$$ now we have to transmit our temporary hashmap to all of this router's neighbors. how to start this?
        DVPacket mapPacket=new DVPacket(tempMap);//special packet which just contains the map needed to be sent to direct neighbor(s) of this router
        //$$ My main concern is how to figure out what the neighbors of the given node are, and how to add to the current node's hashmap other than putting itself in there.
        
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
        //$$ the hashmap is the only thing that the neighbors need receive from the router, correct?
        public DVPacket(HashMap<Integer,DVPair> mapToSend) {
            this.mapToSend=mapToSend;
        }
    }
    
}
