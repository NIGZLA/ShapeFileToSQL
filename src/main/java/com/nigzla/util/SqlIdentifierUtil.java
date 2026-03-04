package com.nigzla.util;

/**
 * @author NIGZLA
 * 2026/3/4 10:28
 */
public class SqlIdentifierUtil {
    public static String quote(String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }
}
