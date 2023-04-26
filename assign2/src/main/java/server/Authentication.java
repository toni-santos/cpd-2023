package server;

import database.DBHandler;
import utils.SHA512Generator;

public class Authentication {
    public boolean auth(String username, String password) {
        // block reads/writes here
        DBHandler dbHandler = new DBHandler();
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
