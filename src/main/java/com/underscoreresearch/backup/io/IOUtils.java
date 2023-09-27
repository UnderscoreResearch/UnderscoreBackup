package com.underscoreresearch.backup.io;

import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.RetryUtils.DEFAULT_BASE;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
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
import com.underscoreresearch.backup.utils.PausedStatusLogger;

@Slf4j
public final class IOUtils {
    public static final long INTERNET_WAIT = 1000;
    private static final Duration INTERNET_SUCCESS_CACHE = Duration.ofSeconds(2);
    private static final int DEFAULT_BUFFER_SIZE = 8192;
    private static final AtomicBoolean waitingForInternetMessage = new AtomicBoolean();
    private static Instant internetSuccessfulUntil = null;

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

            for (int i = 0; true; i++) {
                try {
                    URL url = new URL("https://www.example.com");
                    URLConnection connection = url.openConnection();
                    connection.connect();
                    break;
                } catch (Exception exc) {
                    if (3 == i || !(exc instanceof IOException)) {
                        throw exc;
                    }
                    Thread.sleep((long) Math.pow(2, i) * DEFAULT_BASE);
                }
            }

            internetSuccessfulUntil = Instant.now().plus(INTERNET_SUCCESS_CACHE);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static <T> T waitForInternet(Callable<T> callable) throws Exception {
        for (int i = 0; true; i++) {
            try {
                if (InstanceFactory.isShutdown()) {
                    throw new InterruptedException("Shutting down");
                }
                return callable.call();
            } catch (Exception exc) {
                if (!IOUtils.hasInternet()) {
                    boolean clearFlag = false;
                    try (Closeable ignore = PausedStatusLogger.startPause("Waiting for internet to continue")) {
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
                            } catch (InterruptedException ignored) {
                                Thread.interrupted();
                            }
                            i++;
                        } while (!IOUtils.hasInternet());
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
                } else {
                    throw exc;
                }
            }
        }
    }

    public static void createDirectory(File file, boolean warning) {
        if (!file.exists() && !file.mkdirs()) {
            if (warning)
                log.warn("Failed to create directory {}", file);
            else
                debug(() -> log.debug("Failed to create directory {}", file));
        }
    }

    public static void deleteFile(File file) {
        try {
            deleteFileException(file);
        } catch (IOException e) {
            log.warn(e.getMessage());
        }
    }

    public static void deleteFileException(File file) throws IOException {
        if (file.exists() && !file.delete()) {
            throw new IOException("Failed to delete " + file);
        }
    }

    public static void deleteContents(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    if (!child.getName().startsWith(".")) {
                        if (child.isDirectory()) {
                            deleteContents(child);
                        }
                        deleteFile(child);
                    }
                }
            }
        }
    }

    public static void clearTempFiles() {
        File file = new File(System.getProperty("java.io.tmpdir"));
        File[] files = file.listFiles(pathname -> pathname.getName().toLowerCase().startsWith("underscorebackup"));
        if (files != null) {
            for (File child : files) {
                log.warn("Deleting stale temp file {}", child);
                if (child.isDirectory()) {
                    deleteContents(child);
                }
                deleteFile(child);
            }
        }
    }
}
