import client.Client;
import server.Server;

public class App {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("missing argument");
        }

        switch (args[0]) {
            case "client" -> {
                Client client = new Client();
                client.launch();
            }
            case "server" -> {
                Server server = new Server();
                server.launch();
            }
            default -> System.out.println("invalid argument");
        }

    }
}
