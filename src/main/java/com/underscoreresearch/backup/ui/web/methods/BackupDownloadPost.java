package com.underscoreresearch.backup.ui.web.methods;

import com.underscoreresearch.backup.ui.commands.InteractiveCommand;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.RepositoryOpenMode;
import com.underscoreresearch.backup.io.DownloadScheduler;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.ui.web.ExclusiveImplementation;
import lombok.extern.slf4j.Slf4j;
import org.takes.HttpException;
import org.takes.Request;
import org.takes.Response;
import org.takes.misc.Href;
import org.takes.rq.RqHref;
import org.takes.rs.RsWithBody;
import org.takes.rs.RsWithType;
import org.takes.tk.TkWrap;

import java.io.File;
import java.io.FileInputStream;
import java.net.HttpURLConnection;

import static com.underscoreresearch.backup.ui.web.DestinationDecoder.decodeFile;
import static com.underscoreresearch.backup.ui.web.PrivateKeyRequest.decodePrivateKeyRequest;

/**
 * Web endpoint for downloading backup files.
 * This class handles requests to download files from the backup repository.
 */
@Slf4j
public class BackupDownloadPost extends TkWrap {

    /**
     * Creates a new BackupDownloadPost instance.
     *
     * @param base The base path for the API
     */
    public BackupDownloadPost(String base) {
        super(new Implementation(base));
    }

    /**
     * Implementation class that handles backup file download requests.
     */
    private static class Implementation extends ExclusiveImplementation {
        private final String base;

        /**
         * Creates a new Implementation instance.
         *
         * @param base The base path for the API
         */
        public Implementation(String base) {
            this.base = base + "/api/backup-download";
        }

        /**
         * Processes a request to download a backup file.
         * Retrieves the file from the backup repository and returns it as a response.
         *
         * @param req The HTTP request
         * @return The HTTP response containing the file
         * @throws Exception If an error occurs during processing
         */
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

        /**
         * Gets the message to display when the system is busy.
         *
         * @return The busy message
         */
        @Override
        protected String getBusyMessage() {
            return "Backup file download in progress";
        }
    }
}
