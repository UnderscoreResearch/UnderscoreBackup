package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.cli.web.DestinationDecoder.decodeFile;
import static com.underscoreresearch.backup.cli.web.PrivateKeyRequest.decodePrivateKeyRequest;

import java.io.File;
import java.io.FileInputStream;
import java.net.HttpURLConnection;

import lombok.extern.slf4j.Slf4j;

import org.takes.HttpException;
import org.takes.Request;
import org.takes.Response;
import org.takes.misc.Href;
import org.takes.rq.RqHref;
import org.takes.rs.RsWithBody;
import org.takes.rs.RsWithType;
import org.takes.tk.TkWrap;

import com.underscoreresearch.backup.cli.commands.InteractiveCommand;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.RepositoryOpenMode;
import com.underscoreresearch.backup.io.DownloadScheduler;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.model.BackupFile;

@Slf4j
public class BackupDownloadPost extends TkWrap {

    public BackupDownloadPost(String base) {
        super(new Implementation(base));
    }

    private static class Implementation extends ExclusiveImplementation {
        private final String base;

        public Implementation(String base) {
            this.base = base + "/api/backup-download";
        }

        @Override
        public Response actualAct(Request req) throws Exception {
            String password = decodePrivateKeyRequest(req);

            try {
                String path = decodeFile(req, base);
                Href href = new RqHref.Base(req).href();
                Long timestamp = null;
                for (String ts : href.param("timestamp")) {
                    timestamp = Long.parseLong(ts);
                }

                if (timestamp == null) {
                    throw new HttpException(
                            HttpURLConnection.HTTP_BAD_REQUEST,
                            "Missing timestamp to restore"
                    );
                }

                BackupFile file = InstanceFactory.getInstance(MetadataRepository.class).file(path, timestamp);
                if (file == null || !file.getAdded().equals(timestamp)) {
                    throw new HttpException(
                            HttpURLConnection.HTTP_BAD_REQUEST,
                            "File not found"
                    );
                }

                InstanceFactory.reloadConfigurationWithSource();

                DownloadScheduler scheduler = InstanceFactory.getInstance(DownloadScheduler.class);
                File tempfile = File.createTempFile("temp", null);
                MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);
                repository.open(RepositoryOpenMode.READ_ONLY);
                try {
                    scheduler.scheduleDownload(file, tempfile.getAbsolutePath(), password);
                    scheduler.waitForCompletion();
                } finally {
                    repository.close();
                    scheduler.shutdown();
                }
                tempfile.deleteOnExit();
                Thread thread = new Thread(() -> {
                    try {
                        InstanceFactory.reloadConfiguration(
                                InstanceFactory.getAdditionalSource(),
                                InstanceFactory.getAdditionalSourceName(),
                                InteractiveCommand::startBackupIfAvailable);
                    } catch (Exception e) {
                        log.error("Failed to restart backup", e);
                    }
                }, "PostBackupDownloadGet");
                thread.setDaemon(true);
                thread.start();

                return new RsWithType(new RsWithBody(new FileInputStream(tempfile)), "application/octet-stream");
            } catch (Exception exc) {
                throw new HttpException(
                        HttpURLConnection.HTTP_BAD_REQUEST,
                        exc.getMessage()
                );
            }
        }

        @Override
        protected String getBusyMessage() {
            return "Backup file download in progress";
        }
    }
}
