package com.underscoreresearch.backup.ui.web.methods.service;

import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.base.Strings;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import com.underscoreresearch.backup.ui.web.ExclusiveImplementation;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.Hash;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.service.api.model.SecretRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.takes.Request;
import org.takes.Response;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

import static com.underscoreresearch.backup.ui.web.PsAuthedContent.decodeRequestBody;
import static com.underscoreresearch.backup.configuration.EncryptionModule.ROOT_KEY;
import static com.underscoreresearch.backup.manifest.implementation.ServiceManagerImpl.sendApiFailureOn;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

/**
 * Endpoint for creating secrets in the service.
 * This class handles the creation and storage of encryption keys as secrets in the backup service.
 */
public class CreateSecretPut extends BaseWrap {
    private static final ObjectReader READER = MAPPER.readerFor(CreateSecretPutRequest.class);

    /**
     * Constructor for the CreateSecretPut endpoint.
     */
    public CreateSecretPut() {
        super(new Implementation());
    }

    /**
     * Retrieves the encryption identity from the instance factory.
     * 
     * @return The encryption identity or null if not available
     */
    public static EncryptionIdentity encryptionIdentity() {
        try {
            return InstanceFactory.getInstance(ROOT_KEY, EncryptionIdentity.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Creates a secret in the service using the provided credentials.
     * 
     * @param serviceManager The service manager instance
     * @param encryptionIdentity The encryption identity to use
     * @param region The region to store the secret in
     * @param password The password to encrypt the secret with
     * @param email The email associated with the secret
     * @return Response object if there's an error, null if successful
     * @throws IOException If there's an error communicating with the service
     */
    public static Response createSecret(ServiceManager serviceManager,
                                        EncryptionIdentity encryptionIdentity,
                                        String region, String password, String email) throws IOException {
        EncryptionIdentity secretKey;
        try {
            secretKey = encryptionIdentity.changeEncryptionPassword(password,
                    email, false);
        } catch (GeneralSecurityException exc) {
            return messageJson(403, "Invalid password");
        }
        String secret;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            secretKey.writeKey(EncryptionIdentity.KeyFormat.SERVICE, output);
            secret = output.toString(StandardCharsets.UTF_8);
        }
        String emailHash = Hash.hash64(email.getBytes(StandardCharsets.UTF_8));

        serviceManager.call(region, (api) -> api.createSecret(serviceManager.getSourceId(),
                new SecretRequest().secret(secret).emailHash(emailHash)));
        return null;
    }

    /**
     * Data class for the create secret request.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateSecretPutRequest {
        private String email;
        private String region;
        private String password;
    }

    /**
     * Implementation of the exclusive implementation that handles the HTTP request.
     */
    private static class Implementation extends ExclusiveImplementation {
        /**
         * Processes the HTTP request to create a secret.
         * 
         * @param req The HTTP request
         * @return Response indicating success or failure
         * @throws Exception If there's an error processing the request
         */
        @Override
        public Response actualAct(Request req) throws Exception {
            String config = decodeRequestBody(req);
            CreateSecretPutRequest request = READER.readValue(config);
            try {
                ServiceManager serviceManager = InstanceFactory.getInstance(ServiceManager.class);
                if (Strings.isNullOrEmpty(serviceManager.getSourceId())) {
                    return messageJson(400, "No source selected");
                }

                if (Strings.isNullOrEmpty(request.getRegion())
                        || Strings.isNullOrEmpty(request.getEmail())
                        || Strings.isNullOrEmpty(request.getPassword())) {
                    return messageJson(400, "Missing required parameter");
                }

                Response Invalid_password = createSecret(serviceManager,
                        InstanceFactory.getInstance(EncryptionIdentity.class),
                        request.getRegion(), request.getPassword(), request.getEmail());
                if (Invalid_password != null) return Invalid_password;

                return messageJson(200, "Saved secret");
            } catch (IOException exc) {
                return sendApiFailureOn(exc);
            }
        }

        /**
         * Returns the message to display when the system is busy.
         * 
         * @return The busy message
         */
        @Override
        protected String getBusyMessage() {
            return "Storing private key recovery information";
        }
    }
}
