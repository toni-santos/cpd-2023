package client;

import server.ServerCodes;

import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.*;

/**
 * This program demonstrates a simple TCP/IP socket client.
 *
 * @author www.codejava.net
 */
public class Client {

    private final String HOSTNAME = "localhost";
    private final int PORT = 9000;
    private String user;
    private SocketChannel serverSocket;
    private SocketChannel gameSocket;

    public Client() throws IOException {
        this.serverSocket = SocketChannel.open(new InetSocketAddress(HOSTNAME, PORT));
        this.serverSocket.configureBlocking(true);
    }

    public void run() throws IOException {
        Scanner consoleInput = new Scanner(System.in);

        if (serverSocket.isConnected()) {
            if (logInRegister(consoleInput)) {
                int port = chooseGameMode(consoleInput);
                if (port != -1) {
                    connectToGameServer(port);
                    while (true) {
                        gameLoop(consoleInput);
                    }
                }
            }
        }
        serverSocket.close();
    }

    private void connectToGameServer(int port) throws IOException {
        this.gameSocket = SocketChannel.open(new InetSocketAddress(HOSTNAME, port));
        this.gameSocket.configureBlocking(true);
    }

    private void gameLoop(Scanner consoleInput) throws IOException {
        System.out.println("Talk back: ");
        String res = consoleInput.next();
        gameSocket.write(ByteBuffer.wrap(res.getBytes()));
    }

    public static void launch() throws IOException {
        Client client = new Client();
        client.run();
    }

    private boolean logInRegister(Scanner consoleInput) throws IOException {
        int opt = logInRegisterSelection(consoleInput);

        if (opt == 3) {
            return false;
        }

        List<String> creds = getUserCredentials(consoleInput);

        switch (opt) {
            case 1 -> creds.add(0, String.valueOf(ServerCodes.LOG));
            case 2 -> creds.add(0, String.valueOf(ServerCodes.REG));
        }

        this.serverSocket.write(ByteBuffer.wrap(String.join(",", creds).getBytes()));

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int bytesRead = this.serverSocket.read(buffer);
        if (bytesRead == -1) {
            return false;
        }
        String rawMessage = new String(buffer.array()).trim();
        ServerCodes result = ServerCodes.valueOf(rawMessage);

        if (result == ServerCodes.OK) {
            if (opt == 1) {
                System.out.println("Login successful!");
            } else {
                System.out.println("Register successful!");
            }
            this.user = creds.get(1);
        } else {
            System.out.println("Login/register failed!");
            return logInRegister(consoleInput);
        }
        return true;
    }

    private int chooseGameMode(Scanner consoleInput) throws IOException {
        int opt = gameModeSelection(consoleInput);
        switch (opt) {
            case 1:
                return search("N" + opt);
            case 5:
                return -1;
        }
        return -1;
    }

    private int search(String gamemode) throws IOException {
        System.out.println("Searching...");

        String req = gamemode + "," + this.user;
        serverSocket.write(ByteBuffer.wrap(req.getBytes()));

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int bytesRead = this.serverSocket.read(buffer);
        if (bytesRead == -1) {
            return bytesRead;
        }

        String rawMessage = new String(buffer.array()).trim();
        List<String> result = List.of(rawMessage.split(","));
        String code = result.get(0);
        String port = result.get(1);

        if (code.equals(ServerCodes.GF)) {
            System.out.println("Game found! Waiting for lobby...");
            return Integer.parseInt(port);
        } else {
            System.out.println("Something went wrong!");
        }
        return bytesRead;
    }

    private int gameModeSelection(Scanner consoleInput) {
        System.out.print("Welcome " + this.user + "!\n" +
                "What would you like to do?\n" +
                "1. 1v1 Normal\n" +
                "5. Quit\n" +
                "- ");
        int opt = consoleInput.nextInt();

        switch (opt) {
            case 1, 5 -> {
                return opt;
            }
            default -> {
                System.out.println("Invalid input, try again!");
                return logInRegisterSelection(consoleInput);
            }
        }
    }

    private List<String> getUserCredentials(Scanner consoleInput) {
        String username = getUsername(consoleInput);
        String password = getPassword(consoleInput);

        return new ArrayList<String>(Arrays.asList(username, password));
    }

    private String getUsername(Scanner consoleInput) {
        System.out.print("Username: ");
        String username = consoleInput.next();

        return username;
    }

    private String getPassword(Scanner consoleInput) {
        System.out.print("Password: ");
        String password = consoleInput.next();

        if (password.length() < 8) {
            System.out.println("Invalid password, try again!");
            return getPassword(consoleInput);
        }

        return password;
    }


    private int logInRegisterSelection(Scanner consoleInput) {
        System.out.print("1. Log In\n2. Register\n3. Quit\n- ");

        int opt = consoleInput.nextInt();

        switch (opt) {
            case 1, 2, 3 -> {
                return opt;
            }
            default -> {
                System.out.println("Invalid input, try again!");
                return logInRegisterSelection(consoleInput);
            }
        }
    }


}