package server;

import java.nio.channels.SocketChannel;

public class Player {
    private final SocketChannel socketChannel;
    private String player;
    private String elo;
    private ServerCodes gamemode;

    public Player(String player, String elo, ServerCodes gamemode) {
        this.player = player;
        this.elo = elo;
        this.gamemode = gamemode;
        this.socketChannel = null;
    }

    public Player(String player, String elo, ServerCodes gamemode, SocketChannel socketChannel) {
        this.player = player;
        this.elo = elo;
        this.gamemode = gamemode;
        this.socketChannel = socketChannel;
    }

    public Player(String player, SocketChannel socketChannel) {
        this.player = player;
        this.socketChannel = socketChannel;
        this.elo = null;
        this.gamemode = null;
    }

    public String getPlayer() {
        return player;
    }

    public String getElo() {
        return elo;
    }

    public ServerCodes getGamemode() {
        return gamemode;
    }

    public SocketChannel getSocketChannel() {
        return socketChannel;
    }

}
