package com.nigzla.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author NIGZLA
 * 2026/3/4 10:53
 */
public class ZipExtractor {
    public Path extract(String zipPath) throws IOException {

        Path tempDir = Files.createTempDirectory("shp_import_");

        try (InputStream fis = Files.newInputStream(Paths.get(zipPath));
             ZipInputStream zis = new ZipInputStream(fis)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {

                Path out = tempDir.resolve(entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    try (OutputStream os = Files.newOutputStream(out)) {
                        zis.transferTo(os);
                    }
                }
            }
        }

        return tempDir;
    }
}
