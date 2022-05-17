package com.underscoreresearch.backup.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.configuration.InstanceFactory;

@Slf4j
public final class IOUtils {
    private static final long INTERNET_WAIT = 1000;
    private static final Duration INTERNET_SUCCESS_CACHE = Duration.ofSeconds(2);
    private static Instant internetSuccessfulUntil = null;
    private static AtomicBoolean waitingForInternetMessage = new AtomicBoolean();
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    public static byte[] readAllBytes(InputStream stream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        byte[] buffer = new byte[16384];
        int length;
        while ((length = stream.read(buffer, 0, buffer.length)) > 0) {
            outputStream.write(buffer, 0, length);
        }
        return outputStream.toByteArray();
    }

    public static long copyStream(InputStream in, OutputStream out) throws IOException {
        long transferred = 0;
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        int read;
        while ((read = in.read(buffer, 0, DEFAULT_BUFFER_SIZE)) >= 0) {
            out.write(buffer, 0, read);
            transferred += read;
        }
        return transferred;
    }

    public static boolean hasInternet() {
        try {
            if (internetSuccessfulUntil != null && Instant.now().isBefore(internetSuccessfulUntil)) {
                return true;
            }
            URL url = new URL("https://www.example.com");
            URLConnection connection = url.openConnection();
            connection.connect();
            internetSuccessfulUntil = Instant.now().plus(INTERNET_SUCCESS_CACHE);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static <T> T waitForInternet(Callable<T> callable) throws Exception {
        boolean clearFlag = false;
        try {
            for (int i = 0; true; i++) {
                try {
                    if (InstanceFactory.isShutdown()) {
                        throw new InterruptedException("Shutting down");
                    }
                    return callable.call();
                } catch (Exception exc) {
                    if (!IOUtils.hasInternet()) {
                        do {
                            if (i == 0) {
                                clearFlag = true;
                                synchronized (waitingForInternetMessage) {
                                    if (!waitingForInternetMessage.get()) {
                                        log.warn("Waiting for internet access to continue");
                                        waitingForInternetMessage.set(true);
                                    }
                                }
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
        } finally {
            if (clearFlag) {
                synchronized (waitingForInternetMessage) {
                    if (waitingForInternetMessage.get()) {
                        log.info("Internet access restored");
                        waitingForInternetMessage.set(false);
                    }
                }
            }
        }
    }
}
