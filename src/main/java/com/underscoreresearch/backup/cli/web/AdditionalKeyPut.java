package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.cli.web.PsAuthedContent.encryptResponse;
import static com.underscoreresearch.backup.cli.web.service.CreateSecretPut.encryptionIdentity;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.security.GeneralSecurityException;

import lombok.Data;

import org.takes.Request;
import org.takes.Response;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.IdentityKeys;
import com.underscoreresearch.backup.manifest.ManifestManager;

public class AdditionalKeyPut extends BaseWrap {
    private static final ObjectWriter WRITER = MAPPER.writerFor(ExternalEncryptionKey.class);

    public AdditionalKeyPut() {
        super(new Implementation());
    }

    @Data
    public static class ExternalEncryptionKey {
        private String keyHash;
        private String publicKey;
        private String privateKey;

        public ExternalEncryptionKey(IdentityKeys key, EncryptionIdentity.PrivateIdentity privateIdentity) throws GeneralSecurityException {
            if (key != null) {
                keyHash = key.getPublicKeyHash();
                publicKey = key.getPublicKeyString();
                privateKey = key.getPrivateKeyString(privateIdentity);
            }
        }
    }

    private static class Implementation extends ExclusiveImplementation {
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

        @Override
        protected String getBusyMessage() {
            return "Adding additional key";
        }
    }
}
