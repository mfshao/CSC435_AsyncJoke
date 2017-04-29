/*--------------------------------------------------------

1. Mingfei Shao / 11/06/2016:

2. Java version used: build 1.8.0_102-b14

3. Precise command-line compilation examples / instructions:
> javac AsyncJokeAdminClient.java

4. Precise examples / instructions to run this program:
In separate shell windows:
> java AsyncJokeAdminClient
to connect to the admin server using default address (localhost) and port number (7688).
or
> java AsyncJokeAdminClient <customizedPortNumber1> <customizedPortNumber2> ...
to connect to the admin server using multiple customized admin server port numbers

5. List of files needed for running the program:
a. AsyncJokeServer.java
b. AsyncJokeClient.java
c. AsyncJokeAdminClient.java
d. AsyncJokeLog.txt
e. ChecklistAsyncJoke.html

5. Notes:
a. This AsyncJokeAdminClient can connect to multiple admin servers at the same time. Admin servers are differentiated by different port numbers.
In this current implementation, the address of the admin server is hard coded, but a more flexible implementation is also possible.
b. This AsyncJokeAdminClient is capable to send server mode change command to the server, which can switch the server between joke
and proverb modes, and also to send server shutdown command to the server.
c. The AsyncJokeAdminClient will have two lists storing servers' port numbers and their names. User can choose which server to send by typing their names into the console.
After a server has been shutdown, its information will be removed from both lists. However, the lists inside AsyncJokeClient will not get updated, this leaves some room for improvements.
d. By default, the port number of each admin server is the port number of the joke server add by 1.

----------------------------------------------------------*/

// Get the Input Output libraries

import java.io.*;
// Get the Java networking libraries
import java.net.*;
import java.util.ArrayList;

public class AsyncJokeAdminClient {
    // Define default admin server port number
    private static final int DEFAULT_ADMIN_PORT = 7688;
    // Define default admin server adress
    private static final String DEFAULT_ADMIN_ADDR = "localhost";
    // Initialize a list to hold admin server names
    private static ArrayList<String> SERVER_NAME_TABLE = new ArrayList<>();
    // Initialize a list to hold admin server port numbers
    private static ArrayList<Integer> SERVER_PORT_TABLE = new ArrayList<>();
    // Initialize an array to as alphabet, to be used for server names
    private static char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    static void sendSignal(String command, String serverName, int serverPort) {
        Socket sock;
        BufferedReader fromServer;
        PrintStream toServer;
        String textFromServer;
        try {
            // Open socket using given server address and port number
            sock = new Socket(serverName, serverPort);
            // Initialize the input stream of the socket as BufferedReader
            fromServer = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            // Initialize the output stream of the socket as PrintStream
            toServer = new PrintStream(sock.getOutputStream());
            // Send user input server name to server for query.
            toServer.println(command);
            toServer.flush();
            textFromServer = fromServer.readLine();
            if (textFromServer != null)
                System.out.println(textFromServer);
            // Close the socket
            sock.close();
            // In case the socket cannot be created for some reason
        } catch (IOException x) {
            System.out.println("Socket error.");
            x.printStackTrace();
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
            SERVER_PORT_TABLE.add(DEFAULT_ADMIN_PORT);
            SERVER_NAME_TABLE.add(String.valueOf(ALPHABET[0]));
        }

        System.out.println("Mingfei Shao's AsyncJokeAdminClient.");
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
            String command;
            // Main loop to receive commands from user input
            while (true) {
                // Update server names for display
                allPortName = "";
                for (int i = 0; i < SERVER_NAME_TABLE.size(); i++) {
                    allPortName = allPortName + SERVER_NAME_TABLE.get(i) + " ";
                }
                // Print hints for user
                System.out.print("Enter one of the following letter [ " + allPortName + "] to change the corresponding server mode, or [server letter] shutdown to shut down a server: ");
                // Flush output buffer to clean it
                System.out.flush();

                // Read user's input and separate by white-spaces
                command = in.readLine();
                String[] userInputStrings = command.split("\\s+");
                // If input is a valid server name, then send joke/proverb mode toggling signal to that server
                if (userInputStrings.length == 1 && SERVER_NAME_TABLE.indexOf(userInputStrings[0].toUpperCase()) != -1) {
                    // Send toggling command to server
                    int listIndex = SERVER_NAME_TABLE.indexOf(userInputStrings[0].toUpperCase());
                    sendSignal(command, DEFAULT_ADMIN_ADDR, SERVER_PORT_TABLE.get(listIndex));
                    System.out.println("Server mode toggling signal sent to Admin server " + userInputStrings[0].toUpperCase() + ".");
                } else if (userInputStrings.length == 2 && userInputStrings[1].equalsIgnoreCase("shutdown")) {
                    // If input is a valid server name plus "shutdown", then send shutdown signal to that server
                    int listIndex = SERVER_NAME_TABLE.indexOf(userInputStrings[0].toUpperCase());
                    sendSignal(userInputStrings[1], DEFAULT_ADMIN_ADDR, SERVER_PORT_TABLE.get(listIndex));
                    SERVER_PORT_TABLE.remove(listIndex);
                    SERVER_NAME_TABLE.remove(listIndex);
                    System.out.println("Server shutdown signal sent to Admin server " + userInputStrings[0].toUpperCase() + ".");
                } else {
                    // Other inputs are not supported
                    System.out.println("Invalid command!");
                }
            }
        } catch (IOException x) {
            // In case read from input stream fails
            x.printStackTrace();
        }
    }
}
