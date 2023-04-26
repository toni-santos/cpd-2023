import client.Client;
import server.Server;

import java.io.IOException;

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
