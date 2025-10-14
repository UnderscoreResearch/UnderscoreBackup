package com.underscoreresearch.backup.ui.web.methods;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.model.ExternalBackupFile;
import com.underscoreresearch.backup.ui.web.BaseImplementation;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import org.takes.Request;
import org.takes.Response;

import java.util.ArrayList;
import java.util.stream.Collectors;

import static com.underscoreresearch.backup.ui.web.DestinationDecoder.getRequestFiles;
import static com.underscoreresearch.backup.ui.web.PsAuthedContent.encryptResponse;
import static com.underscoreresearch.backup.utils.SerializationUtils.EXTERNAL_BACKUP_FILES_WRITER;

/**
 * Web endpoint for listing backup files in a directory.
 * This class handles requests to list files in a specific directory in the backup repository.
 */
public class ListBackupFilesGet extends BaseWrap {
    /**
     * Creates a new ListBackupFilesGet instance.
     *
     * @param base The base path for the API
     */
    public ListBackupFilesGet(String base) {
        super(new Implementation(base));
    }

    /**
     * Implementation class that handles backup file listing requests.
     */
    private static class Implementation extends BaseImplementation {
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
         * Processes a request to list backup files.
         * Returns a list of files in the specified directory.
         *
         * @param req The HTTP request
         * @return The HTTP response containing the list of files
         * @throws Exception If an error occurs during processing
         */
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
