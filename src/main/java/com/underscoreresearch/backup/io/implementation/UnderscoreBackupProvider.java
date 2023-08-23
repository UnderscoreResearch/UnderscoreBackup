package com.underscoreresearch.backup.io.implementation;

import static com.underscoreresearch.backup.io.implementation.UnderscoreBackupProvider.UB_TYPE;
import static com.underscoreresearch.backup.manifest.implementation.BaseManifestManagerImpl.IDENTITY_MANIFEST_LOCATION;
import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.RetryUtils.retry;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.Hash;
import com.underscoreresearch.backup.io.IOIndex;
import com.underscoreresearch.backup.io.IOPlugin;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.service.api.BackupApi;
import com.underscoreresearch.backup.service.api.invoker.ApiException;
import com.underscoreresearch.backup.service.api.model.DownloadUrl;
import com.underscoreresearch.backup.service.api.model.FileListResponse;
import com.underscoreresearch.backup.service.api.model.SourceResponse;
import com.underscoreresearch.backup.service.api.model.UploadUrl;
import com.underscoreresearch.backup.utils.RetryUtils;

@IOPlugin(UB_TYPE)
@Slf4j
public class UnderscoreBackupProvider implements IOIndex {
    public static final String UB_TYPE = "UB";
    private static final Duration IDENTITY_TIMEOUT = Duration.ofSeconds(30);
    private static final String TIMEOUT_MESSAGE = "Failed to transfer file in proscribed time";
    private static final long START_TIMEOUT_MINUTES = 1;
    private static final long MAX_TIMEOUT_MINUTES = 1;
    private static final int S3_UPLOAD_TIMEOUT = (int) Duration.ofSeconds(10).toMillis();
    private static final HashSet<String> verifiedSources = new HashSet<>();
    private static String cachedIdentityKey;
    private static Instant cachedIdentityTimeout;
    private static byte[] cachedIdentity;
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
            return retry(callable, null);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private <T> T callRetry(ServiceManager.ApiFunction<T> callable) throws IOException {
        try {
            return getServiceManager().callApi(region, new ServiceManager.ApiFunction<>() {
                @Override
                public boolean shouldRetryMissing(String region) {
                    synchronized (verifiedSources) {
                        if (verifiedSources.contains(getVerifiedKey()))
                            return false;
                    }
                    return region != null && !region.equals("us-west");
                }

                @Override
                public boolean shouldRetry() {
                    return callable.shouldRetry();
                }

                @Override
                public T call(BackupApi api) throws ApiException {
                    T ret = callable.call(api);
                    synchronized (verifiedSources) {
                        verifiedSources.add(getVerifiedKey());
                    }
                    return ret;
                }
            });
        } catch (ApiException e) {
            if (e.getCode() == 404)
                throw new IOException("Source no longer exists in service");
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
    public List<String> availableLogs(String lastSyncedFile) throws IOException {
        debug(() -> log.debug("Getting available logs"));
        FileListResponse response = callRetry((api) -> api.listLogFiles(getSourceId(), lastSyncedFile, shareId));

        // Almost all backups will be less than one page, so we can optimize for that case
        if (Boolean.TRUE.equals(response.getCompleted())) {
            return response.getFiles();
        }

        // Lots of log files so lets go through all the pages.
        List<String> ret = Lists.newArrayList(response.getFiles());
        do {
            response = callRetry((api) -> api.listLogFiles(getSourceId(), ret.get(ret.size() - 1), shareId));
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
            SourceResponse sourceResponse = callRetry((api) -> api.getSource(getSourceId()));
            return sourceResponse.getEncryptionMode() != null && sourceResponse.getDestination() != null && sourceResponse.getKey() != null;
        } else {
            return false;
        }
    }

    @Override
    public String upload(String suggestedKey, byte[] data) throws IOException {
        if (IDENTITY_MANIFEST_LOCATION.equals(suggestedKey)) {
            synchronized (this) {
                cachedIdentityKey = String.format("%s/%s", getSourceId(), region);
                cachedIdentity = data;
                cachedIdentityTimeout = Instant.now().plus(IDENTITY_TIMEOUT);
            }
            debug(() -> log.debug("Identity cached for {} {} until {}", getSourceId(), region, cachedIdentityTimeout));
        }

        String hash = Hash.hash(data);
        int size = data.length;

        final String useKey = normalizeKey(suggestedKey);
        debug(() -> log.debug("Uploading " + useKey));

        try {
            RetryUtils.retry(() -> {
                Stopwatch timer = Stopwatch.createStarted();
                UploadUrl response = callRetry((api) -> api.uploadFile(getSourceId(), useKey, hash, size, shareId));
                if (response.getLocation() != null) {
                    String ret = s3Retry(() -> {
                        if (timer.elapsed(TimeUnit.MINUTES) > START_TIMEOUT_MINUTES) {
                            return null;
                        }
                        URL url = new URL(response.getLocation());
                        HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
                        httpCon.setDoOutput(true);
                        httpCon.setConnectTimeout(S3_UPLOAD_TIMEOUT);
                        httpCon.setReadTimeout(S3_UPLOAD_TIMEOUT);
                        httpCon.setRequestMethod("PUT");
                        try (OutputStream stream = httpCon.getOutputStream()) {
                            stream.write(data);
                        }
                        if (httpCon.getResponseCode() == 403) {
                            return null;
                        }
                        if (httpCon.getResponseCode() != 200) {
                            throw new IOException("Failed to upload data with status code " + httpCon.getResponseCode() + ": " + httpCon.getResponseMessage());
                        }
                        return "success";
                    });
                    if (ret == null || timer.elapsed(TimeUnit.MINUTES) > MAX_TIMEOUT_MINUTES) {
                        throw new IOException(TIMEOUT_MESSAGE);
                    }
                }
                return null;
            }, this::retrySignedException);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return useKey;
    }

    private boolean retrySignedException(Exception exc) {
        if (exc instanceof IOException) {
            return TIMEOUT_MESSAGE.equals(exc.getMessage());
        }
        return false;
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

        if (IDENTITY_MANIFEST_LOCATION.equals(key)) {
            String identityKey = String.format("%s/%s", getSourceId(), region);
            debug(() -> log.debug("Identity cache check {} {} until {}", region, getSourceId(), cachedIdentityTimeout));
            synchronized (this) {
                if (identityKey.equals(cachedIdentityKey) && Instant.now().isBefore(cachedIdentityTimeout)) {
                    debug(() -> log.debug("Downloading cached " + useKey));
                    return cachedIdentity;
                }
            }
        }
        debug(() -> log.debug("Downloading " + useKey));

        try {
            return RetryUtils.retry(() -> {
                Stopwatch timer = Stopwatch.createStarted();
                DownloadUrl response = callRetry((api) -> api.getFile(getSourceId(), useKey, shareId));
                byte[] ret = s3Retry(() -> {
                    if (timer.elapsed(TimeUnit.MINUTES) > START_TIMEOUT_MINUTES) {
                        return null;
                    }
                    URL url = new URL(response.getLocation());
                    HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
                    httpCon.setDoInput(true);
                    byte[] data;
                    try (InputStream stream = httpCon.getInputStream()) {
                        data = IOUtils.readAllBytes(stream);
                    }
                    if (httpCon.getResponseCode() == 403) {
                        return null;
                    }
                    if (httpCon.getResponseCode() != 200) {
                        throw new IOException("Failed to download data with status code " + httpCon.getResponseCode() + ": " + httpCon.getResponseMessage());
                    }
                    return data;
                });
                if (ret == null) {
                    throw new IOException(TIMEOUT_MESSAGE);
                }
                return ret;
            }, this::retrySignedException);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public void delete(String key) throws IOException {
        final String useKey = normalizeKey(key);
        debug(() -> log.debug("Deleting " + useKey));

        callRetry((api) -> api.deleteFile(getSourceId(), useKey, shareId));
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
                verifiedSources.add(getVerifiedKey());
            }
        } catch (ApiException e) {
            throw new IOException(e);
        }
    }

    private String getVerifiedKey() {
        String ret = region + "." + getSourceId();
        if (shareId != null) {
            return ret + "." + shareId;
        }
        return ret;
    }
}
