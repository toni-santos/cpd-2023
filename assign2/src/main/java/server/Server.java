package server;

import game.Game;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * This program demonstrates a simple TCP/IP socket server.
 *
 * @author www.codejava.net
 */
public class Server {

    private static final ReadWriteLock clientsLock = new ReentrantReadWriteLock();
    private static final ReadWriteLock N1Lock = new ReentrantReadWriteLock();
    private static final ReadWriteLock runningGamesLock = new ReentrantReadWriteLock();
    private static final ReadWriteLock gamePortsLock = new ReentrantReadWriteLock();
    private Selector selector;
    private Map<SocketChannel, String> clients;
    private Authentication auth;
    private SocketChannel matchmaking;
    private Matchmaking mmServer;
    private ExecutorService threadPool;
    private List<Player> normal1v1 = new ArrayList<>();
    private List<String> gamePorts = new ArrayList<>();
    private List<String> runningGames = new ArrayList<>();

    public Server() throws IOException {
        // Start main server
        this.auth = new Authentication();
        this.clients = new HashMap<>();
        this.selector = Selector.open();

        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress("localhost" , 9000));
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("Server is listening on port 9000");

        // Start thread pool
        int maxGames = 5;

        this.threadPool = Executors.newFixedThreadPool(maxGames);

        for (int i = 0; i < maxGames; i++) {
            gamePorts.add(String.valueOf(10000+i));
        }
    }

    public void run() throws IOException {
        // Create a thread to check for matches and creates separate threads for them from a thread pool
        new Thread(() -> {
            try {
                checkMatchmaking();
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).start();

        while (true) {
            selector.select();
            Set<SelectionKey> keys = selector.selectedKeys();
            for (SelectionKey key : keys) {
                 if (key == null) continue;
                 if (key.isAcceptable()) {
                     accept(key);
                 } else if (key.isReadable()) {
                     read(key);
                 }
            }
        }
    }

    private void checkMatchmaking() throws IOException, InterruptedException {
        while (true) {
            boolean N1Game = false, N2Game, R1Game, R2Game;
            List<Player> N1Players = new ArrayList<>();
            N1Lock.readLock().lock();
            try {
                if (normal1v1.size() >= 2) {
                    N1Game = true;
                    Player player1 = normal1v1.get(0);
                    Player player2 = normal1v1.get(1);
                    N1Players = Arrays.asList(player1, player2);
                }
            } finally {
                N1Lock.readLock().unlock();
            }

            if (N1Game) {
                N1Lock.writeLock().lock();
                try {
                    normal1v1.remove(0);
                    normal1v1.remove(0);
                } finally {
                    N1Lock.writeLock().unlock();
                }
                startGame(ServerCodes.N1, N1Players);
            }
        }
    }

    private void startGame(ServerCodes gamemode, List<Player> playerList) throws InterruptedException, IOException {
        switch (gamemode) {
            case N1 -> {
                // TODO: do this better
                gamePortsLock.readLock().lock();
                try {
                    while (gamePorts.size() == 0) {
                        Thread.sleep(500);
                    }
                } finally {
                    gamePortsLock.readLock().unlock();
                }
                String port = gamePorts.get(0);

                gamePortsLock.writeLock().lock();
                try {
                    gamePorts.remove(0);
                } finally {
                    gamePortsLock.writeLock().unlock();
                }

                Game game = new Game(playerList, port);
                threadPool.submit(game);

                runningGamesLock.writeLock().lock();
                try {
                    runningGames.add(port);
                } finally {
                    runningGamesLock.writeLock().unlock();
                }

                alertGameFound(playerList, port);
            }
        }
    }

    private void alertGameFound(List<Player> players, String port) throws IOException {
        String str = ServerCodes.GF + "," + port;
        for (Player player: players) {
            write(player.getSocketChannel(), str);
        }
    }

    private void read(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int bytesRead = 0;

        try {
            bytesRead = socketChannel.read(buffer);
        } catch (SocketException e) {
            socketChannel.close();
            return;
        }

        if (bytesRead == -1) {
            disconnect(socketChannel);
            return;
        }

        if (bytesRead == 0) {
            return;
        }

        String rawMessage = new String(buffer.array()).trim();
        List<String> message = List.of(rawMessage.split(","));
        ServerCodes code = ServerCodes.valueOf(message.get(0));

        switch (code) {
            case LOG:
                if (logInAttempt(message.get(1), message.get(2))) {
                    String response = String.valueOf(ServerCodes.OK);
                    clientsLock.writeLock().lock();
                    try {
                        clients.put(socketChannel, message.get(1));
                    } finally {
                        clientsLock.writeLock().unlock();
                    }
                    write(socketChannel, response);
                } else {
                    String response = String.valueOf(ServerCodes.ERR);
                    write(socketChannel, response);
                }
                break;
            case REG:
                if (registerAttempt(message.get(1), message.get(2))) {
                    String response = String.valueOf(ServerCodes.OK);
                    clientsLock.writeLock().lock();
                    try {
                        clients.put(socketChannel, message.get(1));
                    } finally {
                        clientsLock.writeLock().unlock();
                    }
                    write(socketChannel, response);
                } else {
                    String response = String.valueOf(ServerCodes.ERR);
                    write(socketChannel, response);
                }
                break;
            case N1:
                clientsLock.readLock().lock();
                try {
                    String user = clients.get(socketChannel);
                    String elo = auth.getPlayerElo(user);

                    // Lock 1v1 list
                    N1Lock.writeLock().lock();
                    try {
                        normal1v1.add(new Player(user, elo, ServerCodes.N1, socketChannel));
                    } finally {
                        // unlock 1v1 list
                        N1Lock.writeLock().unlock();
                    }
                } finally {
                    clientsLock.readLock().unlock();
                }
                break;
            case GG:
                System.out.println("Game ended");

                String port = message.get(1);
                String winner = message.get(2);
                String loser = message.get(3);

                gamePortsLock.writeLock().lock();
                try {
                    gamePorts.add(port);
                } finally {
                    gamePortsLock.writeLock().unlock();
                }

                runningGamesLock.writeLock().lock();
                try {
                    runningGames.remove(port);
                } finally {
                    runningGamesLock.writeLock().unlock();
                }

                auth.setPlayerElo(winner, String.valueOf(Integer.parseInt(auth.getPlayerElo(winner)) + 20));
                auth.setPlayerElo(loser, String.valueOf(Math.max(0, Integer.parseInt(auth.getPlayerElo(loser)) - 20)));
                break;
            default:
                break;
        }
    }

    private void write(SocketChannel socketChannel, String response) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(response.getBytes());
        socketChannel.write(buffer);
    }

    private void disconnect(SocketChannel socketChannel) throws IOException {
        String clientName;
        clientsLock.readLock().lock();
        clientsLock.writeLock().lock();
        try {
            clientName = clients.get(socketChannel);
            clients.remove(socketChannel);
        } finally {
            clientsLock.readLock().unlock();
            clientsLock.writeLock().unlock();
        }
        System.out.println("Client " + clientName + " disconnected");
        socketChannel.close();
    }

    private void accept(SelectionKey key) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel socketChannel = serverSocketChannel.accept();
        if (socketChannel == null) return;
        socketChannel.configureBlocking(false);
        socketChannel.register(selector, SelectionKey.OP_READ);
        clientsLock.writeLock().lock();
        try {
            this.clients.put(socketChannel, "");
        } finally {
            clientsLock.writeLock().unlock();
        }
        System.out.println("Client " + socketChannel.getRemoteAddress() + " connected");
    }

    private void broadcast(String message, SocketChannel excludeChannel) throws IOException {
        for (SocketChannel socketChannel : clients.keySet()) {
            if (socketChannel != excludeChannel) {
                ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());
                socketChannel.write(buffer);
            }
        }
    }

    public static void launch() throws IOException {
        Server server = new Server();

        server.run();
    }

    private boolean logInAttempt(String username, String password) {
        clientsLock.readLock().lock();
        try {
            for (Map.Entry<SocketChannel, String> socketPlayer: clients.entrySet()) {
                if (socketPlayer.getValue().equals(username)) {
                    return false;
                }
            }
        } finally {
            clientsLock.readLock().unlock();
        }

        return auth.auth(username, password);
    }

    private boolean registerAttempt(String username, String password) {

        return auth.create(username, password);
    }
}