package com.underscoreresearch.backup.manifest;

import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.model.BackupShare;
import com.underscoreresearch.backup.service.api.BackupApi;
import com.underscoreresearch.backup.service.api.invoker.ApiException;
import com.underscoreresearch.backup.service.api.model.ReleaseResponse;
import com.underscoreresearch.backup.service.api.model.ShareResponse;
import com.underscoreresearch.backup.utils.RetryUtils;

import java.io.IOException;
import java.util.List;

public interface ServiceManager {
    boolean activeSubscription() throws IOException;

    ReleaseResponse checkVersion(boolean forceCheck);

    ReleaseResponse newVersion();

    String getToken();

    String getSourceId();

    void setSourceId(String sourceId);

    String getSourceName();

    void setSourceName(String name);

    void generateToken(String code, String codeVerifier) throws IOException;

    void deleteToken() throws IOException;

    void reset();

    void createShare(EncryptionIdentity encryptionIdentity, String shareId, BackupShare share) throws IOException;

    boolean updateShareEncryption(EncryptionIdentity.PrivateIdentity privateIdentity, String shareId, BackupShare share) throws IOException;

    void deleteShare(String shareId) throws IOException;

    List<ShareResponse> getShares() throws IOException;

    List<ShareResponse> getSourceShares() throws IOException;

    <T> T call(String region, ApiFunction<T> callable) throws IOException;

    <T> T callApi(String region, ApiFunction<T> callable) throws ApiException;

    interface ApiFunction<T> {
        default boolean shouldRetryMissing(String region, ApiException apiException) {
            return false;
        }

        default boolean shouldRetry() {
            return true;
        }

        default RetryUtils.LogOrWait logAndWait(String region, ApiException apiException) {
            return RetryUtils.LogOrWait.LOG_AND_WAIT;
        }

        default boolean waitForInternet() {
            return true;
        }

        T call(BackupApi api) throws ApiException;
    }
}
