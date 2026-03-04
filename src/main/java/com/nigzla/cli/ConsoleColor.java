package com.nigzla.cli;

/**
 * @author NIGZLA
 * 2026/3/4 11:27
 */
public class ConsoleColor {

    private static final String RESET = "\033[0m";

    private static final String RED = "\033[31m";
    private static final String GREEN = "\033[32m";
    private static final String YELLOW = "\033[33m";
    private static final String BLUE = "\033[34m";
    private static final String CYAN = "\033[36m";

    private static final boolean ENABLE = true;

    private static String color(String code, String text) {
        if (!ENABLE) {
            return text;
        }
        return code + text + RESET;
    }

    public static String success(String text) {
        return color(GREEN, text);
    }

    public static String error(String text) {
        return color(RED, text);
    }

    public static String warn(String text) {
        return color(YELLOW, text);
    }

    public static String info(String text) {
        return color(CYAN, text);
    }

    public static String title(String text) {
        return color(BLUE, text);
    }
}
