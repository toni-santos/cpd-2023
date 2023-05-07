package server;

import java.nio.channels.SocketChannel;

public class Player {
    private final SocketChannel socketChannel;
    private String player;
    private String elo;
    private String token;
    private ServerCodes gamemode;

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

    public String getElo() {
        return elo;
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


}
