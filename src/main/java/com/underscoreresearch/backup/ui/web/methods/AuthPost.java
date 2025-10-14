package com.underscoreresearch.backup.ui.web.methods;

import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Strings;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.Hash;
import com.underscoreresearch.backup.encryption.encryptors.x25519.X25519;
import com.underscoreresearch.backup.ui.web.ApiAuth;
import com.underscoreresearch.backup.ui.web.BaseImplementation;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.takes.Request;
import org.takes.Response;
import org.takes.rq.RqPrint;
import org.takes.rs.RsText;
import org.takes.rs.RsWithStatus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;

import static com.underscoreresearch.backup.ui.web.PsAuthedContent.ENCRYPTED_CONTENT_TYPE;
import static com.underscoreresearch.backup.ui.web.PsAuthedContent.X_KEYEXCHANGE_HEADER;
import static com.underscoreresearch.backup.ui.web.PsAuthedContent.X_PAYLOAD_HASH_HEADER;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

/**
 * Web endpoint for handling authentication requests.
 * This class manages the authentication process for API access, including key exchange
 * and authenticated request handling.
 */
@Slf4j
public class AuthPost extends BaseWrap {
    private static final ObjectReader REQUEST_READER = MAPPER.readerFor(AuthRequest.class);
    private static final ObjectWriter RESPONSE_WRITER = MAPPER.writerFor(AuthResponse.class);

    /**
     * Creates a new AuthPost instance.
     */
    public AuthPost() {
        super(new Implementation());
    }

    /**
     * Gets the authentication path from a URI.
     *
     * @param uri The URI to extract the path from
     * @return The authentication path
     */
    public static String getAuthPath(URI uri) {
        return uri.getRawPath() + (Strings.isNullOrEmpty(uri.getRawQuery()) ? "" : "?" + uri.getRawQuery());
    }

    /**
     * Performs an authenticated request to the API.
     *
     * @param configurationUrl The base URL for the configuration API
     * @param method The HTTP method to use
     * @param path The path to request
     * @param body The request body, or null if none
     * @return The response body
     * @throws IOException If there's an error during the request
     */
    public static String performAuthenticatedRequest(String configurationUrl, String method, String path, String body) throws IOException {
        byte[] privateKey = X25519.generatePrivateKey();
        URL url;
        try {
            url = new URI(configurationUrl + "api/auth").toURL();
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(3000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setDoInput(true);
        String publicKey;
        try (OutputStream stream = connection.getOutputStream()) {
            AuthRequest authRequest = new AuthRequest();
            try {
                publicKey = Hash.encodeBytes64(X25519.publicFromPrivate(privateKey));
                authRequest.setPublicKey(publicKey);
            } catch (InvalidKeyException e) {
                throw new RuntimeException(e);
            }
            stream.write(MAPPER.writeValueAsBytes(authRequest));
        }
        connection.connect();
        if (connection.getResponseCode() != 200) {
            throw new IOException("Failed to fetch auth");
        }
        try (InputStream stream = connection.getInputStream()) {
            AuthResponse response = MAPPER.readValue(stream, AuthResponse.class);

            byte[] sharedKey;
            try {
                sharedKey = X25519.computeSharedSecret(privateKey, Hash.decodeBytes64(response.getPublicKey()));
            } catch (InvalidKeyException e) {
                throw new IOException(e);
            }

            URI uri = URI.create(configurationUrl + path);
            String authPath = getAuthPath(uri);
            String nonce = "1";
            String hash = ApiAuth.EndpointInfo.computeHash(method, authPath, nonce, Hash.encodeBytes(sharedKey));
            String authHeader = publicKey + " " + nonce + " " + hash;
            try {
                String publicHash = ApiAuth.EndpointInfo.computeHash(method, authPath, nonce, InstanceFactory.getInstance(EncryptionIdentity.class).getPrivateHash());
                authHeader += " " + publicHash;
            } catch (Exception e) {
                log.info("No public key found so assuming no UI authentication is needed");
            }

            URL authedUrl = uri.toURL();
            HttpURLConnection authConnection = (HttpURLConnection) authedUrl.openConnection();
            authConnection.setConnectTimeout(3000);
            authConnection.setRequestMethod(method);
            authConnection.setRequestProperty(X_KEYEXCHANGE_HEADER, authHeader);
            authConnection.setDoInput(true);
            if (body != null) {
                authConnection.setDoOutput(true);
                authConnection.setRequestProperty("Content-Type", ENCRYPTED_CONTENT_TYPE);
                ApiAuth.EncryptedData data = ApiAuth.getInstance().encryptData(sharedKey, body);
                authConnection.setRequestProperty(X_PAYLOAD_HASH_HEADER, data.getHash());
                try (OutputStream outputStream = authConnection.getOutputStream()) {
                    outputStream.write(data.getData());
                }
            }

            if (authConnection.getResponseCode() != 200) {
                throw new IOException("Failed to call \"" + path + "\" (" + authConnection.getResponseCode() + ")");
            }

            try (InputStream inputStream = authConnection.getInputStream()) {
                byte[] encryptedData = new byte[inputStream.available()];
                if (inputStream.read(encryptedData) != encryptedData.length)
                    throw new IOException("Failed to read \"" + path + "\"");
                if (authConnection.getHeaderField("Content-Type").equals(ENCRYPTED_CONTENT_TYPE))
                    return ApiAuth.getInstance().decryptData(sharedKey, encryptedData,
                            authConnection.getHeaderField(X_PAYLOAD_HASH_HEADER));
                return new String(encryptedData, StandardCharsets.UTF_8);
            }
        }
    }

    /**
     * Data class representing an authentication request.
     */
    @Data
    private static class AuthRequest {
        private String publicKey;
    }

    /**
     * Data class representing an authentication response.
     */
    @Data
    private static class AuthResponse {
        private String publicKey;
        private String keySalt;
        private String keyData;
        private boolean needKeyUpgrade;
    }

    /**
     * Implementation class that handles authentication requests.
     */
    private static class Implementation extends BaseImplementation {
        /**
         * Processes an authentication request.
         * Registers the endpoint and returns authentication information.
         *
         * @param req The HTTP request
         * @return The HTTP response with authentication information
         * @throws Exception If an error occurs during processing
         */
        @Override
        public Response actualAct(Request req) throws Exception {
            String body = new RqPrint(req).printBody();
            try {
                AuthRequest authRequest = REQUEST_READER.readValue(body);

                String publicKey = ApiAuth.getInstance().registerEndpoint(authRequest.publicKey);
                AuthResponse authResponse = new AuthResponse();
                authResponse.setPublicKey(publicKey);

                if (ApiAuth.getInstance().needAuthentication()) {
                    EncryptionIdentity key = InstanceFactory.getInstance(EncryptionIdentity.class);
                    authResponse.setKeySalt(key.getSalt());
                    authResponse.setKeyData(key.getKeyData());
                    authResponse.needKeyUpgrade = key.needKeyUnpack();
                }

                return jsonResponse(new RsWithStatus(new RsText(RESPONSE_WRITER.writeValueAsString(authResponse)), 200));
            } catch (Exception e) {
                return messageJson(400, "Invalid request");
            }
        }
    }
}
