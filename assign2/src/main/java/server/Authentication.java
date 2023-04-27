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
        // block reads/writes here
        String dbPassword = dbHandler.getUserPassword(username);
        if (dbPassword.equals("")) return false;

        return SHA512Generator.encrypt(password).equals(dbPassword);
    }

    public boolean create(String username, String password) {
        // block reads/writes here
        DBHandler dbHandler = new DBHandler();
        return dbHandler.createUser(username, password);
    }
}
