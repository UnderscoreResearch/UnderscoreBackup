package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.file.PathNormalizer.ROOT;

import java.io.IOException;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.takes.Request;
import org.takes.Response;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.CloseableStream;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.manifest.BackupContentsAccess;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.utils.ProcessingStoppedException;

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
                log.info("Manually deleting contents of \"{}\"", PathNormalizer.physicalPath(path));
                // We only need to delete all references to the file or directory itself and then let the trim operation
                // do the rest. That's when the blocks get deleted anyway.
                String withoutSeparatorEnd = !path.isEmpty() ? path.substring(0, path.length() - 1) : ROOT;
                String withSeparatorEnd = withoutSeparatorEnd + PATH_SEPARATOR;

                BackupFile file = repository.file(withoutSeparatorEnd, null);
                while (file != null) {
                    repository.deleteFile(file);
                    file = repository.file(withoutSeparatorEnd, null);
                }

                BackupDirectory directory = repository.directory(withSeparatorEnd, null, false);
                while (directory != null) {
                    repository.deleteDirectory(directory.getPath(), directory.getAdded());
                    directory = repository.directory(withSeparatorEnd, null, false);
                }

                try (CloseableStream<BackupDirectory> directories = repository.allDirectories(true)) {
                    directories.stream().forEachOrdered(d -> {
                        if (!"".equals(d.getPath())) {
                            throw new ProcessingStoppedException();
                        }
                        NavigableSet<String> newSet = new TreeSet<>();
                        for (String f : d.getFiles()) {
                            if (!path.equals(ROOT) && !f.equals(withoutSeparatorEnd) && !f.startsWith(withSeparatorEnd)) {
                                newSet.add(f);
                            }
                        }
                        if (newSet.size() != d.getFiles().size()) {
                            try {
                                if (newSet.isEmpty()) {
                                    repository.deleteDirectory(d.getPath(), d.getAdded());
                                } else {
                                    d.setFiles(newSet);
                                    repository.addDirectory(d);
                                }
                            } catch (IOException e) {
                                log.error("Failed to update root directory to remote \"{}\"", path, e);
                            }
                        }
                    });
                } catch (ProcessingStoppedException ignored2) {
                }
            }
        }

        @Override
        protected String getBusyMessage() {
            return "Manually deleting files";
        }
    }
}
