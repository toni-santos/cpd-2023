package database;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DBHandler {

    public boolean createUser(String username, String password) {
        Path path = Paths.get("./users.csv");
        int id;

        if (password.length() < 8) {
            return false;
        }

        try {
            id = Math.toIntExact(Files.lines(path).count());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String information = Stream.of(Integer.toString(id), username, password, "0")
                .map(this::escapeSpecialCharacters)
                .collect(Collectors.joining(","));;

        File dbFile = new File(path.toUri());

        try (PrintWriter pw = new PrintWriter(dbFile)) {
            pw.println(information);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        return true;
    }

    public String getUserPassword(String username) {
        try {
            Path path = Paths.get("./users.csv");
            List<String> lines = Files.lines(path).toList();
            List<String> filtered = lines.stream().filter(line -> evaluateLine(line, username)).toList();
            if (filtered.size() >= 1) return "";

            return Arrays.stream(filtered.get(0).split(",")).toList().get(2);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean evaluateLine(String line, String username) {
        return List.of(line.split(",")).get(1).equals(username);
    }

    public String escapeSpecialCharacters(String str) {
        String escaped = str.replaceAll("\\R", " ");
        if (str.contains(",") || str.contains("\"") || str.contains("'")) {
            str = str.replace("\"", "\"\"");
            escaped = "\"" + str + "\"";
        }
        return escaped;
    }
}
