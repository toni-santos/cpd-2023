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
    private SocketChannel socketChannel;

    public Client() throws IOException {
        this.socketChannel = SocketChannel.open(new InetSocketAddress(HOSTNAME, PORT));
        this.socketChannel.configureBlocking(true);
    }

    public void run() throws IOException {
        Scanner consoleInput = new Scanner(System.in);

        if (socketChannel.isConnected()) {
            if (logInRegister(consoleInput)) {
                chooseGameMode(consoleInput);
            }
        }
        socketChannel.close();
    }

    public static void launch() throws IOException {
        Client client = new Client();
        client.run();
    }

    private boolean logInRegister(Scanner consoleInput) throws IOException {
        int opt = logInRegisterSelection(consoleInput);
        List<String> creds = getUserCredentials(consoleInput);

        if (opt == 1) {
            creds.add(0, String.valueOf(ServerCodes.LOG));
        } else {
            creds.add(0, String.valueOf(ServerCodes.REG));
        }

        this.socketChannel.write(ByteBuffer.wrap(String.join(",", creds).getBytes()));

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int bytesRead = this.socketChannel.read(buffer);
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

    private boolean chooseGameMode(Scanner consoleInput) throws IOException {
        int opt = gameModeSelection(consoleInput);
        switch (opt) {
            case 1:
                search("N" + opt);
            case 5:
                return false;
        }
        return true;
    }

    private void search(String gamemode) throws IOException {
        String req = gamemode + "," + this.user;
        socketChannel.write(ByteBuffer.wrap(req.getBytes()));

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int bytesRead = this.socketChannel.read(buffer);
        if (bytesRead == -1) {
            return;
        }

        String rawMessage = new String(buffer.array()).trim();
        ServerCodes result = ServerCodes.valueOf(rawMessage);

        System.out.println(result);
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
        System.out.print("1. Log In\n2. Register\n- ");

        int opt = consoleInput.nextInt();

        switch (opt) {
            case 1, 2 -> {
                return opt;
            }
            default -> {
                System.out.println("Invalid input, try again!");
                return logInRegisterSelection(consoleInput);
            }
        }
    }


}