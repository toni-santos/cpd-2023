package server;

import java.nio.channels.SocketChannel;

public class Player {
    private final SocketChannel socketChannel;
    private SocketChannel gameChannel;
    private String player;
    private String elo;
    private String token;
    private ServerCodes gamemode;
    private double searchTime;
    private double eloRange = 0.0;

    public Player(String player, String elo, ServerCodes gamemode, SocketChannel socketChannel, String token) {
        this.player = player;
        this.elo = elo;
        this.gamemode = gamemode;
        this.socketChannel = socketChannel;
        this.token = token;
    }

    public String getPlayer() {
        return player;
    }

    public Integer getElo() {
        return Integer.parseInt(elo);
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
    public void setSearchTime() { this.searchTime = System.currentTimeMillis(); }
    public double getEloRange() { return eloRange; }
    public void setEloRange(double currentTime) { this.eloRange = currentTime - searchTime; }

    public void setGameChannel(SocketChannel gameChannel) {
        this.gameChannel = gameChannel;
    }
}
