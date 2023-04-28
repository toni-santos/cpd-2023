package game;

import server.Player;

import java.util.List;

public class Game implements Runnable {

    private List<Player> players;

    public Game(List<Player> players) {
        this.players = players;
    }

    @Override
    public void run() {
        System.out.println(players);
        for (Player p: players) {
            System.out.println("This person is gaming: " + p.getPlayer());
        }
    }
}
