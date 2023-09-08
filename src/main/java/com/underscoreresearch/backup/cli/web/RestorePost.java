package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.block.implementation.FileDownloaderImpl.isNullFile;
import static com.underscoreresearch.backup.cli.web.PsAuthedContent.decodeRequestBody;
import static com.underscoreresearch.backup.io.IOUtils.createDirectory;
import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.logging.log4j.util.Strings;
import org.takes.Request;
import org.takes.Response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.block.FileDownloader;
import com.underscoreresearch.backup.cli.commands.InteractiveCommand;
import com.underscoreresearch.backup.cli.helpers.RestoreExecutor;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.implementation.BackupStatsLogger;
import com.underscoreresearch.backup.io.DownloadScheduler;
import com.underscoreresearch.backup.manifest.BackupContentsAccess;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.model.BackupSetRoot;

@Slf4j
public class RestorePost extends BaseWrap {
    private static final ObjectReader READER = MAPPER.readerFor(BackupRestoreRequest.class);
    private static final ObjectWriter WRITER = MAPPER.writerFor(com.underscoreresearch.backup.cli.web.KeyPost.KeyResponse.class);

    public RestorePost() {
        super(new Implementation());
    }

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

    private static class Implementation extends ExclusiveImplementation {
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
            new Thread(() -> {
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
            }, "RestorePost").start();

            return messageJson(200, "Ok");
        }

        @Override
        protected String getBusyMessage() {
            return "Initializing restore";
        }
    }
}
