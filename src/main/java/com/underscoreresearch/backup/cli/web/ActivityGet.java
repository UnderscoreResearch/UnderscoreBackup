package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.cli.web.PsAuthedContent.encryptResponse;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.misc.Href;
import org.takes.rq.RqHref;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.inject.ProvisionException;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.utils.StateLogger;
import com.underscoreresearch.backup.utils.StatusLine;

@Slf4j
public class ActivityGet extends BaseWrap {

    private static final ObjectWriter WRITER = MAPPER
            .writerFor(StatusResponse.class);

    public ActivityGet() {
        super(new Implementation());
    }

    @AllArgsConstructor
    @Data
    public static class StatusResponse {
        private List<StatusLine> status;
    }

    private static class Implementation implements Take {
        @Override
        public Response act(Request req) throws Exception {
            try {
                Href href = new RqHref.Base(req).href();
                boolean temporal = "true".equals(href.param("temporal").iterator().hasNext() ? href.param("temporal").iterator().next() : "false");
                List<StatusLine> statusLines;
                if (InstanceFactory.hasConfiguration(false) && hasKey()) {
                    statusLines = InstanceFactory.getInstance(StateLogger.class).logData(
                            temporal ? (type -> type == StateLogger.Type.LOG) : type -> type != StateLogger.Type.LOG);
                } else {
                    statusLines = new ArrayList<>();
                }
                return encryptResponse(req, WRITER.writeValueAsString(new StatusResponse(statusLines)));
            } catch (Throwable exc) {
                log.error("Failed to fetch current activity", exc);
            }
            return messageJson(404, "Failed to fetch current activity");
        }

        private boolean hasKey() {
            try {
                InstanceFactory.getInstance(EncryptionIdentity.class);
                return true;
            } catch (ProvisionException exc) {
                return false;
            }
        }
    }
}
