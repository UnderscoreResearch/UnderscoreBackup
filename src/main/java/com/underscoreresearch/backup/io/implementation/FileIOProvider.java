package com.underscoreresearch.backup.io.implementation;

import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.LogUtil.readableSize;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import com.google.common.collect.Lists;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.io.IOIndex;
import com.underscoreresearch.backup.io.IOPlugin;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.model.BackupDestination;

@IOPlugin(("FILE"))
@Slf4j
public class FileIOProvider implements IOIndex, IOProvider {
    private final String root;

    public FileIOProvider(BackupDestination destination) {
        if (!destination.getEndpointUri().contains("://")) {
            root = destination.getEndpointUri();
        } else {
            URI uri = URI.create(destination.getEndpointUri());
            root = PathNormalizer.physicalPath(uri.getPath());
        }
    }

    private File getFile(String key) {
        return Paths.get(root, PathNormalizer.physicalPath(key)).toFile();
    }

    @Override
    public List<String> availableKeys(String prefix) {
        File file = getFile(prefix);
        if (file.isDirectory()) {
            return Lists.newArrayList(file.list());
        }
        return Lists.newArrayList();
    }

    @Override
    public String upload(String key, byte[] data) throws IOException {
        File file = getFile(key);
        if (!file.getParentFile().isDirectory()) {
            file.getParentFile().mkdirs();
        }

        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(data, 0, data.length);
        }

        debug(() -> log.debug("Wrote {} ({})", file.toString(), readableSize(data.length)));

        return key;
    }

    @Override
    public byte[] download(String key) throws IOException {
        File file = getFile(key);
        try (FileInputStream stream = new FileInputStream(file)) {
            byte[] data = IOUtils.readAllBytes(stream);
            debug(() -> log.debug("Read {} ({})", file.toString(), readableSize(data.length)));
            return data;
        }
    }

    @Override
    public void delete(String key) throws IOException {
        File file = getFile(key);
        if (file.exists() && !file.delete()) {
            throw new IOException("Failed to delete " + file);
        }
    }
}
