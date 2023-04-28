package server;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.KeyStore;
import java.util.*;

/**
 * This program demonstrates a simple TCP/IP socket server.
 *
 * @author www.codejava.net
 */
public class Server {

    private Selector selector;
    private Map<SocketChannel, String> clients;
    private Authentication auth;
    private SocketChannel matchmaking;
    private Matchmaking mmServer;

    public Server() throws IOException {
        // Start matchmaking server
        this.mmServer = new Matchmaking();

        new Thread(() -> {
            System.out.println("Started Matchmaking thread!");
            try {
                mmServer.run();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).start();

        // Start main server
        this.auth = new Authentication();
        this.clients = new HashMap<>();
        this.selector = Selector.open();

        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress("localhost" , 9000));
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("Server is listening on port 9000");

        // Connect to matchmaking server
        this.matchmaking = SocketChannel.open(new InetSocketAddress("localhost", 9001));
        this.matchmaking.configureBlocking(true);
    }

    public void run() throws IOException {
        // Create a thread to check for matches and creates separate threads for them from a thread pool
        new Thread(() -> {
            try {
                while (true) {
                    checkMatchmaking();
                }
            } catch (IOException e) {
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

    private void checkMatchmaking() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int bytesRead = matchmaking.read(buffer);
        System.out.println("bytesRead = " + bytesRead);
        if (bytesRead == 0) return;

        if (bytesRead == -1) {
            matchmaking.close();
            return;
        }

        String rawMessage = new String(buffer.array()).trim();
        List<String> message = List.of(rawMessage.split(","));

        ServerCodes code = ServerCodes.valueOf(message.get(0));

        switch (code) {
            case N1:
                List<String> players = Arrays.asList(message.get(1), message.get(2));
                alertPlayers(players);
                break;
            case N2:
            case R1:
            case R2:
            default:
        }
    }

    private void alertPlayers(List<String> players) throws IOException {
        for (String player: players) {
            for (Map.Entry<SocketChannel, String> socketPlayer: clients.entrySet()) {
                if (socketPlayer.getValue().equals(player)) {
                    write(socketPlayer.getKey(), String.valueOf(ServerCodes.OK));
                }
            }
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
                    clients.put(socketChannel, message.get(1));
                    write(socketChannel, response);
                } else {
                    String response = String.valueOf(ServerCodes.ERR);
                    write(socketChannel, response);
                }
                break;
            case REG:
                if (registerAttempt(message.get(1), message.get(2))) {
                    String response = String.valueOf(ServerCodes.OK);
                    clients.put(socketChannel, message.get(1));
                    write(socketChannel, response);
                } else {
                    String response = String.valueOf(ServerCodes.ERR);
                    write(socketChannel, response);
                }
            case N1:
                String user = clients.get(socketChannel);
                String elo = auth.getPlayerElo(user);
                joinMatchmaking(ServerCodes.N1, user, elo);
            default:
                break;
        }
    }

    private void joinMatchmaking(ServerCodes code, String user, String elo) throws IOException {
        String playerInfo = code + "," + user + "," + elo;
        this.matchmaking.write(ByteBuffer.wrap(playerInfo.getBytes()));
    }

    private void write(SocketChannel socketChannel, String response) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(response.getBytes());
        socketChannel.write(buffer);
    }

    private void disconnect(SocketChannel socketChannel) throws IOException {
        String clientName = clients.get(socketChannel);
        clients.remove(socketChannel);
        System.out.println("Client " + clientName + " disconnected");
        socketChannel.close();
    }

    private void accept(SelectionKey key) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel socketChannel = serverSocketChannel.accept();
        if (socketChannel == null) return;
        socketChannel.configureBlocking(false);
        socketChannel.register(selector, SelectionKey.OP_READ);
        this.clients.put(socketChannel, "");
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
        return auth.auth(username, password);
    }

    private boolean registerAttempt(String username, String password) {

        return auth.create(username, password);
    }
}