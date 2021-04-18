/***************
 * LinkStateRouter
 * Author: Christian Duncan
 * Modified by: Bryan Sullivan and Henok Ketsela
 * Represents a router that uses a Link State Routing algorithm.
 ***************/


import java.sql.Time;
import java.util.*;

public class LinkStateRouter extends Router {
    //initlaizing variables
    Map<Integer,Integer> ratTab;
    Map<Integer, Long> RouterTable;
    Map<Integer, Long> DijTab;//dikja alg
    Map<Integer,List<Object>> WholeTable;//the list<object> consists of the sequence number in list index 0 and the router Table inedex 1
    List<Integer> Nodes = new ArrayList<>();


    Time StartTime;
    long StrTime;
    long timeDelay = 900;
    int sequenceNum = 1;
    int count;
    public boolean firstRound;


    // A generator for the given LinkStateRouter class
    public static class Generator extends Router.Generator {
        public Router createRouter(int id, NetworkInterface nic) {
            return new LinkStateRouter(id, nic);
        }
    }
    Debug debug;

    public LinkStateRouter(int nsap, NetworkInterface nic) {
        super(nsap, nic);
        ratTab = new HashMap<>();
        debug = Debug.getInstance();  // For debugging!
        RouterTable  = new HashMap<>();
        firstRound = true;

        //workingLink.put(nsap,-1);
        //workingDistance.put(nsap, 0);
        for( int i : nic.getOutgoingLinks()){
            //send ping to get ping length to use as the key
            RouterTable.put(i, (long) -1);//puts in all outgoing routes
        }
        WholeTable = new HashMap<>();
        StartTime = new Time(System.currentTimeMillis());
        StrTime = StartTime.getTime();
        //PrintTableStats();
    }
    public static class Packet {
        // This is how we will store our Packet Header information
        int source;
        int dest;
        int hopCount;  // Maximum hops to get there
        Object payload;  // The payload!

        public Packet(int source, int dest, int hopCount, Object payload) {
            this.source = source;
            this.dest = dest;
            this.hopCount = hopCount;
            this.payload = payload;
        }
    }
    // each router sends info of it's neighbors
    public class DijPack {
        // This is how we will store our Packet Header information
        int sequence;
        int source;
        int hopcount;
        Map<Integer,Long> routerTableDJ;


        public DijPack(DijPack did){
            this.sequence = did.sequence;
            this.source = did.source;
            this.hopcount = did.hopcount;
            this.routerTableDJ = did.routerTableDJ;

        }
        public DijPack(int sequence, int source, int hopcount,  Map<Integer,Long> routerTableDJ) {
            this.sequence = sequence;
            this.source = source;
            this.hopcount = hopcount;
            this.routerTableDJ =  routerTableDJ;



        }
        public synchronized Object clone() {
            return new DijPack(this);
        }
    }

    // class for to get ping time between routers
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
            if (toSend != null && ratTab.size() == 18) {//will only send when the routing table is finished

                LinkStateRouter.Packet P = new LinkStateRouter.Packet(nic.getNSAP(), toSend.destination, 17, toSend.data);
                int to = ratTab.get(P.dest);
                if(to == -1){
                    nic.sendOnLink(nic.getOutgoingLinks().indexOf(P.dest),P);
                }
                else{
                    nic.sendOnLink(nic.getOutgoingLinks().indexOf(to),P);
                }


                // There is something to send out
                process = true;
                debug.println(3, "(LinkStateRouter.run): I am being asked to transmit: " + toSend.data + " to the destination: " + toSend.destination);
            }


            NetworkInterface.ReceivePair toRoute = nic.getReceived();
            if (toRoute != null) {
                // There is something to route through - or it might have arrived at destination
                process = true;
                if( toRoute.data instanceof LinkStateRouter.Packet){


                    LinkStateRouter.Packet p = (LinkStateRouter.Packet) toRoute.data;
                    if(p.dest == nsap){
                        nic.trackArrivals(p.payload);
                    }
                    int to = ratTab.get(p.dest);
                    if(to == -1){
                        nic.sendOnLink(nic.getOutgoingLinks().indexOf(p.dest),p);
                    }
                    else{
                        nic.sendOnLink(nic.getOutgoingLinks().indexOf(to),p);
                    }
                }
                else if (toRoute.data instanceof LinkStateRouter.PingPacket) {
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
                    if (!WholeTable.containsKey(p.source)|| (Integer)WholeTable.get(p.source).get(0)< p.sequence) {//this means that it contains the val
                        //parseing and checking if the sequence number is the same as what is already stored
                        List<Object> temp = new ArrayList<Object>();
                        temp.add(0, p.sequence);
                        temp.add(1, p.routerTableDJ);
                        WholeTable.put(p.source, temp);
                        //now send it off to the outgoing links
                        if (!Nodes.contains(p.source)) {
                            Nodes.add(p.source);
                            if (nsap == 24) {
                                // debug.println(3, "Node 24: Adding " + p.source);
                            }
                        }
                        if (p.hopcount > 0) {//need to fix this it only decreases the number does not stop sending if lower than 0
                            p.hopcount = p.hopcount -1;
                            DijTest(p);
                        }
                    }
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
            if (timeDelay < System.currentTimeMillis() - StrTime||firstRound == true) {//every three seconds ping is sent out
                firstRound = false;
                //refresh the ping values
                count++;
                StrTime = System.currentTimeMillis();

                List<Object> temp = new ArrayList<Object>();
                temp.add(0, sequenceNum);
                temp.add(1, this.RouterTable);
                WholeTable.put(nic.getNSAP(), temp);
                if(!Nodes.contains(nic.getNSAP())){
                    Nodes.add(nic.getNSAP());
                }
                DijTest(new DijPack((int) sequenceNum++, nic.getNSAP(), 20, RouterTable));
                for (int i : nic.getOutgoingLinks()) {
                    PingTest(i, new PingPacket(nic.getNSAP(), i, System.currentTimeMillis(), false));
                }
                if(count > 1){
                    Integer[] NodeFin = new Integer[Nodes.size()];
                    Integer[] fin = Nodes.toArray(NodeFin);
                        FindRoutes(fin);
                        Collections.sort(Nodes);
                }
            }
        }
    }
    // uses Dijkstra's Algo to construct final table
    public void  FindRoutes(Integer[] Nodes){
        HashMap <Integer,Integer> finalLink = new HashMap();
        HashMap<Integer, Integer> finalDistance = new HashMap();
        HashMap<Integer,Integer> workingLink = new HashMap();
        HashMap<Integer,Integer> workingDistance = new HashMap();

        workingDistance.put(nic.getNSAP(), 0);
        workingLink.put(nic.getNSAP(),-1);
        while(!workingDistance.isEmpty()){
            int min = Integer.MAX_VALUE;
            int index = -1;
            Collection<Integer> Keys = workingDistance.keySet();
            for(int i : Keys){
                if (workingDistance.get(i)<min){
                    min = workingDistance.get(i);
                    index = i;
                }
            }
            finalDistance.put(index, workingDistance.get(index));
            finalLink.put(index, workingLink.get(index));
            workingDistance.remove(index);
            workingLink.remove(index);
            HashMap neighbours =  (HashMap) WholeTable.get(index).get(1);
            Collection<Integer> nei = neighbours.keySet();
            for(int z: nei){//getting all of the neighbours distances
                int val = ((Long) neighbours.get(z)).intValue();
                if(!finalDistance.containsKey(z)){
                   Long newDistnace = min + (Long)neighbours.get(z);
                   if(!workingDistance.containsKey(z)|| newDistnace< workingDistance.get(z)){
                            workingDistance.put(z,((Long) newDistnace).intValue());
                            if(min!= -1){
                                workingLink.put(z,index);
                            }
                            else{
                                workingLink.put(z,nic.getOutgoingLinks().indexOf(val));
                            }
                   }
                }
            }
        }
        ratTab = finalLink;
        fixRatTap();

    }
    public  void PingTest(int dest, PingPacket PP){
        int i =nic.getOutgoingLinks().indexOf(dest);
        nic.sendOnLink(i,PP);

    }
    public void DijTest( DijPack DP ){
        ArrayList<Integer> outGo = nic.getOutgoingLinks();
        int size = outGo.size();
        for (int i = 0; i < size; i++) {
            nic.sendOnLink(i,(DijPack)(DP.clone()));
        }
    }

    // bulids a table with the router that it is sending to next
    public void fixRatTap(){
        boolean flag = true;
        while(flag == true){
            flag = false;
            for(int i :ratTab.keySet()) {
                if (!nic.getOutgoingLinks().contains(ratTab.get(i))&&ratTab.get(i)!=-1){
                    ratTab.put(i,ratTab.get(ratTab.get(i)));
                    flag = true;
                }
            }
        }
    }
}