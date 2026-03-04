package com.nigzla.shp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * @author NIGZLA
 * 2026/3/4 10:53
 */
public class ShapefileLocator {
    public Path findShp(Path dir) throws IOException {

        try (Stream<Path> stream = Files.walk(dir)) {
            Optional<Path> result = stream
                    .filter(p -> p.toString().toLowerCase().endsWith(".shp"))
                    .findFirst();

            if (result.isEmpty()) {
                throw new IllegalStateException("No .shp file found");
            }

            return result.get();
        }
    }
}
