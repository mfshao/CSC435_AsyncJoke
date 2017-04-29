/*--------------------------------------------------------

1. Mingfei Shao / 11/06/2016:

2. Java version used: build 1.8.0_102-b14

3. Precise command-line compilation examples / instructions:
> javac AsyncJokeServer.java

4. Precise examples / instructions to run this program:
In separate shell windows:
> java AsyncJokeServer
or
> java AsyncJokeServer <customizedPortNumber>
if you want run this server with a port number rather than the default (7687).

5. List of files needed for running the program:
a. AsyncJokeServer.java
b. AsyncJokeClient.java
c. AsyncJokeAdminClient.java
d. AsyncJokeLog.txt
e. ChecklistAsyncJoke.html

5. Notes:
a. This AsyncJokeServer can return 4 jokes and 4 proverbs in a random order to a client, and re-order them once a 4-item cycle has finished.
b. This AsyncJokeServer is capable to handle multiple clients at the same time. I've tested it with 2 clients.
c. This AsyncJokeServer can run asynchronously, it will use TCP for receiving requests from client and send response back later via UDP.
d. This AsyncJokeServer can be controlled by a AsyncJokeAdminClient, which can change the server mode and shutdown the server.
e. The default port for AsyncJokeServer and AsyncJokeAdminServer are 7687 and 7688 respectively. If user wants to specify customized port number,
the port number will be used as the port number of AsyncJokeServer, and the port number of AsyncJokeAdminServer will be simply plusing 1.

----------------------------------------------------------*/

// Get the Input Output libraries

import java.io.*;
// Get the Java networking libraries
import java.net.*;
// Get the Java utility libraries
import java.util.*;

// AdminWorker class to handle Admin client requests, each worker class will run on a new thread
class AdminWorker extends Thread {
    Socket sock;

    AdminWorker(Socket s) {
        sock = s;
    }

    public void run() {
        PrintStream out;
        BufferedReader in;
        String state;
        String command;

        try {
            // Initialize the input stream of the socket as BufferedReader
            in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            // Initialize the output stream of the socket as PrintStream
            out = new PrintStream(sock.getOutputStream());
            try {
                // Received a toggling signal from Admin client, we don't care its actual content
                command = in.readLine();

                /*
                A very coarse-grained shutdown logic here.
                Once received a shutdown command from the Admin Client, this logic will create an "internal socket" connection that connect to the AsyncJokeServer running on localhost.
                The first pass socket connection will send a signal (empty username with empty UUID) to let the AsyncJokeServer to change its IS_RUNNING switch to false.
                And then, to set the IS_RUNNING switch on the AdminServer side to false.
                After that, this logic will send another shutdown signal from itself back to itself, to let the AdminServer to get out of the blocking waiting status and shut itself down
                The second pass socket connection will also send the same signal, but this is just for the AsyncJokeServer to get out of the blocking waiting status and shut itself down.
                It is not very fine-grained, but it works.
                 */
                if (command.equalsIgnoreCase("shutdown")) {
                    int internalPort = AsyncJokeServer.getServerPort();
                    Socket internalSock = new Socket("localhost", internalPort);
                    PrintStream toInternal = new PrintStream(internalSock.getOutputStream());
                    toInternal.println();
                    toInternal.flush();
                    toInternal.println();
                    toInternal.flush();
                    internalSock.close();
                    AdminServer.setIsRunningFalse();
                    internalPort = AsyncJokeServer.getAdminPort();
                    internalSock = new Socket("localhost", internalPort);
                    toInternal = new PrintStream(internalSock.getOutputStream());
                    toInternal.println("shutdown");
                    toInternal.flush();
                } else {

                    // Change the mode of AsyncJokeServer
                    AsyncJokeServer.toggleIsJoke();
                    // Decide which state is AsyncJokeServer currently running at
                    state = (AsyncJokeServer.getIsJoke()) ? "joke" : "proverb";
                    // Compose result string that to be send back to Admin client
                    String result = "Server running in " + state + " mode.";
                    // Print result string on server console for reference
                    System.out.println(result);
                    System.out.println();

                    // Send result string back to client
                    out.println(result);
                    out.flush();
                }
                // In case read from input stream fails
            } catch (IOException x) {
                System.out.println("Server read error");
                x.printStackTrace();
            }
            // Close connection to client
            sock.close();
        } catch (IOException ioe) {
            // In case anything wrong with the socket
            System.out.println(ioe);
        }
    }
}

/*
Define a new class that is runnable by a thread. This class serves as the Admin server.
This thread is created and executed in the main function of AsyncJokeServer. So in other words,
it will be initialized by AsyncJokeServer and running simultaneously with AsyncJokeServer on different threads.
 */
class AdminServer implements Runnable {
    private static boolean IS_RUNNING = true;

    public static void setIsRunningFalse() {
        IS_RUNNING = false;
    }

    public void run() {
        int q_len = 6;
        // Assign default primary port number
        int port = AsyncJokeServer.getAdminPort();
        Socket sock;

        try {
            // Create server socket and print notification to user
            ServerSocket servsock = new ServerSocket(port, q_len);
            System.out.println("Mingfei Shao's AsyncJokeAdminServer starting up, listening at port " + port + ".");
            System.out.println();

            while (IS_RUNNING) {
                // Blocking wait for client connection
                sock = servsock.accept();
                // Start AdminWorker thread to handle connected client
                new AdminWorker(sock).start();
            }
            System.out.println("AsyncJokeAdminServer shutdown!");
        } catch (IOException ioe) {
            // In case anything wrong with the socket
            System.out.println(ioe);
        }
    }
}

/*
Define a Data class to store the title and the text of a joke/proverb separately.
Each joke/proverb will be save in one Data object.
 */
class Data {
    // Title of the joke/proverb
    private String title;
    // Text of the joke/proverb
    private String text;

    // Constructor
    Data(String ttl, String txt) {
        title = ttl;
        text = txt;
    }

    // Use getters to keep privacy
    public String getTitle() {
        return title;
    }

    public String getText() {
        return text;
    }
}

/*
Define a ClientStatusTable to store the status of each user. Users are separated by their UUID.
The data structure used for ClientStatusTable is a Hashtable, which uses UUID as a key to retrieve the status table of that UUID.
The value part is an ArrayList of LinkedLists. Each ArrayList contains two LinkedLists, representing the status of joke and proverb respectively.
The reason why to use an ArrayList is because it is easier to manipulate the content inside via its methods.
 */
class ClientStatusTable {
    Hashtable<UUID, ArrayList<LinkedList<Integer>>> csTable;

    // Initialize the ClientStatusTable as a Hashtable
    ClientStatusTable() {
        csTable = new Hashtable<>();
    }

    // Method to add a new user (UUID) into ClientStatusTable
    public void add(UUID uuid) {
        // Create the ArrayList to store 2 status tables
        ArrayList<LinkedList<Integer>> wholeIndexTable = new ArrayList<>();
        // Create the first status table for proverb
        wholeIndexTable.add(initializeIndexTable());
        // Create the second status table for joke
        wholeIndexTable.add(initializeIndexTable());
        // Associate the new status tables with UUID
        csTable.put(uuid, wholeIndexTable);
    }

    // Method to initialize status table
    public LinkedList<Integer> initializeIndexTable() {
        // Create the LinkedList to store 4 indexes
        LinkedList<Integer> indexTable = new LinkedList<>();
        for (int i = 0; i < 4; i++) {
            // Add index 0, 1, 2, 3 into LinkedList sequentially
            indexTable.add(i);
        }
        // Randomize the order of indexes
        Collections.shuffle(indexTable, new Random(System.currentTimeMillis()));
        return indexTable;
    }

    // Setter method to re-create the index table once a four-item cycle has finished
    public void setIndexTable(UUID uuid, int index) {
        // The index values indicates which status table should be re-created, 0 for proverb and 1 for joke
        csTable.get(uuid).set(index, initializeIndexTable());
    }

    // Helper method to check if a UUID is in the ClientStatusTable already
    public boolean containsUUID(UUID uuid) {
        return csTable.containsKey(uuid);
    }

    // Getter method to retrieve the entry of status tables based on UUID
    public ArrayList<LinkedList<Integer>> getIndexTable(UUID uuid) {
        return csTable.get(uuid);
    }

    // Method to remove a entry of status tables based on UUID
    public void removeIndexTable(UUID uuid) {
        csTable.remove(uuid);
    }
}

// Worker class to handle client requests, each worker class will run on a new thread
class Worker extends Thread {
    Socket sock;
    InetAddress toAddr;
    int toPort;
    DatagramSocket outputSock;
    DatagramPacket outputPacket;

    // Constructor to initialize socket
    Worker(Socket s) {
        sock = s;
    }

    // Define the behavior of a running thread
    public void run() {
        BufferedReader in = null;
        // Decide the value of list index based on the mode of JokerServer, this determine which ClientStatusTable will be used
        int listIndex = (AsyncJokeServer.getIsJoke()) ? 1 : 0;
        String username;
        UUID uuid;
        String uuidString;
        String result;
        // Get the address of client
        toAddr = sock.getInetAddress();
        // Get the UDP server port on client side, which is the same as the TCP port on the server side
        toPort = sock.getLocalPort();
        // Byte buffer to hold UDP packet content
        byte[] buf;

        try {
            // Initialize the input stream of the socket as BufferedReader
            in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            // Get whole ClientStatusTable
            ClientStatusTable currentCSTable = AsyncJokeServer.getClientStatusTable();

            try {
                // Read first line of input from input stream as username
                username = in.readLine();
                // Read second line of input from input stream as user's UUID in string format
                uuidString = in.readLine();
                // Break connection after receive request
                sock.close();

                // Received shutdown signal from Admin server
                if (username.isEmpty() && uuidString.isEmpty()) {
                    AsyncJokeServer.setIsRunningFalse();
                    return;
                }

                // Convert the UUID string into UUID
                uuid = UUID.fromString(uuidString);
                // Print client info
                System.out.println("Request received from user: " + username + ", UUID: " + uuidString + ". Processing...");
                // Make server thread sleep after receiving request from client
                Thread.sleep(AsyncJokeServer.getSleepInterval());

                // If user is new, which means no data in current ClientStatusTable
                if (!currentCSTable.containsUUID(uuid)) {
                    // Add new entries to ClientStatusTable
                    currentCSTable.add(uuid);
                }

                // Get the index table based on UUID and current AsyncJokeServer mode
                LinkedList<Integer> currentIndexTable = currentCSTable.getIndexTable(uuid).get(listIndex);
                // Pop out the index value at the top
                int currentIndex = currentIndexTable.pop();
                // Get the joke/proverb list based on current AsyncJokeServer mode
                LinkedList<Data> currentList = AsyncJokeServer.getWholeList().get(listIndex);

                // Compose result string
                result = makeReturnString(currentList, currentIndex, username);

                // Send result string back to client
                buf = result.getBytes();
                // Create UDP socket
                outputSock = new DatagramSocket();
                // Create UDP packet to send to client
                outputPacket = new DatagramPacket(buf, buf.length, toAddr, toPort);
                // Send packet back to client via UDP
                outputSock.send(outputPacket);

                // Print info to user
                System.out.println("Send result string below back to user: " + username + ", UUID: " + uuidString);
                // Print result string on server console for reference
                System.out.println(result);
                System.out.println();

                // If the index table is empty, so the last index has been poped out, which means a four-item cycle has finished
                if (currentIndexTable.isEmpty()) {
                    // Print some info on server console for reference
                    String state = (AsyncJokeServer.getIsJoke()) ? "joke" : "proverb";
                    System.out.println("UUID: " + uuidString + " Has finished a four-item " + state + " cycle.");
                    System.out.println("List of " + state + " re-randomized for UUID: " + uuidString);
                    System.out.println();
                    // Re-initialize the empty index table with 4 indexes
                    currentCSTable.setIndexTable(uuid, listIndex);
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            } catch (InterruptedException ie) {
                ie.printStackTrace();
            }

        } catch (IOException ioe) {
            // In case anything wrong with the socket
            System.out.println(ioe);
        }
    }

    // Method to compose the result string that will be send back to client
    static String makeReturnString(LinkedList<Data> list, int index, String username) {
        // Use a StringBuffer to manipulate Strings
        StringBuffer result = new StringBuffer();
        // Get the joke/proverb from the joke/proverb list based on the index
        Data currentEntry = list.get(index);
        // Title of the joke/proverb comes first
        result.append(currentEntry.getTitle());
        result.append(" ");
        // Username comes second
        result.append(username);
        result.append(": ");
        // Text of the joke/proverb comes last
        result.append(currentEntry.getText());
        return result.toString();
    }
}

public class AsyncJokeServer {
    // Define default server port number
    private static final int DEFAULT_PORT = 7687;
    // Boolean value indicating if the server is running in joke mode
    private static boolean IS_JOKE = true;
    // LinkedList to store all jokes
    private static LinkedList<Data> JOKE_LIST = new LinkedList<>();
    // LinkedList to store all proverbs
    private static LinkedList<Data> PROVERB_LIST = new LinkedList<>();
    // ArrayList of LinkedList that will put above two LinkedLists together
    private static ArrayList<LinkedList<Data>> WHOLE_LIST = new ArrayList<>();
    // Global ClientStatusTable
    private static ClientStatusTable CLIENT_STATUS_TABLE;
    private static boolean IS_RUNNING = true;
    private static int SERVER_PORT = 0;
    private static int ADMIN_PORT = 0;
    // Define thread sleep interval value
    private static int SLEEP_INTERVAL = 40000;

    // Method to change the server mode indicator
    public static void toggleIsJoke() {
        IS_JOKE = !IS_JOKE;
    }

    // Getter method of the server mode indicator
    public static boolean getIsJoke() {
        return IS_JOKE;
    }

    public static void setIsRunningFalse() {
        IS_RUNNING = false;
    }

    // Getter method of the global ClientStatusTable
    public static ClientStatusTable getClientStatusTable() {
        return CLIENT_STATUS_TABLE;
    }

    // Getter method of the Array of all jokes/proverbs
    public static ArrayList<LinkedList<Data>> getWholeList() {
        return WHOLE_LIST;
    }

    // Getter method of the server port number
    public static int getServerPort() {
        return SERVER_PORT;
    }

    // Getter method of the Admin server port number
    public static int getAdminPort() {
        return ADMIN_PORT;
    }

    // Getter method of the server thread sleep interval value
    public static int getSleepInterval() {
        return SLEEP_INTERVAL;
    }

    // Method to initialize all jokes/proverbs
    public static void initializeData() {
        JOKE_LIST.add(new Data("JA", "Apparently I snore so loudly that it scares everyone in the car I'm driving."));
        JOKE_LIST.add(new Data("JB", "Relationships are a lot like algebra. Have you ever looked at your X and wondered Y?"));
        JOKE_LIST.add(new Data("JC", "I started out with nothing, and I still have most of it."));
        JOKE_LIST.add(new Data("JD", "Artificial intelligence is no match for natural stupidity."));
        PROVERB_LIST.add(new Data("PA", "The pen is mightier than the sword."));
        PROVERB_LIST.add(new Data("PB", "Hope for the best, but prepare for the worst."));
        PROVERB_LIST.add(new Data("PC", "The early bird catches the worm."));
        PROVERB_LIST.add(new Data("PD", "You can't judge a book by its cover."));
        // List of proverbs placed at index 0
        WHOLE_LIST.add(PROVERB_LIST);
        // List of jokes placed at index 1
        WHOLE_LIST.add(JOKE_LIST);
    }

    public static void main(String[] args) throws IOException {
        int q_len = 6;
        // Assign default server port
        SERVER_PORT = DEFAULT_PORT;

        // If has more than 1 command line arguments
        if (args.length > 1) {
            System.out.println("Error! At most only 1 argument as server port number is allowed!");
            System.exit(1);
        }

        // If has only 1 command line argument, try to parse it as a port number
        if (args.length == 1) {
            try {
                // If it is a number, then success
                SERVER_PORT = Integer.parseInt(args[0]);
                // Set thread sleep time interval value differently
                SLEEP_INTERVAL = 70000;
            } catch (NumberFormatException nfe) {
                // Output status to console
                System.out.println("Error! Please enter a valid number as port number!");
                System.exit(1);
            }
        }
        // Arbitrarily determine the port number of Admin server
        ADMIN_PORT = SERVER_PORT + 1;

        // Create an new Admin server object
        AdminServer adminServer = new AdminServer();
        // Create a new thread to run the Admin server
        Thread t = new Thread(adminServer);
        // Execute the Admin server thread
        t.start();

        // Initialize all jokes/proverbs
        initializeData();
        // Initialize global ClientStatusTable
        CLIENT_STATUS_TABLE = new ClientStatusTable();

        try {
            Socket sock;
            // Initialize a new server type socket using port number and queue length
            ServerSocket servSock = new ServerSocket(SERVER_PORT, q_len);
            // Print server info
            System.out.println("Mingfei Shao's AsyncJokeServer starting up, listening at port " + SERVER_PORT + ".");
            System.out.println();

            // Stick here to serve any incoming clients
            while (IS_RUNNING) {
                // Wait for client to connect
                sock = servSock.accept();
                // After connected, start a new worker thread to handle client's request, and main thread stays in the loop, waiting for next client
                new Worker(sock).start();
            }
            System.out.println("AsyncJokeServer shutdown!");
        } catch (IllegalArgumentException iae) {
            System.out.println("Fatal Error: port number is not valid!");
            System.exit(1);
        } catch (IOException ioe) {
            System.out.println("Fatal Error: cannot open socket!");
            System.exit(1);
        }
    }
}
