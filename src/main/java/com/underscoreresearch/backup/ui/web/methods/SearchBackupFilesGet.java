package com.underscoreresearch.backup.ui.web.methods;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Strings;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.CloseableStream;
import com.underscoreresearch.backup.manifest.BackupSearchAccess;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.manifest.implementation.BackupSearchAccessImpl;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.ExternalBackupFile;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import com.underscoreresearch.backup.ui.web.ExclusiveImplementation;
import org.takes.HttpException;
import org.takes.Request;
import org.takes.Response;
import org.takes.misc.Href;
import org.takes.rq.RqHref;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.underscoreresearch.backup.ui.web.PsAuthedContent.encryptResponse;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

/**
 * Web endpoint for searching backup files.
 * This class handles requests to search for files in the backup repository using regular expressions.
 */
public class SearchBackupFilesGet extends BaseWrap {
    private static final long MAX_HITS = 1000;
    private static final ObjectWriter WRITER = MAPPER.writerFor(new TypeReference<List<ExternalBackupFile>>() {
    });

    /**
     * Creates a new SearchBackupFilesGet instance.
     *
     * @param base The base path for the API
     */
    public SearchBackupFilesGet(String base) {
        super(new Implementation(base));
    }

    /**
     * Gets files matching a search query from a request.
     * Extracts search parameters from the request and performs the search.
     *
     * @param req The HTTP request
     * @return A list of files matching the search query
     * @throws IOException If an error occurs while searching
     */
    public static List<BackupFile> getRequestFiles(Request req) throws IOException {
        Href href = new RqHref.Base(req).href();

        Long timestamp = null;
        for (String ts : href.param("timestamp")) {
            timestamp = Long.parseLong(ts);
        }
        boolean deleted = false;
        for (String val : href.param("include-deleted")) {
            if ("true".equals(val)) {
                deleted = true;
                break;
            }
        }

        String search = null;
        for (String val : href.param("q")) {
            search = val;
        }

        if (Strings.isNullOrEmpty(search)) {
            throw new HttpException(
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    "Missing search parameter"
            );
        }

        Pattern pattern;
        try {
            pattern = Pattern.compile(search, Pattern.CASE_INSENSITIVE);
        } catch (Exception exc) {
            throw new HttpException(
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    "Invalid search regular expression"
            );
        }

        BackupSearchAccess access = InstanceFactory.getInstance(ManifestManager.class).backupSearch(timestamp,
                deleted);

        List<BackupFile> ret = new ArrayList<>();

        try (CloseableLock interrupt = access.acquireLock()) {
            try (CloseableStream<BackupFile> files = access.searchFiles(pattern, interrupt)) {
                files.stream().forEach(item -> {
                    ret.add(item);
                    if (ret.size() == MAX_HITS) {
                        throw new BackupSearchAccessImpl.InterruptedSearch();
                    }
                });
            } catch (BackupSearchAccessImpl.InterruptedSearch ignored) {
            }
        }

        return ret;
    }

    /**
     * Implementation class that handles backup file search requests.
     */
    private static class Implementation extends ExclusiveImplementation {
        /**
         * Creates a new Implementation instance.
         *
         * @param base The base path for the API
         */
        public Implementation(String base) {
        }

        /**
         * Processes a request to search for backup files.
         * Returns a list of files matching the search query.
         *
         * @param req The HTTP request
         * @return The HTTP response containing the list of matching files
         * @throws Exception If an error occurs during processing
         */
        @Override
        public Response actualAct(Request req) throws Exception {
            return encryptResponse(req, WRITER.writeValueAsString(
                    getRequestFiles(req)
                            .stream()
                            .map(ExternalBackupFile::new)
                            .collect(Collectors.toList())));
        }

        /**
         * Gets the message to display when the system is busy.
         *
         * @return The busy message
         */
        @Override
        protected String getBusyMessage() {
            return "Searching files";
        }
    }
}
