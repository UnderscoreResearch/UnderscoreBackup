package com.underscoreresearch.backup.ui;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for securely reading passwords from the console.
 * This class attempts to use the console's password reading functionality
 * when available, falling back to standard input when necessary.
 */
public final class PasswordReader {
    /**
     * Reads a password from the console, masking input if possible.
     *
     * @param format The format string for the prompt
     * @param args Arguments referenced by the format specifiers in the format string
     * @return The password entered by the user
     * @throws IOException If an I/O error occurs
     */
    public static String readPassword(String format, Object... args)
            throws IOException {
        return readLine(format, args);
    }

    /**
     * Reads a line of text from the console, using password masking if available.
     *
     * @param format The format string for the prompt
     * @param args Arguments referenced by the format specifiers in the format string
     * @return The line entered by the user
     * @throws IOException If an I/O error occurs
     */
    private static String readLine(String format, Object... args) throws IOException {
        if (System.console() != null) {
            return new String(System.console().readPassword(format, args));
        }
        System.out.printf(format, args);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int c = System.in.read();
        while (c != -1 && c != '\n') {
            if (c != '\r') {
                baos.write(c);
            }
            c = System.in.read();
        }
        return baos.toString(StandardCharsets.UTF_8);
    }
}
