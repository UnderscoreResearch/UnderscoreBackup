package com.underscoreresearch.backup.ui.web.methods;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.ui.web.BaseImplementation;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import com.underscoreresearch.backup.ui.web.PrivateKeyRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.takes.HttpException;
import org.takes.Request;
import org.takes.Response;

import static com.underscoreresearch.backup.ui.web.PsAuthedContent.encryptResponse;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

/**
 * Web endpoint for checking if an encryption key is specified and validating passwords.
 * This class handles requests to check if an encryption key exists and to validate passwords.
 */
@Slf4j
public class KeyPost extends BaseWrap {
    private static final ObjectWriter WRITER = MAPPER
            .writerFor(KeyResponse.class);

    /**
     * Creates a new KeyPost instance.
     */
    public KeyPost() {
        super(new Implementation());
    }

    /**
     * Response class for key validation requests.
     * Contains a boolean indicating if the key is specified.
     */
    @Data
    @AllArgsConstructor
    public static class KeyResponse {
        private Boolean isSpecified;
    }

    /**
     * Implementation class that handles key validation requests.
     */
    private static class Implementation extends BaseImplementation {
        /**
         * Processes a request to check if an encryption key is specified or to validate a password.
         * If a password is provided, validates it against the encryption key.
         * If no password is provided, checks if an encryption key exists.
         *
         * @param req The HTTP request
         * @return The HTTP response indicating if the key is specified or if the password is valid
         * @throws Exception If an error occurs during processing
         */
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
