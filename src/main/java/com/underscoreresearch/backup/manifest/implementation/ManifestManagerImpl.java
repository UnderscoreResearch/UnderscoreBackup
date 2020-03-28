package com.underscoreresearch.backup.manifest.implementation;

import static com.underscoreresearch.backup.configuration.CommandLineModule.CONFIG_DATA;
import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.Encryptor;
import com.underscoreresearch.backup.encryption.PublicKeyEncrypion;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.io.IOIndex;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.io.RateLimitController;
import com.underscoreresearch.backup.manifest.BackupContentsAccess;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.LoggingMetadataRepository;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.BackupActivePath;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupFilePart;

@Slf4j
public class ManifestManagerImpl implements ManifestManager {
    private final static DateTimeFormatter LOG_FILE_FORMATTER
            = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss.n").withZone(ZoneId.of("UTC"));
    private static final String LOG_ROOT = "logs";

    private final BackupConfiguration configuration;
    private final RateLimitController rateLimitController;
    private final String localRoot;
    private final BackupDestination manifestDestination;
    private final IOProvider provider;
    private final Encryptor encryptor;

    private String currentLogName;
    private OutputStream currentLogStream;
    private long currentLogLength;
    private Object lock = new Object();
    private ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    private boolean initialized = false;

    public ManifestManagerImpl(BackupConfiguration configuration,
                               IOProvider provider,
                               Encryptor encryptor,
                               RateLimitController rateLimitController)
            throws IOException {
        this.configuration = configuration;
        this.rateLimitController = rateLimitController;
        this.localRoot = configuration.getManifest().getLocalLocation();

        manifestDestination = configuration.getDestinations().get(configuration.getManifest().getDestination());
        if (manifestDestination == null) {
            throw new IllegalArgumentException("Can't find destination for manifest");
        }

        this.provider = provider;
        if (!(provider instanceof IOIndex)) {
            throw new IllegalArgumentException("Manifest destination must be able to support listing files");
        }
        this.encryptor = encryptor;
    }

    private void uploadPending() throws IOException {
        PublicKeyEncrypion publicKeyEncrypion = InstanceFactory.getInstance(PublicKeyEncrypion.class);
        try {
            PublicKeyEncrypion existingPublicKey = new ObjectMapper().readValue(provider.download("publickey.json"),
                    PublicKeyEncrypion.class);
            if (!publicKeyEncrypion.getSalt().equals(existingPublicKey.getSalt())
                    || !publicKeyEncrypion.getPublicKey().equals(existingPublicKey.getPublicKey())) {
                throw new IOException("Public key that exist in destination does not match current public key");
            }
        } catch (Exception exc) {
            if (!IOUtils.hasInternet()) {
                throw exc;
            }
            log.info("Public key does not exist");
            uploadConfigData("publickey.json",
                    new ObjectMapper().writeValueAsBytes(publicKeyEncrypion),
                    false);
        }

        uploadConfigData("configuration.json",
                InstanceFactory.getInstance(CONFIG_DATA).getBytes(Charset.forName("UTF-8")),
                false);

        File parent = Paths.get(configuration.getManifest().getLocalLocation(), "logs").toFile();
        if (parent.isDirectory()) {
            for (String filename : parent.list()) {
                File file = new File(parent, filename);
                try (FileInputStream stream = new FileInputStream(file)) {
                    uploadConfigData(transformLogFilename(filename), IOUtils.readAllBytes(stream), true);
                }
                file.delete();
            }
        }
    }

    public void initialize() {
        synchronized (lock) {
            if (!initialized) {
                try {
                    uploadPending();
                    initialized = true;
                } catch (IOException e) {
                    throw new RuntimeException("Failed to initialize metadata system for writing", e);
                }
            }
        }
    }

    private void uploadConfigData(String filename, byte[] data, boolean encrypt) throws IOException {
        rateLimitController.acquireUploadPermits(manifestDestination, data.length);
        if (encrypt)
            data = encryptor.encryptBlock(data);
        provider.upload(filename, data);
    }

    private String transformLogFilename(String path) {
        String filename = Paths.get(path).getFileName().toString();
        return "logs" + PATH_SEPARATOR + filename.replaceAll("(\\d{4}-\\d{2}-\\d{2})-", "$1" + PATH_SEPARATOR);
    }

    @Override
    public void addLogEntry(String type, String jsonDefinition) {
        synchronized (lock) {
            initialize();

            try {
                if (currentLogStream == null) {
                    createNewLogFile();
                }
                byte[] data = (type + ":" + jsonDefinition + "\n").getBytes(Charset.forName("UTF-8"));
                currentLogStream.write(data);
                currentLogLength += data.length;

                if (currentLogLength > configuration.getManifest().getMaximumUnsyncedSize()) {
                    createNewLogFile();
                }
            } catch (IOException exc) {
                log.error("Failed to save log entry: " + type + ": " + jsonDefinition, exc);
            }
        }
    }

    private void createNewLogFile() throws IOException {
        closeLogFile();
        currentLogLength = 0;
        currentLogName = Paths.get(localRoot, LOG_ROOT, LOG_FILE_FORMATTER.format(Instant.now()) + ".gz").toString();

        File dir = new File(currentLogName).getParentFile();
        if (!dir.isDirectory())
            dir.mkdirs();

        FileOutputStream fileOutputStream = new FileOutputStream(currentLogName);
        currentLogStream = new GZIPOutputStream(fileOutputStream);
        if (configuration.getManifest().getMaximumUnsyncedSeconds() != null) {
            String logName = currentLogName;
            executor.schedule(() -> {
                        synchronized (lock) {
                            if (logName.equals(currentLogName)) {
                                try {
                                    createNewLogFile();
                                } catch (IOException exc) {
                                    log.error("Failed to create new log file", exc);
                                }
                            }
                        }
                    },
                    configuration.getManifest().getMaximumUnsyncedSeconds(),
                    TimeUnit.SECONDS);
        }
    }

    private void closeLogFile() throws IOException {
        if (currentLogStream != null) {
            currentLogStream.close();
            try (FileInputStream stream = new FileInputStream(currentLogName)) {
                uploadConfigData(transformLogFilename(currentLogName), IOUtils.readAllBytes(stream), true);
            }
            new File(currentLogName).delete();
            currentLogStream = null;
            currentLogName = null;
        }
    }

    @Override
    public void replayLog(LogConsumer consumer) throws IOException {
        IOIndex index = (IOIndex) provider;
        for (String day : index.availableKeys(LOG_ROOT)) {
            for (String file : index.availableKeys(LOG_ROOT + PATH_SEPARATOR + day)) {
                String path = LOG_ROOT + PATH_SEPARATOR + day;
                if (!path.endsWith(PATH_SEPARATOR)) {
                    path += PATH_SEPARATOR;
                }
                path += file;
                byte[] data = provider.download(path);

                try {
                    log.info("Processing log file {}", path);
                    byte[] unencryptedData = encryptor.decodeBlock(data);
                    try (ByteArrayInputStream inputStream = new ByteArrayInputStream(unencryptedData)) {
                        try (GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)) {
                            try (InputStreamReader inputStreamReader
                                         = new InputStreamReader(gzipInputStream)) {
                                try (BufferedReader reader = new BufferedReader(inputStreamReader)) {
                                    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                                        int ind = line.indexOf(':');
                                        try {
                                            consumer.replayLogEntry(line.substring(0, ind),
                                                    line.substring(ind + 1));
                                        } catch (Exception exc) {
                                            log.error("Failed processing log line: " + line, exc);
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception exc) {
                    log.error("Failed to read log file " + file, exc);
                }
            }
        }
    }

    @Override
    public void optimizeLog(MetadataRepository existingRepository) throws IOException {
        IOIndex index = (IOIndex) provider;
        List<String> existingLogs = new ArrayList<>();
        for (String day : index.availableKeys(LOG_ROOT)) {
            for (String file : index.availableKeys(LOG_ROOT + PATH_SEPARATOR + day)) {
                String path = LOG_ROOT + PATH_SEPARATOR + day;
                if (!path.endsWith(PATH_SEPARATOR)) {
                    path += PATH_SEPARATOR;
                }
                existingLogs.add(path + file);
            }
        }

        LoggingMetadataRepository copyRepository = new LoggingMetadataRepository(new MetadataRepository() {
            @Override
            public void addFile(BackupFile file) throws IOException {
            }

            @Override
            public List<BackupFile> file(String path) throws IOException {
                return null;
            }

            @Override
            public BackupFile lastFile(String path) throws IOException {
                return null;
            }

            @Override
            public boolean deleteFile(BackupFile file) throws IOException {
                return false;
            }

            @Override
            public List<BackupFilePart> existingFilePart(String partHash) throws IOException {
                return null;
            }

            @Override
            public boolean deleteFilePart(BackupFilePart filePart) throws IOException {
                return false;
            }

            @Override
            public void addBlock(BackupBlock block) throws IOException {
            }

            @Override
            public BackupBlock block(String hash) throws IOException {
                return null;
            }

            @Override
            public boolean deleteBlock(BackupBlock block) throws IOException {
                return false;
            }

            @Override
            public void addDirectory(BackupDirectory directory) throws IOException {
            }

            @Override
            public List<BackupDirectory> directory(String path) throws IOException {
                return null;
            }

            @Override
            public BackupDirectory lastDirectory(String path) throws IOException {
                return null;
            }

            @Override
            public boolean deleteDirectory(String path, long timestamp) throws IOException {
                return false;
            }

            @Override
            public void pushActivePath(String setId, String path, BackupActivePath pendingFiles) throws IOException {
            }

            @Override
            public boolean hasActivePath(String setId, String path) throws IOException {
                return false;
            }

            @Override
            public void popActivePath(String setId, String path) throws IOException {
            }

            @Override
            public TreeMap<String, BackupActivePath> getActivePaths(String setId) throws IOException {
                return null;
            }

            @Override
            public void flushLogging() throws IOException {
            }

            @Override
            public void open(boolean readOnly) throws IOException {
            }

            @Override
            public void close() throws IOException {
            }

            @Override
            public Stream<BackupFile> allFiles() throws IOException {
                return null;
            }

            @Override
            public Stream<BackupBlock> allBlocks() throws IOException {
                return null;
            }

            @Override
            public Stream<BackupDirectory> allDirectories() throws IOException {
                return null;
            }
        }, this);

        existingRepository.allFiles().forEach((file) -> {
            try {
                copyRepository.addFile(file);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        existingRepository.allBlocks().forEach((block) -> {
            try {
                copyRepository.addBlock(block);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        existingRepository.allDirectories().forEach((dir) -> {
            try {
                copyRepository.addDirectory(dir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        existingRepository.getActivePaths(null).forEach((path, dir) -> {
            for (String setId : dir.getSetIds()) {
                try {
                    copyRepository.pushActivePath(setId, path, dir);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        for (String oldFile : existingLogs) {
            provider.delete(oldFile);
        }
    }

    @Override
    public BackupContentsAccess backupContents(Long timestamp) throws IOException {
        return new BackupContentsAccessImpl(InstanceFactory.getInstance(MetadataRepository.class),
                timestamp);
    }

    @Override
    public void shutdown() throws IOException {
        synchronized (lock) {
            executor.shutdownNow();
            closeLogFile();
        }
    }
}