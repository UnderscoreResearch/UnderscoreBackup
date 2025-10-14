package com.underscoreresearch.backup.ui.web.methods;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.IdentityKeys;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.ui.web.AdditionalPrivateKeyRequest;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import com.underscoreresearch.backup.ui.web.ExclusiveImplementation;
import lombok.Data;
import org.takes.Request;
import org.takes.Response;

import java.security.GeneralSecurityException;

import static com.underscoreresearch.backup.ui.web.PsAuthedContent.encryptResponse;
import static com.underscoreresearch.backup.ui.web.methods.service.CreateSecretPut.encryptionIdentity;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

/**
 * Web endpoint for adding additional encryption keys.
 * This class handles the creation and addition of new encryption keys to the system.
 */
public class AdditionalKeyPut extends BaseWrap {
    private static final ObjectWriter WRITER = MAPPER.writerFor(ExternalEncryptionKey.class);

    /**
     * Creates a new AdditionalKeyPut instance.
     */
    public AdditionalKeyPut() {
        super(new Implementation());
    }

    /**
     * Data class representing an external encryption key.
     */
    @Data
    public static class ExternalEncryptionKey {
        private String keyHash;
        private String publicKey;
        private String privateKey;

        /**
         * Creates a new ExternalEncryptionKey from the provided key and private identity.
         *
         * @param key The identity keys
         * @param privateIdentity The private identity for encryption
         * @throws GeneralSecurityException If there's an error with the encryption
         */
        public ExternalEncryptionKey(IdentityKeys key, EncryptionIdentity.PrivateIdentity privateIdentity) throws GeneralSecurityException {
            if (key != null) {
                keyHash = key.getPublicKeyHash();
                publicKey = key.getPublicKeyString();
                privateKey = key.getPrivateKeyString(privateIdentity);
            }
        }
    }

    /**
     * Implementation class that handles adding additional encryption keys.
     */
    private static class Implementation extends ExclusiveImplementation {
        /**
         * Processes the request to add an additional encryption key.
         * Validates the password, creates or imports a key, and adds it to the system.
         *
         * @param req The HTTP request
         * @return The HTTP response containing the new key information
         * @throws Exception If an error occurs during processing
         */
        @Override
        public Response actualAct(Request req) throws Exception {
            AdditionalPrivateKeyRequest request = AdditionalPrivateKeyRequest.decodePrivateKeyRequest(req);

            EncryptionIdentity masterKey = encryptionIdentity();
            EncryptionIdentity.PrivateIdentity privateIdentity;
            try {
                privateIdentity = masterKey.getPrivateIdentity(request.getPassword());
            } catch (Exception exc) {
                return messageJson(403, "Invalid password provided");
            }

            IdentityKeys key;
            if (request.getPrivateKey() != null) {
                key = IdentityKeys.fromString(request.getPrivateKey(), privateIdentity);
                if (!key.hasPrivateKey()) {
                    return messageJson(400, "Not a private key");
                }
                try {
                    masterKey.getIdentityKeyForHash(key.getKeyIdentifier());
                    return encryptResponse(req, WRITER.writeValueAsString(new ExternalEncryptionKey(null, null)));
                } catch (IndexOutOfBoundsException exc) {
                    // Should end up here if key doesn't already exist.
                }
            } else {
                key = IdentityKeys.createIdentityKeys(privateIdentity);
            }
            masterKey.getAdditionalKeys().add(key);
            InstanceFactory.getInstance(ManifestManager.class).updateKeyData(masterKey);

            return encryptResponse(req, WRITER.writeValueAsString(new ExternalEncryptionKey(key, privateIdentity)));
        }

        /**
         * Gets the message to display when the system is busy.
         *
         * @return The busy message
         */
        @Override
        protected String getBusyMessage() {
            return "Adding additional key";
        }
    }
}
