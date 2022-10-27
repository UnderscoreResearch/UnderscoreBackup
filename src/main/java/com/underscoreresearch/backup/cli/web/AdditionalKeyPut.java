package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import lombok.Data;

import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.rs.RsText;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionKey;
import com.underscoreresearch.backup.manifest.ManifestManager;

public class AdditionalKeyPut extends JsonWrap {
    private static ObjectWriter WRITER = MAPPER.writerFor(ExternalEncryptionKey.class);

    public AdditionalKeyPut() {
        super(new Implementation());
    }

    @Data
    public static class ExternalEncryptionKey {
        private String publicKey;
        private String privateKey;

        public ExternalEncryptionKey(EncryptionKey key) {
            if (key != null) {
                publicKey = key.getPublicKey();
                privateKey = key.getPrivateKey(null).getDisplayPrivateKey();
            }
        }
    }

    private static class Implementation implements Take {
        @Override
        public synchronized Response act(Request req) throws Exception {
            AdditionalPrivateKeyRequest request = AdditionalPrivateKeyRequest.decodePrivateKeyRequest(req);

            EncryptionKey.PrivateKey masterKey;
            try {
                masterKey = InstanceFactory.getInstance(EncryptionKey.class).getPrivateKey(request.getPassphrase());
            } catch (Exception exc) {
                return messageJson(403, "Invalid passphrase provided");
            }

            EncryptionKey key;
            if (request.getPrivateKey() != null) {
                key = EncryptionKey.createWithPrivateKey(request.getPrivateKey());
                if (!masterKey.getAdditionalKeyManager()
                        .addNewKey(key, InstanceFactory.getInstance(ManifestManager.class))) {
                    return new RsText(WRITER.writeValueAsString(new ExternalEncryptionKey(null)));
                }
            } else {
                key = masterKey.getAdditionalKeyManager()
                        .generateNewKey(InstanceFactory.getInstance(ManifestManager.class));
            }

            return new RsText(WRITER.writeValueAsString(new ExternalEncryptionKey(key)));
        }
    }
}