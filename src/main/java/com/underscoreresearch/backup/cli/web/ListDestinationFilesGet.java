package com.underscoreresearch.backup.cli.web;

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
import com.underscoreresearch.backup.io.IOIndex;
import com.underscoreresearch.backup.model.ExternalBackupFile;

public class ListDestinationFilesGet extends JsonWrap {
    private static ObjectWriter WRITER = new ObjectMapper()
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
            .writerFor(new TypeReference<List<ExternalBackupFile>>() {
            });

    public ListDestinationFilesGet(String base) {
        super(new Implementation(base));
    }

    private static class Implementation implements Take {
        private final String base;

        public Implementation(String base) {
            this.base = base + "/api/destination-files";
        }

        @Override
        public Response act(Request req) throws Exception {
            DestinationDecoder destination = new DestinationDecoder(req, base);
            if (destination.getResponse() != null) {
                return destination.getResponse();
            }
            if (!(destination.getProvider() instanceof IOIndex)) {
                return messageJson(400, "Destination " + destination + " does not support index");
            }

            IOIndex index = (IOIndex) destination.getProvider();
            return new RsText(WRITER.writeValueAsString(index.availableKeys(destination.getPath()).stream()
                    .map(t -> ExternalBackupFile.builder().path(destination.getPath() + t).build())
                    .collect(Collectors.toList())));
        }
    }
}
