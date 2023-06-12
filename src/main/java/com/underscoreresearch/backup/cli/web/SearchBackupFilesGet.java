package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.takes.HttpException;
import org.takes.Request;
import org.takes.Response;
import org.takes.misc.Href;
import org.takes.rq.RqHref;
import org.takes.rs.RsText;

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

public class SearchBackupFilesGet extends JsonWrap {
    private static final long MAX_HITS = 1000;
    private static ObjectWriter WRITER = MAPPER.writerFor(new TypeReference<List<ExternalBackupFile>>() {
    });

    public SearchBackupFilesGet(String base) {
        super(new Implementation(base));
    }

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
            } catch (BackupSearchAccessImpl.InterruptedSearch exc) {
            }
        }

        return ret;
    }

    private static class Implementation extends ExclusiveImplementation {
        public Implementation(String base) {
        }

        @Override
        public Response actualAct(Request req) throws Exception {
            return new RsText(WRITER.writeValueAsString(
                    getRequestFiles(req)
                            .stream()
                            .map(t -> new ExternalBackupFile(t))
                            .collect(Collectors.toList())));
        }

        @Override
        protected String getBusyMessage() {
            return "Searching files";
        }
    }
}
