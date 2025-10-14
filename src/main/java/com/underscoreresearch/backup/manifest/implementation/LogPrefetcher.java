package com.underscoreresearch.backup.manifest.implementation;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.underscoreresearch.backup.encryption.Encryptor;
import com.underscoreresearch.backup.encryption.IdentityKeys;
import com.underscoreresearch.backup.model.BackupConfiguration;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.underscoreresearch.backup.configuration.RestoreModule.getGlobalDownloadThreads;
import static com.underscoreresearch.backup.utils.log.LogUtil.debug;

/**
 * Prefetches log files asynchronously to improve performance.
 * Downloads and decrypts log files in the background while they are being processed.
 */
@Slf4j
public class LogPrefetcher {

    private static final long MAX_WAIT = 10;
    private final LinkedBlockingDeque<String> logFiles;
    private final Downloader downloadData;
    private final Encryptor encryptor;
    private final IdentityKeys.PrivateKeys privateKey;
    private final ExecutorService executor;
    private final Integer maxConcurrency;
    private final Map<String, Holder> data = new HashMap<>();
    private final HashSet<String> syncCompletions = new HashSet<>();
    private final AtomicBoolean stop = new AtomicBoolean(false);
    private final AtomicReference<Throwable> error = new AtomicReference<>();

    /**
     * Constructor for LogPrefetcher.
     *
     * @param logFiles List of log files to prefetch
     * @param configuration The backup configuration
     * @param downloadData The downloader for log files
     * @param encryptor The encryptor for decrypting log files
     * @param privateKey The private key for decryption
     */
    public LogPrefetcher(List<String> logFiles, BackupConfiguration configuration, Downloader downloadData,
                         Encryptor encryptor, IdentityKeys.PrivateKeys privateKey) {
        this.logFiles = new LinkedBlockingDeque<String>(logFiles);
        this.downloadData = downloadData;
        this.encryptor = encryptor;
        this.privateKey = privateKey;
        maxConcurrency = getGlobalDownloadThreads(configuration);
        executor = Executors.newFixedThreadPool(maxConcurrency,
                new ThreadFactoryBuilder().setNameFormat(getClass().getSimpleName() + "-%d").build());
    }

    /**
     * Start prefetching log files.
     * Creates worker threads to download and decrypt log files.
     */
    public void start() {
        try {
            for (int i = 0; i < maxConcurrency; i++) {
                executor.submit(() -> {
                    try {
                        while (true) {
                            try {
                                synchronized (data) {
                                    while (data.size() > maxConcurrency && !shouldComplete()) {
                                        try {
                                            data.wait();
                                        } catch (InterruptedException e) {
                                            Thread.currentThread().interrupt();
                                            setError(e);
                                            return;
                                        }
                                    }
                                }
                                if (shouldComplete()) {
                                    return;
                                }
                                String finalFile = logFiles.removeFirst();

                                debug(() -> log.debug("Fetching log file \"{}\"", finalFile));
                                try {
                                    byte[] fileData = downloadData.downloadFile(finalFile);
                                    byte[] unencryptedData = encryptor.decodeBlock(null, fileData, privateKey);
                                    addResult(finalFile, new Holder(unencryptedData));
                                } catch (Exception exc) {
                                    addResult(finalFile, new Holder(exc));
                                }
                            } catch (NoSuchElementException exc) {
                                return;
                            }
                        }
                    } catch (Throwable e) {
                        synchronized (data) {
                            setError(e);
                        }
                    }
                });
            }
        } catch (Throwable e) {
            synchronized (data) {
                setError(e);
            }
            executor.shutdownNow();
        }
    }

    /**
     * Add a result to the data map.
     *
     * @param finalFile The log file name
     * @param holder The holder containing the data or exception
     */
    private void addResult(String finalFile, Holder holder) {
        synchronized (data) {
            if (syncCompletions.contains(finalFile)) {
                log.info("Discarded log file \"{}\" as it was downloaded synchronously", finalFile);
                return;
            }
            data.put(finalFile, holder);
            data.notifyAll();
        }
    }

    /**
     * Set an error and notify waiting threads.
     *
     * @param e The error to set
     */
    private void setError(Throwable e) {
        error.set(e);
        data.notifyAll();
    }

    /**
     * Check if the prefetcher should complete.
     *
     * @return True if the prefetcher should complete, false otherwise
     */
    private boolean shouldComplete() {
        return stop.get() || error.get() != null;
    }

    /**
     * Stop prefetching and shutdown the executor.
     */
    public void stop() {
        stop.set(true);
        executor.shutdownNow();
    }

    /**
     * Shutdown the executor if not already stopped.
     */
    public void shutdown() {
        if (!stop.get()) {
            executor.shutdown();
        }
    }

    /**
     * Get a log file by ID.
     * Waits for the log file to be prefetched, or downloads it synchronously if it takes too long.
     *
     * @param logId The log file ID
     * @return The decrypted log file data
     * @throws IOException If there's an error getting the log file
     */
    public byte[] getLog(String logId) throws IOException {
        synchronized (data) {
            throwError();
            Holder ret = data.remove(logId);
            Stopwatch stopwatch = Stopwatch.createStarted();
            while (ret == null) {
                try {
                    debug(() -> log.debug("Waiting for log file \"{}\"", logId));
                    data.wait(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    setError(e);
                }
                throwError();
                ret = data.remove(logId);
                if (ret == null && stopwatch.elapsed(TimeUnit.SECONDS) > MAX_WAIT) {
                    log.warn("Waited unsuccessfully for log file \"{}\" for {} seconds, downloading synchronously", logId, MAX_WAIT);
                    //noinspection ResultOfMethodCallIgnored
                    logFiles.remove(logId);
                    syncCompletions.add(logId);

                    try {
                        ret = new Holder(encryptor.decodeBlock(null,
                                downloadData.downloadFile(logId), privateKey));
                    } catch (Exception exc) {
                        ret = new Holder(exc);
                    }
                }
            }
            data.notifyAll();
            if (ret.exc != null) {
                if (ret.exc instanceof IOException ioException)
                    throw ioException;
                throw new IOException(ret.exc);
            }
            return ret.data;
        }
    }

    /**
     * Throw an error if one is set.
     *
     * @throws IOException If there's an error
     */
    private void throwError() throws IOException {
        Throwable e = error.get();
        if (e instanceof IOException ioException)
            throw ioException;
        if (e instanceof RuntimeException runtimeException)
            throw runtimeException;
        if (e != null)
            throw new RuntimeException(e);
    }

    /**
     * Interface for downloading log files.
     */
    public interface Downloader {
        /**
         * Download a log file.
         *
         * @param file The log file name
         * @return The downloaded log file data
         * @throws IOException If there's an error downloading the file
         */
        byte[] downloadFile(String file) throws IOException;
    }

    /**
     * Holder for log file data or exception.
     */
    private static class Holder {
        private byte[] data;
        private Throwable exc;

        /**
         * Constructor for successful download.
         *
         * @param data The log file data
         */
        public Holder(byte[] data) {
            this.data = data;
        }

        /**
         * Constructor for failed download.
         *
         * @param exc The exception that occurred
         */
        public Holder(Throwable exc) {
            this.exc = exc;
        }
    }
}
