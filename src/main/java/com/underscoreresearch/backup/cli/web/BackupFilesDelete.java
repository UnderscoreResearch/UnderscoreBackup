package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;

import java.io.IOException;
import java.util.List;

import org.takes.Request;
import org.takes.Response;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.manifest.BackupContentsAccess;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.BackupFile;

public class BackupFilesDelete extends JsonWrap {
    public BackupFilesDelete(String base) {
        super(new Implementation(base));
    }

    private static class Implementation extends BaseImplementation {
        private final String base;

        public Implementation(String base) {
            this.base = base + "/api/backup-files";
        }

        @Override
        public Response actualAct(Request req) throws Exception {
            String path = DestinationDecoder.decodePath(req, base);

            MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);
            BackupContentsAccess access = InstanceFactory.getInstance(ManifestManager.class)
                    .backupContents(null, true);

            deleteContents(repository, access, path);

            return messageJson(200, "Deleted");
        }

        private void deleteContents(MetadataRepository repository, BackupContentsAccess access, String path) throws IOException {
            List<BackupFile> dirs = access.directoryFiles(path);
            if (dirs != null) {
                for (BackupFile file : dirs) {
                    if (file.isDirectory()) {
                        deleteContents(repository, access, file.getPath());
                    } else {
                        deleteFiles(repository, file.getPath());
                    }
                }
            } else if (!PathNormalizer.ROOT.equals(path)) {
                if (path.endsWith(PATH_SEPARATOR))
                    path = path.substring(0, path.length() - 1);

                deleteFiles(repository, path);
            }

            BackupDirectory directory = repository.directory(path, null, false);
            while (directory != null) {
                repository.deleteDirectory(directory.getPath(), directory.getAdded());
                directory = repository.directory(path, null, false);
            }
        }

        private void deleteFiles(MetadataRepository repository, String path) throws IOException {
            BackupFile file = repository.file(path, null);
            while (file != null) {
                repository.deleteFile(file);
                file = repository.file(path, null);
            }
        }
    }
}
