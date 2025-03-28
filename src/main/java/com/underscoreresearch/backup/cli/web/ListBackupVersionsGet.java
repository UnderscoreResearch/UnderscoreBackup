package com.underscoreresearch.backup.cli.web;

import com.google.common.collect.Lists;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.model.ExternalBackupFile;
import org.takes.Request;
import org.takes.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.underscoreresearch.backup.cli.web.DestinationDecoder.decodeFile;
import static com.underscoreresearch.backup.cli.web.PsAuthedContent.encryptResponse;
import static com.underscoreresearch.backup.utils.SerializationUtils.EXTERNAL_BACKUP_FILES_WRITER;

public class ListBackupVersionsGet extends BaseWrap {

    public ListBackupVersionsGet(String base) {
        super(new Implementation(base));
    }

    private static class Implementation extends BaseImplementation {
        private final String base;

        public Implementation(String base) {
            this.base = base + "/api/backup-versions";
        }

        public static List<ExternalBackupFile> getRequestVersions(Request req, String base) throws IOException {
            String path = decodeFile(req, base);

            List<ExternalBackupFile> versions = InstanceFactory.getInstance(MetadataRepository.class).file(path);

            return versions != null ? Lists.reverse(versions) : new ArrayList<>();
        }

        @Override
        public Response actualAct(Request req) throws Exception {
            return encryptResponse(req, EXTERNAL_BACKUP_FILES_WRITER.writeValueAsString(
                    getRequestVersions(req, base)));
        }
    }
}
