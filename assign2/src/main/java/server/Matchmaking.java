package server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Matchmaking {
    private Selector selector;
    private SocketChannel mainServer;
    private List<Player> normal1v1 = new ArrayList<Player>();

    public Matchmaking() throws IOException {
        this.selector = Selector.open();

        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress("localhost", 9001));
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("Started matchmaking server on port 9001");
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
                    read();
                }
            }

            if (this.normal1v1.size() >= 2) {
                gameFound(normal1v1, ServerCodes.N1);
            }
        }
    }

    private void gameFound(List<Player> game, ServerCodes gamemode) throws IOException {
        String player1 = game.get(0).getPlayer();
        String player2 = game.get(1).getPlayer();
        game.remove(0);
        game.remove(0);
        String response = gamemode + "," +  player1 + "," + player2;
        mainServer.write(ByteBuffer.wrap(response.getBytes()));
    }

    private void read() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1024);

        int bytesRead = this.mainServer.read(buffer);
        if (bytesRead == -1) {
            return;
        }

        String rawMessage = new String(buffer.array()).trim();
        List<String> result  = List.of(rawMessage.split(","));
        ServerCodes gamemode = ServerCodes.valueOf(result.get(0));
        Player newPlayer = new Player(result.get(1), result.get(2), gamemode, null, null);

        switch (gamemode) {
            case N1:
                this.normal1v1.add(newPlayer);
            case N2:
            case R1:
            case R2:
            default:
                return;
        }

    }

    private void accept(SelectionKey key) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel socketChannel = serverSocketChannel.accept();
        if (socketChannel == null) return;
        socketChannel.configureBlocking(false);
        socketChannel.register(selector, SelectionKey.OP_READ);
        this.mainServer = socketChannel;
        System.out.println("Main Server connected on" + socketChannel.getRemoteAddress());
    }
}
