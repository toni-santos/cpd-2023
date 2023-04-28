package server;

import java.nio.channels.SocketChannel;

public class Player {
    private final SocketChannel socketChannel;
    private String player;
    private String ELO;
    private ServerCodes gamemode;

    public Player(String player, String ELO, ServerCodes gamemode) {
        this.player = player;
        this.ELO = ELO;
        this.gamemode = gamemode;
        this.socketChannel = null;
    }

    public Player(String player, SocketChannel socketChannel) {
        this.player = player;
        this.socketChannel = socketChannel;
        this.ELO = null;
        this.gamemode = null;
    }

    public String getPlayer() {
        return player;
    }

    public String getELO() {
        return ELO;
    }

    public ServerCodes getGamemode() {
        return gamemode;
    }

    public SocketChannel getSocketChannel() {
        return socketChannel;
    }

}
