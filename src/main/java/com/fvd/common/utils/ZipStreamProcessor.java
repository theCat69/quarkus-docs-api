package com.fvd.common.utils;

import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utility for processing zip stream entries with filtering.
 */
@UtilityClass
public class ZipStreamProcessor {

    /**
     * Iterates over entries in a zip stream, filtering by name and processing matching entries.
     *
     * @param zipStream the input stream of the zip archive
     * @param filter predicate to test entry names (only matching entries are processed)
     * @param processor consumer that receives the entry name and content bytes
     * @throws IOException if reading the zip stream fails
     */
    public static void processEntries(
            InputStream zipStream,
            Predicate<String> filter,
            BiConsumer<String, byte[]> processor) throws IOException {

        try (ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                String entryName = entry.getName();
                if (filter.test(entryName)) {
                    processor.accept(entryName, zis.readAllBytes());
                }
                zis.closeEntry();
            }
        }
    }
}
