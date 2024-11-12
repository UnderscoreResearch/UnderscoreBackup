package com.underscoreresearch.backup.io.implementation;

import static com.underscoreresearch.backup.io.implementation.DropboxIOProvider.DROPBOX_TYPE;
import static com.underscoreresearch.backup.utils.LogUtil.debug;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

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
import com.underscoreresearch.backup.io.IOIndex;
import com.underscoreresearch.backup.io.IOPlugin;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.utils.ProcessingStoppedException;
import com.underscoreresearch.backup.utils.RetryUtils;

@IOPlugin(DROPBOX_TYPE)
@Slf4j
public class DropboxIOProvider implements IOIndex {
    public static final String DROPBOX_TYPE = "DROPBOX";
    private final DbxClientV2 clientV2;
    private final String root;

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
    }

    @Override
    public List<String> availableKeys(String prefix) throws IOException {

        try {
            ListFolderResult result = RetryUtils.retry(() -> clientV2.files().listFolder(getFullPath(prefix)),
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

    private String getFullPath(String prefix) {
        if (prefix.startsWith("/"))
            return root + prefix.substring(1);
        return root + prefix;
    }

    @Override
    public String upload(String key, byte[] data) throws IOException {
        try {
            RetryUtils.retry(() -> {
                UploadBuilder builder = clientV2.files().uploadBuilder(getFullPath(key));
                builder.withMode(WriteMode.OVERWRITE);
                builder.withMute(true);
                try (UploadUploader file = builder.start()) {
                    file.uploadAndFinish(new ByteArrayInputStream(data));
                }
                return null;
            }, null);
        } catch (IOException | ProcessingStoppedException e) {
            throw e;
        } catch (Exception exc) {
            throw new IOException(exc);
        }
        return key;
    }

    @Override
    public byte[] download(String key) throws IOException {
        try {
            return RetryUtils.retry(() -> {
                try (DbxDownloader<FileMetadata> file = clientV2.files().download(getFullPath(key))) {
                    return IOUtils.readAllBytes(file.getInputStream());
                }
            }, (e) -> !(e instanceof DownloadErrorException));
        } catch (IOException | ProcessingStoppedException e) {
            throw e;
        } catch (Exception exc) {
            throw new IOException(exc);
        }
    }

    @Override
    public boolean exists(String key) throws IOException {
        try {
            boolean ret = RetryUtils.retry(() -> {
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
            }, (e) -> !(e instanceof DownloadErrorException));
            debug(() -> log.debug("Exists \"{}\" ({})", key, ret));
            return ret;
        } catch (IOException | ProcessingStoppedException e) {
            throw e;
        } catch (Exception exc) {
            throw new IOException(exc);
        }
    }

    @Override
    public void delete(String key) throws IOException {
        try {
            RetryUtils.retry(() -> clientV2.files().deleteV2(getFullPath(key)), null);
            debug(() -> log.debug("Deleted \"{}\"", key));
        } catch (IOException | ProcessingStoppedException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(String.format("Failed to delete \"%s\"", root + key), e);
        }
    }

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

    @Override
    public boolean hasConsistentWrites() {
        return true;
    }
}
