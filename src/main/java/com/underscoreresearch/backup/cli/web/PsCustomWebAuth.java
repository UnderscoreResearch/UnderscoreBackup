package com.underscoreresearch.backup.cli.web;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.EqualsAndHashCode;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.jetbrains.annotations.NotNull;
import org.takes.Request;
import org.takes.Response;
import org.takes.facets.auth.Identity;
import org.takes.facets.auth.Pass;
import org.takes.facets.forward.RsForward;
import org.takes.misc.Href;
import org.takes.misc.Opt;
import org.takes.rq.RqHeaders;
import org.takes.rq.RqHref;
import org.takes.rq.RqMethod;
import org.takes.rs.RsEmpty;
import org.takes.rs.RsWithHeader;

import com.google.common.base.Strings;
import com.underscoreresearch.backup.encryption.Hash;
import com.underscoreresearch.backup.encryption.x25519.X25519;

@EqualsAndHashCode
public final class PsCustomWebAuth implements Pass {
    public static final String X_KEYEXCHANGE_HEADER = "x-keyexchange";
    private static final Pattern PARSE_PARAMETER = Pattern.compile("\\s*([a-z]+)\\s*\\=\\s*(?:(?:\\\"([^\\\"]*)\\\")|([^\\s,]+))\\s*(?:,|$)");
    private static final Pattern PATH_PATTERN = Pattern.compile("^.*\\:\\/\\/[^\\/]+(.*)$");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final MessageDigest MD5;

    static {
        try {
            MD5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Entry to validate user information.
     */
    private final Entry entry;

    /**
     * Realm.
     */
    private final String realm;

    private final Map<String, byte[]> privateKeys = new HashMap<>();

    /**
     * Ctor.
     *
     * @param rlm   Realm
     * @param entry Entry
     */
    public PsCustomWebAuth(final String rlm, final PsCustomWebAuth.Entry entry) {
        this.realm = rlm;
        this.entry = entry;
    }

    @Override
    public Opt<Identity> enter(final Request request) throws IOException {
        final Iterator<String> keyExchange = new RqHeaders.Smart(request)
                .header(X_KEYEXCHANGE_HEADER).iterator();
        byte[] privateKeyExchangeKey = null;
        if (keyExchange.hasNext()) {
            String[] vals = keyExchange.next().split(" ");
            if (vals.length == 1) {
                privateKeyExchangeKey = registerPrivateKeyAuth(vals);
            } else {
                return validateKeyExchangeAuth(request, vals);
            }
        }

        final Iterator<String> headers = new RqHeaders.Smart(request)
                .header("authorization").iterator();
        if (!headers.hasNext()) {
            rejectNotAuthed(request, new RsEmpty());
        }

        String headerValue = headers.next();

        Map<String, String> parsedHeader = parseHeader(headerValue, request);

        Boolean authenticated = validateUser(parsedHeader, request);
        if (!authenticated) {
            rejectNotAuthed(request, new RsEmpty());
        }
        return createKeyExchangeAuth(privateKeyExchangeKey);
    }

    @NotNull
    private byte[] registerPrivateKeyAuth(String[] vals) {
        byte[] privateKeyExchangeKey;
        byte[] privateKey = X25519.generatePrivateKey();
        synchronized (privateKeys) {
            privateKeys.put(vals[0], privateKey);
        }
        privateKeyExchangeKey = privateKey;
        return privateKeyExchangeKey;
    }

    @NotNull
    private Opt<Identity> validateKeyExchangeAuth(Request request, String[] vals) throws IOException {
        if (vals.length == 3) {
            byte[] privateKey = privateKeys.get(vals[0]);
            if (privateKey != null) {
                long now = Long.parseLong(vals[1]);
                if (Math.abs(now - System.currentTimeMillis()) < 1000 || true) {
                    try {
                        URI uri = URI.create(new RqHref.Base(request).href().toString());

                        String auth = new RqMethod.Base(request).method() + ":"
                                + uri.getRawPath() + (Strings.isNullOrEmpty(uri.getRawQuery()) ? "" : "?" + uri.getRawQuery()) + ":"
                                + Hash.encodeBytes64(X25519.computeSharedSecret(privateKey, Base64.decodeBase64(vals[0]))) + ":"
                                + vals[1];
                        String key = Hash.hash64(auth.getBytes(StandardCharsets.UTF_8));
                        if (key.equals(vals[2])) {
                            return createKeyExchangeAuth(privateKey);
                        }
                    } catch (InvalidKeyException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        throw new RsForward(
                new RsEmpty(),
                HttpURLConnection.HTTP_UNAUTHORIZED,
                new RqHref.Base(request).href()
        );
    }

    private Opt<Identity> createKeyExchangeAuth(byte[] privateKey) {
        if (privateKey == null) {
            return new Opt.Single<>(new Identity.Simple(""));
        }
        try {
            return new Opt.Single<>(new Identity.Simple(Base64.encodeBase64String(X25519.publicFromPrivate(privateKey)).replace("=", "")));
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean validateUser(Map<String, String> params, Request request) throws IOException {
        String username = params.get("username");
        String realm = params.get("realm");
        String nonce = params.get("nonce");
        String uri = params.get("uri");
        String qop = params.get("qop");
        String cnonce = params.get("cnonce");
        String opaque = params.get("opaque");
        String response = params.get("response");
        String method = params.get("method");
        String nc = params.get("nc");

        if (username == null || qop == null || realm == null || nonce == null || uri == null || cnonce == null || opaque == null || response == null || method == null || nc == null) {
            return false;
        }

        Href href = new RqHref.Base(request).href();
        Matcher matcher = PATH_PATTERN.matcher(href.toString());

        if (!matcher.matches() || !matcher.group(1).equals(uri)) {
            return false;
        }

        final Opt<String> password = this.entry.passwordForUser(params.get("username"));
        if (!password.has()) {
            return false;
        }

        String ha1str = String.format("%s:%s:%s", username, realm, password.get());
        String ha2str = String.format("%s:%s", method, uri);
        String ha1 = Hex.encodeHexString(MD5.digest(ha1str.getBytes(StandardCharsets.UTF_8)));
        String ha2 = Hex.encodeHexString(MD5.digest(ha2str.getBytes(StandardCharsets.UTF_8)));

        String hashStr;
        if ("auth".equals(qop))
            hashStr = String.format("%s:%s:%s:%s:%s:%s", ha1, nonce, nc, cnonce, qop, ha2);
        else
            hashStr = String.format("%s:%s:%s", ha1, nonce, ha2);
        String hash = Hex.encodeHexString(MD5.digest(hashStr.getBytes(StandardCharsets.UTF_8)));

        if (hash.equals(response))
            return true;
        return false;
    }

    private void rejectNotAuthed(Request request, Response response) throws IOException {
        byte[] nonce = new byte[16];
        SECURE_RANDOM.nextBytes(nonce);
        String nonceStr = Hex.encodeHexString(nonce);
        String opaqueStr = Hex.encodeHexString(MD5.digest(nonce));

        throw new RsForward(
                new RsWithHeader(
                        response,
                        String.format(
                                "WWW-Authenticate: Digest realm=\"%s\", qop=\"auth\", nonce=\"%s\", opaque=\"%s\"",
                                this.realm,
                                nonceStr,
                                opaqueStr
                        )
                ),
                HttpURLConnection.HTTP_UNAUTHORIZED,
                new RqHref.Base(request).href()
        );
    }

    private Map<String, String> parseHeader(String headerValue, Request request) throws IOException {
        Map<String, String> ret = new HashMap<>();
        if (headerValue.startsWith("Digest ")) {
            Matcher pieces = PARSE_PARAMETER.matcher(headerValue.substring(7));
            while (pieces.find()) {
                ret.put(pieces.group(1), pieces.group(3) != null ? pieces.group(3) : pieces.group(2));
            }
            ret.put("method", new RqMethod.Base(request).method());
        }
        return ret;
    }

    @Override
    public Response exit(final Response response, final Identity identity) {
        String publicKey = identity.urn();

        if (Strings.isNullOrEmpty(publicKey) || publicKey.startsWith("urn:")) {
            return response;
        }
        return new RsWithHeader(response,
                String.format(
                        "%s: %s", X_KEYEXCHANGE_HEADER, identity.urn()
                ));
    }

    /**
     * Entry interface that is used to check if the received information is
     * valid.
     *
     * @since 0.20
     */
    public interface Entry {
        /**
         * Check if is a valid user.
         *
         * @return Identity.
         */
        Opt<String> passwordForUser(String user);
    }
}
