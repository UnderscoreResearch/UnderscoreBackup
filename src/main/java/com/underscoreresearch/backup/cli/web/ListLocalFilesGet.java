package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.cli.web.DestinationDecoder.decodePath;
import static com.underscoreresearch.backup.utils.SerializationUtils.EXTERNAL_BACKUP_FILES_WRITER;

import java.util.Set;
import java.util.stream.Collectors;

import org.takes.Request;
import org.takes.Response;
import org.takes.rs.RsText;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.FileSystemAccess;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.ExternalBackupFile;

public class ListLocalFilesGet extends JsonWrap {

    public ListLocalFilesGet(String base) {
        super(new Implementation(base));
    }

    private static class Implementation extends BaseImplementation {
        private final String base;

        public Implementation(String base) {
            this.base = base + "/api/local-files";
        }

        @Override
        public Response actualAct(Request req) throws Exception {
            String path = decodePath(req, base);
            Set<BackupFile> files = InstanceFactory.getInstance(FileSystemAccess.class).directoryFiles(path);
            return new RsText(EXTERNAL_BACKUP_FILES_WRITER.writeValueAsString(files
                    .stream().map(ExternalBackupFile::new).collect(Collectors.toList())));
        }
    }
}
