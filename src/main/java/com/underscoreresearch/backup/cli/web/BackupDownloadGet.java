package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.cli.web.DestinationDecoder.getRequestFiles;
import static java.nio.file.Files.newInputStream;

import java.io.File;
import java.util.List;
import java.util.Set;

import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.rs.RsWithBody;
import org.takes.rs.RsWithType;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.io.DownloadScheduler;
import com.underscoreresearch.backup.model.BackupFile;

public class BackupDownloadGet extends JsonWrap {
    private static ObjectWriter WRITER = new ObjectMapper()
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
            .writerFor(new TypeReference<Set<BackupFile>>() {
            });

    public BackupDownloadGet(String base) {
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
                List<BackupFile> files = getRequestFiles(req, base);
                if (files.size() > 0) {
                    return messageJson(400, "More than 1 file matching");
                } else if (files.size() == 0) {
                    return messageJson(400, "No file matching");
                }
                DownloadScheduler scheduler = InstanceFactory.getInstance(DownloadScheduler.class);
                File file = File.createTempFile("temp", null);
                scheduler.scheduleDownload(files.get(0), file.getAbsolutePath());
                scheduler.waitForCompletion();
                file.deleteOnExit();
                return new RsWithType(new RsWithBody(newInputStream(file.toPath())), "application/octet-stream");
            } catch (Exception exc) {
                return messageJson(400, exc.getMessage());
            }
        }
    }
}
