package utils;

public class Log {
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BLACK = "\u001B[30m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_PURPLE = "\u001B[35m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_WHITE = "\u001B[37m";
    private static final String UNI_CROSS = "✘";
    private static final String UNI_MARK = "✔";
    private static final String UNI_DOT = "·";

    public static String ERROR(String message) {
        return ANSI_RED + "[" + UNI_CROSS + "] " + message + ANSI_RESET;
    }

    public static String SUCCESS(String message) {
        return ANSI_GREEN + "[" + UNI_MARK + "] " + message + ANSI_RESET;
    }

    public static String REGULAR(String message) {
        return ANSI_YELLOW + "[" + UNI_DOT + "] " + message + ANSI_RESET;
    }
}
