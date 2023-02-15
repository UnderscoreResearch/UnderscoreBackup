package com.underscoreresearch.backup.cli.web;

import org.takes.Request;
import org.takes.Response;

import com.underscoreresearch.backup.io.IOIndex;

public class RebuildAvailableGet extends JsonWrap {
    public RebuildAvailableGet() {
        super(new Implementation());
    }

    private static class Implementation extends BaseImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {
            DestinationDecoder destination = new DestinationDecoder(req);
            if (destination.getResponse() != null) {
                return destination.getResponse();
            }
            if (!(destination.getProvider() instanceof IOIndex)) {
                return messageJson(400, "Destination " + destination + " does not support index");
            }

            IOIndex index = (IOIndex) destination.getProvider();
            if (index.rebuildAvailable())
                return messageJson(200, "Rebuild " + destination + " available");
            return messageJson(404, "Rebuild " + destination + " not available");
        }
    }
}
