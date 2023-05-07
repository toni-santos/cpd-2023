package database;

import utils.SHA512Generator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DBHandler {
    Path path = Paths.get("src/main/java/database/users.csv");
    private static final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    public boolean createUser(String username, String password) {
        List<List<String>> allLines;
        rwLock.readLock().lock();
        try {
            try {
                List<String> all = Files.readAllLines(path);
                allLines = all.stream().map(line -> {
                    return List.of(line.split(","));
                }).toList();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } finally {
            rwLock.readLock().unlock();
        }

        if (allLines.size()>0 && userExists(allLines, username))
            return false;

        String information = Stream.of(Integer.toString(allLines.size()+1), username, password, "0")
                .map(this::escapeSpecialCharacters)
                .collect(Collectors.joining(","));

        rwLock.writeLock().lock();
        try {
            File dbFile = new File(path.toUri());

            try (PrintWriter pw = new PrintWriter(dbFile)) {
                for(List<String> line : allLines) {
                    pw.println(String.join(",", line));
                }
                pw.println(information);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }

            return true;
        } finally {
            rwLock.writeLock().unlock();
        }

    }

    private boolean userExists(List<List<String>> lines, String username) {
        for (List<String> line: lines) {
            if (line.get(1).equals(username)) {
                return true;
            }
        }

        return false;
    }

    public String getUserPassword(String username) {
        rwLock.readLock().lock();
        try {
            List<List<String>> allLines;

            try {
                List<String> all = Files.readAllLines(path);
                allLines = all.stream().map(line -> {
                    return List.of(line.split(","));
                }).toList();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            for (List<String> line: allLines) {
                if (line.get(1).equals(username)) {
                    return line.get(2);
                }
            }
            return null;

        } finally {
            rwLock.readLock().unlock();
        }
    }

    public String getElo(String username) {
        rwLock.readLock().lock();
        try {
            List<List<String>> allLines;

            try {
                List<String> all = Files.readAllLines(path);
                allLines = all.stream().map(line -> {
                    return List.of(line.split(","));
                }).toList();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            for (List<String> line: allLines) {
                if (line.get(1).equals(username)) {
                    return line.get(3);
                }
            }
            return null;

        } finally {
            rwLock.readLock().unlock();
        }
    }


    public String escapeSpecialCharacters(String str) {
        String escaped = str.replaceAll("\\R", " ");
        if (str.contains(",") || str.contains("\"") || str.contains("'")) {
            str = str.replace("\"", "\"\"");
            escaped = "\"" + str + "\"";
        }
        return escaped;
    }

    public void setPlayerElo(String username, String elo) {
        List<List<String>> allLines;
        rwLock.readLock().lock();
        try {
            try {
                List<String> all = Files.readAllLines(path);
                allLines = all.stream().map(line -> {
                    return Arrays.asList(line.split(","));
                }).toList();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } finally {
            rwLock.readLock().unlock();
        }

        for (List<String> line: allLines) {
            if (line.get(1).equals(username)) {
                line.set(3, elo);
            }
        }

        rwLock.writeLock().lock();
        try {
            File dbFile = new File(path.toUri());

            try (PrintWriter pw = new PrintWriter(dbFile)) {
                for(List<String> line : allLines) {
                    pw.println(String.join(",", line));
                }
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }

        } finally {
            rwLock.writeLock().unlock();
        }
    }
}
