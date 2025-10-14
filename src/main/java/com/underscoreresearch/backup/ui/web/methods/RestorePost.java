package com.underscoreresearch.backup.ui.web.methods;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectReader;
import com.underscoreresearch.backup.block.FileDownloader;
import com.underscoreresearch.backup.ui.commands.InteractiveCommand;
import com.underscoreresearch.backup.ui.helpers.RestoreExecutor;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.FileSystemAccess;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.implementation.BackupStatsLogger;
import com.underscoreresearch.backup.io.DownloadScheduler;
import com.underscoreresearch.backup.manifest.BackupContentsAccess;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.model.BackupSetRoot;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import com.underscoreresearch.backup.ui.web.ExclusiveImplementation;
import com.underscoreresearch.backup.ui.web.PrivateKeyRequest;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.takes.Request;
import org.takes.Response;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.underscoreresearch.backup.block.implementation.FileDownloaderImpl.isNullFile;
import static com.underscoreresearch.backup.ui.web.PsAuthedContent.decodeRequestBody;
import static com.underscoreresearch.backup.io.IOUtils.createDirectory;
import static com.underscoreresearch.backup.utils.log.LogUtil.debug;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

/**
 * Web endpoint for restoring files from a backup.
 * This class handles requests to restore files from the backup repository to the filesystem.
 */
@Slf4j
public class RestorePost extends BaseWrap {
    private static final ObjectReader READER = MAPPER.readerFor(BackupRestoreRequest.class);

    /**
     * Creates a new RestorePost instance.
     */
    public RestorePost() {
        super(new Implementation());
    }

    /**
     * Request class for restoring files from a backup.
     * Contains information about the files to restore and restore options.
     */
    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class BackupRestoreRequest extends PrivateKeyRequest {
        private String destination;
        private List<BackupSetRoot> files;
        private boolean overwrite;
        private Long timestamp;
        private boolean includeDeleted;
        private boolean skipPermissions;

        /**
         * Creates a new BackupRestoreRequest with the specified parameters.
         *
         * @param destination The destination directory to restore to
         * @param files The list of files to restore
         * @param password The password for the encryption key
         * @param overwrite Whether to overwrite existing files
         * @param skipPermissions Whether to skip restoring file permissions
         * @param timestamp The timestamp to restore from
         */
        @JsonCreator
        @Builder
        public BackupRestoreRequest(@JsonProperty("destination") String destination,
                                    @JsonProperty("files") List<BackupSetRoot> files,
                                    @JsonProperty("password") String password,
                                    @JsonProperty("overwrite") Boolean overwrite,
                                    @JsonProperty("skipPermissions") Boolean skipPermissions,
                                    @JsonProperty("timestamp") Long timestamp) {
            super(password);

            this.destination = destination;
            this.files = files;
            this.overwrite = overwrite != null ? overwrite : false;
            this.timestamp = timestamp;
            this.skipPermissions = skipPermissions != null ? skipPermissions : false;
        }
    }

    /**
     * Implementation class that handles restore requests.
     */
    private static class Implementation extends ExclusiveImplementation {
        /**
         * Processes a request to restore files.
         * Validates the request and initiates the restore process in a separate thread.
         *
         * @param req The HTTP request
         * @return The HTTP response
         * @throws Exception If an error occurs during processing
         */
        @Override
        public Response actualAct(Request req) throws Exception {
            BackupRestoreRequest request = READER.readValue(decodeRequestBody(req));

            if (Strings.isEmpty(request.getPassword())) {
                return messageJson(400, "Missing password to restore");
            }

            if (!PrivateKeyRequest.validatePassword(request.getPassword())) {
                return messageJson(403, "Invalid password provided");
            }

            if (request.getFiles() == null || request.getFiles().isEmpty()) {
                return messageJson(400, "Missing files to restore");
            }

            InstanceFactory.reloadConfigurationWithSource();
            Thread thread = new Thread(() -> {
                AtomicBoolean restart = new AtomicBoolean(true);
                try {
                    MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);
                    ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);
                    BackupContentsAccess contents = manifestManager.backupContents(request.timestamp,
                            request.includeDeleted);
                    FileDownloader downloader = InstanceFactory.getInstance(FileDownloader.class);

                    InstanceFactory.addOrderedCleanupHook(() -> {
                        debug(() -> log.debug("Shutdown initiated"));

                        InstanceFactory.shutdown();
                        InstanceFactory.getInstance(DownloadScheduler.class).shutdown();

                        try {
                            downloader.shutdown();
                            repository.flushLogging();
                            manifestManager.shutdown();
                            repository.close();
                            restart.set(false);
                        } catch (IOException e) {
                            log.error("Failed to close manifest", e);
                        }

                        log.info("Restore shutdown completed");
                    });

                    String destination = request.getDestination();

                    try {
                        RestoreExecutor restoreExecutor = new RestoreExecutor(contents,
                                InstanceFactory.getInstance(FileSystemAccess.class),
                                repository,
                                request.getPassword(),
                                InstanceFactory.getInstance(BackupStatsLogger.class));
                        if (destination != null && !isNullFile(destination)) {
                            createDirectory(new File(destination), true);
                        }
                        restoreExecutor.restorePaths(request.files, destination, true,
                                request.overwrite,
                                request.skipPermissions);
                    } catch (Exception exc) {
                        log.error("Failed to complete restore", exc);
                    }
                } catch (Exception e) {
                    log.error("Failed to start restore", e);
                }

                InstanceFactory.waitForShutdown();

                if (restart.get()) {
                    InstanceFactory.reloadConfiguration(InstanceFactory.getAdditionalSource(),
                            InstanceFactory.getAdditionalSourceName(),
                            InteractiveCommand::startBackupIfAvailable);
                }
            }, "RestorePost");
            thread.setDaemon(true);
            thread.start();

            return messageJson(200, "Ok");
        }

        /**
         * Gets the message to display when the system is busy.
         *
         * @return The busy message
         */
        @Override
        protected String getBusyMessage() {
            return "Initializing restore";
        }
    }
}
