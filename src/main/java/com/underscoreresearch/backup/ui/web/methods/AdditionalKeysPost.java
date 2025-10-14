package com.underscoreresearch.backup.ui.web.methods;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.IdentityKeys;
import com.underscoreresearch.backup.ui.web.BaseImplementation;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import com.underscoreresearch.backup.ui.web.PrivateKeyRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.takes.Request;
import org.takes.Response;

import java.util.ArrayList;
import java.util.List;

import static com.underscoreresearch.backup.ui.web.PsAuthedContent.encryptResponse;
import static com.underscoreresearch.backup.ui.web.methods.service.CreateSecretPut.encryptionIdentity;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

/**
 * Web endpoint for retrieving additional encryption keys.
 * This class handles requests to retrieve all additional encryption keys in the system.
 */
public class AdditionalKeysPost extends BaseWrap {
    private static final ObjectWriter WRITER = MAPPER.writerFor(AdditionalKeysResponse.class);

    /**
     * Creates a new AdditionalKeysPost instance.
     */
    public AdditionalKeysPost() {
        super(new Implementation());
    }

    /**
     * Data class representing the response containing additional keys.
     */
    @Data
    @AllArgsConstructor
    private static class AdditionalKeysResponse {
        private List<AdditionalKeyPut.ExternalEncryptionKey> keys;
    }

    /**
     * Implementation class that handles retrieving additional encryption keys.
     */
    private static class Implementation extends BaseImplementation {
        /**
         * Processes the request to retrieve additional encryption keys.
         * Validates the password and returns all additional keys in the system.
         *
         * @param req The HTTP request
         * @return The HTTP response containing the additional keys
         * @throws Exception If an error occurs during processing
         */
        @Override
        public Response actualAct(Request req) throws Exception {
            String password = PrivateKeyRequest.decodePrivateKeyRequest(req);

            EncryptionIdentity masterKey = encryptionIdentity();
            EncryptionIdentity.PrivateIdentity privateIdentity;
            try {
                privateIdentity = masterKey.getPrivateIdentity(password);
            } catch (Exception exc) {
                return messageJson(403, "Invalid password provided");
            }

            List<AdditionalKeyPut.ExternalEncryptionKey> keys = new ArrayList<>();
            for (IdentityKeys key : masterKey.getAdditionalKeys()) {
                keys.add(new AdditionalKeyPut.ExternalEncryptionKey(key, privateIdentity));
            }

            return encryptResponse(req, WRITER.writeValueAsString(new AdditionalKeysResponse(keys)));
        }
    }
}
