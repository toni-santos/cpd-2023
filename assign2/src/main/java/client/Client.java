package client;

import java.net.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * This program demonstrates a simple TCP/IP socket client.
 *
 * @author www.codejava.net
 */
public class Client {

    public void launch() {

        String hostname = "localhost";
        int port = 9000;

        try (Socket socket = new Socket(hostname, port)) {
            Scanner consoleInput = new Scanner(System.in);

            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);

            InputStream input = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));

            System.out.println("Connected to the server!");

            logInRegister(consoleInput, writer, reader);

        } catch (UnknownHostException ex) {

            System.out.println("Server not found: " + ex.getMessage());

        } catch (IOException ex) {

            System.out.println("I/O error: " + ex.getMessage());
        }
    }

    private boolean logInRegister(Scanner consoleInput, PrintWriter writer, BufferedReader reader) {
        int opt = logInRegisterSelection(consoleInput);
        List<String> creds = getUserCredentials(consoleInput);

        creds.add(String.valueOf(opt));

        writer.write(String.join(",", creds));

        String result = null;

        try {
            result = reader.readLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (parseLogInResult(result)) {
            System.out.println("Login successful");
            return true;
        } else {
            System.out.println("Failed to login!");
            return logInRegister(consoleInput, writer, reader);
        }
    }

    private boolean parseLogInResult(String result) {
        List<String> res = List.of(result.split(","));

        return res.get(0).equals("OK");
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