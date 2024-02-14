package com.underscoreresearch.backup.manifest.implementation;

import static com.underscoreresearch.backup.configuration.RestoreModule.getGlobalDownloadThreads;
import static com.underscoreresearch.backup.utils.LogUtil.debug;

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

import lombok.extern.slf4j.Slf4j;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.underscoreresearch.backup.encryption.EncryptionKey;
import com.underscoreresearch.backup.encryption.Encryptor;
import com.underscoreresearch.backup.model.BackupConfiguration;

@Slf4j
public class LogPrefetcher {

    private static final long MAX_WAIT = 10;
    private final LinkedBlockingDeque<String> logFiles;
    private final Downloader downloadData;
    private final Encryptor encryptor;
    private final EncryptionKey.PrivateKey privateKey;
    private final ExecutorService executor;
    private final Integer maxConcurrency;
    private final Map<String, Holder> data = new HashMap<>();
    private final HashSet<String> syncCompletions = new HashSet<>();
    private final AtomicBoolean stop = new AtomicBoolean(false);
    private final AtomicReference<Throwable> error = new AtomicReference<>();

    public LogPrefetcher(List<String> logFiles, BackupConfiguration configuration, Downloader downloadData, Encryptor encryptor, EncryptionKey.PrivateKey privateKey) {
        this.logFiles = new LinkedBlockingDeque<>(logFiles);
        this.downloadData = downloadData;
        this.encryptor = encryptor;
        this.privateKey = privateKey;
        maxConcurrency = getGlobalDownloadThreads(configuration);
        executor = Executors.newFixedThreadPool(maxConcurrency,
                new ThreadFactoryBuilder().setNameFormat(getClass().getSimpleName() + "-%d").build());
    }

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

    private void setError(Throwable e) {
        error.set(e);
        data.notifyAll();
    }

    private boolean shouldComplete() {
        return stop.get() || error.get() != null;
    }

    public void stop() {
        stop.set(true);
        executor.shutdownNow();
    }

    public void shutdown() {
        if (!stop.get()) {
            executor.shutdown();
        }
    }

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
                    setError(e);
                }
                throwError();
                ret = data.remove(logId);
                if (ret == null && stopwatch.elapsed(TimeUnit.SECONDS) > MAX_WAIT) {
                    log.warn("Waited unsuccessfully for log file \"{}\" for {} seconds, downloading synchronously", logId, MAX_WAIT);
                    //noinspection ResultOfMethodCallIgnored
                    logFiles.remove(logId);
                    syncCompletions.add(logId);
                    ret = new Holder(downloadData.downloadFile(logId));
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

    private void throwError() throws IOException {
        Throwable e = error.get();
        if (e instanceof IOException ioException)
            throw ioException;
        if (e instanceof RuntimeException runtimeException)
            throw runtimeException;
        if (e != null)
            throw new RuntimeException(e);
    }

    public interface Downloader {
        byte[] downloadFile(String file) throws IOException;
    }

    private static class Holder {
        private byte[] data;
        private Throwable exc;

        public Holder(byte[] data) {
            this.data = data;
        }

        public Holder(Throwable exc) {
            this.exc = exc;
        }
    }
}
