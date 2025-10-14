package com.underscoreresearch.backup.ui.web.methods;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.FileSystemAccess;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.ExternalBackupFile;
import com.underscoreresearch.backup.ui.web.BaseImplementation;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import org.takes.Request;
import org.takes.Response;

import java.util.Set;
import java.util.stream.Collectors;

import static com.underscoreresearch.backup.ui.web.DestinationDecoder.decodePath;
import static com.underscoreresearch.backup.ui.web.PsAuthedContent.encryptResponse;
import static com.underscoreresearch.backup.utils.SerializationUtils.EXTERNAL_BACKUP_FILES_WRITER;

/**
 * Web endpoint for listing files in a local directory.
 * This class handles requests to list files in a specific directory on the local filesystem.
 */
public class ListLocalFilesGet extends BaseWrap {

    /**
     * Creates a new ListLocalFilesGet instance.
     *
     * @param base The base path for the API
     */
    public ListLocalFilesGet(String base) {
        super(new Implementation(base));
    }

    /**
     * Implementation class that handles local file listing requests.
     */
    private static class Implementation extends BaseImplementation {
        private final String base;

        /**
         * Creates a new Implementation instance.
         *
         * @param base The base path for the API
         */
        public Implementation(String base) {
            this.base = base + "/api/local-files";
        }

        /**
         * Processes a request to list local files.
         * Returns a list of files in the specified directory on the local filesystem.
         *
         * @param req The HTTP request
         * @return The HTTP response containing the list of files
         * @throws Exception If an error occurs during processing
         */
        @Override
        public Response actualAct(Request req) throws Exception {
            String path = decodePath(req, base);
            Set<BackupFile> files = InstanceFactory.getInstance(FileSystemAccess.class).directoryFiles(path);
            return encryptResponse(req, EXTERNAL_BACKUP_FILES_WRITER.writeValueAsString(files
                    .stream().map(ExternalBackupFile::new).collect(Collectors.toList())));
        }
    }
}
