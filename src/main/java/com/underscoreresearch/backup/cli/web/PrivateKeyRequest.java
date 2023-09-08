package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.cli.web.PsAuthedContent.decodeRequestBody;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.io.IOException;
import java.net.HttpURLConnection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.takes.HttpException;
import org.takes.Request;

import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.base.Strings;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionKey;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
public class PrivateKeyRequest {
    private static ObjectReader READER = MAPPER
            .readerFor(PrivateKeyRequest.class);
    private String password;

    public static String decodePrivateKeyRequest(Request req) throws IOException {
        String request = decodeRequestBody(req);
        PrivateKeyRequest ret = READER.readValue(request);
        if (Strings.isNullOrEmpty(ret.getPassword())) {
            throw new HttpException(
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    "Missing required parameter password"
            );
        }
        return ret.getPassword();
    }

    public static boolean validatePassword(String password) {
        EncryptionKey encryptionKey = InstanceFactory.getInstance(EncryptionKey.class);

        try {
            encryptionKey.getPrivateKey(password);
            return true;
        } catch (Exception exc) {
            log.warn("Failed to validate key", exc);
            return false;
        }
    }
}
