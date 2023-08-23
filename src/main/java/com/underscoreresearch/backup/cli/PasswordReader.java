package com.underscoreresearch.backup.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

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
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                System.in));
        return reader.readLine();
    }
}