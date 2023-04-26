package server;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;

/**
 * This program demonstrates a simple TCP/IP socket server.
 *
 * @author www.codejava.net
 */
public class Server {

    private Selector selector;
    private Map<SocketChannel, String> clients;

    public Server() throws IOException {
        int PORT = 9000;
        this.clients = new HashMap<>();
        this.selector = Selector.open();
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(PORT));
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("Server is listening on port " + PORT);
    }

    public void run() throws IOException {
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

    private void read(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int bytesRead = socketChannel.read(buffer);
        if (bytesRead == -1) {
            disconnect(socketChannel);
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
                    write(key, response);
                } else {
                    System.out.println("error :D");
                    String response = String.valueOf(ServerCodes.ERR);
                    write(key, response);
                }
                break;
            case REG:
                if (registerAttempt(message.get(1), message.get(2))) {
                    String response = String.valueOf(ServerCodes.OK);
                    clients.put(socketChannel, message.get(1));
                    write(key, response);
                } else {
                    String response = String.valueOf(ServerCodes.ERR);
                    System.out.println(response);
                    write(key, response);
                }
            default:
                break;
        }
    }

    private void write(SelectionKey key, String response) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(response.getBytes());
        SocketChannel socketChannel = (SocketChannel) key.channel();

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
        Authentication auth = new Authentication();
        return auth.auth(username, password);
    }

    private boolean registerAttempt(String username, String password) {
        Authentication auth = new Authentication();
        return auth.create(username, password);
    }
}