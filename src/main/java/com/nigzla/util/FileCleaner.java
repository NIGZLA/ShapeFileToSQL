package com.nigzla.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * @author NIGZLA
 * 2026/3/4 10:57
 */
public class FileCleaner {
    public static void delete(Path path) throws IOException {

        if (Files.notExists(path)) {
            return;
        }

        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.delete(p); }
                        catch (IOException ignored) {}
                    });
        }
    }
}
