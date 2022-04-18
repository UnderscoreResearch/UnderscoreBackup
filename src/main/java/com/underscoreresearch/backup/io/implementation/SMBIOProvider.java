package com.underscoreresearch.backup.io.implementation;

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

import com.google.common.collect.Lists;
import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;
import com.underscoreresearch.backup.io.IOIndex;
import com.underscoreresearch.backup.io.IOPlugin;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.model.BackupDestination;

@IOPlugin(("SMB"))
@Slf4j
public class SMBIOProvider implements IOIndex, IOProvider, Closeable {
    private String root;
    private static final Pattern PATH_PARSER = Pattern.compile("^\\\\\\\\([^\\\\]+)\\\\([^\\\\]+)\\\\?(.*)");
    private static final Pattern URI_PARSER = Pattern.compile("^smb://([^//]+)/([^//]+)/?(.*)");
    private final SMBClient client;
    private final Connection connection;
    private final Session session;
    private final DiskShare share;

    public SMBIOProvider(BackupDestination destination) {
        Matcher matcher = PATH_PARSER.matcher(destination.getEndpointUri());
        if (!matcher.find()) {
            matcher = URI_PARSER.matcher(destination.getEndpointUri());
            if (!matcher.find()) {
                throw new IllegalArgumentException("Invalid SMB path or URI" + destination.getEndpointUri());
            }
            root = matcher.group(3).replace("/", "\\");
        } else {
            root = matcher.group(3);
        }
        if (!root.endsWith("\\"))
            root += "\\";

        client = new SMBClient();
        try {
            connection = client.connect(matcher.group(1));
        } catch (IOException exc) {
            throw new RuntimeException(exc);
        }
        session = connection.authenticate(new AuthenticationContext(destination.getPrincipal(),
                destination.getCredential().toCharArray(),
                destination.getProperty("domain", "WORKGROUP")));
        share = (DiskShare) session.connectShare(matcher.group(2));
    }

    @Override
    public List<String> availableKeys(String prefix) {
        String physicalKey = physicalPath(prefix);
        if (share.folderExists(root + physicalKey)) {
            return share.list(root + physicalKey)
                    .stream()
                    .map(t -> t.getFileName())
                    .filter(t -> !t.equals(".") && !t.equals(".."))
                    .collect(Collectors.toList());
        }
        return Lists.newArrayList();
    }

    private String physicalPath(String prefix) {
        String ret = prefix.replace("/", "\\");
        if (ret.startsWith("\\"))
            return ret.substring(1);
        return ret;
    }

    private String parentPath(String physicalKey) {
        int index = physicalKey.indexOf("\\");
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


        try (File file = share.openFile(root + physicalKey,
                EnumSet.of(AccessMask.FILE_WRITE_DATA),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                null)) {
            file.write(data, 0);
        }
        return key;
    }

    private void createParent(String parent) {
        if (parent.length() > 0 && !share.folderExists(parent)) {
            createParent(parentPath(parent));
            share.mkdir(parent);
        }
    }

    @Override
    public byte[] download(String key) throws IOException {
        String physicalKey = physicalPath(key);
        try (File file = share.openFile(root + physicalKey,
                EnumSet.of(AccessMask.FILE_READ_DATA),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null)) {
            try (InputStream stream = file.getInputStream()) {
                byte[] data = IOUtils.readAllBytes(stream);
                debug(() -> log.debug("Read {} ({})", key, readableSize(data.length)));
                return data;
            }
        }
    }

    @Override
    public void delete(String key) throws IOException {
        String physicalKey = physicalPath(key);
        if (share.fileExists(root + physicalKey)) {
            share.rm(root + physicalKey);
        }
    }

    @Override
    public void close() throws IOException {
        share.close();
        session.close();
        connection.close();
        client.close();
    }
}
