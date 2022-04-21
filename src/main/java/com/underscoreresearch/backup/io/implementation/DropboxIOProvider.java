package com.underscoreresearch.backup.io.implementation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import com.dropbox.core.DbxDownloader;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.DownloadErrorException;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.ListFolderErrorException;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.UploadBuilder;
import com.dropbox.core.v2.files.UploadUploader;
import com.dropbox.core.v2.files.WriteMode;
import com.google.common.collect.Lists;
import com.underscoreresearch.backup.io.IOIndex;
import com.underscoreresearch.backup.io.IOPlugin;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.utils.RetryUtils;

@IOPlugin(("DROPBOX"))
@Slf4j
public class DropboxIOProvider implements IOIndex, IOProvider {
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
            return result.getEntries().stream().map(item -> item.getName()).collect(Collectors.toList());
        } catch (ListFolderErrorException e) {
            return Lists.newArrayList();
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
        } catch (Exception exc) {
            throw new IOException(exc);
        }
    }

    @Override
    public void delete(String key) throws IOException {
        try {
            RetryUtils.retry(() -> clientV2.files().deleteV2(getFullPath(key)), null);
        } catch (Exception e) {
            throw new IOException(String.format("Failed to delete %s", root + key), e);
        }
    }
}
