package com.underscoreresearch.backup.io;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.utils.log.PausedStatusLogger;
import com.underscoreresearch.backup.utils.ProcessingStoppedException;
import com.underscoreresearch.backup.machinestate.MachineState;
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

import static com.underscoreresearch.backup.utils.log.LogUtil.debug;
import static com.underscoreresearch.backup.utils.RetryUtils.DEFAULT_BASE;

/**
 * Utility class for IO operations.
 * Provides methods for file operations, internet connectivity checks, and process execution.
 */
@Slf4j
public final class IOUtils {
    public static final long INTERNET_WAIT = 1000;
    private static final Duration INTERNET_SUCCESS_CACHE = Duration.ofSeconds(2);
    private static final int DEFAULT_BUFFER_SIZE = 8192;
    private static final AtomicBoolean waitingForInternetMessage = new AtomicBoolean();
    private static final long HOUR_IN_MILLIS = Duration.ofHours(1).toMillis();
    private static Instant internetSuccessfulUntil = null;

    /**
     * Read all bytes from an input stream into a byte array.
     *
     * @param stream The input stream to read from
     * @return The bytes read from the stream
     * @throws IOException If there's an error reading from the stream
     */
    public static byte[] readAllBytes(InputStream stream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        byte[] buffer = new byte[16384];
        int length;
        while ((length = stream.read(buffer, 0, buffer.length)) > 0) {
            outputStream.write(buffer, 0, length);
        }
        return outputStream.toByteArray();
    }

    /**
     * Copy data from an input stream to an output stream.
     *
     * @param in The input stream to read from
     * @param out The output stream to write to
     * @return The number of bytes copied
     * @throws IOException If there's an error during the copy operation
     */
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

    /**
     * Check if internet connectivity is available.
     * Uses a cached result if a recent check was successful.
     *
     * @return True if internet is available, false otherwise
     */
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

    /**
     * Internal method to check internet connectivity by making a request to example.com.
     *
     * @return True if the request succeeds, false otherwise
     */
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

    /**
     * Execute a callable, waiting for internet connectivity if needed.
     * Will retry the callable if it fails due to internet connectivity issues.
     *
     * @param callable The callable to execute
     * @param immediateFailOnShutdown Whether to fail immediately if shutdown is in progress
     * @param <T> The return type of the callable
     * @return The result of the callable
     * @throws Exception If the callable throws an exception
     */
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

    /**
     * Check if shutdown is in progress and throw an exception if it is.
     *
     * @throws ProcessingStoppedException If shutdown is in progress
     */
    private static void failOnShutdown() {
        if (InstanceFactory.isShutdown())
            throw new ProcessingStoppedException("Shutting down");
    }

    /**
     * Create a directory if it doesn't exist.
     * Logs a warning or debug message if creation fails.
     *
     * @param file The directory to create
     * @param warning Whether to log a warning or debug message on failure
     */
    public static void createDirectory(File file, boolean warning) {
        if (!file.mkdirs() && !file.isDirectory()) {
            if (warning)
                log.warn("Failed to create directory \"{}\"", file);
            else
                debug(() -> log.debug("Failed to create directory \"{}\"", file));
        }
    }

    /**
     * Delete a file, logging a warning if deletion fails.
     *
     * @param file The file to delete
     */
    public static void deleteFile(File file) {
        try {
            deleteFileException(file);
        } catch (IOException e) {
            log.warn(e.getMessage());
        }
    }

    /**
     * Set permissions on a file so that only the owner can access it.
     *
     * @param file The file to set permissions on
     * @throws IOException If there's an error setting permissions
     */
    public static void setOwnerOnlyPermissions(File file) throws IOException {
        MachineState state = InstanceFactory.getInstance(MachineState.class);

        state.setOwnerOnlyPermissions(file);
    }

    /**
     * Delete a file, throwing an exception if deletion fails.
     *
     * @param file The file to delete
     * @throws IOException If the file exists but cannot be deleted
     */
    public static void deleteFileException(File file) throws IOException {
        if (file.exists() && !file.delete()) {
            throw new IOException("Failed to delete \"" + file + "\"");
        }
    }

    /**
     * Delete the contents of a directory recursively.
     *
     * @param file The directory whose contents should be deleted
     */
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

    /**
     * Clear temporary files created by the application.
     * Looks for files with names starting with "underscorebackup" in the system temp directory.
     */
    public static void clearTempFiles() {
        File file = new File(System.getProperty("java.io.tmpdir"));
        File[] files = file.listFiles(pathname -> pathname.getName().toLowerCase().startsWith("underscorebackup"));
        if (files != null) {
            for (File child : files) {
                clearTempFiles(child);
            }
        }
    }

    /**
     * Helper method for clearing temporary files.
     * Recursively checks if files are stale and deletes them if they are.
     *
     * @param parent The file or directory to check
     * @return True if the file was deleted or all children were deleted
     */
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

    /**
     * Execute a process and log its output.
     *
     * @param kind A description of the process for logging
     * @param cmd The command to execute
     * @return The process object
     * @throws IOException If there's an error starting the process
     */
    public static Process executeProcess(String kind, String[] cmd) throws IOException {
        log.info("{} with command: \"{}\"", kind, String.join(" ", cmd));
        Process process = executeQuietProcess(kind, cmd);
        new Thread(() -> printOutput(kind, "output", process.getInputStream()), "ProcessOutput").start();
        return process;
    }

    /**
     * Execute a process without logging its standard output.
     * Error output is still logged.
     *
     * @param kind A description of the process for logging
     * @param cmd The command to execute
     * @return The process object
     * @throws IOException If there's an error starting the process
     */
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

    /**
     * Print the output from a process stream.
     *
     * @param kind A description of the process for logging
     * @param name The name of the stream (e.g., "output" or "error output")
     * @param errorStream The stream to read from
     */
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
