package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.cli.web.DestinationDecoder.decodePath;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.rs.RsText;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.FileSystemAccess;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.ExternalBackupFile;

public class ListLocalFilesGet extends JsonWrap {
    private static ObjectWriter WRITER = new ObjectMapper()
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
            .writerFor(new TypeReference<List<ExternalBackupFile>>() {
            });

    public ListLocalFilesGet(String base) {
        super(new Implementation(base));
    }

    private static class Implementation extends BaseImplementation {
        private final String base;

        public Implementation(String base) {
            this.base = base + "/api/local-files";
        }

        @Override
        public Response actualAct(Request req) throws Exception {
            String path = decodePath(req, base);
            Set<BackupFile> files = InstanceFactory.getInstance(FileSystemAccess.class).directoryFiles(path);
            return new RsText(WRITER.writeValueAsString(files
                    .stream().map(t -> new ExternalBackupFile(t)).collect(Collectors.toList())));
        }
    }
}
