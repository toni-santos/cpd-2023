package client;

import game.GameCodes;
import server.ServerCodes;
import utils.SHA512Generator;

import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.*;

import static server.ServerCodes.GG;

/**
 * This program demonstrates a simple TCP/IP socket client.
 *
 * @author www.codejava.net
 */
public class Client {

    private final String HOSTNAME = "localhost";
    private final int PORT = 9000;
    private final Scanner consoleInput;
    private String user;
    private String token;
    private SocketChannel serverSocket;
    private SocketChannel gameSocket;
    private ServerCodes gamemode;
    private List<String> team1;
    private List<String> team2;

    public Client() throws IOException {
        this.serverSocket = SocketChannel.open(new InetSocketAddress(HOSTNAME, PORT));
        this.serverSocket.configureBlocking(true);
        this.consoleInput = new Scanner(System.in);
    }

    public void run() throws IOException {
        if (serverSocket.isConnected()) {
            if (logInRegister()) {
                while (true) {
                    int port = chooseGameMode();
                    if (port != -1) {
                        connectToGameServer(port);
                        gameLoop();
                    } else {
                        break;
                    }
                }
            }
        }
        serverSocket.close();
    }

    private void connectToGameServer(int port) throws IOException {
        this.gameSocket = SocketChannel.open(new InetSocketAddress(HOSTNAME, port));
        this.gameSocket.configureBlocking(true);

        String readyString = GameCodes.READY + "," + this.token;
        this.gameSocket.write(ByteBuffer.wrap(readyString.getBytes()));

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int bytesRead = this.gameSocket.read(buffer);

        if (bytesRead == -1) {
            return;
        }

        String rawMessage = new String(buffer.array()).trim();
        List<String> message = List.of(rawMessage.split(","));
        GameCodes code = GameCodes.valueOf(message.get(0));
        System.out.println(message.get(0));
        if (code == GameCodes.START) {
            switch (this.gamemode) {
                case N1, R1 -> {
                    this.team1 = Arrays.asList(message.get(1));
                    this.team2 = Arrays.asList(message.get(2));
                }
                case N2, R2 -> {
                    this.team1 = Arrays.asList(message.get(1), message.get(2));
                    this.team2 = Arrays.asList(message.get(3), message.get(4));
                }
            }
            System.out.println("Game Starting...");
        } else {
            this.gameSocket.write(ByteBuffer.wrap((GameCodes.ERR + "," + token).getBytes()));
        }
    }

    private void gameLoop() throws IOException {
        System.out.println("\nGame Started!\n");
        System.out.println(String.join(" ", team1) + " vs " + String.join(" ", team2));
        while (true) {
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            int bytesRead = this.gameSocket.read(buffer);
            System.out.println("bytesRead = " + bytesRead);
            if (bytesRead == -1) {
                return;
            }
            String rawMessage = new String(buffer.array()).trim();
            List<String> message = List.of(rawMessage.split(","));
            GameCodes code = GameCodes.valueOf(message.get(0));

            switch (code) {
                case TURN -> {
                    String turnNumber = message.get(1);
                    System.out.println("Turn " + turnNumber + "!");
                    playerAction();
                }
                case GG -> {
                    GameCodes result = GameCodes.valueOf(message.get(1));
                    if (result == GameCodes.W) {
                        System.out.println("You Won!\nReturning to main menu...");
                    } else if (result == GameCodes.L) {
                        System.out.println("You Lost!\nReturning to main menu...");
                    }
                    return;
                }
                case UPDATE -> {
                    String playerName = message.get(1);
                    String damage = message.get(2);
                    String team1HP = message.get(3);
                    String team2HP = message.get(4);

                    if (playerName.equals(this.user)) {
                        System.out.println();
                    } else {
                        System.out.println();
                    }
                }
            }
        }
    }

    private void playerAction() throws IOException {
        System.out.print("""
                Pick a dice to roll!
                1. D6
                2. D12
                3. D20
                -\s""");

        int opt = consoleInput.nextInt();

        switch (opt) {
            case 1 -> {
                String actionString = GameCodes.ACTION + "," + token + "," + GameCodes.D6;
                gameSocket.write(ByteBuffer.wrap(actionString.getBytes()));
            }
            case 2 -> {
                String actionString = GameCodes.ACTION + "," + token + "," + GameCodes.D12;
                gameSocket.write(ByteBuffer.wrap(actionString.getBytes()));
            }
            case 3 -> {
                String actionString = GameCodes.ACTION + "," + token + "," + GameCodes.D20;
                gameSocket.write(ByteBuffer.wrap(actionString.getBytes()));
            }
            default -> {
                System.out.println("Invalid input, try again!");
                playerAction();
            }
        }

    }

    public static void launch() throws IOException {
        Client client = new Client();
        client.run();
    }

    private boolean logInRegister() throws IOException {
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
        List<String> message = List.of(rawMessage.split(","));

        if (ServerCodes.OK == ServerCodes.valueOf(message.get(0))) {
            if (opt == 1) {
                System.out.println("Login successful!");
            } else {
                System.out.println("Register successful!");
            }
            this.token = message.get(1);
            this.user = creds.get(1);
        } else {
            System.out.println("Login/register failed!");
            return logInRegister();
        }
        return true;
    }

    private int chooseGameMode() throws IOException {
        int opt = gameModeSelection(consoleInput);
        switch (opt) {
            case 1, 2:
                this.gamemode = ServerCodes.valueOf("N" + opt);
                return search("N" + opt);
            case 3, 4:
                this.gamemode = ServerCodes.valueOf("R" + (opt - 2));
                return search("R" + (opt - 2));
            case 5:
                disconnect(this.serverSocket);
                return -1;
        }
        return -1;
    }

    private void disconnect(SocketChannel socket) throws IOException {
        socket.close();
    }

    private int search(String gamemode) throws IOException {
        System.out.println("Searching...");

        String req = gamemode + "," + this.token;
        serverSocket.write(ByteBuffer.wrap(req.getBytes()));

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int bytesRead = this.serverSocket.read(buffer);
        if (bytesRead == -1) {
            return bytesRead;
        }

        String rawMessage = new String(buffer.array()).trim();
        List<String> result = List.of(rawMessage.split(","));
        System.out.println("result = " + result);
        ServerCodes code = ServerCodes.valueOf(result.get(0));
        String port = result.get(1);

        if (code == ServerCodes.GF) {
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
                "2. 2v2 Normal\n" +
                "3. 1v1 Ranked\n" +
                "4. 2v2 Ranked\n" +
                "5. Quit\n" +
                "- ");
        int opt = consoleInput.nextInt();

        switch (opt) {
            case 1, 2, 3, 4, 5 -> {
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

        return new ArrayList<>(Arrays.asList(username, SHA512Generator.encrypt(password)));
    }

    private String getUsername(Scanner consoleInput) {
        System.out.print("Username: ");
        String username = consoleInput.next();

        return username;
    }

    private String getPassword(Scanner consoleInput) {
        System.out.print("Password: ");
        String password = consoleInput.next();

        //TODO: Change password lengths back to just < 8
        if (password.length() < 8 && password.length() > 1) {
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