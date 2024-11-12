package com.underscoreresearch.backup.io.implementation;

import static com.underscoreresearch.backup.io.implementation.SMBIOProvider.SMB_TYPE;
import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.LogUtil.readableSize;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.fileinformation.FileDirectoryQueryableInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.mssmb2.SMBApiException;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.common.SMBRuntimeException;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;
import com.underscoreresearch.backup.io.IOIndex;
import com.underscoreresearch.backup.io.IOPlugin;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.model.BackupDestination;

@IOPlugin(SMB_TYPE)
@Slf4j
public class SMBIOProvider implements IOIndex, Closeable {
    public static final String SMB_TYPE = "SMB";
    private static final Pattern PATH_PARSER = Pattern.compile("^\\\\\\\\([^\\\\]+)\\\\([^\\\\]+)\\\\?(.*)");
    private static final Pattern URI_PARSER = Pattern.compile("^smb://([^/]+)/([^/]+)/?(.*)");
    private final BackupDestination destination;
    private final String host;
    private final String shareName;
    private String root;
    private SMBClient client;
    private Connection connection;
    private Session session;
    private DiskShare share;

    public SMBIOProvider(BackupDestination destination) {
        this.destination = destination;

        Matcher matcher = PATH_PARSER.matcher(destination.getEndpointUri());
        if (!matcher.find()) {
            matcher = URI_PARSER.matcher(destination.getEndpointUri());
            if (!matcher.find()) {
                throw new IllegalArgumentException("Invalid SMB path or URI \"" + destination.getEndpointUri() + "\"");
            }
            root = matcher.group(3).replace("/", "\\");
        } else {
            root = matcher.group(3);
        }
        if (!root.endsWith("\\"))
            root += "\\";

        host = matcher.group(1);
        shareName = matcher.group(2);
    }

    private DiskShare getShare() throws IOException {
        if (share == null) {
            if (connection != null) {
                throw new IOException("Failed to connect to SMB share");
            }

            client = new SMBClient();
            try {
                connection = client.connect(host);
                session = connection.authenticate(new AuthenticationContext(destination.getPrincipal(),
                        destination.getCredential() != null ? destination.getCredential().toCharArray() : new char[0],
                        destination.getProperty("domain", "WORKGROUP")));
                share = (DiskShare) session.connectShare(shareName);
            } catch (SMBRuntimeException exc) {
                throw new IOException("Failed to connect to SMB share", exc);
            }
        }
        return share;
    }

    @Override
    public List<String> availableKeys(String prefix) throws IOException {
        try {
            String physicalKey = physicalPath(prefix);
            if (getShare().folderExists(root + physicalKey)) {
                return getShare().list(root + physicalKey)
                        .stream()
                        .map(FileDirectoryQueryableInformation::getFileName)
                        .filter(t -> !t.equals(".") && !t.equals(".."))
                        .collect(Collectors.toList());
            }
            return Lists.newArrayList();
        } catch (SMBRuntimeException exc) {
            throw new IOException("Failed to list SMB folder", exc);
        }
    }

    private String physicalPath(String prefix) {
        String ret = prefix.replace('/', '\\');
        if (ret.startsWith("\\"))
            return ret.substring(1);
        return ret;
    }

    private String parentPath(String physicalKey) {
        int index = physicalKey.lastIndexOf("\\");
        if (index >= 0) {
            return physicalKey.substring(0, index);
        }
        return "";
    }

    @Override
    public String upload(String key, byte[] data) throws IOException {
        String physicalKey = physicalPath(key);
        String parent = parentPath(physicalKey);
        createParent(root + parent);

        try {
            try (File file = getShare().openFile(root + physicalKey,
                    EnumSet.of(AccessMask.FILE_WRITE_DATA),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OVERWRITE_IF,
                    null)) {
                file.write(data, 0);
            }
        } catch (SMBRuntimeException exc) {
            throw new IOException("Failed to upload file", exc);
        }
        return key;
    }

    private void createParent(String parent) throws IOException {
        try {
            if (parent.endsWith("\\"))
                parent = parent.substring(0, parent.length() - 1);

            if (!parent.isEmpty() && !getShare().folderExists(parent)) {
                createParent(parentPath(parent));
                getShare().mkdir(parent);
            }
        } catch (SMBRuntimeException exc) {
            throw new IOException("Failed to create directory", exc);
        }
    }

    @Override
    public boolean hasConsistentWrites() {
        return true;
    }

    @Override
    public byte[] download(String key) throws IOException {
        String physicalKey = physicalPath(key);
        try {
            try (File file = getShare().openFile(root + physicalKey,
                    EnumSet.of(AccessMask.FILE_READ_DATA),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null)) {
                try (InputStream stream = file.getInputStream()) {
                    byte[] data = IOUtils.readAllBytes(stream);
                    debug(() -> log.debug("Read \"{}\" ({})", key, readableSize(data.length)));
                    return data;
                }
            }
        } catch (SMBRuntimeException exc) {
            throw new IOException("Failed to download file", exc);
        }
    }

    @Override
    public boolean exists(String key) throws IOException {
        String physicalKey = physicalPath(key);

        boolean ret = getShare().fileExists(root + physicalKey);
        debug(() -> log.debug("Exists \"{}\" ({})", key, ret));
        return ret;
    }

    @Override
    public void delete(String key) throws IOException {
        String physicalKey = physicalPath(key);
        try {
            if (getShare().fileExists(root + physicalKey)) {
                getShare().rm(root + physicalKey);
            }

            String parent = parentPath(physicalKey);
            while (!Strings.isNullOrEmpty(parent)) {
                try {
                    getShare().rmdir(root + parent, false);
                    parent = parentPath(physicalKey);
                } catch (SMBApiException exc) {
                    break;
                }
            }
            debug(() -> log.debug("Deleted \"{}\"", key));
        } catch (SMBRuntimeException exc) {
            throw new IOException("Failed to delete file", exc);
        }
    }

    @Override
    public void checkCredentials(boolean readOnly) throws IOException {
        if (!getShare().folderExists(root)) {
            if (readOnly)
                throw new IOException("Root folder does not exist");
            createParent(root);
        }
    }

    @Override
    public void close() throws IOException {
        if (share != null)
            share.close();
        if (session != null)
            session.close();
        if (connection != null)
            connection.close();
        if (client != null)
            client.close();
    }
}
