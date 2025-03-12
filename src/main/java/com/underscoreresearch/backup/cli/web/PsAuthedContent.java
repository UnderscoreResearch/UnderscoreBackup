package com.underscoreresearch.backup.cli.web;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import lombok.extern.slf4j.Slf4j;
import org.takes.Request;
import org.takes.Response;
import org.takes.facets.auth.Identity;
import org.takes.facets.auth.Pass;
import org.takes.facets.auth.RqAuth;
import org.takes.facets.forward.RsForward;
import org.takes.misc.Opt;
import org.takes.rq.RqHeaders;
import org.takes.rq.RqHref;
import org.takes.rq.RqMethod;
import org.takes.rq.RqPrint;
import org.takes.rq.RqRequestLine;
import org.takes.rs.RsEmpty;
import org.takes.rs.RsText;
import org.takes.rs.RsWithHeader;
import org.takes.rs.RsWithType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import static com.underscoreresearch.backup.configuration.EncryptionModule.ROOT_KEY;

@Slf4j
public class PsAuthedContent implements Pass {
    public static final String X_KEYEXCHANGE_HEADER = "x-keyexchange";
    public static final String X_PAYLOAD_HASH_HEADER = "x-payload-hash";

    public static final String ENCRYPTED_CONTENT_TYPE = "x-application/encrypted-json";
    private static final Pattern AUTH_PATH = Pattern.compile("^/[^/]+/api/encryption-key$");

    public static Response encryptResponse(Request request, String data) throws Exception {
        ApiAuth.EndpointInfo info = endpointInfoOrUnauthed(request);
        ApiAuth.EncryptedData encryptedData = ApiAuth.getInstance().encryptData(info, data);
        return new RsWithType(new RsWithHeader(new RsText(encryptedData.getData()),
                X_PAYLOAD_HASH_HEADER, encryptedData.getHash()), ENCRYPTED_CONTENT_TYPE);
    }

    public static String decodeRequestBody(Request request) throws IOException {
        RqHeaders.Base headers = new RqHeaders.Base(request);
        List<String> contentType = headers.header("Content-Type");
        if (contentType.contains(ENCRYPTED_CONTENT_TYPE)) {
            ApiAuth.EndpointInfo info = endpointInfoOrUnauthed(request);
            try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
                new RqPrint(request).printBody(stream);
                byte[] data = stream.toByteArray();
                if (data.length > 0) {
                    List<String> hashHeaders = headers.header(X_PAYLOAD_HASH_HEADER);
                    if (hashHeaders.size() != 1)
                        throw new IOException("Missing hash header for encrypted content");
                    return ApiAuth.getInstance().decryptData(info, data, hashHeaders.get(0));
                }
                return "";
            }
        } else {
            throw new IOException("Expected encrypted request payload");
        }
    }

    private static ApiAuth.EndpointInfo endpointInfoOrUnauthed(Request request) throws IOException {
        String publicKey = new RqAuth(request).identity().urn();
        ApiAuth.EndpointInfo info = ApiAuth.getInstance().getEndpoint(publicKey);
        if (info == null) {
            throw new RsForward(
                    new RsEmpty(),
                    HttpURLConnection.HTTP_UNAUTHORIZED,
                    new RqHref.Base(request).href()
            );
        }
        return info;
    }

    private static boolean allowedMethodsWithoutPasswordAuth(EncryptionIdentity encryptionKey, String path, String method) {
        if (encryptionKey.getPrivateHash() == null && AUTH_PATH.matcher(path).matches() && "POST".equals(method)) {
            // log.info("Bypassed public key hash check for key upgrade request");
            return true;
        }
        return false;
    }

    @Override
    public Opt<Identity> enter(Request request) throws Exception {
        final Iterator<String> keyExchange = new RqHeaders.Smart(request)
                .header(X_KEYEXCHANGE_HEADER).iterator();
        if (keyExchange.hasNext()) {
            String[] vals = keyExchange.next().split(" ");
            if (vals.length == 3 || vals.length == 4) {
                String publicKey = vals[0];
                String nonce = vals[1];
                String hash = vals[2];
                String publicKeyHash = vals.length == 4 ? vals[3] : null;
                if (validateKeyExchange(request, publicKey, nonce, hash, publicKeyHash)) {
                    return new Opt.Single<>(new Identity.Simple(publicKey));
                }
            }
        }
        return unauthenticated(request);
    }

    private synchronized boolean validateKeyExchange(Request request, String publicKey, String nonce, String hash, String publicKeyHash) throws IOException {
        ApiAuth.EndpointInfo endpointInfo = ApiAuth.getInstance().getEndpoint(publicKey);
        if (endpointInfo != null) {
            if (endpointInfo.validateNonce(nonce)) {
                String method = new RqMethod.Base(request).method();
                String path = new RqRequestLine.Base(request).uri();

                if (validateHash(hash, method, path, nonce, endpointInfo.getSharedKey())) {
                    boolean success;
                    if (publicKeyHash != null) {
                        EncryptionIdentity encryptionKey = InstanceFactory.getInstance(ROOT_KEY, EncryptionIdentity.class);
                        if (allowedMethodsWithoutPasswordAuth(encryptionKey, path, method)) {
                            success = true;
                        } else {
                            if (validateHash(publicKeyHash, method, path, nonce, encryptionKey.getPrivateHash())) {
                                ApiAuth.getInstance().setEndpointAuthenticated(endpointInfo);
                                success = true;
                            } else {
//                            log.info("Invalid public key hash");
                                success = false;
                            }
                        }
                    } else {
                        success = !ApiAuth.getInstance().needAuthentication();
                        if (!success) {
                            EncryptionIdentity encryptionKey = InstanceFactory.getInstance(ROOT_KEY, EncryptionIdentity.class);
                            success = allowedMethodsWithoutPasswordAuth(encryptionKey, path, method);
                        }
                    }
                    if (success) {
                        if (!endpointInfo.recordNonce(nonce))
                            return false;
                    }
                    return success;
//                } else {
//                    log.info("Invalid hash");
                }
//            } else {
//                log.info("Invalid nonce");
            }
//        } else {
//            log.info("Missing endpoint info");
        }
        return false;
    }

    private boolean validateHash(String hash, String method, String path, String nonce, String sharedKey) {
        String expectedHash = ApiAuth.EndpointInfo.computeHash(method, path, nonce, sharedKey);
//        System.out.printf("%s == %s?%n", hash, expectedHash);
        return expectedHash.equals(hash);
    }

    private Opt<Identity> unauthenticated(Request request) throws IOException {
        throw new RsForward(
                new RsEmpty(),
                HttpURLConnection.HTTP_UNAUTHORIZED,
                new RqHref.Base(request).href()
        );
    }

    @Override
    public Response exit(Response response, Identity identity) throws Exception {
        return response;
    }
}
