package server;

import game.Game;
import utils.Log;
import utils.TokenGenerator;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.lang.System.currentTimeMillis;
import static java.lang.System.exit;
import static utils.Log.*;
import static utils.Log.REGULAR;

public class Server {

    private static final ReadWriteLock clientsLock = new ReentrantReadWriteLock();
    private static final ReadWriteLock playerSocketsLock = new ReentrantReadWriteLock();
    private static final ReadWriteLock N1Lock = new ReentrantReadWriteLock();
    private static final ReadWriteLock R1Lock = new ReentrantReadWriteLock();
    private static final ReadWriteLock N2Lock = new ReentrantReadWriteLock();
    private static final ReadWriteLock R2Lock = new ReentrantReadWriteLock();
    private static final ReadWriteLock runThreadLock = new ReentrantReadWriteLock();
    private static final ReadWriteLock timeOutLock = new ReentrantReadWriteLock();
    private static final ReadWriteLock inGameLock = new ReentrantReadWriteLock();
    private Selector selector;
    private Map<String, Double> timeOutList = new HashMap<>();
    private double timeOut = 30.0;
    private Map<Player, String> inGamePlayers = new HashMap<>();
    private Map<SocketChannel, Player> clients;
    private Map<String, SocketChannel> playerSockets;
    private Authentication auth;
    private ExecutorService threadPool;
    private List<Player> normal1v1 = new ArrayList<>();
    private List<Player> normal2v2 = new ArrayList<>();
    private List<Player> ranked1v1 = new ArrayList<>();
    private List<Player> ranked2v2 = new ArrayList<>();
    private Boolean runThread = true;

    public Server(String port) throws IOException {
        // Start main server
        this.auth = new Authentication();
        this.clients = new HashMap<>();
        this.playerSockets = new HashMap<>();
        this.selector = Selector.open();

        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress("localhost" , Integer.parseInt(port)));
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println(SUCCESS("Server is listening on port 9000"));

        // Start thread pool
        int maxGames = 1;

        this.threadPool = Executors.newFixedThreadPool(maxGames);
    }

    public void run() throws IOException {

        // Create a thread to check for matches and start games
        Thread matchmaking = new Thread(() -> {
            runThreadLock.readLock().lock();
            try {
                synchronized (this) {
                    while (runThread) {
                        if (!ranked1v1.isEmpty() || !ranked2v2.isEmpty()) {
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
                }
            } finally {
                runThreadLock.readLock().unlock();
            }
            exit(0);
        });
        matchmaking.start();

        Thread timeOutThread = new Thread(() -> {
            runThreadLock.readLock().lock();
            try {
                while (runThread) {
                    try {
                        checkTimeouts();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            } finally {
                runThreadLock.readLock().unlock();
            }
            exit(0);
        });
        timeOutThread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            runThreadLock.writeLock().lock();
            try {
                runThread = false;
            } finally {
                runThreadLock.writeLock().unlock();
            }

            try {
                matchmaking.join();
                threadPool.shutdownNow();
                timeOutThread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            for (SocketChannel socket: clients.keySet()) {
                try {
                    disconnect(socket);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
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

    private void checkTimeouts() throws IOException {
        if (timeOutList.size() != 0) {
            timeOutLock.readLock().lock();
            try {
                Iterator<Map.Entry<String, Double>> iterator = timeOutList.entrySet().iterator();
                while(iterator.hasNext()) {
                    Map.Entry<String, Double> entry = iterator.next();
                    double timePassed = (double) currentTimeMillis()/1000 -  entry.getValue()/1000;
                    if (timePassed >= timeOut) {
                        Player player = null;

                        playerSocketsLock.writeLock().lock();
                        try {
                            playerSockets.remove(entry.getKey());
                        } finally {
                            playerSocketsLock.writeLock().unlock();
                        }

                        clientsLock.writeLock().lock();
                        try {
                            Iterator<Map.Entry<SocketChannel, Player>> clientsIterator = clients.entrySet().iterator();
                            while (clientsIterator.hasNext()) {
                                Map.Entry<SocketChannel, Player> e = clientsIterator.next();
                                if (e.getValue().getName().equals(entry.getKey())) {
                                    player = e.getValue();
                                    clientsIterator.remove();
                                    break;
                                }
                            }
                        } finally {
                            clientsLock.writeLock().unlock();
                        }

                        inGameLock.writeLock().lock();
                        try {
                            Iterator<Player> inGameIterator = inGamePlayers.keySet().iterator();
                            while (inGameIterator.hasNext()) {
                                Player p = inGameIterator.next();
                                if (p.getName().equals(entry.getKey())) {
                                    inGameIterator.remove();
                                    break;
                                }
                            }
                        } finally {
                            inGameLock.writeLock().unlock();
                        }

                        // Remove from queues
                        N1Lock.writeLock().lock();
                        try {
                            normal1v1.remove(player);
                        } finally {
                            N1Lock.writeLock().unlock();
                        }
                        N2Lock.writeLock().lock();
                        try {
                            normal2v2.remove(player);
                        } finally {
                            N2Lock.writeLock().unlock();
                        }
                        R1Lock.writeLock().lock();
                        try {
                            ranked1v1.remove(player);
                        } finally {
                            R1Lock.writeLock().unlock();
                        }
                        R2Lock.writeLock().lock();
                        try {
                            ranked2v2.remove(player);
                        } finally {
                            R2Lock.writeLock().unlock();
                        }

                        iterator.remove();
                    }
                }
            } finally {
                timeOutLock.readLock().unlock();
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
        try {
            if (normal1v1.size() >= 2) {
                System.out.println(REGULAR("Found a 1v1 Normal game"));
                List<Player> temp = new ArrayList<>();
                for (Player p : normal1v1) {
                    timeOutLock.readLock().lock();
                    try {
                        if (!timeOutList.containsKey(p.getName())) {
                            temp.add(p);
                        }
                    } finally {
                        timeOutLock.readLock().unlock();
                    }
                    if (temp.size() == 2) {
                        N1Game = true;
                        N1Players = temp;
                        break;
                    }
                }
            }
            if (normal2v2.size() >= 4) {
                System.out.println(REGULAR("Found a 2v2 Normal game"));
                List<Player> temp = new ArrayList<>();
                for (Player p : normal1v1) {
                    timeOutLock.readLock().lock();
                    try {
                        if (!timeOutList.containsKey(p.getName())) {
                            temp.add(p);
                        }
                    } finally {
                        timeOutLock.readLock().unlock();
                    }
                    if (temp.size() == 4) {
                        N2Game = true;
                        N2Players = temp;
                        break;
                    }
                }
            }
            if (ranked1v1.size() >= 2) {
                for (int i = 0; i < ranked1v1.size() - 1; i++) {
                    Player player1 = ranked1v1.get(i);
                    for (int j = i + 1; j < ranked1v1.size(); j++) {
                        Player player2 = ranked1v1.get(j);
                        List<Player> playerArray = Arrays.asList(player1, player2);
                        boolean elosInRange = true;
                        for (Player player: playerArray) {
                            elosInRange = elosInRange && checkEloRange(player, playerArray);
                        }
                        if (elosInRange) {
                            System.out.println(REGULAR("Found a 1v1 Ranked game"));
                            R1Game = true;
                            R1Players = Arrays.asList(player1, player2);
                        }
                        if (R1Game) break;
                    }
                    if (R1Game) break;
                }
            }
            if (ranked2v2.size() >= 4) {
                for (int i = 0; i < ranked2v2.size() - 3; i++) {
                    Player player1 = ranked2v2.get(i);
                    for (int j = i + 1; j < ranked2v2.size() - 2; j++) {
                        Player player2 = ranked2v2.get(j);
                        Player player3 = ranked2v2.get(j+1);
                        Player player4 = ranked2v2.get(j+2);
                        List<Player> playerArray = Arrays.asList(player1, player2, player3, player4);
                        boolean elosInRange = true;
                        for (Player player: playerArray) {
                            elosInRange = elosInRange && checkEloRange(player, playerArray);
                        }
                        if (elosInRange) {
                            System.out.println(REGULAR("Found a 2v2 Ranked game"));
                            R2Game = true;
                            R2Players = balanceTeams(playerArray);
                        }
                        if (R2Game) break;
                    }
                    if (R2Game) break;
                }
            }

            updateEloRange(ranked1v1);
            updateEloRange(ranked2v2);

        } finally {
            N1Lock.readLock().unlock();
            R1Lock.readLock().unlock();
            N2Lock.readLock().unlock();
            R2Lock.readLock().unlock();
        }

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
            try {
                startGame(ServerCodes.R1, R1Players);
            } finally {
                R1Lock.writeLock().unlock();
            }
        }
        if (R2Game){
            R2Lock.writeLock().lock();
            try {
                startGame(ServerCodes.R2, R2Players);
            } finally {
                R2Lock.writeLock().unlock();
            }
        }
    }

    private List<Player> balanceTeams(List<Player> players) {
        int sumTeam1and2 = getTeamEloDifference(players, Arrays.asList(0, 1, 2, 3));
        int sumTeam1and3 = getTeamEloDifference(players, Arrays.asList(0, 2, 1, 3));
        int sumTeam1and4 = getTeamEloDifference(players, Arrays.asList(0, 3, 1, 2));
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
        double loserAverageElo = losingPlayers.stream().mapToInt(Player::getElo).average().orElse(0.0);
        int eloChange = (int) Math.floor(20 * loserAverageElo / winnerAverageElo);

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

    private void updateEloRange(List<Player> playerList) {
        double currentSearchTime = System.currentTimeMillis();
        for (Player player : playerList) {
            player.setEloRange((currentSearchTime - player.getSearchTime()) / 100);
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
        // Create game object and add it to the queue
        Game game = new Game(playerList, gamemode);
        String port = game.getPort();
        inGameLock.writeLock().lock();
        try {
            for (Player p: playerList) {
                inGamePlayers.put(p, port);
            }
        } finally {
            inGameLock.writeLock().unlock();
        }
        threadPool.submit(game);
        List<Player> list = new ArrayList<>();
        List<Player> waitList = new ArrayList<>();
        ReadWriteLock lock = new ReentrantReadWriteLock();
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
            }
            case R2 -> {
                lock = R2Lock;
                lock.readLock().lock();
                list = ranked2v2;
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
            timeOutLock.writeLock().lock();
            timeOutLock.readLock().lock();
            clientsLock.readLock().lock();
            try {
                if (clients.containsKey(socketChannel) && !timeOutList.containsKey(clients.get(socketChannel).getName())) {
                    key.cancel();
                    timeOutList.put(clients.get(socketChannel).getName(), (double) currentTimeMillis());
                }
            } finally {
                clientsLock.readLock().unlock();
                timeOutLock.readLock().unlock();
                timeOutLock.writeLock().unlock();
            }
            return;
        }

        if (bytesRead == -1) {
            timeOutLock.writeLock().lock();
            clientsLock.readLock().lock();
            try {
                if (clients.containsKey(socketChannel) && !timeOutList.containsKey(clients.get(socketChannel).getName())) {
                    key.cancel();
                    timeOutList.put(clients.get(socketChannel).getName(), (double) currentTimeMillis());
                }
            } finally {
                clientsLock.readLock().unlock();
                timeOutLock.writeLock().unlock();
            }
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
                    if (inTimeOutPeriod(message.get(1))) {

                        Player p = null;
                        ServerCodes pos = null;
                        String response;
                        ServerCodes queue = null;

                        clientsLock.writeLock().lock();
                        inGameLock.readLock().lock();
                        N1Lock.readLock().lock();
                        N2Lock.readLock().lock();
                        R1Lock.readLock().lock();
                        R2Lock.readLock().lock();
                        try {
                            for (Player player: clients.values()) {
                                if (player != null && player.getName().equals(message.get(1))) {
                                    p = player;
                                    break;
                                }
                            }
                            if (normal1v1.contains(p)) {
                                pos = ServerCodes.Q;
                                queue = ServerCodes.N1;
                            } else if (normal2v2.contains(p)) {
                                pos = ServerCodes.Q;
                                queue = ServerCodes.N2;
                            } else if (ranked1v1.contains(p)) {
                                pos = ServerCodes.Q;
                                queue = ServerCodes.R1;
                            } else if (ranked2v2.contains(p)) {
                                pos = ServerCodes.Q;
                                queue = ServerCodes.R2;
                            } else if (inGamePlayers.containsKey(p)) {
                                pos = ServerCodes.G;
                            }
                        } finally {
                            clientsLock.writeLock().unlock();
                            inGameLock.readLock().unlock();
                            N1Lock.readLock().unlock();
                            N2Lock.readLock().unlock();
                            R1Lock.readLock().unlock();
                            R2Lock.readLock().unlock();
                        }

                        if (pos != null && pos.equals(ServerCodes.Q)) {
                            response = ServerCodes.REC + "," + p.getToken() + "," + pos + "," + queue;
                        } else if (pos != null && pos.equals(ServerCodes.G)) {
                            response = ServerCodes.REC + "," + p.getToken() + "," + pos + "," + inGamePlayers.get(p);
                        } else {
                            response = ServerCodes.REC + "," + p.getToken();
                        }

                        System.out.println(SUCCESS(p.getName() + " reconnected"));

                        updateLists(socketChannel, queue, p);
                        write(socketChannel, response);
                        if (pos != null && pos.equals(ServerCodes.Q)) {
                            try {
                                checkMatchmaking();
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    } else {
                        // generate tokens, get elo, etcetc
                        String token = TokenGenerator.generateToken();
                        String response = ServerCodes.OK + "," + token;
                        String elo = auth.getPlayerElo(message.get(1));
                        Player player = new Player(message.get(1), elo, null, socketChannel, token);

                        // add player to tracking lists
                        clientsLock.writeLock().lock();
                        playerSocketsLock.writeLock().lock();
                        try {
                            clients.put(socketChannel, player);
                            playerSockets.put(player.getName(), socketChannel);
                        } finally {
                            clientsLock.writeLock().unlock();
                            playerSocketsLock.writeLock().unlock();
                        }

                        System.out.println(SUCCESS(player.getName() + " logged in"));

                        // tell player they were logged in
                        write(socketChannel, response);
                    }

                } else {
                    String response = String.valueOf(ServerCodes.ERR);

                    write(socketChannel, response);

                    System.out.println(ERROR("A login attempt has failed"));
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

                    System.out.println(REGULAR(player.getName() + " has just registered"));

                    write(socketChannel, response);
                } else {
                    String response = String.valueOf(ServerCodes.ERR);

                    write(socketChannel, response);

                    System.out.println(ERROR("A register attempt has failed"));
                }
                break;
            case DC:
                System.out.println(REGULAR("Disconnected a client"));
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
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        } finally {
                            N1Lock.writeLock().unlock();
                            N2Lock.writeLock().unlock();
                        }
                        System.out.println(REGULAR(player.getName() + " is looking for a normal game"));
                    } finally {
                        clientsLock.readLock().unlock();
                    }
                } else {
                    System.out.println(ERROR("Invalid token found, disconnecting client"));
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
                        System.out.println(REGULAR(player.getName() + " is looking for a ranked game"));
                    } finally {
                        clientsLock.readLock().unlock();
                    }
                } else {
                    System.out.println(ERROR("Invalid token found, disconnecting client"));
                    disconnect(socketChannel);
                }
                break;
            case GG:
                String port;
                ServerCodes gamemode;

                if (message.get(1).equals(ServerCodes.ERR)) {
                    port = message.get(2);

                    inGameLock.writeLock().lock();
                    try {
                        List<String> players = List.of(message.get(3).split(","));

                        for (Map.Entry<Player, String> entry: inGamePlayers.entrySet()) {
                            if (players.contains(entry.getKey().getName())) {
                                inGamePlayers.remove(entry.getKey());
                            }
                        }
                    } finally {
                        inGameLock.writeLock().unlock();
                    }

                    System.out.println(ERROR("Game ended abruptly in port " + port));
                } else {
                    port = message.get(2);
                    gamemode = ServerCodes.valueOf(message.get(1));
                    List<String> winners, losers;
                    winners = List.of(message.get(3).split("-"));
                    losers = List.of(message.get(4).split("-"));
                    if (gamemode == ServerCodes.R1 || gamemode == ServerCodes.R2) {
                        updateElo(winners, losers);
                    }

                    inGameLock.writeLock().lock();
                    inGameLock.readLock().lock();
                    try {
                        Iterator<Player> iterator = inGamePlayers.keySet().iterator();
                        while (iterator.hasNext()) {
                            Player p = iterator.next();
                            if (winners.contains(p.getName()) || losers.contains(p.getName())) {
                                iterator.remove();
                            }
                        }
                    } finally {
                        inGameLock.writeLock().unlock();
                        inGameLock.readLock().unlock();
                    }

                    System.out.println(REGULAR("Game ended in port " + port));
                }
                break;
            default:
                break;
        }
    }

    private void updateLists(SocketChannel socketChannel, ServerCodes queue, Player player) {
        if (queue != null) {
            switch (queue) {
                case N1 -> {
                    N1Lock.writeLock().lock();
                    try {
                        normal1v1.forEach(p -> {
                            if (p == player) {
                                p.setSocketChannel(socketChannel);
                            }
                        });
                    } finally {
                        N1Lock.writeLock().unlock();
                    }

                }
                case N2 -> {
                    N2Lock.writeLock().lock();
                    try {
                        normal2v2.forEach(p -> {
                            if (p == player) {
                                p.setSocketChannel(socketChannel);
                            }
                        });
                    } finally {
                        N2Lock.writeLock().unlock();
                    }
                }
                case R1 -> {
                    R1Lock.writeLock().lock();
                    try {
                        ranked1v1.forEach(p -> {
                            if (p == player) {
                                p.setSocketChannel(socketChannel);
                            }
                        });
                    } finally {
                        R1Lock.writeLock().unlock();
                    }
                }
                case R2 -> {
                    R2Lock.writeLock().lock();
                    try {
                        ranked2v2.forEach(p -> {
                            if (p == player) {
                                p.setSocketChannel(socketChannel);
                            }
                        });
                    } finally {
                        R2Lock.writeLock().unlock();
                    }

                }
            }
        }

        player.setSocketChannel(socketChannel);

        inGameLock.writeLock().lock();
        try {
            String port = inGamePlayers.get(player);
            inGamePlayers.remove(player);
            inGamePlayers.put(player, port);
        } finally {
            inGameLock.writeLock().unlock();
        }

        clientsLock.writeLock().lock();
        try {
            for (Map.Entry<SocketChannel, Player> entry: clients.entrySet()) {
                if (entry.getValue() == player) {
                    clients.remove(entry.getKey());
                    break;
                }
            }
            clients.put(socketChannel, player);
        } finally {
            clientsLock.writeLock().unlock();
        }

        timeOutLock.writeLock().lock();
        try {
            timeOutList.remove(player.getName());
        } finally {
            timeOutLock.writeLock().unlock();
        }

    }

    private boolean inTimeOutPeriod(String username) {
        timeOutLock.readLock().lock();
        try {
            return timeOutList.containsKey(username);
        } finally {
            timeOutLock.readLock().unlock();
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
            if (player != null) playerSockets.remove(player.getName());
            clients.remove(socketChannel);
        } finally {
            clientsLock.writeLock().unlock();
            playerSocketsLock.writeLock().unlock();
        }

        System.out.println(REGULAR("Client " + clientName + " disconnected"));
        socketChannel.close();
    }

    private void accept(SelectionKey key) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel socketChannel = serverSocketChannel.accept();
        if (socketChannel == null) return;
        socketChannel.configureBlocking(false);
        socketChannel.register(selector, SelectionKey.OP_READ);
/*        clientsLock.writeLock().lock();
        try {
            clients.put(socketChannel, null);
        } finally {
            clientsLock.writeLock().unlock();
        }*/
        System.out.println(REGULAR("Client " + socketChannel.getRemoteAddress() + " connected"));
    }

    public static void main(String [] args) throws IOException {
        Server server = new Server(args[0]);

        server.run();
    }

    public static void launch() throws IOException {
        Server server = new Server("9000");

        server.run();
    }

    private boolean logInAttempt(String username, String password) {
        clientsLock.readLock().lock();
        timeOutLock.readLock().lock();
        try {
            for (Map.Entry<SocketChannel, Player> socketPlayer: clients.entrySet()) {
                if (socketPlayer.getValue() != null && socketPlayer.getValue().getName().equals(username) && !timeOutList.containsKey(username)) {
                    return false;
                }
            }
        } finally {
            clientsLock.readLock().unlock();
            timeOutLock.readLock().unlock();
        }

        return auth.auth(username, password);
    }

    private boolean registerAttempt(String username, String password) {

        return auth.create(username, password);
    }
}