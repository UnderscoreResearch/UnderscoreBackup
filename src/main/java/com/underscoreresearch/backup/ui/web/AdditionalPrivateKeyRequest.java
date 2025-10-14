package com.underscoreresearch.backup.ui.web;

import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.base.Strings;
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
 * Request class for additional private key operations.
 * This class represents a request to add or manage an additional private key,
 * containing the password for authentication and optionally a private key to import.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
public class AdditionalPrivateKeyRequest {
    private static final ObjectReader READER = MAPPER
            .readerFor(AdditionalPrivateKeyRequest.class);
    private String password;
    private String privateKey;

    /**
     * Decodes a private key request from an HTTP request.
     * Validates that the password is provided in the request.
     *
     * @param req The HTTP request
     * @return The decoded AdditionalPrivateKeyRequest
     * @throws IOException If there's an error reading the request
     * @throws HttpException If the request is invalid
     */
    public static AdditionalPrivateKeyRequest decodePrivateKeyRequest(Request req) throws IOException {
        String request = decodeRequestBody(req);
        AdditionalPrivateKeyRequest ret = READER.readValue(request);
        if (Strings.isNullOrEmpty(ret.getPassword())) {
            throw new HttpException(
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    "Missing required parameter password"
            );
        }
        return ret;
    }
}
