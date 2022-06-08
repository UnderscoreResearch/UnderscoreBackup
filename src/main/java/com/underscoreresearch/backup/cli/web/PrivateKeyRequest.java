package com.underscoreresearch.backup.cli.web;

import java.io.IOException;
import java.net.HttpURLConnection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.takes.HttpException;
import org.takes.Request;
import org.takes.rq.RqPrint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.base.Strings;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.PublicKeyEncrypion;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
public class PrivateKeyRequest {
    private String passphrase;

    private static ObjectReader READER = new ObjectMapper()
            .readerFor(PrivateKeyRequest.class);

    public static String decodePrivateKeyRequest(Request req) throws IOException {
        String request = new RqPrint(req).printBody();
        PrivateKeyRequest configuration = READER.readValue(request);
        if (Strings.isNullOrEmpty(configuration.getPassphrase())) {
            throw new HttpException(
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    "Missing required parameter passphrase"
            );
        }
        return configuration.getPassphrase();
    }

    public static boolean validatePassphrase(String passphrase) {
        PublicKeyEncrypion publicKeyEncrypion = InstanceFactory.getInstance(PublicKeyEncrypion.class);

        try {
            PublicKeyEncrypion ret = PublicKeyEncrypion.generateKeyWithPassphrase(passphrase, publicKeyEncrypion);
            if (publicKeyEncrypion.getPublicKey().equals(ret.getPublicKey())) {
                return true;
            } else {
                return false;
            }
        } catch (Exception exc) {
            log.warn("Failed to validate key", exc);
            return false;
        }
    }
}
