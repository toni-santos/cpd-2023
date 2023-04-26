package client;

import server.ServerCodes;

import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import static server.ServerCodes.OK;

/**
 * This program demonstrates a simple TCP/IP socket client.
 *
 * @author www.codejava.net
 */
public class Client {

    private final String HOSTNAME = "localhost";
    private final int PORT = 9000;
    private String user;

    public void run() throws IOException {
        SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress(HOSTNAME, PORT));
        socketChannel.configureBlocking(false);

        Scanner consoleInput = new Scanner(System.in);

        if (socketChannel.isConnected()) {
            logInRegister(consoleInput, socketChannel);
        }
        socketChannel.close();
    }

    public static void launch() throws IOException {
        Client client = new Client();
        client.run();
    }

    private boolean logInRegister(Scanner consoleInput, SocketChannel socketChannel) throws IOException {
        int opt = logInRegisterSelection(consoleInput);
        List<String> creds = getUserCredentials(consoleInput);

        if (opt == 1) {
            creds.add(0, String.valueOf(ServerCodes.LOG));
        } else {
            creds.add(0, String.valueOf(ServerCodes.REG));
        }

        socketChannel.write(ByteBuffer.wrap(String.join(",", creds).getBytes()));
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int bytesRead = socketChannel.read(buffer);
        if (bytesRead == -1) {
            return false;
        }
        String rawMessage = new String(buffer.array()).trim();
        System.out.println(rawMessage);
        ServerCodes result = ServerCodes.valueOf(rawMessage);

        if (result == OK) {
            if (opt == 1) {
                System.out.println("Login successful!");
            } else {
                System.out.println("Register successful!");
            }
        } else {
            System.out.println("Login/register failed!");
            return logInRegister(consoleInput, socketChannel);
        }
        return true;
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