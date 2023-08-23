package com.underscoreresearch.backup.manifest.implementation;

import static com.underscoreresearch.backup.configuration.CommandLineModule.MANIFEST_LOCATION;
import static com.underscoreresearch.backup.encryption.AesEncryptor.AES_ENCRYPTION;
import static com.underscoreresearch.backup.io.IOUtils.createDirectory;
import static com.underscoreresearch.backup.io.IOUtils.deleteFile;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_DESTINATION_WRITER;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.takes.rs.RsText;
import org.takes.rs.RsWithStatus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.inject.name.Named;
import com.underscoreresearch.backup.cli.commands.VersionCommand;
import com.underscoreresearch.backup.cli.web.ConfigurationPost;
import com.underscoreresearch.backup.encryption.EncryptionKey;
import com.underscoreresearch.backup.encryption.EncryptorFactory;
import com.underscoreresearch.backup.encryption.Hash;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.model.BackupShare;
import com.underscoreresearch.backup.service.api.BackupApi;
import com.underscoreresearch.backup.service.api.invoker.ApiClient;
import com.underscoreresearch.backup.service.api.invoker.ApiException;
import com.underscoreresearch.backup.service.api.model.DeleteTokenRequest;
import com.underscoreresearch.backup.service.api.model.GenerateTokenRequest;
import com.underscoreresearch.backup.service.api.model.GetSubscriptionResponse;
import com.underscoreresearch.backup.service.api.model.ListSharingKeysRequest;
import com.underscoreresearch.backup.service.api.model.ListSharingKeysResponse;
import com.underscoreresearch.backup.service.api.model.ReleaseResponse;
import com.underscoreresearch.backup.service.api.model.ScopedTokenResponse;
import com.underscoreresearch.backup.service.api.model.SharePrivateKeys;
import com.underscoreresearch.backup.service.api.model.ShareRequest;
import com.underscoreresearch.backup.service.api.model.ShareResponse;
import com.underscoreresearch.backup.utils.RetryUtils;

@Slf4j
public class ServiceManagerImpl implements ServiceManager {
    public static final String CLIENT_ID = "DEFAULT_CLIENT";
    private static final Pattern PRE_RELEASE = Pattern.compile("[a-z]", Pattern.CASE_INSENSITIVE);
    private static final Pattern VERSION_PARSER = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+)\\S*$");
    private static final ObjectReader READER = MAPPER.readerFor(ServiceManagerData.class);
    private static final ObjectWriter WRITER = MAPPER.writerFor(ServiceManagerData.class);

    private final String manifestLocation;
    private final Map<String, BackupApi> clients = new HashMap<>();
    private ServiceManagerData data;

    public ServiceManagerImpl(@Named(MANIFEST_LOCATION) String manifestLocation) throws IOException {
        this(manifestLocation, createData(manifestLocation));
    }

    public ServiceManagerImpl(String manifestLocation, ServiceManagerData data) {
        this.manifestLocation = manifestLocation;
        this.data = data;
    }

    private static ServiceManagerData createData(String manifestLocation) throws IOException {
        File file = getDataFile(manifestLocation);
        if (file.exists()) {
            return READER.readValue(file);
        } else {
            return new ServiceManagerData();
        }
    }

    public static RsWithStatus sendApiFailureOn(IOException exc) throws IOException {
        if (exc.getCause() instanceof ApiException apiExc) {
            return new RsWithStatus(new RsText(apiExc.getResponseBody()), apiExc.getCode());
        }
        throw exc;
    }

    private static String getShareDestinationString(String sourceId, BackupShare share, EncryptionKey publicKey) throws JsonProcessingException {
        return Hash.encodeBytes64(EncryptorFactory.encryptBlock(AES_ENCRYPTION, null,
                BACKUP_DESTINATION_WRITER.writeValueAsBytes(share.getDestination().strippedDestination(sourceId, publicKey.getPublicKey())), publicKey));
    }

    static <T> T callApi(BackupApi client, String region, ApiFunction<T> callable) throws ApiException {
        try {
            return RetryUtils.retry(RetryUtils.DEFAULT_RETRIES, RetryUtils.DEFAULT_BASE, () -> callable.call(client), (exc) -> {
                if (exc instanceof ApiException) {
                    int code = ((ApiException) exc).getCode();
                    if (code >= 400 && code < 500) {
                        return code == 404 && callable.shouldRetryMissing(region);
                    }
                }
                return callable.shouldRetry();
            }, callable.waitForInternet());
        } catch (ApiException exc) {
            throw exc;
        } catch (Exception exc) {
            throw new RuntimeException(exc.getMessage(), exc);
        }
    }

    private static File getDataFile(String manifestLocation) {
        return Paths.get(manifestLocation, "service.json").toFile();
    }

    private static long[] parseVersion(String version) {
        if (version != null) {
            Matcher matcher = VERSION_PARSER.matcher(version);
            if (matcher.find()) {
                long[] ret = new long[matcher.groupCount()];
                for (int i = 0; i < ret.length; i++) {
                    ret[i] = Long.parseLong(matcher.group(i + 1));
                }
                return ret;
            }
        }
        return null;
    }

    public <T> T call(String region, ApiFunction<T> callable) throws IOException {
        try {
            return callApi(region, callable);
        } catch (ApiException exc) {
            throw new IOException(exc);
        } catch (Exception exc) {
            if (exc.getCause() instanceof IOException) {
                throw (IOException) exc.getCause();
            }
            throw new IOException(exc.getMessage(), exc.getCause());
        }
    }

    public <T> T callApi(String region, ApiFunction<T> callable) throws ApiException {
        return callApi(getClient(region), region, callable);
    }

    public File getDataFile() {
        return getDataFile(manifestLocation);
    }

    private void saveFile() {
        try {
            File file = getDataFile();
            boolean exists = file.exists();
            createDirectory(file.getParentFile());
            WRITER.writeValue(getDataFile(), data);
            if (!exists)
                ConfigurationPost.setOwnerOnlyPermissions(file);
        } catch (IOException exc) {
            throw new RuntimeException(exc);
        }
    }

    @Override
    public boolean activeSubscription() throws IOException {
        if (data.getToken() != null) {
            try {
                return call(null, new ApiFunction<GetSubscriptionResponse>() {
                    @Override
                    public GetSubscriptionResponse call(BackupApi api) throws ApiException {
                        return api.getSubscription();
                    }

                    @Override
                    public boolean waitForInternet() {
                        return false;
                    }
                }).getActive();
            } catch (IOException exc) {
                if (exc.getCause() instanceof ApiException) {
                    switch (((ApiException) exc.getCause()).getCode()) {
                        case 401, 403 -> {
                            log.error("Invalid token, lost service authorization");
                            data.setToken(null);
                            saveFile();
                            return false;
                        }
                    }
                }
                throw exc;
            }
        }
        return false;
    }

    @Override
    public ReleaseResponse checkVersion() {
        try {
            ReleaseResponse release;
            if (PRE_RELEASE.matcher(VersionCommand.getVersion()).find())
                release = callApi(null, new ApiFunction<>() {
                    @Override
                    public boolean shouldRetry() {
                        return false;
                    }

                    @Override
                    public ReleaseResponse call(BackupApi api) throws ApiException {
                        return api.preRelease();
                    }
                });
            else
                release = callApi(null, new ApiFunction<>() {
                    @Override
                    public boolean shouldRetry() {
                        return false;
                    }

                    @Override
                    public ReleaseResponse call(BackupApi api) throws ApiException {
                        return api.currentRelease();
                    }
                });
            String lastVersion;
            if (data.getLastRelease() != null) {
                lastVersion = data.getLastRelease().getVersion();
            } else {
                lastVersion = VersionCommand.getVersion();
            }
            if (!release.getVersion().equals(lastVersion)) {
                data.setLastRelease(release);
                saveFile();
                if (!newerVersion(VersionCommand.getVersion(), release.getVersion())) {
                    return release;
                }
            }
            return null;
        } catch (ApiException e) {
            log.warn("Failed to check new version", e);
        }
        return null;
    }

    private boolean newerVersion(String version, String lastVersion) {
        long[] currentVersion = parseVersion(version);
        long[] newVersion = parseVersion(lastVersion);
        if (currentVersion == null) {
            return true;
        }
        if (newVersion == null) {
            return false;
        }
        if (currentVersion.length == newVersion.length) {
            for (int i = 0; i < currentVersion.length; i++) {
                if (currentVersion[i] > newVersion[i]) {
                    return false;
                } else if (currentVersion[i] < newVersion[i]) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    public ReleaseResponse newVersion() {
        if (data.getLastRelease() != null && newerVersion(VersionCommand.getVersion(), data.getLastRelease().getVersion())) {
            return data.getLastRelease();
        }
        return null;
    }

    @Override
    public String getToken() {
        return data.getToken();
    }

    @Override
    public String getSourceId() {
        return data.getSourceId();
    }

    @Override
    public void setSourceId(String sourceId) {
        if (!Objects.equals(data.getSourceId(), sourceId)) {
            data.setSourceId(sourceId);
            saveFile();
        }
    }

    @Override
    public String getSourceName() {
        String ret = data.getSourceName();
        if (ret == null) {
            try {
                String name = InetAddress.getLocalHost().getHostName();
                int idx = name.indexOf('.');
                if (idx > 0)
                    name = name.substring(0, idx);
                return name;
            } catch (UnknownHostException ignored) {
            }
        }
        return ret;
    }

    @Override
    public void setSourceName(String name) {
        if (!Objects.equals(data.getSourceName(), name)) {
            data.setSourceName(name);
            saveFile();
        }
    }

    @Override
    public void generateToken(String code, String codeVerifier) throws IOException {
        ScopedTokenResponse response = call(null, (api) -> api.generateToken(new GenerateTokenRequest()
                .clientId(CLIENT_ID)
                .codeVerifier(codeVerifier)
                .code(code)));
        data.setToken(response.getToken());
        saveFile();
    }

    @Override
    public void deleteToken() throws IOException {
        if (data.getToken() != null) {
            call(null, (api) -> api.deleteToken(new DeleteTokenRequest()
                    .token(data.getToken())));
            data.setToken(null);
            saveFile();
        }
    }

    private synchronized BackupApi getClient(String region) {
        if (region == null) {
            region = "us-west";
        }
        String finalRegion = region;
        return clients.computeIfAbsent(region, (key) -> {
            ApiClient client = new ApiClient();
            client.setRequestInterceptor((builder) -> {
                if (data.getToken() != null) {
                    builder.setHeader("Authorization", "Bearer " + data.getToken());
                }
            });
            if ("true".equals(System.getenv("BACKUP_DEV"))) {
                client.setHost(finalRegion + "-api.dev.underscorebackup.com");
            } else {
                client.setHost(finalRegion + "-api.underscorebackup.com");
            }
            client.setConnectTimeout(Duration.ofSeconds(5));
            client.setReadTimeout(Duration.ofSeconds(20));
            return new BackupApi(client);
        });
    }

    @Override
    public void createShare(String shareId, BackupShare share) throws IOException {
        final String targetAccountHash = Hash.hash64(share.getTargetEmail().getBytes(StandardCharsets.UTF_8));

        EncryptionKey publicKey = EncryptionKey.createWithPublicKey(shareId);

        final ShareRequest request = new ShareRequest()
                .targetAccountEmailHash(targetAccountHash);

        String destination = getShareDestinationString(getSourceId(), share, publicKey);
        request.setTargetAccountEmailHash(targetAccountHash);
        request.setName(share.getName());
        request.setDestination(destination);

        try {
            callApi(null, (api) -> api.createShare(getSourceId(), shareId, request));
        } catch (ApiException exc) {
            if (exc.getCode() == 409) {
                throw new IOException("Share already exists");
            }
            throw new IOException(exc);
        }
    }

    @Override
    public boolean updateShareEncryption(EncryptionKey.PrivateKey privateKey, String shareId, BackupShare share) throws IOException {
        try {
            final String targetAccountHash = Hash.hash64(share.getTargetEmail().getBytes(StandardCharsets.UTF_8));
            final ShareResponse response = callApi(null, (api) -> api.getShare(getSourceId(), shareId));
            final ListSharingKeysResponse keys = callApi(null, (api) -> api.listSharingKeys(new ListSharingKeysRequest().
                    targetAccountEmailHash(targetAccountHash)));

            if (keys.getPublicKeys().stream().anyMatch(publicKey ->
                    response.getPrivateKeys().stream().noneMatch(entry -> entry.getPublicKey().equals(publicKey)))) {

                if (privateKey != null) {
                    EncryptionKey publicKey = EncryptionKey.createWithPublicKey(shareId);
                    EncryptionKey shareKey = privateKey.getAdditionalKeyManager().findMatchingPrivateKey(publicKey);
                    if (shareKey != null) {
                        byte[] sharePrivateKey = Hash.decodeBytes(shareKey.getPrivateKey(null).getPrivateKey());

                        String destination = getShareDestinationString(getSourceId(), share, shareKey);
                        ShareRequest request = new ShareRequest()
                                .destination(destination);
                        request.setTargetAccountEmailHash(targetAccountHash);
                        request.setName(share.getName());

                        for (String key : keys.getPublicKeys()) {
                            request.addPrivateKeysItem(new SharePrivateKeys().publicKey(key).encryptedKey(
                                    Hash.encodeBytes64(EncryptorFactory.encryptBlock(AES_ENCRYPTION, null,
                                            sharePrivateKey, EncryptionKey.createWithPublicKey(key)))));
                        }

                        callApi(null, (api) -> api.updateShare(getSourceId(), shareId, request));
                    } else {
                        log.error("Could not find matching private key for sharing key");
                    }
                } else {
                    log.warn("Share primary keys need to be updated for share {}", share.getName());
                }
                return false;
            }
            return true;
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                return false;
            }
            throw new IOException(e);
        }
    }

    @Override
    public void deleteShare(String shareId) throws IOException {
        try {
            callApi(null, (api) -> api.deleteShare(getSourceId(), shareId));
        } catch (ApiException e) {
            throw new IOException(e);
        }
    }

    @Override
    public List<ShareResponse> getShares() throws IOException {
        try {
            return callApi(null, (api) -> api.listShares().getShares());
        } catch (ApiException e) {
            throw new IOException(e);
        }
    }

    @Override
    public List<ShareResponse> getSourceShares() throws IOException {
        try {
            return callApi(null, (api) -> api.listSourceShares(getSourceId()).getShares());
        } catch (ApiException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void reset() {
        deleteFile(getDataFile());
        data = new ServiceManagerData();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ServiceManagerData {
        private String token;
        private ReleaseResponse lastRelease;
        private String sourceId;
        private String sourceName;
    }

}
