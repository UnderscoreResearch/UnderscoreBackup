package com.underscoreresearch.backup.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.Callable;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class IOUtils {
    private static final long INTERNET_WAIT = 1000;

    public static byte[] readAllBytes(InputStream stream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        byte[] buffer = new byte[16384];
        int length;
        while ((length = stream.read(buffer, 0, buffer.length)) > 0) {
            outputStream.write(buffer, 0, length);
        }
        return outputStream.toByteArray();
    }

    public static boolean hasInternet() {
        try {
            URL url = new URL("http://www.example.com");
            URLConnection connection = url.openConnection();
            connection.connect();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static void waitForInternet(Callable<Void> callable) throws Exception {
        for (int i = 0; true; i++) {
            try {
                callable.call();
                break;
            } catch (Exception exc) {
                if (!IOUtils.hasInternet()) {
                    do {
                        if (i == 0) {
                            log.warn("Waiting for internet access to continue");
                        }
                        try {
                            Thread.sleep(INTERNET_WAIT);
                        } catch (InterruptedException e) {
                            Thread.interrupted();
                        }
                        i++;
                    } while (!IOUtils.hasInternet());
                } else {
                    throw exc;
                }
            }
        }
    }
}
