package server;

import game.Game;
import utils.TokenGenerator;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * This program demonstrates a simple TCP/IP socket server.
 *
 * @author www.codejava.net
 */
public class Server {

    private static final ReadWriteLock clientsLock = new ReentrantReadWriteLock();
    private static final ReadWriteLock playerSocketsLock = new ReentrantReadWriteLock();
    private static final ReadWriteLock N1Lock = new ReentrantReadWriteLock();
    private static final ReadWriteLock R1Lock = new ReentrantReadWriteLock();
    private static final ReadWriteLock N2Lock = new ReentrantReadWriteLock();
    private static final ReadWriteLock R2Lock = new ReentrantReadWriteLock();
    private static final ReadWriteLock waitListLock = new ReentrantReadWriteLock();

    private static final ReadWriteLock runningGamesLock = new ReentrantReadWriteLock();
    private static final ReadWriteLock gamePortsLock = new ReentrantReadWriteLock();
    private Object serverLock = new Object();
    private boolean availableGame = true;
    private Selector selector;
    private Map<SocketChannel, Player> clients;
    private Map<String, SocketChannel> playerSockets;
    private Authentication auth;
    private ExecutorService threadPool;
    private List<Player> normal1v1 = new ArrayList<>();
    private List<Player> normal2v2 = new ArrayList<>();

    private List<Player> ranked1v1 = new ArrayList<>();
    private List<Player> ranked2v2 = new ArrayList<>();
    private List<Player> waitingForRanked1v1 = new ArrayList<>();
    private List<Player> waitingForRanked2v2 = new ArrayList<>();
    private List<String> gamePorts = new ArrayList<>();
    private List<String> runningGames = new ArrayList<>();

    public Server() throws IOException {
        // Start main server
        this.auth = new Authentication();
        this.clients = new HashMap<>();
        this.playerSockets = new HashMap<>();
        this.selector = Selector.open();

        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress("localhost" , 9000));
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("Server is listening on port 9000");

        // Start thread pool
        int maxGames = 1;

        this.threadPool = Executors.newFixedThreadPool(maxGames);

        for (int i = 0; i < maxGames; i++) {
            gamePorts.add(String.valueOf(10000+i));
        }
    }

    public void run() throws IOException {
        AtomicBoolean runThread = new AtomicBoolean(true);
        // Create a thread to check for matches and creates separate threads for them from a thread pool
        Thread thread = new Thread(() -> {
            while (runThread.get()) {
                if (!ranked1v1.isEmpty() || !ranked2v2.isEmpty() || !availableGame) {
                    try {
                        checkMatchmaking();
                    } catch (IOException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        thread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            runThread.set(false);
            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }));

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
        boolean N1Game = false, N2Game = false, R1Game = false, R2Game = false;
        List<Player> N1Players = new ArrayList<>();
        List<Player> R1Players = new ArrayList<>();
        List<Player> N2Players = new ArrayList<>();
        List<Player> R2Players = new ArrayList<>();
        N1Lock.readLock().lock();
        R1Lock.readLock().lock();
        N2Lock.readLock().lock();
        R2Lock.readLock().lock();
        waitListLock.writeLock().lock();
        waitListLock.readLock().lock();
        System.out.println("Before try");
        try {
            if (normal1v1.size() >= 2) {
                System.out.println("enough players gaming");
                System.out.println("normal1v1 = " + normal1v1);
                N1Game = true;
                Player player1 = normal1v1.get(0);
                Player player2 = normal1v1.get(1);
                N1Players = Arrays.asList(player1, player2);
            }
            if (normal2v2.size() >= 4) {
                System.out.println("enough players gaming");
                System.out.println("normal2v2 = " + normal2v2);
                N2Game = true;
                Player player1 = normal2v2.get(0);
                Player player2 = normal2v2.get(1);
                Player player3 = normal2v2.get(2);
                Player player4 = normal2v2.get(3);
                N2Players = Arrays.asList(player1, player2, player3, player4);
            }
            if (waitingForRanked1v1.size() >= 2) {
                Player player1 = waitingForRanked1v1.get(0);
                Player player2 = waitingForRanked1v1.get(1);
                R1Game = true;
                R1Players = Arrays.asList(player1, player2);
            }
            else if (ranked1v1.size() >= 2) {
                for (int i = 0; i < ranked1v1.size() - 1; i++) {
                    Player player1 = ranked1v1.get(i);
                    System.out.println("player1.getElo() = " + player1.getElo());
                    for (int j = i + 1; j < ranked1v1.size(); j++) {
                        Player player2 = ranked1v1.get(j);
                        if (waitingForRanked1v1.contains(player2)) continue;
                        System.out.println("player2.getElo() = " + player2.getElo());
                        List<Player> playerArray = Arrays.asList(player1, player2);
                        boolean elosInRange = true;
                        for (Player player: playerArray) {
                            elosInRange = elosInRange && checkEloRange(player, playerArray);
                            System.out.println("elosInRange = " + elosInRange);
                        }
                        if (elosInRange) {
                            System.out.println("Range match");
                            R1Game = true;
                            R1Players = Arrays.asList(player1, player2);
                            for (Player player: R1Players) {
                                if (!waitingForRanked1v1.contains(player)) waitingForRanked1v1.add(player);
                            }
                        }
                        if (R1Game) break;
                    }
                    if (R1Game) break;
                }
            }
            if (waitingForRanked2v2.size() >= 4 && availableGame) {
                Player player1 = waitingForRanked2v2.get(0);
                Player player2 = waitingForRanked2v2.get(1);
                Player player3 = waitingForRanked2v2.get(2);
                Player player4 = waitingForRanked2v2.get(3);
                R2Game = true;
                R2Players = Arrays.asList(player1, player2, player3, player4);
            }
            else if (ranked2v2.size() >= 4) {
                for (int i = 0; i < ranked2v2.size() - 3; i++) {
                    Player player1 = ranked2v2.get(i);
                    System.out.println("player1.getElo() = " + player1.getElo());
                    for (int j = i + 1; j < ranked2v2.size() - 2; j++) {
                        Player player2 = ranked2v2.get(j);
                        Player player3 = ranked2v2.get(j+1);
                        Player player4 = ranked2v2.get(j+2);
                        if (waitingForRanked2v2.contains(player2) || waitingForRanked2v2.contains(player3)
                        || waitingForRanked2v2.contains(player4)) continue;
                        System.out.println("player2.getElo() = " + player2.getElo());
                        List<Player> playerArray = Arrays.asList(player1, player2, player3, player4);
                        boolean elosInRange = true;
                        for (Player player: playerArray) {
                            elosInRange = elosInRange && checkEloRange(player, playerArray);
                            System.out.println("elosInRange = " + elosInRange);
                        }
                        if (elosInRange) {
                            System.out.println("Range match");
                            R2Game = true;
                            R2Players = balanceTeams(playerArray);
                            for (Player player: R2Players) {
                                if (!waitingForRanked2v2.contains(player)) waitingForRanked2v2.add(player);
                            }
                        }
                        if (R2Game) break;
                    }
                    if (R2Game) break;
                }
            }

            updateEloRange(ranked1v1, waitingForRanked1v1);
            updateEloRange(ranked2v2, waitingForRanked2v2);

        } finally {
            N1Lock.readLock().unlock();
            R1Lock.readLock().unlock();
            N2Lock.readLock().unlock();
            R2Lock.readLock().unlock();
            waitListLock.writeLock().unlock();
            waitListLock.readLock().unlock();
        }
        System.out.println("After try");

        if (N1Game) {
            N1Lock.writeLock().lock();
            try {
                startGame(ServerCodes.N1, N1Players);
            } finally {
                N1Lock.writeLock().unlock();
            }
        }
        if (N2Game) {
            N2Lock.writeLock().lock();
            try {
                startGame(ServerCodes.N2, N2Players);
            } finally {
                N2Lock.writeLock().unlock();
            }
        }
        if (R1Game) {
            R1Lock.writeLock().lock();
            waitListLock.writeLock().lock();
            try {
                startGame(ServerCodes.R1, R1Players);
            } finally {
                R1Lock.writeLock().unlock();
                waitListLock.writeLock().unlock();
            }
        }
        if (R2Game){
            R2Lock.writeLock().lock();
            waitListLock.writeLock().lock();
            try {
                startGame(ServerCodes.R2, R2Players);
            } finally {
                R2Lock.writeLock().unlock();
                waitListLock.writeLock().unlock();
            }
        }
    }

    private List<Player> balanceTeams(List<Player> players) {
        int sumTeam1and2 = getTeamEloDifference(players, Arrays.asList(0, 1, 2, 3));
        System.out.println("sumTeam1and2 = " + sumTeam1and2);
        int sumTeam1and3 = getTeamEloDifference(players, Arrays.asList(0, 2, 1, 3));
        System.out.println("sumTeam1and3 = " + sumTeam1and3);
        int sumTeam1and4 = getTeamEloDifference(players, Arrays.asList(0, 3, 1, 2));
        System.out.println("sumTeam1and4 = " + sumTeam1and4);
        if (sumTeam1and2 <= sumTeam1and3 && sumTeam1and2 <= sumTeam1and4) {
            return Arrays.asList(players.get(0), players.get(1), players.get(2), players.get(3));
        }
        else if (sumTeam1and3 <= sumTeam1and2 && sumTeam1and3 <= sumTeam1and4) {
            return Arrays.asList(players.get(0), players.get(2), players.get(1), players.get(3));
        }
        else {
            return Arrays.asList(players.get(0), players.get(3), players.get(1), players.get(2));
        }
    }

    private int getTeamEloDifference(List<Player> players, List<Integer> indices) {
        return Math.abs(Math.abs(players.get(indices.get(0)).getElo() + players.get(indices.get(1)).getElo()) -
                Math.abs(players.get(indices.get(2)).getElo() + players.get(indices.get(3)).getElo()));
    }

    private void updateElo(List<String> winners, List<String> losers) {

        List<Player> winningPlayers = winners.stream().map(playerSockets::get).map(clients::get).toList();
        List<Player> losingPlayers = losers.stream().map(playerSockets::get).map(clients::get).toList();

        double winnerAverageElo = winningPlayers.stream().mapToInt(Player::getElo).average().orElse(0.0);
        System.out.println("winnerAverageElo = " + winnerAverageElo);
        double loserAverageElo = losingPlayers.stream().mapToInt(Player::getElo).average().orElse(0.0);
        System.out.println("loserAverageElo = " + loserAverageElo);
        int eloChange = (int) Math.floor(20 * loserAverageElo / winnerAverageElo);
        System.out.println("eloChange = " + eloChange);

        for (String winner : winners) {
            Player player = clients.get(playerSockets.get(winner));
            auth.setPlayerElo(winner, String.valueOf(Integer.parseInt(auth.getPlayerElo(winner)) +
                    eloChange));
            player.setElo(auth.getPlayerElo(winner));
        }
        for (String loser: losers) {
            Player player = clients.get(playerSockets.get(loser));
            auth.setPlayerElo(loser, String.valueOf(Integer.parseInt(auth.getPlayerElo(loser)) -
                    eloChange));
            player.setElo(auth.getPlayerElo(loser));
        }
    }

    private void updateEloRange(List<Player> playerList, List<Player> waitList) {
        double currentSearchTime = System.currentTimeMillis();
        for (Player player : playerList) {
            if (waitList.contains(player)) continue;
            player.setEloRange((currentSearchTime - player.getSearchTime()) / 100);
            System.out.println("player.getElo() = " + player.getElo());
            System.out.println("Range between " + (player.getElo() - player.getEloRange()) + " and " +
                    (player.getElo() + player.getEloRange()));
        }
    }

    private boolean checkEloRange(Player chosenPlayer, List<Player> list) {
        double lowerBound = chosenPlayer.getElo() - chosenPlayer.getEloRange();
        double upperBound = chosenPlayer.getElo() + chosenPlayer.getEloRange();
        for (Player player: list) {
            if (chosenPlayer.equals(player)) continue;
            if (lowerBound >= player.getElo() || upperBound <= player.getElo()) return false;
        }
        return true;
    }

    private void startGame(ServerCodes gamemode, List<Player> playerList) throws IOException {
        // Wait until a game is available
        if (!availableGame) {
            System.out.println("Unavailable servers!");
            return;
        }

        // Games are available
        String port = gamePorts.get(0);

        // Update list of available game ports
        gamePortsLock.writeLock().lock();
        try {
            gamePorts.remove(0);
        } finally {
            gamePortsLock.writeLock().unlock();
        }

        // Update list of running game ports
        runningGamesLock.writeLock().lock();
        try {
            runningGames.add(port);
        } finally {
            runningGamesLock.writeLock().unlock();
        }

        // Update, if necessary, game availability
        if (gamePorts.size() == 0) {
            System.out.println("no more servers");
            availableGame = false;
        }

        // Create game object and add it to the queue
        Game game = new Game(playerList, port, gamemode);
        threadPool.submit(game);
        System.out.println("Game found!");
        List<Player> list = new ArrayList<>();
        List<Player> waitList = new ArrayList<>();
        ReadWriteLock lock = new ReentrantReadWriteLock();
        System.out.println("normal1v1 = " + normal1v1);
        switch (gamemode) {
            case N1 -> {
                lock = N1Lock;
                lock.readLock().lock();
                list = normal1v1;
            }
            case N2 -> {
                lock = N2Lock;
                lock.readLock().lock();
                list = normal2v2;
            }
            case R1 -> {
                lock = R1Lock;
                lock.readLock().lock();
                list = ranked1v1;
                waitList = waitingForRanked1v1;
            }
            case R2 -> {
                lock = R2Lock;
                lock.readLock().lock();
                list = ranked2v2;
                waitList = waitingForRanked2v2;
            }
        }
        try {
            for (int i = 0; i < playerList.size(); i++) {
                int index = i;
                list.removeIf(obj -> obj.equals(playerList.get(index)));
                if (gamemode == ServerCodes.R1 || gamemode == ServerCodes.R2)
                    waitList.removeIf(obj -> obj.equals(playerList.get(index)));
            }
        } finally {
            lock.readLock().unlock();
        }
        // Tell players to join game
        alertGameFound(playerList, port);
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
        System.out.println("message = " + message);
        ServerCodes code = ServerCodes.valueOf(message.get(0));

        switch (code) {
            case LOG:
                if (logInAttempt(message.get(1), message.get(2))) {
                    String token = TokenGenerator.generateToken();
                    String response = ServerCodes.OK + "," + token;
                    String elo = auth.getPlayerElo(message.get(1));
                    Player player = new Player(message.get(1), elo, null, socketChannel, token);
                    System.out.println("socketChannel.getRemoteAddress().toString() = " + socketChannel.socket().getRemoteSocketAddress().toString());
                    clientsLock.writeLock().lock();
                    playerSocketsLock.writeLock().lock();
                    try {
                        clients.put(socketChannel, player);
                        playerSockets.put(player.getName(), socketChannel);
                    } finally {
                        clientsLock.writeLock().unlock();
                        playerSocketsLock.writeLock().unlock();
                    }
                    write(socketChannel, response);
                    System.out.println("clients = " + clients);
                    System.out.println("playerSockets = " + playerSockets);
                } else {
                    String response = String.valueOf(ServerCodes.ERR);
                    write(socketChannel, response);
                }
                break;
            case REG:
                if (registerAttempt(message.get(1), message.get(2))) {
                    String token = TokenGenerator.generateToken();
                    String response = ServerCodes.OK + "," + token;
                    String elo = auth.getPlayerElo(message.get(1));
                    Player player = new Player(message.get(1), elo, null, socketChannel, token);
                    clientsLock.writeLock().lock();
                    playerSocketsLock.writeLock().lock();
                    try {
                        clients.put(socketChannel, player);
                        playerSockets.put(player.getName(), socketChannel);
                    } finally {
                        clientsLock.writeLock().unlock();
                        playerSocketsLock.writeLock().unlock();
                    }
                    write(socketChannel, response);
                } else {
                    String response = String.valueOf(ServerCodes.ERR);
                    write(socketChannel, response);
                }
                break;
            case DC:
                disconnect(socketChannel);
                break;
            case N1, N2:
                if (verifyToken(socketChannel, message.get(1))) {
                    clientsLock.readLock().lock();
                    try {
                        Player player = clients.get(socketChannel);
                        player.setGamemode(code);

                        N1Lock.writeLock().lock();
                        N2Lock.writeLock().lock();
                        try {
                            if (code == ServerCodes.N1) normal1v1.add(player);
                            else normal2v2.add(player);
                            checkMatchmaking();
                            System.out.println("After check matchmaking");
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        } finally {
                            N1Lock.writeLock().unlock();
                            N2Lock.writeLock().unlock();
                        }
                    } finally {
                        clientsLock.readLock().unlock();
                    }
                } else {
                    System.out.println("INVALID TOKEN FOUND REMOVING CLIENT");
                    disconnect(socketChannel);
                }
                break;
            case R1, R2:
                if (verifyToken(socketChannel, message.get(1))) {
                    clientsLock.readLock().lock();
                    try {
                        Player player = clients.get(socketChannel);
                        player.setGamemode(code);

                        R1Lock.writeLock().lock();
                        R2Lock.writeLock().lock();
                        try {
                            player.setSearchTime();
                            if (code == ServerCodes.R1) {
                                ranked1v1.add(player);
                                ranked1v1.sort(Comparator.comparing(Player::getElo));
                            }
                            else {
                                ranked2v2.add(player);
                                ranked2v2.sort(Comparator.comparing(Player::getEloRange).reversed());
                            }
                        } finally {
                            R1Lock.writeLock().unlock();
                            R2Lock.writeLock().unlock();
                        }
                    } finally {
                        clientsLock.readLock().unlock();
                    }
                } else {
                    System.out.println("INVALID TOKEN FOUND REMOVING CLIENT");
                    disconnect(socketChannel);
                }
                break;
            case GG:
                String port;
                ServerCodes gamemode;

                if (message.get(1).equals(ServerCodes.ERR)) {
                    port = message.get(2);
                } else {
                    System.out.println(message);
                    port = message.get(2);
                    gamemode = ServerCodes.valueOf(message.get(1));
                    if (gamemode == ServerCodes.R1 || gamemode == ServerCodes.R2) {
                        List<String> winners, losers;
                        winners = List.of(message.get(3).split("-"));
                        losers = List.of(message.get(4).split("-"));
                        updateElo(winners, losers);
                    }
                }

                System.out.println("Game ended in port " + port);

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

                if (gamePorts.size() != 0) {
                    System.out.println("New server available");
                    availableGame = true;
                }

                break;
            default:
                break;
        }
    }

    private boolean verifyToken(SocketChannel socketChannel, String token) {
        return clients.get(socketChannel).getToken().equals(token);
    }

    private void write(SocketChannel socketChannel, String response) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(response.getBytes());
        socketChannel.write(buffer);
    }

    private void disconnect(SocketChannel socketChannel) throws IOException {
        String clientName;
        clientsLock.readLock().lock();
        try {
            if (clients.get(socketChannel) != null) {
                clientName = clients.get(socketChannel).getName();
            } else {
                clientName = "[NOT LOGGED IN]";
            }
        } finally {
            clientsLock.readLock().unlock();
        }

        clientsLock.writeLock().lock();
        playerSocketsLock.writeLock().lock();
        try {
            Player player = clients.get(socketChannel);
            System.out.println("player = " + player);
            if (player != null) playerSockets.remove(player.getName());
            clients.remove(socketChannel);
        } finally {
            clientsLock.writeLock().unlock();
            playerSocketsLock.writeLock().unlock();
        }

        System.out.println("Client "+ clientName + " disconnected");
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
            clients.put(socketChannel, null);
        } finally {
            clientsLock.writeLock().unlock();
        }
        System.out.println("Client " + socketChannel.getRemoteAddress() + " connected");
    }

    public static void launch() throws IOException {
        Server server = new Server();

        server.run();
    }

    private boolean logInAttempt(String username, String password) {
        clientsLock.readLock().lock();
        try {
            for (Map.Entry<SocketChannel, Player> socketPlayer: clients.entrySet()) {
                if (socketPlayer.getValue() != null && socketPlayer.getValue().getName().equals(username)) {
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