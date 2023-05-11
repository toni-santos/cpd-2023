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
import java.util.*;
import java.util.stream.Collectors;

public class Game implements Runnable {

    private final SocketChannel mainServerSocket;
    private List<Player> players;
    private Map<String, Player> playerTokenMap;
    private String port;
    private String serverPrefix;
    private Selector selector;
    private ServerSocketChannel serverSocketChannel;
    private ServerCodes gamemode;
    private boolean gameOver = false;
    private List<Player> winner;
    private List<Player> loser;
    private int turnCount = 1, readyCount, actionCount;
    private List<Player> team1 = new ArrayList<>();
    private List<Player> team2 = new ArrayList<>();
    private List<Player> playersReady = new ArrayList<>();
    private int team1HP, team2HP;
    private boolean error;

    public Game(List<Player> players, String port, ServerCodes gamemode) throws IOException {
        this.players = players;
        this.port = port;
        this.gamemode = gamemode;
        this.selector = Selector.open();
        this.serverPrefix = "[" + port + "]";

        switch (gamemode) {
            case N1, R1 -> {
                this.readyCount = 2;
                this.team1 = Arrays.asList(players.get(0));
                this.team2 = Arrays.asList(players.get(1));
                this.team1HP = 10;
                this.team2HP = 10;
            }
            case N2, R2 -> {
                this.readyCount = 4;
                this.team1 = Arrays.asList(players.get(0), players.get(1));
                this.team2 = Arrays.asList(players.get(2), players.get(3));
                this.team1HP = 20;
                this.team2HP = 20;
            }
        }

        this.serverSocketChannel = ServerSocketChannel.open();
        this.serverSocketChannel.bind(new InetSocketAddress("localhost" , Integer.parseInt(port)));
        this.serverSocketChannel.configureBlocking(false);
        this.serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        this.mainServerSocket = SocketChannel.open(new InetSocketAddress("localhost", 9000));
        this.mainServerSocket.configureBlocking(true);

        this.playerTokenMap = players.stream().collect(Collectors.toMap(Player::getToken, player -> player));

        System.out.println(serverPrefix + " Started game server.");
        System.out.print(serverPrefix + " Players:");
        for (Player p : players) {
            System.out.print(" " + p.getPlayer());
        }
        System.out.println();
    }

    @Override
    public void run() {
        while (!gameOver) {
            try {
                selector.select();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            Set<SelectionKey> keys = selector.selectedKeys();
            for (SelectionKey key : keys) {
                if (!key.isValid()) {
                    continue;
                }
                if (key.isAcceptable()) {
                    try {
                        System.out.println("Acceptable");
                        accept(key);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else if (key.isReadable()) {
                    try {
                        System.out.println("Readable");
                        read(key);
                        System.out.println("after game read");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        System.out.println(serverPrefix + " Game over!");
        closeServer();
    }

    private void closeServer() {
        String endGameString;
        if (this.error) {
            endGameString = ServerCodes.GG + "," + ServerCodes.ERR + "," + this.port;
        } else {
            String winnerStr = String.join("-", this.winner.stream().map(Player::getPlayer).toList());
            String loserStr = String.join("-", this.loser.stream().map(Player::getPlayer).toList());
            endGameString = ServerCodes.GG + "," + this.gamemode + "," + this.port + "," + winnerStr + "," + loserStr;
        }

        try {
            serverSocketChannel.close();
            selector.selectNow();
            write(mainServerSocket, endGameString);
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

        System.out.println(serverPrefix + " Player connected to game on socket " + socketChannel.getRemoteAddress());
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

        GameCodes code = GameCodes.valueOf(message.get(0));
        String token = message.get(1);

        switch (code) {
            case ERR:
                System.out.println(serverPrefix + " FATAL ERROR - CLOSING SERVER");
                this.error = true;
                endGame(null, null);
                break;
            case READY:
                if (verifyToken(token)) {
                    Player player = players.stream().filter(p -> p.getToken().equals(token)).toList().get(0);
                    player.setGameChannel(socketChannel);
                    if (!playersReady.contains(player)) playersReady.add(player);
                    if (playersReady.size() == readyCount) {
                        List<String> team1Names = team1.stream().map(Player::getPlayer).toList();
                        List<String> team2Names = team2.stream().map(Player::getPlayer).toList();
                        String team1String = String.join(",", team1Names);
                        String team2String = String.join(",", team2Names);
                        String startMessage = GameCodes.START + "," + team1String + "," + team2String;
                        broadcast(startMessage, players);
                        String turnMessage = GameCodes.TURN + "," + turnCount;
                        broadcast(turnMessage, players);
                    }
                }
                break;
            case ACTION:
                if (verifyToken(token)) {
                    GameCodes actionType = GameCodes.valueOf(message.get(2));
                    resolveAction(actionType, token);
                    switch (gamemode) {
                        case N1, R1 -> {
                            if (actionCount == 2) {
                                newTurn();
                            }
                        }
                        case N2, R2 -> {
                            if (actionCount == 4) {
                                newTurn();
                            }
                        }
                    }
                }
                break;
            default:
                break;
        }
    }

    private void newTurn() throws IOException {
        if (team1HP <= 0) {
            System.out.println("team 2 wins");
            endGame(team2, team1);
        } else if (team2HP <= 0) {
            System.out.println("team 1 wins");
            endGame(team1, team2);
        } else {
            turnCount++;
            String turnMessage = GameCodes.TURN + "," + turnCount;
            broadcast(turnMessage, players);
            System.out.println("after broadcast turn");
        }
    }

    private void resolveAction(GameCodes actionType, String token) throws IOException {
        System.out.println("hi");
        Random random = new Random();
        int damage = 0, selfdamage = 0;

        switch (actionType) {
            case D6 -> {
                damage = random.nextInt(6) + 1;
                selfdamage = 6 - damage;
            }
            case D12 -> {
                damage = random.nextInt(12) + 1;
                selfdamage = 12 - damage;
            }
            case D20 -> {
                damage = random.nextInt(20) + 1;
                selfdamage = 20 - damage;
            }
        }

        System.out.println("damage = " + damage);
        System.out.println("selfdamage = " + selfdamage);
        Player player = playerTokenMap.get(token);
        System.out.println(player);
        if (team1.contains(player)) {
            team2HP -= damage;
            team1HP -= selfdamage;
        } else {
            team1HP -= damage;
            team2HP -= selfdamage;
        }
        String update = GameCodes.UPDATE + "," + player.getPlayer() + "," + damage + "," + team1HP + "," + team2HP;
        System.out.println(update);
        broadcast(update, players);

        actionCount++;
    }

    private boolean verifyToken(String token) {
        List<Player> filtered = players.stream().filter(player -> player.getToken().equals(token)).toList();
        return filtered.size() == 1;
    }

    private void endGame(List<Player> winner, List<Player> loser) throws IOException {
        if (this.error) {
            for (Player p: players) {
                disconnect(p.getSocketChannel());
            }
            this.gameOver = true;
            return;
        }
        
        try {
            for (Player p: players) {
                if (winner.contains(p)) {
                    write(p.getGameChannel(), GameCodes.GG + "," + GameCodes.W);
                } else {
                    write(p.getGameChannel(), GameCodes.GG + "," + GameCodes.L);
                }
                disconnect(p.getGameChannel());
            }
            this.winner = winner;
            this.loser = loser;
            this.gameOver = true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void broadcast(String message, List<Player> channels) throws IOException {
        for (Player player : channels) {
            ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());
            player.getGameChannel().write(buffer);
        }
    }

    private void write(SocketChannel socketChannel, String response) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(response.getBytes());
        socketChannel.write(buffer);
    }

    private void disconnect(SocketChannel socketChannel) throws IOException {
        System.out.println(serverPrefix + " Client player disconnected");
        socketChannel.close();
    }
}
