package com.underscoreresearch.backup.io;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.utils.PausedStatusLogger;
import com.underscoreresearch.backup.utils.ProcessingStoppedException;
import com.underscoreresearch.backup.utils.state.MachineState;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.RetryUtils.DEFAULT_BASE;

@Slf4j
public final class IOUtils {
    public static final long INTERNET_WAIT = 1000;
    private static final Duration INTERNET_SUCCESS_CACHE = Duration.ofSeconds(2);
    private static final int DEFAULT_BUFFER_SIZE = 8192;
    private static final AtomicBoolean waitingForInternetMessage = new AtomicBoolean();
    private static final long HOUR_IN_MILLIS = Duration.ofHours(1).toMillis();
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
        if (internetSuccessfulUntil != null && Instant.now().isBefore(internetSuccessfulUntil)) {
            return true;
        }

        if (internalHasInternet()) {
            internetSuccessfulUntil = Instant.now().plus(INTERNET_SUCCESS_CACHE);
            return true;
        }
        return false;
    }

    private static boolean internalHasInternet() {
        try {
            for (int i = 0; true; i++) {
                try {
                    URL url = new URI("http://www.example.com").toURL();
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("HEAD");
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(10000);
                    connection.setUseCaches(false);
                    connection.connect();
                    if (connection.getResponseCode() != 200) {
                        throw new IOException();
                    }
                    break;
                } catch (Exception exc) {
                    if (3 == i || !(exc instanceof IOException)) {
                        throw exc;
                    }
                    Thread.sleep((long) Math.pow(2, i) * DEFAULT_BASE);
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static <T> T waitForInternet(Callable<T> callable, boolean immediateFailOnShutdown) throws Exception {
        for (int i = 0; true; i++) {
            if (immediateFailOnShutdown)
                failOnShutdown();
            try {
                return callable.call();
            } catch (Exception exc) {
                failOnShutdown();
                if (!IOUtils.hasInternet()) {
                    boolean clearFlag = false;
                    try (Closeable ignore = PausedStatusLogger.startPause("Paused until internet access is restored")) {
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
                                Thread.currentThread().interrupt();
                            }
                            i++;
                            failOnShutdown();
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

    private static void failOnShutdown() {
        if (InstanceFactory.isShutdown())
            throw new ProcessingStoppedException("Shutting down");
    }

    public static void createDirectory(File file, boolean warning) {
        if (!file.mkdirs() && !file.isDirectory()) {
            if (warning)
                log.warn("Failed to create directory \"{}\"", file);
            else
                debug(() -> log.debug("Failed to create directory \"{}\"", file));
        }
    }

    public static void deleteFile(File file) {
        try {
            deleteFileException(file);
        } catch (IOException e) {
            log.warn(e.getMessage());
        }
    }

    public static void setOwnerOnlyPermissions(File file) throws IOException {
        MachineState state = InstanceFactory.getInstance(MachineState.class);

        state.setOwnerOnlyPermissions(file);
    }

    public static void deleteFileException(File file) throws IOException {
        if (file.exists() && !file.delete()) {
            throw new IOException("Failed to delete \"" + file + "\"");
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
                clearTempFiles(child);
            }
        }
    }

    private static boolean clearTempFiles(File parent) {
        if (parent.isDirectory()) {
            boolean allChildren = true;
            File[] files = parent.listFiles();
            if (files != null) {
                for (File child : files) {
                    if (!child.getName().startsWith(".")) {
                        allChildren &= clearTempFiles(child);
                    }
                }
            }
            if (allChildren) {
                log.info("Deleting stale temp file \"{}\"", parent);
                deleteFile(parent);
            }
            return allChildren;
        } else {
            try {
                BasicFileAttributes attr = Files.readAttributes(parent.toPath(), BasicFileAttributes.class);
                // On Windows modified time is not necessarily updated until the file is closed so at least go with
                // creation time.
                long modifiedTime = Math.max(attr.creationTime().toMillis(), attr.lastModifiedTime().toMillis());

                if (modifiedTime < System.currentTimeMillis() - HOUR_IN_MILLIS) {
                    log.info("Deleting stale temp file \"{}\"", parent);
                    deleteFile(parent);
                    return true;
                }
            } catch (IOException exc) {
                log.warn("Failed to get last modified time for \"{}\"", parent, exc);
            }
            debug(() -> log.debug("Skipping temp file \"{}\"", parent));
            return false;
        }
    }

    public static Process executeProcess(String kind, String[] cmd) throws IOException {
        log.info("{} with command: \"{}\"", kind, String.join(" ", cmd));
        Process process = executeQuietProcess(kind, cmd);
        new Thread(() -> printOutput(kind, "output", process.getInputStream()), "ProcessOutput").start();
        return process;
    }

    public static Process executeQuietProcess(String kind, String[] cmd) throws IOException {
        Process process = Runtime.getRuntime().exec(cmd);
        new Thread(() -> {
            try {
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    log.info("{} process exited with exit code {}", kind, process.waitFor());
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "ProcessExit").start();
        new Thread(() -> printOutput(kind, "error output", process.getErrorStream()), "ProcessError").start();
        return process;
    }

    private static void printOutput(String kind, String name, InputStream errorStream) {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        try {
            errorStream.transferTo(data);
        } catch (IOException ignored) {
        }
        String output = data.toString(StandardCharsets.UTF_8);
        if (!output.isBlank())
            log.warn("{} process {}:\n{}", kind, name, output);
    }
}
