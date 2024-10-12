package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;

import java.io.IOException;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.takes.Request;
import org.takes.Response;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.manifest.BackupContentsAccess;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.BackupFile;

@Slf4j
public class BackupFilesDelete extends BaseWrap {
    public BackupFilesDelete(String base) {
        super(new Implementation(base));
    }

    private static class Implementation extends ExclusiveImplementation {
        private final String base;

        public Implementation(String base) {
            this.base = base + "/api/backup-files";
        }

        @Override
        public Response actualAct(Request req) throws Exception {
            String path = DestinationDecoder.decodePath(req, base);

            deleteContents(path);

            return messageJson(200, "Deleted");
        }

        private void deleteContents(String path) throws IOException {
            MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);

            try (CloseableLock ignored = repository.acquireLock()) {
                log.info("Manually deleting contents of \"{}\"", path);
                // We only need to delete all references to the file or directory itself and then let the trim operation
                // do the rest. That's when the blocks get deleted anyway.
                BackupFile file = repository.file(path, null);
                while (file != null) {
                    repository.deleteFile(file);
                    file = repository.file(path, null);
                }

                BackupDirectory directory = repository.directory(path, null, false);
                while (directory != null) {
                    repository.deleteDirectory(directory.getPath(), directory.getAdded());
                    directory = repository.directory(path, null, false);
                }
            }
        }

        @Override
        protected String getBusyMessage() {
            return "Manually deleting files";
        }
    }
}
