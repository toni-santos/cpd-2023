package server;

import java.io.*;
import java.net.*;

public class Server {
    public void launch() {
        try {
            // Create a server socket bound to a specific port
            ServerSocket serverSocket = new ServerSocket(5000);
            System.out.println("Server started. Listening for client connections...");

            while (true) {
                // Accept incoming client connections
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress().getHostAddress());

                // Create input and output streams for the client
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

                // Read data from the client
                String message = in.readLine();
                System.out.println("Received message from client: " + message);

                // Process the data (e.g., perform business logic)
                String response = "Server: Received your message: " + message;

                // Send response back to the client
                out.println(response);

                // Close client socket
                clientSocket.close();
                System.out.println("Client disconnected.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
