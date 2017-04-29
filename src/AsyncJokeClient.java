/*--------------------------------------------------------

1. Mingfei Shao / 11/06/2016:

2. Java version used: build 1.8.0_102-b14

3. Precise command-line compilation examples / instructions:
> javac AsyncJokeClient.java

4. Precise examples / instructions to run this program:
In separate shell windows:
> java AsyncJokeClient
to connect to the server using default address (localhost) and port number (7687).
or
> java JokeClient <customizedPortNumber1> <customizedPortNumber2> ...
to connect to the server using multiple customized server port numbers

5. List of files needed for running the program:
a. AsyncJokeServer.java
b. AsyncJokeClient.java
c. AsyncJokeAdminClient.java
d. AsyncJokeLog.txt
e. ChecklistAsyncJoke.html

5. Notes:
a. This AsyncJokeClient can connect to multiple servers at the same time. Servers are differentiated by different port numbers.
In this current implementation, the address of the server is hard coded, but a more flexible implementation is possible.
b. This AsyncJokeClient is capable to send request and receive jokes/proverbs from a AsyncJokeServer.
c. The AsyncJokeClient will have two lists storing servers' port numbers and their names. User can choose which server to send by typing their names into the console.
d. The AsyncJokeClient will use TCP to send request to a server and immediately spawn an UDP server monitoring the same port after the request is send.
The use of the same port number for server and client is to simplify the logic in determine from which server the packet is received.
f. While the UDP server is waiting, the main thread will continue to provide the user with a basic number adding function to play with.
Response from server will only be displayed after current number adding function has ended.

----------------------------------------------------------*/

// Get the Input Output libraries

import java.io.*;
// Get the Java networking libraries
import java.net.*;
// Get the UUID API in Java utility libraries
import java.util.ArrayList;
import java.util.UUID;

// UDP server class to wait for server's response
class UDPServer extends Thread {
    // Define default UDP packet size
    private final int DEFAULT_PACKET_SIZE = 255;
    DatagramSocket inputSock;
    // Initialize an UDP packet object
    DatagramPacket inputPacket = new DatagramPacket(new byte[DEFAULT_PACKET_SIZE], DEFAULT_PACKET_SIZE);
    int port;
    // Define packet receive indicator
    boolean isPacketReceived = false;

    // Default constructor, save the port number of this current UDP socket
    UDPServer(DatagramSocket ds) {
        inputSock = ds;
        port = ds.getLocalPort();
    }

    // Getter for packet receive indicator
    public boolean isPacketReceived() {
        return isPacketReceived;
    }

    // Getter for UDP packet
    public DatagramPacket getInputPacket() {
        return inputPacket;
    }

    // Getter for this socket's port number
    public int getPort() {
        return port;
    }

    public void run() {
        try {
            // Blocking wait for UDP packet send via this socket
            inputSock.receive(inputPacket);
            // Change the packet receive indicator
            isPacketReceived = true;
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        inputSock.close();
    }
}

public class AsyncJokeClient {
    // Define default server port number
    private static final int DEFAULT_SERVER_PORT = 7687;
    // Define default server address
    private static final String DEFAULT_SERVER_ADDR = "localhost";
    // Initialize a list to hold server names
    private static ArrayList<String> SERVER_NAME_TABLE = new ArrayList<>();
    // Initialize a list to hold server port numbers
    private static ArrayList<Integer> SERVER_PORT_TABLE = new ArrayList<>();
    // Initialize an array to as alphabet, to be used for server names
    private static char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    // Define a byte buffer to hold the content of UDP packet
    private static byte[] buf;
    // Initialize a list to hold running UDP servers
    private static ArrayList<UDPServer> UDP_SERVER_LIST = new ArrayList<>();

    static void sendRemoteResponse(String username, String uuid, String serverName, int serverPort) {
        Socket sock;
        PrintStream toServer;
        try {
            // Open socket using given server address and port number
            sock = new Socket(serverName, serverPort);
            // Initialize the output stream of the socket as PrintStream
            toServer = new PrintStream(sock.getOutputStream());
            // Send user input server name to server for query.
            toServer.println(username);
            toServer.flush();
            toServer.println(uuid);
            toServer.flush();
            // Close the socket
            sock.close();
            // In case the socket cannot be created for some reason
        } catch (IOException ioe) {
            System.out.println("Socket error.");
            ioe.printStackTrace();
        }
    }

    public static void main(String args[]) {
        // If user has defined port number(s) in the command line argument(s)
        if (args.length > 0) {
            try {
                // Try to parse each arguments as port numbers
                for (int i = 0; i < args.length; i++) {
                    int port = Integer.parseInt(args[i]);
                    // Port number out of boundary
                    if (port < 0 || port > 65535) {
                        System.out.println("Error! Port number out of boundary!");
                        System.exit(1);
                    }
                    // If everything goes well, save as new server into the lists
                    SERVER_PORT_TABLE.add(port);
                    SERVER_NAME_TABLE.add(String.valueOf(ALPHABET[i]));
                }
            } catch (NumberFormatException nfe) {
                // If something cannot be parsed as integers
                System.out.println("Error! Please enter a valid number as port number!");
                System.exit(1);
            }
        } else {
            // If user didn't defined port number(s) in the command line argument(s), then use default value
            SERVER_PORT_TABLE.add(DEFAULT_SERVER_PORT);
            SERVER_NAME_TABLE.add(String.valueOf(ALPHABET[0]));
        }

        // Generate a random UUID for client
        UUID uuid = UUID.randomUUID();

        System.out.println("Mingfei Shao's AsyncJokeClient.");
        System.out.println();

        // Print the information (server's port numbers and their names) to console
        String allPortName = "";
        for (int i = 0; i < SERVER_NAME_TABLE.size(); i++) {
            System.out.println("Server " + SERVER_NAME_TABLE.get(i) + " at " + Integer.toString(SERVER_PORT_TABLE.get(i)));
            allPortName = allPortName + SERVER_NAME_TABLE.get(i) + " ";
        }

        // Initialize input stream as a BufferedReader
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        try {
            String username;
            String command;
            do {
                System.out.println("Please enter your name: ");
                System.out.flush();
                // Get username from user input
                username = in.readLine();

                // Empty input is not a valid username
                if (username.isEmpty()) {
                    System.out.println("Username cannot be empty.");
                    System.out.flush();
                }
            } while (username.isEmpty());

            // Main loop, user can either enter a server's name to request for a new joke/proverb, or input several numbers for addition
            while (true) {
                System.out.print("Enter one of the following letter [ " + allPortName + "] to get a joke or proverb, or numbers for sum: ");
                // Flush output buffer to clean it
                System.out.flush();

                UDPServer udpServer;
                // Read user's input and separate by white-spaces
                command = in.readLine();
                String[] userInputStrings = command.split("\\s+");
                // If input is a valid server name
                if (userInputStrings.length == 1 && SERVER_NAME_TABLE.indexOf(command.toUpperCase()) != -1) {
                    // Find the index of the server in both tables
                    int listIndex = SERVER_NAME_TABLE.indexOf(command.toUpperCase());
                    // Send UUID and username to server, requesting new joke/proverb
                    sendRemoteResponse(username, uuid.toString(), DEFAULT_SERVER_ADDR, SERVER_PORT_TABLE.get(listIndex));
                    try {
                        // Create an UDP socket with the same port number as server
                        DatagramSocket datagramSock = new DatagramSocket(SERVER_PORT_TABLE.get(listIndex));
                        // Create an UDP server with that UDP socket
                        udpServer = new UDPServer(datagramSock);
                        // Execute the UDP server thread
                        udpServer.start();
                        // Add running UDP server into UDP server list
                        UDP_SERVER_LIST.add(udpServer);
                    } catch (SocketException se) {
                        // Error handlings
                        System.out.println("Fatal Error: cannot create UDP server!");
                        se.printStackTrace();
                        System.exit(1);
                    }
                    // Finished, re-start from the head of the while loop
                    continue;
                }

                // User didn't enter a right server name, try to perform addition over user input values
                int sum = 0;
                // Empty user input, error!
                if (userInputStrings.length == 0) {
                    System.out.println("Please input some valid number(s)!");
                } else {
                    try {
                        // Try to parse user's input as integers and perform addition
                        for (int i = 0; i < userInputStrings.length; i++) {
                            sum += Integer.parseInt(userInputStrings[i]);
                        }
                        // If everything goes fine, output sum value to console
                        System.out.println("Your sum is: " + sum);
                    } catch (NumberFormatException nfe) {
                        // Can't parse, error!
                        System.out.println("Please input some valid number(s)!");
                    }
                }

                // If have any running UDP servers
                if (!UDP_SERVER_LIST.isEmpty()) {
                    // Try to see if any of the running UDP servers has received a new packet
                    for (int i = 0; i < UDP_SERVER_LIST.size(); i++) {
                        udpServer = UDP_SERVER_LIST.get(i);
                        if (udpServer.isPacketReceived()) {
                            // If received a new packet, get the packet
                            DatagramPacket packet = udpServer.getInputPacket();
                            // And the server port number
                            int remotePort = udpServer.getPort();
                            // Get data from the packet
                            buf = packet.getData();
                            // Reconstruct the result string
                            String result = new String(buf);

                            // Output result string
                            System.out.println();
                            System.out.print("Server " + SERVER_NAME_TABLE.get(SERVER_PORT_TABLE.indexOf(remotePort)) + " responds: ");
                            System.out.println(result);
                            System.out.println();
                            // The UDP server's job is done, remove it from the list
                            UDP_SERVER_LIST.remove(i);
                        }
                    }
                }
            }
        } catch (IOException x) {
            // In case read from input stream fails
            x.printStackTrace();
        }
    }
}
