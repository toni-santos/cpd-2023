package server;

public class Player {
    String player;
    String ELO;
    ServerCodes gamemode;
    public Player(String player, String ELO, ServerCodes gamemode) {
        this.player = player;
        this.ELO = ELO;
        this.gamemode = gamemode;
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
}
