package com.underscoreresearch.backup.ui.web;

import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.base.Strings;
import com.underscoreresearch.backup.ui.commands.InteractiveCommand;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.manifest.ManifestManager;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.takes.HttpException;
import org.takes.Request;

import java.io.IOException;
import java.net.HttpURLConnection;

import static com.underscoreresearch.backup.ui.web.PsAuthedContent.decodeRequestBody;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

/**
 * Request class for private key operations.
 * This class represents a request containing a password for accessing private keys,
 * and provides utility methods for decoding and validating such requests.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
public class PrivateKeyRequest {
    private static ObjectReader READER = MAPPER
            .readerFor(PrivateKeyRequest.class);
    private String password;

    /**
     * Decodes a private key request from an HTTP request.
     * Validates that the password is provided in the request.
     *
     * @param req The HTTP request
     * @return The password from the request
     * @throws IOException If there's an error reading the request
     * @throws HttpException If the request is invalid
     */
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

    /**
     * Validates a password against the encryption identity.
     * If the key needs unpacking, it will also upgrade the key file to the new format.
     *
     * @param password The password to validate
     * @return True if the password is valid, false otherwise
     */
    public static boolean validatePassword(String password) {
        EncryptionIdentity encryptionKey = InstanceFactory.getInstance(EncryptionIdentity.class);

        try {
            boolean needKeyUnpack = encryptionKey.needKeyUnpack();
            EncryptionIdentity.PrivateIdentity identity = encryptionKey.getPrivateIdentity(password);
            if (Strings.isNullOrEmpty(InstanceFactory.getAdditionalSource()) && needKeyUnpack) {
                log.info("Upgraded key file to new format");
                encryptionKey.unpackKeys(identity);
                InstanceFactory.getInstance(ManifestManager.class).updateKeyData(encryptionKey);

                if (Strings.isNullOrEmpty(InstanceFactory.getAdditionalSource()))
                    InstanceFactory.reloadConfiguration(InteractiveCommand::startBackupIfAvailable);
                else
                    InstanceFactory.reloadConfigurationWithSource();
            }
            return true;
        } catch (Exception exc) {
            log.warn("Failed to validate key", exc);
            return false;
        }
    }
}
