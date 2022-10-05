package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.cli.web.DestinationDecoder.decodeFile;
import static com.underscoreresearch.backup.cli.web.PrivateKeyRequest.decodePrivateKeyRequest;

import java.io.File;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import org.takes.HttpException;
import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.misc.Href;
import org.takes.rq.RqHref;
import org.takes.rs.RsWithBody;
import org.takes.rs.RsWithType;
import org.takes.tk.TkWrap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.cli.commands.InteractiveCommand;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.io.DownloadScheduler;
import com.underscoreresearch.backup.model.BackupFile;

@Slf4j
public class BackupDownloadPost extends TkWrap {
    private static ObjectWriter WRITER = new ObjectMapper()
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
            .writerFor(new TypeReference<Set<BackupFile>>() {
            });

    public BackupDownloadPost(String base) {
        super(new Implementation(base));
    }

    private static class Implementation implements Take {
        private final String base;

        public Implementation(String base) {
            this.base = base + "/api/backup-download";
        }

        @Override
        public Response act(Request req) throws Exception {
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

                String passphrase = decodePrivateKeyRequest(req);

                List<BackupFile> files = InstanceFactory.getInstance(MetadataRepository.class).file(path);
                if (files == null) {
                    throw new HttpException(
                            HttpURLConnection.HTTP_BAD_REQUEST,
                            "File not found"
                    );
                }

                for (BackupFile file : files) {
                    if (file.getAdded().equals(timestamp)) {
                        InstanceFactory.reloadConfiguration(passphrase, InstanceFactory.getAdditionalSource());

                        DownloadScheduler scheduler = InstanceFactory.getInstance(DownloadScheduler.class);
                        File tempfile = File.createTempFile("temp", null);
                        scheduler.scheduleDownload(file, tempfile.getAbsolutePath());
                        scheduler.waitForCompletion();
                        tempfile.deleteOnExit();
                        new Thread(() -> {
                            try {
                                InstanceFactory.reloadConfiguration(null,
                                        InstanceFactory.getAdditionalSource(),
                                        () -> InteractiveCommand.startBackupIfAvailable());
                            } catch (Exception e) {
                                log.error("Failed to restart backup", e);
                            }
                        }, "PostBackupDownloadGet").start();
                        return new RsWithType(new RsWithBody(new FileInputStream(tempfile)), "application/octet-stream");
                    }
                }

                throw new HttpException(
                        HttpURLConnection.HTTP_BAD_REQUEST,
                        "File not found"
                );
            } catch (Exception exc) {
                throw new HttpException(
                        HttpURLConnection.HTTP_BAD_REQUEST,
                        exc.getMessage()
                );
            }
        }
    }
}
