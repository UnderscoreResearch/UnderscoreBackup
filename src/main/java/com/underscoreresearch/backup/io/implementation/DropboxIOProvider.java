package com.underscoreresearch.backup.io.implementation;

import com.dropbox.core.DbxDownloader;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.DownloadErrorException;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.GetMetadataErrorException;
import com.dropbox.core.v2.files.ListFolderErrorException;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.LookupError;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.files.UploadBuilder;
import com.dropbox.core.v2.files.UploadUploader;
import com.dropbox.core.v2.files.WriteMode;
import com.google.common.collect.Lists;
import com.underscoreresearch.backup.io.ConnectionLimiter;
import com.underscoreresearch.backup.io.IOIndex;
import com.underscoreresearch.backup.io.IOPlugin;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.utils.ProcessingStoppedException;
import com.underscoreresearch.backup.utils.RetryUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static com.underscoreresearch.backup.io.implementation.DropboxIOProvider.DROPBOX_TYPE;
import static com.underscoreresearch.backup.utils.log.LogUtil.debug;

/**
 * IO provider implementation for Dropbox storage.
 * Provides methods for storing and retrieving backup data from Dropbox.
 */
@IOPlugin(DROPBOX_TYPE)
@Slf4j
public class DropboxIOProvider implements IOIndex {
    public static final String DROPBOX_TYPE = "DROPBOX";
    private final DbxClientV2 clientV2;
    private final String root;
    private final String cacheKey;
    private final ConnectionLimiter limiter;

    /**
     * Constructor for DropboxIOProvider.
     *
     * @param destination The backup destination configuration
     */
    public DropboxIOProvider(BackupDestination destination) {
        DbxRequestConfig requestConfig = new DbxRequestConfig("Underscore Backup");
        clientV2 = new DbxClientV2(requestConfig, new DbxCredential(destination.getPrincipal(), -1L,
                destination.getCredential(), "tlt1aw0jc8wlcox"));

        String calculatedRoot = destination.getEndpointUri();
        if (!calculatedRoot.startsWith("/"))
            calculatedRoot = "/" + calculatedRoot;

        if (!calculatedRoot.endsWith("/"))
            calculatedRoot += "/";

        root = calculatedRoot;
        cacheKey = destination.getPrincipal() + "/" + root;
        limiter = new ConnectionLimiter(destination);
    }

    /**
     * List all available keys with the specified prefix.
     *
     * @param prefix The prefix to filter keys by
     * @return List of keys matching the prefix
     * @throws IOException If there's an error accessing Dropbox
     */
    @Override
    public List<String> availableKeys(String prefix) throws IOException {

        try {
            ListFolderResult result = RetryUtils.retry(() -> limiter.call(() -> clientV2.files().listFolder(getFullPath(prefix))),
                    (e) -> !(e instanceof ListFolderErrorException));
            return result.getEntries().stream().map(Metadata::getName).collect(Collectors.toList());
        } catch (ListFolderErrorException e) {
            return Lists.newArrayList();
        } catch (IOException | ProcessingStoppedException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to list folder", e);
        }
    }

    /**
     * Get the full path for a key in Dropbox.
     *
     * @param prefix The key prefix
     * @return The full path in Dropbox
     */
    private String getFullPath(String prefix) {
        if (prefix.startsWith("/"))
            return root + prefix.substring(1);
        return root + prefix;
    }

    /**
     * Upload data to Dropbox with a suggested key.
     *
     * @param key The suggested key for the data
     * @param data The data to upload
     * @return The actual key used for the uploaded data
     * @throws IOException If there's an error uploading the data
     */
    @Override
    public String upload(String key, byte[] data) throws IOException {
        try {
            RetryUtils.retry(limiter.call(() -> () -> {
                UploadBuilder builder = clientV2.files().uploadBuilder(getFullPath(key));
                builder.withMode(WriteMode.OVERWRITE);
                builder.withMute(true);
                try (UploadUploader file = builder.start()) {
                    file.uploadAndFinish(new ByteArrayInputStream(data));
                }
                return null;
            }), null);
        } catch (IOException | ProcessingStoppedException e) {
            throw e;
        } catch (Exception exc) {
            throw new IOException(exc);
        }
        return key;
    }

    /**
     * Download data from Dropbox using a key.
     *
     * @param key The key for the data to download
     * @return The downloaded data
     * @throws IOException If there's an error downloading the data
     */
    @Override
    public byte[] download(String key) throws IOException {
        try {
            return RetryUtils.retry(() -> limiter.call(() -> {
                try (DbxDownloader<FileMetadata> file = clientV2.files().download(getFullPath(key))) {
                    return IOUtils.readAllBytes(file.getInputStream());
                }
            }), (e) -> !(e instanceof DownloadErrorException));
        } catch (IOException | ProcessingStoppedException e) {
            throw e;
        } catch (Exception exc) {
            throw new IOException(exc);
        }
    }

    /**
     * Get a unique cache key for this provider.
     *
     * @return The cache key
     */
    @Override
    public String getCacheKey() {
        return cacheKey;
    }

    /**
     * Check if data exists at the specified key.
     *
     * @param key The key to check
     * @return True if data exists at the key, false otherwise
     * @throws IOException If there's an error checking for existence
     */
    @Override
    public boolean exists(String key) throws IOException {
        try {
            boolean ret = RetryUtils.retry(() -> limiter.call(() -> {
                try {
                    clientV2.files().getMetadata(getFullPath(key));
                    return true;
                } catch (GetMetadataErrorException e) {
                    if (e.errorValue.isPath()) {
                        LookupError le = e.errorValue.getPathValue();
                        if (le.isNotFound()) {
                            return false;
                        }
                    }
                    throw e;
                }
            }), (e) -> !(e instanceof DownloadErrorException));
            debug(() -> log.debug("Exists \"{}\" ({})", key, ret));
            return ret;
        } catch (IOException | ProcessingStoppedException e) {
            throw e;
        } catch (Exception exc) {
            throw new IOException(exc);
        }
    }

    /**
     * Delete data at the specified key.
     *
     * @param key The key for the data to delete
     * @throws IOException If there's an error deleting the data
     */
    @Override
    public void delete(String key) throws IOException {
        try {
            RetryUtils.retry(() -> limiter.call(() -> clientV2.files().deleteV2(getFullPath(key))), null);
            debug(() -> log.debug("Deleted \"{}\"", key));
        } catch (IOException | ProcessingStoppedException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(String.format("Failed to delete \"%s\"", root + key), e);
        }
    }

    /**
     * Check if the provider credentials are valid.
     *
     * @param readOnly Whether to check for read-only access
     * @throws IOException If the credentials are invalid or there's an error checking
     */
    @Override
    public void checkCredentials(boolean readOnly) throws IOException {
        try {
            String strippedRoot = root.substring(0, root.length() - 1);
            try {
                clientV2.files().listFolder(strippedRoot);
            } catch (ListFolderErrorException exc) {
                if (!readOnly) {
                    clientV2.files().createFolderV2(strippedRoot);
                } else {
                    throw exc;
                }
            }
        } catch (DbxException e) {
            throw new IOException("Failed to access Dropbox", e);
        }
    }

    /**
     * Check if the provider guarantees consistent writes.
     * A consistent write means that once a write operation completes,
     * the data is guaranteed to be durably stored.
     *
     * @return True if writes are consistent, false otherwise
     */
    @Override
    public boolean hasConsistentWrites() {
        return true;
    }
}
