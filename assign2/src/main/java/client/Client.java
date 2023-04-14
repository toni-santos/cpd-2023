package client;

import java.io.*;
import java.net.*;

public class Client {
    public void launch() {
        try {
            // Create a socket to connect to the server
            Socket socket = new Socket("localhost", 5000);
            System.out.println("Connected to server.");

            // Create input and output streams for the socket
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            // Send data to the server
            String message = "Hello from client!";
            out.println(message);

            // Receive response from the server
            String response = in.readLine();
            System.out.println("Received response from server: " + response);

            // Close socket
            socket.close();
            System.out.println("Disconnected from server.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
