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

/**
 * Interface for managing interactions with the backup service.
 * Provides methods for authentication, share management, and API calls.
 */
public interface ServiceManager {
    /**
     * Check if there is an active subscription.
     * 
     * @return True if there is an active subscription, false otherwise
     * @throws IOException If there's an error checking the subscription
     */
    boolean activeSubscription() throws IOException;

    /**
     * Check for a new version of the software.
     * 
     * @param forceCheck Whether to force a check even if recently checked
     * @return Information about the latest release
     */
    ReleaseResponse checkVersion(boolean forceCheck);

    /**
     * Get information about a new version if available.
     * 
     * @return Information about the new version, or null if no new version
     */
    ReleaseResponse newVersion();

    /**
     * Get the authentication token.
     * 
     * @return The authentication token
     */
    String getToken();

    /**
     * Get the source identifier.
     * 
     * @return The source identifier
     */
    String getSourceId();

    /**
     * Set the source identifier.
     * 
     * @param sourceId The source identifier
     */
    void setSourceId(String sourceId);

    /**
     * Get the source name.
     * 
     * @return The source name
     */
    String getSourceName();

    /**
     * Set the source name.
     * 
     * @param name The source name
     */
    void setSourceName(String name);

    /**
     * Generate an authentication token using an authorization code.
     * 
     * @param code The authorization code
     * @param codeVerifier The code verifier for PKCE
     * @throws IOException If there's an error generating the token
     */
    void generateToken(String code, String codeVerifier) throws IOException;

    /**
     * Delete the authentication token.
     * 
     * @throws IOException If there's an error deleting the token
     */
    void deleteToken() throws IOException;

    /**
     * Reset the service manager state.
     */
    void reset();

    /**
     * Create a share with the specified parameters.
     * 
     * @param encryptionIdentity The encryption identity
     * @param shareId The share identifier
     * @param share The share configuration
     * @throws IOException If there's an error creating the share
     */
    void createShare(EncryptionIdentity encryptionIdentity, String shareId, BackupShare share) throws IOException;

    /**
     * Update share encryption with the provided private identity.
     * 
     * @param privateIdentity The private identity for encryption
     * @param shareId The share identifier
     * @param share The share configuration
     * @return True if the update was successful, false otherwise
     * @throws IOException If there's an error updating share encryption
     */
    boolean updateShareEncryption(EncryptionIdentity.PrivateIdentity privateIdentity, String shareId, BackupShare share) throws IOException;

    /**
     * Delete a share.
     * 
     * @param shareId The share identifier
     * @throws IOException If there's an error deleting the share
     */
    void deleteShare(String shareId) throws IOException;

    /**
     * Get a list of all shares.
     * 
     * @return List of share responses
     * @throws IOException If there's an error retrieving shares
     */
    List<ShareResponse> getShares() throws IOException;

    /**
     * Get a list of shares for the current source.
     * 
     * @return List of share responses
     * @throws IOException If there's an error retrieving source shares
     */
    List<ShareResponse> getSourceShares() throws IOException;

    /**
     * Make an API call with retry logic.
     * 
     * @param region The region for the API call
     * @param callable The function to call
     * @param <T> The return type
     * @return The result of the API call
     * @throws IOException If there's an error making the API call
     */
    <T> T call(String region, ApiFunction<T> callable) throws IOException;

    /**
     * Make a direct API call without retry logic.
     * 
     * @param region The region for the API call
     * @param callable The function to call
     * @param <T> The return type
     * @return The result of the API call
     * @throws ApiException If there's an error making the API call
     */
    <T> T callApi(String region, ApiFunction<T> callable) throws ApiException;

    /**
     * Interface for API functions that can be called with retry logic.
     * 
     * @param <T> The return type of the API call
     */
    interface ApiFunction<T> {
        /**
         * Determine if a missing resource should trigger a retry.
         * 
         * @param region The region for the API call
         * @param apiException The exception that occurred
         * @return True if the call should be retried, false otherwise
         */
        default boolean shouldRetryMissing(String region, ApiException apiException) {
            return false;
        }

        /**
         * Determine if the call should be retried on failure.
         * 
         * @return True if the call should be retried, false otherwise
         */
        default boolean shouldRetry() {
            return true;
        }

        /**
         * Determine how to handle an API exception.
         * 
         * @param region The region for the API call
         * @param apiException The exception that occurred
         * @return How to handle the exception
         */
        default RetryUtils.LogOrWait logAndWait(String region, ApiException apiException) {
            return RetryUtils.LogOrWait.LOG_AND_WAIT;
        }

        /**
         * Determine if the call should wait for internet connectivity.
         * 
         * @return True if the call should wait for internet, false otherwise
         */
        default boolean waitForInternet() {
            return true;
        }

        /**
         * Make the API call.
         * 
         * @param api The API client
         * @return The result of the API call
         * @throws ApiException If there's an error making the API call
         */
        T call(BackupApi api) throws ApiException;
    }
}
