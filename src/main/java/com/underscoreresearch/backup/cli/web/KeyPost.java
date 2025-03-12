package com.underscoreresearch.backup.cli.web;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.takes.HttpException;
import org.takes.Request;
import org.takes.Response;

import static com.underscoreresearch.backup.cli.web.PsAuthedContent.encryptResponse;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

@Slf4j
public class KeyPost extends BaseWrap {
    private static final ObjectWriter WRITER = MAPPER
            .writerFor(KeyResponse.class);

    public KeyPost() {
        super(new Implementation());
    }

    @Data
    @AllArgsConstructor
    public static class KeyResponse {
        private Boolean isSpecified;
    }

    private static class Implementation extends BaseImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {
            String password = null;
            try {
                password = PrivateKeyRequest.decodePrivateKeyRequest(req);
            } catch (HttpException ignored) {
            }
            try {
                if (password != null) {
                    if (PrivateKeyRequest.validatePassword(password)) {
                        return encryptResponse(req, WRITER.writeValueAsString(new KeyResponse(true)));
                    } else {
                        return messageJson(403, "Invalid password provided");
                    }
                }

                InstanceFactory.getInstance(EncryptionIdentity.class);
                return encryptResponse(req, WRITER.writeValueAsString(new KeyResponse(true)));
            } catch (Exception exc) {
                log.warn("Failed to get key");
                return encryptResponse(req, WRITER.writeValueAsString(new KeyResponse(false)));
            }
        }
    }
}
