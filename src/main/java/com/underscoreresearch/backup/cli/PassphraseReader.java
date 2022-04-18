package com.underscoreresearch.backup.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public final class PassphraseReader {
    public static String readPassphrase(String format, Object... args)
            throws IOException {
        return readLine(format, args);
    }

    private static String readLine(String format, Object... args) throws IOException {
        if (System.console() != null) {
            return new String(System.console().readPassword(format, args));
        }
        System.out.print(String.format(format, args));
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                System.in));
        return reader.readLine();
    }
}