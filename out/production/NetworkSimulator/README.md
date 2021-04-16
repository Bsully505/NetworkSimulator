Introduction
------------

This project is a very simple network simulator that is designed mostly to practice
creating router algorithms.

Group Members
------------

Bryan Sullivan
Henok Ketsela
Harrison Dominique
Jack Zemlanicky
Dylan Irwin

How to compile/run the program:
------------

1) Compile the code within the folder containing all the source code
2) run the file "App.java"
3) Modify the GUI you are prompted by loading a network and selecting an algorithm, packets per second to be sent, and debug level (if you want)
4) Hit the "Run" button!

Breakdown of work
------------

We broke the work down as follows:

Dylan and Jack: Handle the Distance Vector Routing algorithm

Bryan and Henok: Handle the Link State Routing algorithm

Harrison: Float between the two algorithms, acting as a sort of handyman for each

Individual work completed
------------

Jack- With the help of Professor Duncan, created the buildTable method, route method, and handling of routing/sending/creating different types of packets (DV packets, ping packets, normal packets)

Dylan- Altered and implemented Bryan and Henok's PingPacket Class/Method to work for Distance Vector Routing.

Bryan- Worked on LinkStateRouter with Henok and created WholeTable method, and used PingTest to check the time between each router. Then have Dijtest flood the whole network so each table can create the topology of the network.

Henok- Worked on LinkStateRouter with Bryan and created WholeTable method, and used PingTest to check the time between each router. Then have Dijtest flood the whole network so each table can create the topology of the network.

Harrison- Worked on implementing Dijkstra's algorithm and converted the wholeTable Hashmap to a 2D Array to run the algorithm.