package client;

import game.GameCodes;
import server.ServerCodes;
import utils.SHA512Generator;

import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.*;

import static utils.Log.*;
import static utils.Log.GAME;

/**
 * This program demonstrates a simple TCP/IP socket client.
 *
 * @author www.codejava.net
 */
public class Client {

    private Selector gameSelector;
    private String HOSTNAME;
    private final Scanner consoleInput;
    private String user;
    private String token;
    private SocketChannel serverSocket;
    private SocketChannel gameSocket;
    private ServerCodes gamemode;
    private List<String> team1;
    private List<String> team2;
    private int gamePort;
    private ServerCodes reconnected;
    private boolean gameOver = false;
    private boolean inputing;
    private Thread actionInputThread;

    public Client(String hostname, String port) throws IOException {
        this.serverSocket = SocketChannel.open(new InetSocketAddress(hostname, Integer.parseInt(port)));
        this.HOSTNAME = hostname;
        this.serverSocket.configureBlocking(true);
        this.consoleInput = new Scanner(System.in);
    }

    public void run() throws IOException {
        if (serverSocket.isConnected()) {
            if (logInRegister()) {
                while (true) {
                    if (this.reconnected != ServerCodes.G) {
                        this.gamePort = chooseGameMode();
                    }
                    if (this.gamePort != -1) {
                        connectToGameServer();
                        gameLoop();
                    } else {
                        break;
                    }
                }
            }
        }
        serverSocket.close();
    }

    private void connectToGameServer() throws IOException {
        this.gameSelector = Selector.open();
        this.gameSocket = SocketChannel.open(new InetSocketAddress(HOSTNAME, this.gamePort));
        this.gameSocket.configureBlocking(false);
        this.gameSocket.register(gameSelector, SelectionKey.OP_READ);

        if (this.reconnected == ServerCodes.G) {
            this.reconnected = null;

            String reconnectString = GameCodes.RECONNECT + "," + token + "," + this.user;
            this.gameSocket.write(ByteBuffer.wrap(reconnectString.getBytes()));
            System.out.println(SUCCESS("Reconnected to game!"));
        } else {
            String readyString = GameCodes.READY + "," + this.token;
            this.gameSocket.write(ByteBuffer.wrap(readyString.getBytes()));
        }
    }

    private void gameLoop() throws IOException {
        while (!gameOver) {
            gameSelector.select();
            Set<SelectionKey> keys = gameSelector.selectedKeys();
            for (SelectionKey key : keys) {
                if (key == null) continue;
                if (key.isReadable()) {
                    readGameInstruction(key);
                }
            }
        }
    }

    private void readGameInstruction(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int bytesRead = 0;
        try {
            bytesRead = channel.read(buffer);
        } catch (SocketException e) {
            System.out.println(ERROR("Server went offline, exiting!"));
            System.exit(0);
        }
        if (bytesRead == -1) {
            return;
        }
        if (bytesRead == 0) {
            return;
        }
        String rawMessage = new String(buffer.array()).trim();
        List<String> message = List.of(rawMessage.split(","));

        GameCodes code = GameCodes.valueOf(message.get(0));
        switch (code) {
            case CANCEL -> {
                System.out.println(ERROR("Game cancelled, GGs!"));
                disconnect(gameSocket);
                gameSelector.close();
                this.inputing = false;
                this.gameOver = true;
            }
            case TURN -> {
                String turnNumber = message.get(1);
                String team1HP = message.get(2);
                String team2HP = message.get(3);
                System.out.println(GAME("Turn " + turnNumber + "!"));
                System.out.println(GAME("Team 1 - " + team1HP + " | " + team2HP + " - Team 2"));
                playerAction();
            }
            case GG -> {
                GameCodes result = GameCodes.valueOf(message.get(1));
                if (result == GameCodes.W) {
                    System.out.println(GAME("You Won!\nReturning to main menu..."));
                } else if (result == GameCodes.L) {
                    System.out.println(GAME("You Lost!\nReturning to main menu..."));
                }
                this.gameOver = true;
            }
            case UPDATE -> {
                String damage = message.get(1);

                System.out.println(GAME("You dealt " + damage + "!"));
            }
            case DISCONNECT -> {
                System.out.println(GAME("A player has lost connection!\nWaiting 45 seconds for them to reconnect, if they don't the game will be cancelled!"));
                if (actionInputThread.isAlive()) this.inputing = false;
            }
            case RECONNECT -> {
                if (message.get(1).equals(GameCodes.DISCONNECT.toString())) {
                    System.out.println(GAME("Not all players have reconnected, waiting..."));
                } else {
                    String turnNumber = message.get(1);
                    if (team1 == null) team1 = List.of(message.get(2).split("-"));
                    if (team2 == null) team2 = List.of(message.get(3).split("-"));
                    String team1HP = message.get(4);
                    String team2HP = message.get(5);
                    boolean actionable = Boolean.parseBoolean(message.get(6));

                    System.out.println(GAME("Game has been unpaused!"));
                    System.out.println(GAME("Turn " + turnNumber + "!"));
                    System.out.println(GAME("Team 1: " + team1 + " - " + team1HP));
                    System.out.println(GAME("Team 2: " + team2 + " - " + team2HP));

                    if (actionable) {
                        playerAction();
                    } else {
                        System.out.println(GAME("Waiting for the other player's actions"));
                    }
                }
            }
        }

    }

    private void playerAction() throws IOException {
        System.out.print(GAME("""
                Pick a dice to roll!
                1. D6
                2. D12
                3. D20
                -\s"""));

        this.inputing = true;

        this.actionInputThread = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            int opt = 0;
            boolean validOp = false;
            while (!validOp) {
                try {
                    opt = scanner.nextInt();
                } catch (InputMismatchException e) {
                    if (opt >= 1 && opt <= 3) {
                        validOp = true;
                    }
                }
            }
            String actionString = "";
            switch (opt) {
                case 1 -> {
                    actionString = GameCodes.ACTION + "," + token + "," + GameCodes.D6;
                }
                case 2 -> {
                    actionString = GameCodes.ACTION + "," + token + "," + GameCodes.D12;
                }
                case 3 -> {
                    actionString = GameCodes.ACTION + "," + token + "," + GameCodes.D20;
                }
            }
            try {
                gameSocket.write(ByteBuffer.wrap(actionString.getBytes()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        actionInputThread.start();
    }

    public static void main(String[] args) throws IOException {
        Client client = new Client(args[0], args[1]);
        client.run();
    }

    public static void launch() throws IOException {
        Client client = new Client("localhost", "9000");
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
        try {
            this.serverSocket.write(ByteBuffer.wrap(String.join(",", creds).getBytes()));
        } catch (IOException e) {
            System.out.println(ERROR("Server went offline, exiting!"));
            System.exit(0);
        }

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int bytesRead = 0;
        try {
            bytesRead = this.serverSocket.read(buffer);
        } catch (SocketException e) {
            System.out.println(ERROR("Server went offline, exiting!"));
            System.exit(0);
        }
        if (bytesRead == -1) {
            return false;
        }
        String rawMessage = new String(buffer.array()).trim();
        List<String> message = List.of(rawMessage.split(","));

        if (ServerCodes.OK == ServerCodes.valueOf(message.get(0))) {
            if (opt == 1) {
                System.out.println(SUCCESS("Login successful!"));
            } else {
                System.out.println(SUCCESS("Register successful!"));
            }
            this.token = message.get(1);
            this.user = creds.get(1);
        } else if (ServerCodes.REC == ServerCodes.valueOf(message.get(0))) {
            System.out.println(SUCCESS("Reconnected successfully!"));
            if (message.size() > 2) {
                if (message.get(2).equals((ServerCodes.Q).toString())) {
                    this.gamemode = ServerCodes.valueOf(message.get(3));
                    this.reconnected = ServerCodes.Q;
                } else if (message.get(2).equals((ServerCodes.G).toString())) {
                    this.gamePort = Integer.parseInt(message.get(3));
                    this.reconnected = ServerCodes.G;
                }
            }

            this.token = message.get(1);
            this.user = creds.get(1);
        } else {
            System.out.println(ERROR("Login/register failed!"));
            return logInRegister();
        }
        return true;
    }

    private int chooseGameMode() throws IOException {
        if (this.reconnected == ServerCodes.Q) {
            return search(String.valueOf(this.gamemode));
        }

        int opt = gameModeSelection(consoleInput);

        switch (opt) {
            case 1, 2:
                this.gamemode = ServerCodes.valueOf("N" + opt);
                return search("N" + opt);
            case 3, 4:
                this.gamemode = ServerCodes.valueOf("R" + (opt - 2));
                return search("R" + (opt - 2));
            case 5:
                serverSocket.write(ByteBuffer.wrap(ServerCodes.DC.toString().getBytes()));
                disconnect(this.serverSocket);
                return -1;
        }
        return -1;
    }

    private void disconnect(SocketChannel socket) throws IOException {
        socket.close();
    }

    private int search(String gamemode) throws IOException {
        System.out.println(GAME("Searching..."));
        if (this.reconnected != ServerCodes.Q) {
            String req = gamemode + "," + this.token;
            try {
                serverSocket.write(ByteBuffer.wrap(req.getBytes()));
            } catch (IOException e) {
                System.out.println(ERROR("Server went offline, exiting!"));
                System.exit(0);
            }
        } else {
            this.reconnected = null;
        }

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int bytesRead = 0;
        try {
            bytesRead = this.serverSocket.read(buffer);
        } catch (SocketException e) {
            System.out.println(ERROR("Server went offline, exiting!"));
            System.exit(0);
        }
        if (bytesRead == -1) {
            return bytesRead;
        }

        String rawMessage = new String(buffer.array()).trim();
        List<String> result = List.of(rawMessage.split(","));
        ServerCodes code = ServerCodes.valueOf(result.get(0));
        String port = result.get(1);

        if (code == ServerCodes.GF) {
            System.out.println(SUCCESS("Game found! Waiting for lobby..."));
            return Integer.parseInt(port);
        } else {
            System.out.println(ERROR("Something went wrong!"));
        }
        return bytesRead;
    }

    private int gameModeSelection(Scanner consoleInput) {
        System.out.print(GAME("Welcome " + this.user + "!\n" +
                "What would you like to do?\n" +
                "1. 1v1 Normal\n" +
                "2. 2v2 Normal\n" +
                "3. 1v1 Ranked\n" +
                "4. 2v2 Ranked\n" +
                "5. Quit\n" +
                "- "));
        int opt = consoleInput.nextInt();

        switch (opt) {
            case 1, 2, 3, 4, 5 -> {
                return opt;
            }
            default -> {
                System.out.println(ERROR("Invalid input, try again!"));
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
        System.out.print(GAME("Username: "));
        String username = consoleInput.next();

        return username;
    }

    private String getPassword(Scanner consoleInput) {
        System.out.print(GAME("Password: "));
        String password = consoleInput.next();

        //TODO: Change password lengths back to just < 8
        if (password.length() < 8 && password.length() > 1) {
            System.out.println(ERROR("Invalid password, try again!"));
            return getPassword(consoleInput);
        }

        return password;
    }


    private int logInRegisterSelection(Scanner consoleInput) {
        System.out.print(GAME("1. Log In\n2. Register\n3. Quit\n- "));

        int opt = consoleInput.nextInt();

        switch (opt) {
            case 1, 2, 3 -> {
                return opt;
            }
            default -> {
                System.out.println(ERROR("Invalid input, try again!"));
                return logInRegisterSelection(consoleInput);
            }
        }
    }


}