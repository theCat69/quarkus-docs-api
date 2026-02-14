package com.fvd.common;

import lombok.experimental.UtilityClass;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@UtilityClass
public class TestZipHelper {

    /**
     * Builds an in-memory ZIP archive from name/content pairs.
     * Arguments must be provided in pairs: name1, content1, name2, content2, ...
     *
     * @param nameContentPairs alternating entry names and their string content
     * @return the ZIP archive as a byte array
     */
    public static byte[] createZip(String... nameContentPairs) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (int i = 0; i < nameContentPairs.length; i += 2) {
                zos.putNextEntry(new ZipEntry(nameContentPairs[i]));
                zos.write(nameContentPairs[i + 1].getBytes());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    /**
     * Builds an in-memory ZIP archive and returns it as an InputStream.
     * Convenience wrapper around {@link #createZip(String...)}.
     */
    public static InputStream createZipAsStream(String... nameContentPairs) throws IOException {
        return new ByteArrayInputStream(createZip(nameContentPairs));
    }
}
