package com.underscoreresearch.backup.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class PasswordReader {
    public static String readPassword(String format, Object... args)
            throws IOException {
        return readLine(format, args);
    }

    private static String readLine(String format, Object... args) throws IOException {
        if (System.console() != null) {
            return new String(System.console().readPassword(format, args));
        }
        System.out.printf(format, args);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int c = System.in.read();
        while(c != -1 && c != '\n') {
            if (c != '\r') {
                baos.write(c);
            }
            c = System.in.read();
        }
        return baos.toString(StandardCharsets.UTF_8);
    }
}