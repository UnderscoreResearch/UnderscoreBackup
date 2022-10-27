package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import lombok.AllArgsConstructor;
import lombok.Data;

import org.takes.HttpException;
import org.takes.Request;
import org.takes.Response;
import org.takes.rs.RsText;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionKey;

public class KeyPost extends JsonWrap {
    private static ObjectWriter WRITER = MAPPER
            .writerFor(KeyResponse.class);

    public KeyPost() {
        super(new Implementation());
    }

    @Data
    @AllArgsConstructor
    public static class KeyResponse {
        private Boolean specified;
    }

    private static class Implementation extends BaseImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {
            String passphrase = null;
            try {
                passphrase = PrivateKeyRequest.decodePrivateKeyRequest(req);
            } catch (HttpException exc) {
            }
            try {
                if (passphrase != null) {
                    if (PrivateKeyRequest.validatePassphrase(passphrase)) {
                        return new RsText(WRITER.writeValueAsString(new KeyResponse(true)));
                    } else {
                        return messageJson(403, "Invalid passphrase provided");
                    }
                }

                InstanceFactory.getInstance(EncryptionKey.class);
                return new RsText(WRITER.writeValueAsString(new KeyResponse(true)));
            } catch (Exception exc) {
                return new RsText(WRITER.writeValueAsString(new KeyResponse(false)));
            }
        }
    }
}
