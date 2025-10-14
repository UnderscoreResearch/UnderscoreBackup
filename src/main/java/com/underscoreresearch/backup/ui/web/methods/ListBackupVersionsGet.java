package com.underscoreresearch.backup.ui.web.methods;

import com.google.common.collect.Lists;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.model.ExternalBackupFile;
import com.underscoreresearch.backup.ui.web.BaseImplementation;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import org.takes.Request;
import org.takes.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.underscoreresearch.backup.ui.web.DestinationDecoder.decodeFile;
import static com.underscoreresearch.backup.ui.web.PsAuthedContent.encryptResponse;
import static com.underscoreresearch.backup.utils.SerializationUtils.EXTERNAL_BACKUP_FILES_WRITER;

/**
 * Web endpoint for listing versions of a backup file.
 * This class handles requests to list all versions of a specific file in the backup repository.
 */
public class ListBackupVersionsGet extends BaseWrap {

    /**
     * Creates a new ListBackupVersionsGet instance.
     *
     * @param base The base path for the API
     */
    public ListBackupVersionsGet(String base) {
        super(new Implementation(base));
    }

    /**
     * Implementation class that handles backup file version listing requests.
     */
    private static class Implementation extends BaseImplementation {
        private final String base;

        /**
         * Creates a new Implementation instance.
         *
         * @param base The base path for the API
         */
        public Implementation(String base) {
            this.base = base + "/api/backup-versions";
        }

        /**
         * Gets the versions of a file from a request.
         * Extracts the file path from the request and retrieves all versions of the file.
         *
         * @param req The HTTP request
         * @param base The base path for the API
         * @return A list of file versions
         * @throws IOException If an error occurs while retrieving the versions
         */
        public static List<ExternalBackupFile> getRequestVersions(Request req, String base) throws IOException {
            String path = decodeFile(req, base);

            List<ExternalBackupFile> versions = InstanceFactory.getInstance(MetadataRepository.class).file(path);

            return versions != null ? Lists.reverse(versions) : new ArrayList<>();
        }

        /**
         * Processes a request to list file versions.
         * Returns a list of all versions of the specified file.
         *
         * @param req The HTTP request
         * @return The HTTP response containing the list of file versions
         * @throws Exception If an error occurs during processing
         */
        @Override
        public Response actualAct(Request req) throws Exception {
            return encryptResponse(req, EXTERNAL_BACKUP_FILES_WRITER.writeValueAsString(
                    getRequestVersions(req, base)));
        }
    }
}
