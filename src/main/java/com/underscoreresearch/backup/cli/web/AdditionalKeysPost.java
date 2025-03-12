package com.underscoreresearch.backup.cli.web;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.IdentityKeys;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.takes.Request;
import org.takes.Response;

import java.util.ArrayList;
import java.util.List;

import static com.underscoreresearch.backup.cli.web.PsAuthedContent.encryptResponse;
import static com.underscoreresearch.backup.cli.web.service.CreateSecretPut.encryptionIdentity;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

public class AdditionalKeysPost extends BaseWrap {
    private static final ObjectWriter WRITER = MAPPER.writerFor(AdditionalKeysResponse.class);

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
