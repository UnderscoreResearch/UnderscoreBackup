package com.underscoreresearch.backup.io.implementation;

import static com.underscoreresearch.backup.io.IOUtils.createDirectory;
import static com.underscoreresearch.backup.io.IOUtils.deleteFileException;
import static com.underscoreresearch.backup.io.implementation.FileIOProvider.FILE_TYPE;
import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.LogUtil.readableSize;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.List;

import com.underscoreresearch.backup.io.ConnectionLimiter;
import lombok.extern.slf4j.Slf4j;

import com.google.common.collect.Lists;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.io.IOIndex;
import com.underscoreresearch.backup.io.IOPlugin;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.model.BackupDestination;

@IOPlugin(FILE_TYPE)
@Slf4j
public class FileIOProvider implements IOIndex {
    public static final String FILE_TYPE = "FILE";
    private final String root;
    private final ConnectionLimiter limiter;

    public FileIOProvider(BackupDestination destination) {
        if (!destination.getEndpointUri().contains("://")) {
            root = destination.getEndpointUri();
        } else {
            URI uri = URI.create(destination.getEndpointUri());
            root = PathNormalizer.physicalPath(uri.getPath());
        }
        limiter = new ConnectionLimiter(destination);
    }

    private File getFile(String key) {
        return Paths.get(root, PathNormalizer.physicalPath(key)).toFile();
    }

    @Override
    public List<String> availableKeys(String prefix) throws IOException {
        File file = getFile(prefix);
        if (file.isDirectory()) {
            try {
                return Lists.newArrayList(limiter.call(() -> file.list()));
            } catch (IOException|RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return Lists.newArrayList();
    }

    @Override
    public String upload(String key, byte[] data) throws IOException {
        File file = getFile(key);

        try {
            limiter.call(() -> {
                createDirectory(file.getParentFile(), true);

                try (FileOutputStream stream = new FileOutputStream(file)) {
                    stream.write(data, 0, data.length);
                }
                return null;
            });
        } catch (IOException|RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        debug(() -> log.debug("Wrote \"{}\" ({})", file, readableSize(data.length)));

        return key;
    }

    @Override
    public byte[] download(String key) throws IOException {
        File file = getFile(key);
        try {
            return limiter.call(() -> {
                try (FileInputStream stream = new FileInputStream(file)) {
                    byte[] data = IOUtils.readAllBytes(stream);
                    debug(() -> log.debug("Read \"{}\" ({})", file, readableSize(data.length)));
                    return data;
                }
            });
        } catch (IOException|RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean exists(String key) throws IOException {
        File file = getFile(key);
        try {
            boolean exist = limiter.call(() -> file.exists());
            debug(() -> log.debug("Exists \"{}\" ({})", file, exist));
            return exist;
        } catch (IOException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void delete(String key) throws IOException {
        File file = getFile(key);
        try {
            limiter.call(() -> {
                deleteFileException(file);

                File parent = file.getParentFile();
                File root = new File(this.root);
                while (parent != null && !parent.equals(root) && parent.exists() && parent.list().length == 0) {
                    if (!parent.delete()) {
                        log.warn("Failed to delete directory \"{}\"", parent);
                        return null;
                    }
                    parent = parent.getParentFile();
                }
                return null;
            });
        } catch (IOException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        debug(() -> log.debug("Deleted \"{}\"", key));
    }

    @Override
    public void checkCredentials(boolean readOnly) throws IOException {
        File file = new File(root);
        if (!file.exists()) {
            if (readOnly || !file.mkdirs()) {
                throw new IOException("Failed to create root of local destination");
            }
        }
    }

    @Override
    public boolean hasConsistentWrites() {
        return true;
    }
}
