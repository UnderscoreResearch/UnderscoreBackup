package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.cli.web.DestinationDecoder.decodeFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.rs.RsText;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.Lists;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.ExternalBackupFile;

public class ListBackupVersionsGet extends JsonWrap {
    private static ObjectWriter WRITER = new ObjectMapper()
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
            .writerFor(new TypeReference<List<ExternalBackupFile>>() {
            });

    public ListBackupVersionsGet(String base) {
        super(new Implementation(base));
    }

    private static class Implementation extends BaseImplementation {
        private final String base;

        public Implementation(String base) {
            this.base = base + "/api/backup-versions";
        }

        @Override
        public Response actualAct(Request req) throws Exception {
            return new RsText(WRITER.writeValueAsString(
                    getRequestVersions(req, base)
                            .stream()
                            .map(t -> new ExternalBackupFile(t))
                            .collect(Collectors.toList())));
        }

        public static List<BackupFile> getRequestVersions(Request req, String base) throws IOException {
            String path = decodeFile(req, base);

            List<BackupFile> versions = InstanceFactory.getInstance(MetadataRepository.class).file(path);

            return versions != null ? Lists.reverse(versions) : new ArrayList<>();
        }
    }
}
