package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.io.IOException;
import java.net.HttpURLConnection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.takes.HttpException;
import org.takes.Request;
import org.takes.rq.RqPrint;

import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.base.Strings;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
public class AdditionalPrivateKeyRequest {
    private static final ObjectReader READER = MAPPER
            .readerFor(AdditionalPrivateKeyRequest.class);
    private String password;
    private String privateKey;

    public static AdditionalPrivateKeyRequest decodePrivateKeyRequest(Request req) throws IOException {
        String request = new RqPrint(req).printBody();
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
