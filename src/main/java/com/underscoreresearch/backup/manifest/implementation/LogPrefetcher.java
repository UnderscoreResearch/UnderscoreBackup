package com.underscoreresearch.backup.manifest.implementation;

import static com.underscoreresearch.backup.configuration.RestoreModule.getGlobalDownloadThreads;
import static com.underscoreresearch.backup.utils.LogUtil.debug;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.slf4j.Slf4j;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.underscoreresearch.backup.encryption.EncryptionKey;
import com.underscoreresearch.backup.encryption.Encryptor;
import com.underscoreresearch.backup.model.BackupConfiguration;

@Slf4j
public class LogPrefetcher {

    private final LinkedBlockingDeque<String> logFiles;
    private final Downloader downloadData;
    private final Encryptor encryptor;
    private final EncryptionKey.PrivateKey privateKey;
    private final ExecutorService executor;
    private final Integer maxConcurrency;
    private final Map<String, Holder> data = new HashMap<>();
    private final AtomicBoolean stop = new AtomicBoolean(false);
    private final AtomicReference<Exception> error = new AtomicReference<>();

    public LogPrefetcher(List<String> logFiles, BackupConfiguration configuration, Downloader downloadData, Encryptor encryptor, EncryptionKey.PrivateKey privateKey) {
        this.logFiles = new LinkedBlockingDeque<String>(logFiles);
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
                                    while (data.size() > maxConcurrency) {
                                        try {
                                            data.wait();
                                        } catch (InterruptedException e) {
                                            error.set(e);
                                            return;
                                        }
                                    }
                                }
                                String finalFile = logFiles.removeFirst();
                                if (stop.get() || error.get() != null) {
                                    return;
                                }

                                debug(() -> log.debug("Fetching log file \"{}\"", finalFile));
                                try {
                                    byte[] fileData = downloadData.downloadFile(finalFile);
                                    byte[] unencryptedData = encryptor.decodeBlock(null, fileData, privateKey);
                                    synchronized (data) {
                                        data.put(finalFile, new Holder(unencryptedData));
                                        data.notifyAll();
                                    }
                                } catch (Exception exc) {
                                    synchronized (data) {
                                        data.put(finalFile, new Holder(exc));
                                        data.notifyAll();
                                    }
                                }
                            } catch (NoSuchElementException exc) {
                                return;
                            }
                        }
                    } catch (Exception e) {
                        synchronized (data) {
                            error.set(e);
                            data.notifyAll();
                        }
                    }
                });
            }
        } catch (Exception e) {
            synchronized (data) {
                error.set(e);
                data.notifyAll();
            }
            executor.shutdownNow();
        }
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
            while (ret == null) {
                try {
                    debug(() -> log.debug("Waiting for log file \"{}\"", logId));
                    data.wait();
                } catch (InterruptedException e) {
                    error.set(e);
                }
                throwError();
                ret = data.remove(logId);
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
        Exception e = error.get();
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
        private Exception exc;

        public Holder(byte[] data) {
            this.data = data;
        }

        public Holder(Exception exc) {
            this.exc = exc;
        }
    }
}
