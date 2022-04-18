package com.underscoreresearch.backup.cli.web;

import lombok.AllArgsConstructor;
import lombok.Data;

import org.takes.HttpException;
import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.rs.RsText;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.PublicKeyEncrypion;

public class KeyPost extends JsonWrap {
    @Data
    @AllArgsConstructor
    public static class KeyResponse {
        private Boolean specified;
    }

    private static ObjectWriter WRITER = new ObjectMapper()
            .writerFor(KeyResponse.class);

    public KeyPost() {
        super(new Implementation());
    }

    private static class Implementation implements Take {
        @Override
        public Response act(Request req) throws Exception {
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

                InstanceFactory.getInstance(PublicKeyEncrypion.class);
                return new RsText(WRITER.writeValueAsString(new KeyResponse(true)));
            } catch (Exception exc) {
                return new RsText(WRITER.writeValueAsString(new KeyResponse(false)));
            }
        }
    }
}
