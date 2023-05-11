package server;

import java.io.IOException;
import java.nio.channels.SocketChannel;

public class Player {
    private final SocketChannel socketChannel;
    private SocketChannel gameChannel;
    private String name;
    private String elo;
    private String token;
    private ServerCodes gamemode;
    private double searchTime;
    private double eloRange = 0.0;

    public Player(String name, String elo, ServerCodes gamemode, SocketChannel socketChannel, String token) {
        this.name = name;
        this.elo = elo;
        this.gamemode = gamemode;
        this.socketChannel = socketChannel;
        this.token = token;
    }

    public String getName() {
        return name;
    }

    public Integer getElo() {
        return Integer.parseInt(elo);
    }

    public void setElo(String elo) {
        this.elo = elo;
    }

    public ServerCodes getGamemode() {
        return gamemode;
    }

    public SocketChannel getSocketChannel() {
        return socketChannel;
    }

    public String getToken() {
        return token;
    }

    public void setGamemode(ServerCodes gamemode) {
        this.gamemode = gamemode;
    }

    public SocketChannel getGameChannel() {
        return gameChannel;
    }
    public double getSearchTime() { return searchTime; }

    public void setSearchTime() { this.searchTime = System.currentTimeMillis(); }
    public double getEloRange() { return eloRange; }
    public void setEloRange(double currentTime) { this.eloRange = currentTime; }

    public void setGameChannel(SocketChannel gameChannel) {
        this.gameChannel = gameChannel;
    }

    public boolean equals(Object obj) {
        if (obj instanceof Player p) {
            return this.socketChannel == p.socketChannel;
        }
        return false;
    }
}
