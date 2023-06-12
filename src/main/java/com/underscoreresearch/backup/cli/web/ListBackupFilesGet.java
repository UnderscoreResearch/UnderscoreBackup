package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.cli.web.DestinationDecoder.getRequestFiles;
import static com.underscoreresearch.backup.utils.SerializationUtils.EXTERNAL_BACKUP_FILES_WRITER;

import java.util.stream.Collectors;

import org.takes.Request;
import org.takes.Response;
import org.takes.rs.RsText;

import com.underscoreresearch.backup.model.ExternalBackupFile;

public class ListBackupFilesGet extends JsonWrap {
    public ListBackupFilesGet(String base) {
        super(new Implementation(base));
    }

    private static class Implementation extends BaseImplementation {
        private final String base;

        public Implementation(String base) {
            this.base = base + "/api/backup-files";
        }

        @Override
        public Response actualAct(Request req) throws Exception {
            return new RsText(EXTERNAL_BACKUP_FILES_WRITER.writeValueAsString(
                    getRequestFiles(req, base)
                            .stream()
                            .map(ExternalBackupFile::new)
                            .collect(Collectors.toList())));
        }
    }
}
