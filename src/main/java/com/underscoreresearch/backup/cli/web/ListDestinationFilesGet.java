package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.utils.SerializationUtils.EXTERNAL_BACKUP_FILES_WRITER;

import java.util.stream.Collectors;

import org.takes.Request;
import org.takes.Response;
import org.takes.rs.RsText;

import com.underscoreresearch.backup.io.IOIndex;
import com.underscoreresearch.backup.model.ExternalBackupFile;

public class ListDestinationFilesGet extends JsonWrap {

    public ListDestinationFilesGet(String base) {
        super(new Implementation(base));
    }

    private static class Implementation extends BaseImplementation {
        private final String base;

        public Implementation(String base) {
            this.base = base + "/api/destination-files";
        }

        @Override
        public Response actualAct(Request req) throws Exception {
            DestinationDecoder destination = new DestinationDecoder(req, base);
            if (destination.getResponse() != null) {
                return destination.getResponse();
            }
            if (!(destination.getProvider() instanceof IOIndex)) {
                return messageJson(400, "Destination " + destination + " does not support index");
            }

            IOIndex index = (IOIndex) destination.getProvider();
            return new RsText(EXTERNAL_BACKUP_FILES_WRITER.writeValueAsString(index.availableKeys(destination.getPath()).stream()
                    .map(t -> ExternalBackupFile.builder().path(destination.getPath() + t).build())
                    .collect(Collectors.toList())));
        }
    }
}
