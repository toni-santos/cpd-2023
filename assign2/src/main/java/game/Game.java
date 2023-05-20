package game;

import server.Player;
import server.ServerCodes;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static java.lang.System.currentTimeMillis;
import static utils.Log.*;

public class Game implements Runnable {

    private static final ReadWriteLock timeOutLock = new ReentrantReadWriteLock();
    private static final ReadWriteLock gameOverLock = new ReentrantReadWriteLock();
    private final SocketChannel mainServerSocket;
    private List<Player> players;
    private Map<String, Player> playerTokenMap;
    private Map<String, Double> timeOutList = new HashMap<>();
    private String port;
    private String serverPrefix;
    private Selector selector;
    private ServerSocketChannel serverSocketChannel;
    private ServerCodes gamemode;
    private List<Player> winner;
    private List<Player> loser;
    private int turnCount = 1, readyCount;
    private List<String> played = new ArrayList<>();
    private List<Player> team1 = new ArrayList<>();
    private List<Player> team2 = new ArrayList<>();
    private List<Player> playersReady = new ArrayList<>();
    private int team1HP, team2HP;
    private volatile boolean gameOver = false;
    private boolean error;
    private double timeOut = 40.0;


    public Game(List<Player> players, ServerCodes gamemode) throws IOException {
        this.players = players;
        this.gamemode = gamemode;
        this.selector = Selector.open();

        switch (gamemode) {
            case N1, R1 -> {
                this.readyCount = 2;
                this.team1 = Arrays.asList(players.get(0));
                this.team2 = Arrays.asList(players.get(1));
                this.team1HP = 20;
                this.team2HP = 20;
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
        this.serverSocketChannel.bind(new InetSocketAddress("localhost" , 0));
        this.serverSocketChannel.configureBlocking(false);
        this.serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        this.port = String.valueOf(serverSocketChannel.socket().getLocalPort());
        this.serverPrefix = "[" + port + "]";

        this.mainServerSocket = SocketChannel.open(new InetSocketAddress("localhost", 9000));
        this.mainServerSocket.configureBlocking(true);

        this.playerTokenMap = players.stream().collect(Collectors.toMap(Player::getToken, player -> player));

        System.out.println(SUCCESS(serverPrefix + " Started game server"));
    }

    @Override
    public void run() {
        Thread timeoutThread = new Thread(() -> {
            while (!gameOver) {
                checkTimeouts();
            }
        });
        timeoutThread.start();

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
                        accept(key);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else if (key.isReadable()) {
                    try {
                        read(key);
                    } catch (IOException e) {
                        System.out.println(e);
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        System.out.println(REGULAR(serverPrefix + " Game over"));
        closeServer();
    }

    private void checkTimeouts() {
        timeOutLock.readLock().lock();
        try {
            if (timeOutList.size() != 0) {
                for (Map.Entry<String, Double> entry: timeOutList.entrySet()) {
                    double timePassed = (double) currentTimeMillis()/1000 -  entry.getValue()/1000;
                    if (timePassed >= timeOut) {
                        for (Player p: players) {
                            if (!p.getName().equals(entry.getKey()) && !timeOutList.containsKey(p.getName())) {
                                try {
                                    write(p.getGameChannel(), GameCodes.CANCEL.toString());
                                    disconnect(p.getGameChannel());
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }

                        this.gameOver = true;
                        return;
                    }
                }
            }
        } finally {
            timeOutLock.readLock().unlock();
        }
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void closeServer() {
        String endGameString;
        if (this.error) {
            endGameString = ServerCodes.GG + "," + ServerCodes.ERR + "," + this.port + "," + String.join(",", this.players.stream().map(Player::getName).toList());
        } else {
            String winnerStr = String.join("-", this.winner.stream().map(Player::getName).toList());
            String loserStr = String.join("-", this.loser.stream().map(Player::getName).toList());
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

        System.out.println(SUCCESS(serverPrefix + " Player connected to game on socket " + socketChannel.getRemoteAddress()));
    }

    private void read(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int bytesRead = 0;
        try {
            bytesRead = socketChannel.read(buffer);
        } catch (IOException e) {
            timeOutLock.writeLock().lock();
            timeOutLock.readLock().lock();
            try {
                for (Player p: players) {
                    if (p.getGameChannel() == socketChannel && !timeOutList.containsKey(p.getName())) {
                        // key.cancel();
                        timeOutList.put(p.getName(), (double) currentTimeMillis());

                        List<Player> connectedPlayers = players.stream().filter(player -> {
                            return player.getGameChannel() != socketChannel && !timeOutList.containsKey(player.getName());
                        }).toList();

                        String dcMsg = GameCodes.DISCONNECT.toString();
                        broadcast(dcMsg, connectedPlayers);

                        break;
                    }
                }
            } finally {
                timeOutLock.writeLock().unlock();
                timeOutLock.readLock().unlock();
            }

            return;
        }

        if (bytesRead == -1) {
            timeOutLock.writeLock().lock();
            timeOutLock.readLock().lock();
            try {
                for (Player p: players) {
                    if (p.getGameChannel() == socketChannel && !timeOutList.containsKey(p.getName())) {
                        // key.cancel();
                        timeOutList.put(p.getName(), (double) currentTimeMillis());

                        List<Player> connectedPlayers = players.stream().filter(player -> {
                            return player.getGameChannel() != socketChannel && !timeOutList.containsKey(player.getName());
                        }).toList();

                        String dcMsg = GameCodes.DISCONNECT.toString();
                        broadcast(dcMsg, connectedPlayers);

                        break;
                    }
                }
            } finally {
                timeOutLock.writeLock().unlock();
                timeOutLock.readLock().unlock();
            }

            return;
        }

        if (bytesRead == 0) {
            return;
        }

        String rawMessage = new String(buffer.array()).trim();
        List<String> message = List.of(rawMessage.split(","));
        GameCodes code = GameCodes.valueOf(message.get(0));
        String token = message.get(1);

        List<String> team1Names = team1.stream().map(Player::getName).toList();
        List<String> team2Names = team2.stream().map(Player::getName).toList();
        String team1String = String.join("-", team1Names);
        String team2String = String.join("-", team2Names);

        switch (code) {
            case RECONNECT:
                timeOutLock.writeLock().lock();
                try {
                    timeOutList.remove(message.get(2));
                } finally {
                    timeOutLock.writeLock().unlock();
                }
                boolean continueGame = false;

                timeOutLock.readLock().lock();
                try {
                    if (timeOutList.isEmpty()) {
                        continueGame = true;
                    }
                } finally {
                    timeOutLock.readLock().unlock();
                }

                playerTokenMap.get(token).setGameChannel(socketChannel);
                for (Player p: players) {
                    if (p.getName().equals(message.get(2))) {
                        p.setGameChannel(socketChannel);
                    }

                    String reconnectMsg;
                    if (continueGame) {
                        if (played.contains(p.getToken())) {
                            reconnectMsg = GameCodes.RECONNECT + "," + turnCount + "," + team1String + "," + team2String + "," + team1HP + "," + team2HP + "," + "False";
                        } else {
                            reconnectMsg = GameCodes.RECONNECT + "," + turnCount + "," + team1String + "," + team2String + "," + team1HP + "," + team2HP + "," + "True";
                        }
                    } else {
                        reconnectMsg = GameCodes.RECONNECT + "," + GameCodes.DISCONNECT;
                    }

                    timeOutLock.readLock().lock();
                    try {
                        if (!timeOutList.containsKey(p.getName())) {
                            write(p.getGameChannel(), reconnectMsg);
                        }
                    } finally {
                        timeOutLock.readLock().unlock();
                    }
                }

                break;
            case ERR:
                System.out.println(ERROR(serverPrefix + " FATAL ERROR - CLOSING SERVER"));
                this.error = true;
                endGame(null, null);
                break;
            case READY:
                if (verifyToken(token)) {
                    Player player = players.stream().filter(p -> p.getToken().equals(token)).toList().get(0);
                    player.setGameChannel(socketChannel);
                    if (!playersReady.contains(player)) playersReady.add(player);
                    if (playersReady.size() == readyCount) {
                        String turnMessage = GameCodes.TURN + "," + turnCount + "," + team1HP + "," + team2HP;
                        broadcast(turnMessage, players);
                    }
                }
                break;
            case ACTION:
                if (verifyToken(token) && !played.contains(token)) {
                    played.add(token);
                    GameCodes actionType = GameCodes.valueOf(message.get(2));
                    resolveAction(actionType, token);
                    switch (gamemode) {
                        case N1, R1 -> {
                            if (played.size() == 2) {
                                played.clear();
                                newTurn();
                            }
                        }
                        case N2, R2 -> {
                            if (played.size() == 4) {
                                played.clear();
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
            System.out.println(SUCCESS(serverPrefix + " Team 2 wins"));
            endGame(team2, team1);
        } else if (team2HP <= 0) {
            System.out.println(SUCCESS(serverPrefix + " Team 1 wins"));
            endGame(team1, team2);
        } else {
            turnCount++;
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            String turnMessage = GameCodes.TURN +  "," + turnCount + "," + team1HP + "," + team2HP;
            broadcast(turnMessage, players);
        }
    }

    private void resolveAction(GameCodes actionType, String token) throws IOException {
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

        Player player = playerTokenMap.get(token);
        if (team1.contains(player)) {
            team2HP -= damage;
            team1HP -= selfdamage;
        } else {
            team1HP -= damage;
            team2HP -= selfdamage;
        }

        // tell the player what they did
        String update = GameCodes.UPDATE + "," + damage;
        write(player.getGameChannel(), update);
    }

    private boolean verifyToken(String token) {
        List<Player> filtered = players.stream().filter(player -> player.getToken().equals(token)).toList();
        return filtered.size() == 1;
    }

    private void endGame(List<Player> winner, List<Player> loser) throws IOException {
        if (this.error) {
            for (Player p: players) {
                write(p.getGameChannel(), GameCodes.CANCEL.toString());
                disconnect(p.getGameChannel());
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
        System.out.println(REGULAR(serverPrefix + " Client player disconnected"));

        socketChannel.close();
    }

    public String getPort() {
        return this.port;
    }
}
