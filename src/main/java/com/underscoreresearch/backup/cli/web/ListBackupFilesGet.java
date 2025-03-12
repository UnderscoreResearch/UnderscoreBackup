package com.underscoreresearch.backup.cli.web;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.model.ExternalBackupFile;
import org.takes.Request;
import org.takes.Response;

import java.util.ArrayList;
import java.util.stream.Collectors;

import static com.underscoreresearch.backup.cli.web.DestinationDecoder.getRequestFiles;
import static com.underscoreresearch.backup.cli.web.PsAuthedContent.encryptResponse;
import static com.underscoreresearch.backup.utils.SerializationUtils.EXTERNAL_BACKUP_FILES_WRITER;

public class ListBackupFilesGet extends BaseWrap {
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
            if (InstanceFactory.getInstance(ManifestManager.class).isBusy()) {
                return encryptResponse(req, EXTERNAL_BACKUP_FILES_WRITER.writeValueAsString(new ArrayList<>()));
            }
            return encryptResponse(req, EXTERNAL_BACKUP_FILES_WRITER.writeValueAsString(
                    getRequestFiles(req, base)
                            .stream()
                            .map(ExternalBackupFile::new)
                            .collect(Collectors.toList())));
        }
    }
}
