package com.underscoreresearch.backup.ui.web.methods;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.CloseableStream;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import com.underscoreresearch.backup.ui.web.DestinationDecoder;
import com.underscoreresearch.backup.ui.web.ExclusiveImplementation;
import com.underscoreresearch.backup.utils.ProcessingStoppedException;
import lombok.extern.slf4j.Slf4j;
import org.takes.Request;
import org.takes.Response;

import java.io.IOException;
import java.util.NavigableSet;
import java.util.TreeSet;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.file.PathNormalizer.ROOT;

/**
 * Web endpoint for deleting backup files.
 * This class handles requests to delete files or directories from the backup repository.
 */
@Slf4j
public class BackupFilesDelete extends BaseWrap {
    /**
     * Creates a new BackupFilesDelete instance.
     *
     * @param base The base path for the API
     */
    public BackupFilesDelete(String base) {
        super(new Implementation(base));
    }

    /**
     * Implementation class that handles backup file deletion requests.
     */
    private static class Implementation extends ExclusiveImplementation {
        private final String base;

        /**
         * Creates a new Implementation instance.
         *
         * @param base The base path for the API
         */
        public Implementation(String base) {
            this.base = base + "/api/backup-files";
        }

        /**
         * Processes a request to delete backup files.
         * Deletes the specified path and all its contents from the backup repository.
         *
         * @param req The HTTP request
         * @return The HTTP response
         * @throws Exception If an error occurs during processing
         */
        @Override
        public Response actualAct(Request req) throws Exception {
            String path = DestinationDecoder.decodePath(req, base);

            deleteContents(path);

            return messageJson(200, "Deleted");
        }

        /**
         * Deletes the contents of a path from the backup repository.
         *
         * @param path The path to delete
         * @throws IOException If an error occurs during deletion
         */
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

        /**
         * Gets the message to display when the system is busy.
         *
         * @return The busy message
         */
        @Override
        protected String getBusyMessage() {
            return "Manually deleting files";
        }
    }
}
