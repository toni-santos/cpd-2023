package game;

import server.Player;
import server.ServerCodes;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Set;

public class Game implements Runnable {

    private final SocketChannel mainServerSocket;
    private List<Player> players;
    private String port;
    private boolean talked = false;
    private Selector selector;
    private ServerSocketChannel serverSocketChannel;
    private ServerCodes gamemode;
    private boolean gameOver = false;
    private String winner;
    private String loser;

    public Game(List<Player> players, String port, ServerCodes gamemode) throws IOException {
        this.players = players;
        this.port = port;
        this.gamemode = gamemode;
        this.selector = Selector.open();

        this.serverSocketChannel = ServerSocketChannel.open();
        this.serverSocketChannel.bind(new InetSocketAddress("localhost" , Integer.parseInt(port)));
        this.serverSocketChannel.configureBlocking(false);
        this.serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        this.mainServerSocket = SocketChannel.open(new InetSocketAddress("localhost", 9000));
        this.mainServerSocket.configureBlocking(true);

        System.out.print("Started game server on port: " + port + "\nPlayers:");
        for (Player p : players) {
            System.out.print(" " + p.getPlayer());
        }
        System.out.println();
    }

    @Override
    public void run() {
        while (true) {
            if (gameOver) {
                break;
            }
            try {
                selector.select();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            Set<SelectionKey> keys = selector.selectedKeys();
            for (SelectionKey key : keys) {
                if (key == null) continue;
                if (key.isAcceptable()) {
                    try {
                        accept(key);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else if (key.isReadable()) {
                    try {
                        read(key);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        String endGameString = ServerCodes.GG + "," + this.port + "," + this.winner + "," + this.loser;
        try {
            serverSocketChannel.close();
            selector.selectNow();
            write(mainServerSocket, endGameString);
            System.out.println(endGameString);
            mainServerSocket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void accept(SelectionKey key) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel socketChannel = serverSocketChannel.accept();
        if (socketChannel == null) return;
        socketChannel.configureBlocking(false);
        socketChannel.register(selector, SelectionKey.OP_READ);

        System.out.println("Player connected to game (" + this.port + ") on socket: " + socketChannel.getRemoteAddress());
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
        System.out.println(message);
        if (talked) {
            endGame(players.get(0), players.get(1));
        }
        talked = true;
        // TODO: do game stuff now OuO
        /*
        ServerCodes code = ServerCodes.valueOf(message.get(0));

        switch (code) {
            default:
                System.out.println(code);
                break;
        }
        */
    }

    private void endGame(Player winner, Player loser) {
        try {
            this.winner = winner.getPlayer();
            this.loser = loser.getPlayer();

            for (Player p: players) {
                disconnect(p.getSocketChannel());
            }
            this.gameOver = true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    private void write(SocketChannel socketChannel, String response) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(response.getBytes());
        socketChannel.write(buffer);
    }

    private void disconnect(SocketChannel socketChannel) throws IOException {
        System.out.println("Client player disconnected");
        socketChannel.close();
    }
}
