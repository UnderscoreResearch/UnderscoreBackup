package com.underscoreresearch.backup.cli.web;

import org.takes.Request;
import org.takes.Response;
import org.takes.rs.RsWithBody;
import org.takes.rs.RsWithType;

public class DestinationDownloadGet extends JsonWrap {

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
