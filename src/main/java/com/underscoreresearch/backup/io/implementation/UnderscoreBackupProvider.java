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
import com.underscoreresearch.backup.manifest.implementation.ServiceManagerImpl;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.service.api.invoker.ApiException;
import com.underscoreresearch.backup.service.api.model.FileListResponse;
import com.underscoreresearch.backup.service.api.model.ResponseUrl;
import com.underscoreresearch.backup.service.api.model.SourceResponse;

@IOPlugin(UB_TYPE)
@Slf4j
public class UnderscoreBackupProvider implements IOIndex {
    public static final String UB_TYPE = "UB";
    private static final Duration IDENTITY_TIMEOUT = Duration.ofSeconds(30);
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
        }
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

    private static <T> T callRetry(Callable<T> callable) throws IOException {
        try {
            return ServiceManagerImpl.retryApi(callable);
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
        FileListResponse response = callRetry(() -> getServiceManager().getClient(region).listLogFiles(getSourceId(), lastSyncedFile, shareId));

        // Almost all backups will be less than one page, so we can optimize for that case
        if (response.getCompleted()) {
            return response.getFiles();
        }

        // Lots of log files so lets go through all the pages.
        List<String> ret = Lists.newArrayList(response.getFiles());
        do {
            response = callRetry(() -> getServiceManager().getClient(region).listLogFiles(getSourceId(), ret.get(ret.size() - 1), shareId));
            ret.addAll(response.getFiles());
        } while (!response.getCompleted());
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
            SourceResponse sourceResponse = callRetry(() -> getServiceManager().getClient(region).getSource(getSourceId()));
            return sourceResponse.getEncryptionMode() != null && sourceResponse.getDestination() != null && sourceResponse.getKey() != null;
        } else {
            return false;
        }
    }

    @Override
    public String upload(String suggestedKey, byte[] data) throws IOException {
        if (IDENTITY_MANIFEST_LOCATION.equals(suggestedKey)) {
            synchronized (this) {
                cachedIdentityKey = String.format("%s/%s", sourceId, region);
                cachedIdentity = data;
                cachedIdentityTimeout = Instant.now().plus(IDENTITY_TIMEOUT);
            }
        }

        String hash = Hash.hash(data);
        int size = data.length;

        final String useKey = normalizeKey(suggestedKey);
        debug(() -> log.debug("Uploading " + useKey));

        Stopwatch timer = Stopwatch.createStarted();
        ResponseUrl response = callRetry(() -> getServiceManager().getClient(region).uploadFile(getSourceId(), useKey, hash, size, shareId));
        if (response.getLocation() != null) {
            s3Retry(() -> {
                if (timer.elapsed(TimeUnit.MINUTES) > 2) {
                    return null;
                }
                URL url = new URL(response.getLocation());
                HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
                httpCon.setDoOutput(true);
                httpCon.setRequestMethod("PUT");
                try (OutputStream stream = httpCon.getOutputStream()) {
                    stream.write(data);
                }
                if (httpCon.getResponseCode() != 200) {
                    throw new IOException("Failed to upload data with status code " + httpCon.getResponseCode() + ": " + httpCon.getResponseMessage());
                }
                return null;
            });
        }
        if (timer.elapsed(TimeUnit.MINUTES) > 2) {
            throw new IOException("Failed to upload file in proscribed 2 minutes");
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
        if (IDENTITY_MANIFEST_LOCATION.equals(key)) {
            String identityKey = String.format("%s/%s", sourceId, region);
            synchronized (this) {
                if (identityKey.equals(cachedIdentityKey) && Instant.now().isBefore(cachedIdentityTimeout)) {
                    return cachedIdentity;
                }
            }
        }
        final String useKey = normalizeKey(key);
        debug(() -> log.debug("Downloading " + useKey));

        ResponseUrl response = callRetry(() -> getServiceManager().getClient(region).getFile(getSourceId(), useKey, shareId));
        return s3Retry(() -> {
            URL url = new URL(response.getLocation());
            HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
            httpCon.setDoInput(true);
            byte[] data;
            try (InputStream stream = httpCon.getInputStream()) {
                data = IOUtils.readAllBytes(stream);
            }
            if (httpCon.getResponseCode() != 200) {
                throw new IOException("Failed to download data with status code " + httpCon.getResponseCode() + ": " + httpCon.getResponseMessage());
            }
            return data;
        });
    }

    @Override
    public void delete(String key) throws IOException {
        final String useKey = normalizeKey(key);
        debug(() -> log.debug("Deleting " + useKey));

        callRetry(() -> getServiceManager().getClient(region).deleteFile(getSourceId(), useKey, shareId));
    }

    @Override
    public void checkCredentials(boolean readonly) throws IOException {
        try {
            debug(() -> log.debug("Checking credentials"));
            if (!getServiceManager().activeSubscription()) {
                throw new IOException("No active subscription");
            }
            if (shareId != null) {
                getServiceManager().getClient(region).getShare(getSourceId(), shareId);
            } else {
                getServiceManager().getClient(region).getSource(getSourceId());
            }
        } catch (ApiException e) {
            throw new IOException(e);
        }
    }
}
