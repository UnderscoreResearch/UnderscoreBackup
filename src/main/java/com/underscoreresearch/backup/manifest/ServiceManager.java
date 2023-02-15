package com.underscoreresearch.backup.manifest;

import java.io.IOException;
import java.util.List;

import com.underscoreresearch.backup.encryption.EncryptionKey;
import com.underscoreresearch.backup.model.BackupShare;
import com.underscoreresearch.backup.service.api.BackupApi;
import com.underscoreresearch.backup.service.api.model.ReleaseResponse;
import com.underscoreresearch.backup.service.api.model.ShareResponse;

public interface ServiceManager {
    boolean activeSubscription() throws IOException;

    ReleaseResponse checkVersion();

    ReleaseResponse newVersion();

    String getToken();

    String getSourceId();

    void setSourceId(String sourceId);

    String getSourceName();

    void setSourceName(String name);

    void generateToken(String code, String codeVerifier) throws IOException;

    void deleteToken() throws IOException;

    BackupApi getClient(String region);

    BackupApi getClient();

    void reset();

    void createShare(String shareId, BackupShare share) throws IOException;

    boolean updateShareEncryption(EncryptionKey.PrivateKey privateKey, String shareId, BackupShare share) throws IOException;

    void deleteShare(String shareId) throws IOException;

    List<ShareResponse> getShares() throws IOException;

    List<ShareResponse> getSourceShares() throws IOException;
}
