package com.underscoreresearch.backup.cli.web;

import java.util.Set;

import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.rs.RsWithBody;
import org.takes.rs.RsWithType;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.model.BackupFile;

public class DestinationDownloadGet extends JsonWrap {
    private static ObjectWriter WRITER = new ObjectMapper()
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
            .writerFor(new TypeReference<Set<BackupFile>>() {
            });

    public DestinationDownloadGet(String base) {
        super(new Implementation(base));
    }

    private static class Implementation extends BaseImplementation {
        private final String base;

        public Implementation(String base) {
            this.base = base + "/api/backup-download";
        }

        @Override
        public Response actualAct(Request req) throws Exception {
            try {
                DestinationDecoder destination
                        = new DestinationDecoder(req, base);
                if (destination.getResponse() != null) {
                    return destination.getResponse();
                }
                return new RsWithType(new RsWithBody(destination.getProvider().download(destination.getPath())),
                        "application/octet-stream");
            } catch (Exception exc) {
                return messageJson(400, exc.getMessage());
            }
        }
    }
}
