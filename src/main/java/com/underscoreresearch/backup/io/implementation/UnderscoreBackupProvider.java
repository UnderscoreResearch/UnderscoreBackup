package com.underscoreresearch.backup.io.implementation;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.Hash;
import com.underscoreresearch.backup.io.ConnectionLimiter;
import com.underscoreresearch.backup.io.IOIndex;
import com.underscoreresearch.backup.io.IOPlugin;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.service.SubscriptionLackingException;
import com.underscoreresearch.backup.service.api.BackupApi;
import com.underscoreresearch.backup.service.api.invoker.ApiException;
import com.underscoreresearch.backup.service.api.model.DownloadUrl;
import com.underscoreresearch.backup.service.api.model.FileListResponse;
import com.underscoreresearch.backup.service.api.model.SourceResponse;
import com.underscoreresearch.backup.service.api.model.UploadUrl;
import com.underscoreresearch.backup.utils.ProcessingStoppedException;
import com.underscoreresearch.backup.utils.RetryUtils;
import lombok.extern.slf4j.Slf4j;
import org.takes.HttpException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.underscoreresearch.backup.io.implementation.UnderscoreBackupProvider.UB_TYPE;
import static com.underscoreresearch.backup.manifest.implementation.ServiceManagerImpl.extractApiMessage;
import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.RetryUtils.retry;

@IOPlugin(UB_TYPE)
@Slf4j
public class UnderscoreBackupProvider implements IOIndex {

    public static final String UB_TYPE = "UB";
    private static final Duration IDENTITY_TIMEOUT = Duration.ofSeconds(30);
    private static final String TIMEOUT_MESSAGE = "Failed to transfer file in proscribed time";
    private static final long MAX_TIMEOUT_SECONDS = 60;
    private static final int S3_UPLOAD_TIMEOUT = (int) Duration.ofSeconds(10).toMillis();
    private static final Map<String, Instant> verifiedSources = new HashMap<>();
    private static final String SOURCE_NO_LONGER_IN_SERVICE = "Source no longer exists in service";
    private static final Duration NEGATIVE_CACHE_TIMEOUT = Duration.ofSeconds(10);
    private static final String MOVED_RETRY_MESSAGE = "File has been moved, retrying";
    private static final RetryUtils.ShouldRetry RETRY_PRE_SIGNING = new RetryUtils.ShouldRetry() {
        @Override
        public boolean shouldRetry(Exception exc) {
            if (exc instanceof IOException) {
                return TIMEOUT_MESSAGE.equals(exc.getMessage()) || MOVED_RETRY_MESSAGE.equals(exc.getMessage());
            }
            return false;
        }

        @Override
        public RetryUtils.LogOrWait logAndWait(Exception exc) {
            if (exc instanceof IOException && MOVED_RETRY_MESSAGE.equals(exc.getMessage())) {
                return RetryUtils.LogOrWait.NONE;
            }
            return RetryUtils.LogOrWait.LOG_AND_WAIT;
        }
    };
    private final ConnectionLimiter limiter;
    private String region;
    private String sourceId;
    private String shareId;
    private ServiceManager serviceManager;


    public UnderscoreBackupProvider(BackupDestination destination) {
        region = destination.getEndpointUri();
        String[] parts = region.split("/");
        if (parts.length == 3) {
            region = parts[0];
            sourceId = parts[1];
            shareId = parts[2];
        } else if (parts.length == 2) {
            region = parts[0];
            sourceId = parts[1];
        }
        limiter = new ConnectionLimiter(destination);
    }

    public static String getRegion(String endpointUri) {
        if (endpointUri == null)
            return null;

        String region = endpointUri;
        String[] parts = region.split("/");
        if (parts.length == 3) {
            region = parts[0];
        }
        return region;
    }

    public static <T> T s3Retry(Callable<T> callable) throws IOException {
        try {
            return retry(callable, (exc) -> {
                if (exc instanceof HttpException httpException) {
                    return httpException.code() != 404;
                }
                if (exc.getMessage().equals(MOVED_RETRY_MESSAGE)) {
                    return false;
                }
                return true;
            });
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private static boolean isPresignTimeout(Stopwatch timer) {
        return timer.elapsed(TimeUnit.SECONDS) > MAX_TIMEOUT_SECONDS;
    }

    private <T> T callRetry(boolean limited, ServiceManager.ApiFunction<T> callable) throws IOException {
        try {
            return getServiceManager().callApi(region, new ServiceManager.ApiFunction<>() {
                @Override
                public boolean shouldRetryMissing(String region, ApiException apiException) {
                    // This means the object and not the source is missing. This should not have issues
                    // with replication.
                    if (apiException.getResponseBody().contains("Object not found"))
                        return false;

                    return isUnverifiedSource();
                }

                private boolean isUnverifiedSource() {
                    synchronized (verifiedSources) {
                        Instant instant = verifiedSources.get(getCacheKey());
                        if (instant != null && instant.plus(NEGATIVE_CACHE_TIMEOUT).isBefore(Instant.now()))
                            return false;
                    }
                    return true;
                }

                @Override
                public RetryUtils.LogOrWait logAndWait(String region, ApiException apiException) {
                    if (apiException.getCode() == 404 && shouldRetryMissing(region, apiException)) {
                        return RetryUtils.LogOrWait.WAIT;
                    }
                    return RetryUtils.LogOrWait.LOG_AND_WAIT;
                }

                @Override
                public boolean shouldRetry() {
                    return callable.shouldRetry();
                }

                private T internalCall(BackupApi api) throws ApiException {
                    T ret = callable.call(api);
                    synchronized (verifiedSources) {
                        verifiedSources.putIfAbsent(getCacheKey(), Instant.now());
                    }
                    return ret;
                }

                @Override
                public T call(BackupApi api) throws ApiException {
                    if (limited) {
                        try {
                            return limiter.call(() -> internalCall(api));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    } else {
                        return internalCall(api);
                    }
                }
            });
        } catch (ApiException e) {
            if (e.getCode() == 404)
                throw new IOException(SOURCE_NO_LONGER_IN_SERVICE);
            if (e.getCode() == 402)
                throw new SubscriptionLackingException(extractApiMessage(e));
            if (e.getCode() == 401 || e.getCode() == 403)
                throw new IOException("Authorization to service is missing or invalid");
            throw new IOException(e);
        }
    }

    private String getSourceId() {
        if (sourceId == null) {
            sourceId = getServiceManager().getSourceId();
        }
        return sourceId;
    }

    @Override
    public List<String> availableKeys(String prefix) throws IOException {
        return new ArrayList<>();
    }

    @Override
    public List<String> availableLogs(String lastSyncedFile, boolean all) throws IOException {
        debug(() -> log.debug("Getting available logs"));
        FileListResponse response = callRetry(true, (api) -> api.listLogFiles(getSourceId(), lastSyncedFile, shareId));

        // Almost all backups will be less than one page, so we can optimize for that case
        if (!all || Boolean.TRUE.equals(response.getCompleted()) || response.getFiles().isEmpty()) {
            return response.getFiles();
        }

        // Lots of log files so lets go through all the pages.
        List<String> ret = Lists.newArrayList(response.getFiles());
        do {
            response = callRetry(true, (api) -> api.listLogFiles(getSourceId(), ret.getLast(), shareId));
            ret.addAll(response.getFiles());
        } while (Boolean.FALSE.equals(response.getCompleted()));
        return ret;
    }

    private synchronized ServiceManager getServiceManager() {
        if (serviceManager == null) {
            serviceManager = InstanceFactory.getInstance(ServiceManager.class);
        }
        return serviceManager;
    }

    @Override
    public boolean rebuildAvailable() throws IOException {
        debug(() -> log.debug("Checking rebuild available"));
        if (getServiceManager().getToken() != null) {
            SourceResponse sourceResponse = callRetry(true, (api) -> api.getSource(getSourceId()));
            return sourceResponse.getEncryptionMode() != null && sourceResponse.getDestination() != null && sourceResponse.getKey() != null;
        } else {
            return false;
        }
    }

    @Override
    public String upload(String suggestedKey, byte[] data) throws IOException {
        String hash = Hash.hash(data);
        int size = data.length;

        final String useKey = normalizeKey(suggestedKey);
        debug(() -> log.debug("Uploading \"" + useKey + "\""));

        try {
            RetryUtils.retry(() -> limiter.call(() -> {
                Stopwatch timer = Stopwatch.createStarted();
                UploadUrl response = callRetry(false, (api) -> api.uploadFile(getSourceId(), useKey, hash, size, shareId));
                if (response.getLocation() != null) {
                    String ret = s3Retry(() -> {
                        if (isPresignTimeout(timer)) {
                            return null;
                        }
                        debug(() -> log.debug("Uploading \"{}\" for path \"{}}\"", response.getLocation(), useKey));
                        URL url = new URI(response.getLocation()).toURL();
                        HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
                        httpCon.setConnectTimeout(10000);
                        httpCon.setDoOutput(true);
                        httpCon.setConnectTimeout(S3_UPLOAD_TIMEOUT);
                        httpCon.setReadTimeout(S3_UPLOAD_TIMEOUT);
                        httpCon.setRequestMethod("PUT");
                        try (OutputStream stream = httpCon.getOutputStream()) {
                            stream.write(data);
                        }
                        if (httpCon.getResponseCode() == 403 && isPresignTimeout(timer)) {
                            return null;
                        }
                        if (httpCon.getResponseCode() != 200) {
                            throw new IOException("Failed to upload data with status code " + httpCon.getResponseCode() + ": \"" + httpCon.getResponseMessage() + "\"");
                        }
                        debug(() -> log.debug("Completed upload of \"" + useKey + "\""));
                        return "success";
                    });
                    if (ret == null || isPresignTimeout(timer)) {
                        throw new IOException(TIMEOUT_MESSAGE);
                    }
                }
                return null;
            }), RETRY_PRE_SIGNING);
        } catch (IOException | ProcessingStoppedException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return useKey;
    }

    private String normalizeKey(String suggestedKey) {
        if (suggestedKey.startsWith("/")) {
            return suggestedKey.substring(1);
        } else {
            return suggestedKey;
        }
    }

    @Override
    public byte[] download(String key) throws IOException {
        // This is needed because it is quite common to read this immediately after writing it and
        // the service only has eventual consistency.
        final String useKey = normalizeKey(key);

        debug(() -> log.debug("Downloading \"" + useKey + "\""));

        try {
            AtomicInteger missingRetryCount = new AtomicInteger(0);

            return RetryUtils.retry(() -> limiter.call(() -> {
                Stopwatch timer = Stopwatch.createStarted();
                DownloadUrl response = callRetry(false, (api) -> api.getFile(getSourceId(), useKey, shareId));
                byte[] ret = s3Retry(() -> {
                    if (isPresignTimeout(timer)) {
                        return null;
                    }
                    debug(() -> log.debug("Fetching \"{}\" for path \"{}}\"", response.getLocation(), useKey));
                    URL url = new URI(response.getLocation()).toURL();
                    HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
                    httpCon.setConnectTimeout(10000);
                    httpCon.setDoInput(true);
                    if (httpCon.getResponseCode() == 403 && isPresignTimeout(timer)) {
                        return null;
                    }
                    // We can expect at most one retry for a 404 because of incoming moving of data
                    if (httpCon.getResponseCode() == 404 && missingRetryCount.getAndIncrement() == 0) {
                        throw new IOException(MOVED_RETRY_MESSAGE);
                    }
                    if (httpCon.getResponseCode() != 200) {
                        throw new HttpException(httpCon.getResponseCode(), "Failed to download data with status code " + httpCon.getResponseCode() + ": \"" + httpCon.getResponseMessage() + "\"");
                    }
                    byte[] data;
                    try (InputStream stream = httpCon.getInputStream()) {
                        data = IOUtils.readAllBytes(stream);
                    }
                    return data;
                });
                if (ret == null) {
                    throw new IOException(TIMEOUT_MESSAGE);
                }
                return ret;
            }), RETRY_PRE_SIGNING);
        } catch (IOException | ProcessingStoppedException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public boolean exists(String key) throws IOException {
        final String useKey = normalizeKey(key);

        try {
            boolean ret;
            DownloadUrl response = null;
            try {
                response = callRetry(true, (api) -> api.getFile(getSourceId(), useKey, shareId));
            } catch (IOException exc) {
                if (!exc.getMessage().equals(SOURCE_NO_LONGER_IN_SERVICE)) {
                    throw exc;
                }
            }
            ret = response != null && (response.getDeleted() == null || !response.getDeleted());
            debug(() -> log.debug("Exists \"{}\" ({})", key, ret));
            return ret;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public void delete(String key) throws IOException {
        final String useKey = normalizeKey(key);

        callRetry(true, (api) -> api.deleteFile(getSourceId(), useKey, shareId));
        debug(() -> log.debug("Deleted \"{}\"", key));
    }

    @Override
    public void checkCredentials(boolean readonly) throws IOException {
        try {
            debug(() -> log.debug("Checking credentials"));
            if (!getServiceManager().activeSubscription()) {
                throw new IOException("No active subscription");
            }
            if (shareId != null) {
                getServiceManager().callApi(region, new ServiceManager.ApiFunction<>() {
                    public boolean shouldRetry() {
                        return false;
                    }

                    @Override
                    public Object call(BackupApi api) throws ApiException {
                        return api.getShare(getSourceId(), shareId);
                    }
                });
            } else {
                getServiceManager().callApi(region, new ServiceManager.ApiFunction<>() {
                    @Override
                    public boolean shouldRetry() {
                        return false;
                    }

                    @Override
                    public Object call(BackupApi api) throws ApiException {
                        return api.getSource(getSourceId());
                    }
                });
            }
            synchronized (verifiedSources) {
                verifiedSources.putIfAbsent(getCacheKey(), Instant.now());
            }
        } catch (ApiException e) {
            throw new IOException(e);
        }
    }

    @Override
    public String getCacheKey() {
        String ret = region + "." + getSourceId();
        if (shareId != null) {
            return ret + "." + shareId;
        }
        return ret;
    }
}
