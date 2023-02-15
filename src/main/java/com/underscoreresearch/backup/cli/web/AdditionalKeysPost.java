package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.configuration.EncryptionModule.ROOT_KEY;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

import org.takes.Request;
import org.takes.Response;
import org.takes.rs.RsText;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionKey;

public class AdditionalKeysPost extends JsonWrap {
    private static ObjectWriter WRITER = MAPPER.writerFor(AdditionalKeysResponse.class);

    public AdditionalKeysPost() {
        super(new Implementation());
    }

    @Data
    @AllArgsConstructor
    private static class AdditionalKeysResponse {
        private List<AdditionalKeyPut.ExternalEncryptionKey> keys;
    }

    private static class Implementation extends BaseImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {
            String password = PrivateKeyRequest.decodePrivateKeyRequest(req);

            EncryptionKey.PrivateKey masterKey;
            try {
                EncryptionKey publicKey = InstanceFactory.getInstance(ROOT_KEY, EncryptionKey.class);
                masterKey = publicKey.getPrivateKey(password);
            } catch (Exception exc) {
                return messageJson(403, "Invalid password provided");
            }

            List<AdditionalKeyPut.ExternalEncryptionKey> keys = new ArrayList<>();
            for (EncryptionKey key : masterKey.getAdditionalKeyManager().getKeys()) {
                keys.add(new AdditionalKeyPut.ExternalEncryptionKey(key));
            }

            return new RsText(WRITER.writeValueAsString(new AdditionalKeysResponse(keys)));
        }
    }
}
