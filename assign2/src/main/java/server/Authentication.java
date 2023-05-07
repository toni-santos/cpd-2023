package server;

import database.DBHandler;
import utils.SHA512Generator;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Authentication {
    private DBHandler dbHandler;

    public Authentication() {
        dbHandler = new DBHandler();
    }

    public boolean auth(String username, String password) {
        String dbPassword = dbHandler.getUserPassword(username);
        if (dbPassword == null || dbPassword.equals("")) return false;
        return password.equals(dbPassword);
    }

    public boolean create(String username, String password) {
        return dbHandler.createUser(username, password);
    }

    public String getPlayerElo(String username) {
        return dbHandler.getElo(username);
    }
    
    public void setPlayerElo(String username, String elo ){
        dbHandler.setPlayerElo(username, elo);
    }
}
