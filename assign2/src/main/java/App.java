import client.Client;
import server.Matchmaking;
import server.Server;

import java.io.IOException;
import java.security.SecureRandom;

public class App {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("missing argument");
        }

        switch (args[0]) {
            case "client" -> {
                Client.launch();
            }
            case "server" -> {
                Server.launch();
            }
            default -> System.out.println("invalid argument");
        }

    }
}
