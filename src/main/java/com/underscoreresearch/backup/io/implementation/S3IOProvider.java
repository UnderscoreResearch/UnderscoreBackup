package com.underscoreresearch.backup.io.implementation;

import com.underscoreresearch.backup.io.ConnectionLimiter;
import com.underscoreresearch.backup.io.IOIndex;
import com.underscoreresearch.backup.io.IOPlugin;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.utils.ProcessingStoppedException;
import com.underscoreresearch.backup.utils.RetryUtils;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.io.implementation.S3IOProvider.S3_TYPE;
import static com.underscoreresearch.backup.utils.log.LogUtil.debug;
import static com.underscoreresearch.backup.utils.log.LogUtil.readableSize;

/**
 * IO provider implementation for S3-compatible storage.
 * Provides methods for storing and retrieving backup data from S3 buckets.
 */
@IOPlugin(S3_TYPE)
@Slf4j
public class S3IOProvider implements IOIndex, Closeable {
    public static final String S3_TYPE = "S3";
    private final S3Client client;
    private final String root;
    private final String bucket;
    private final ConnectionLimiter limiter;

    /**
     * Constructor for S3IOProvider.
     *
     * @param destination The backup destination configuration
     */
    public S3IOProvider(BackupDestination destination) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(destination.getPrincipal(),
                destination.getCredential());

        S3ClientBuilder builder = S3Client.builder()
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .retryStrategy(s -> s.maxAttempts(1)).build())
                .credentialsProvider(StaticCredentialsProvider.create(credentials));

        String endpoint = destination.getProperty("apiEndpoint", null);
        if (endpoint != null) {
            builder.endpointOverride(URI.create(endpoint));
        }

        String region = destination.getProperty("region", null);
        if (region != null) {
            builder.region(Region.of(region));
        }

        client = builder.build();

        URI uri = URI.create(destination.getEndpointUri());
        String path = uri.getPath();
        if (path.startsWith(PATH_SEPARATOR)) {
            path = path.substring(1);
        }
        if (path.endsWith(PATH_SEPARATOR)) {
            root = path.substring(0, path.length() - 1);
        } else {
            root = path;
        }
        bucket = uri.getHost();

        limiter = new ConnectionLimiter(destination);
    }

    /**
     * Get the full S3 key for a relative key.
     *
     * @param key The relative key
     * @return The full S3 key
     */
    private String getRootedKey(String key) {
        if (!key.startsWith(PATH_SEPARATOR)) {
            return root + PATH_SEPARATOR + key;
        } else {
            return root + key;
        }
    }

    /**
     * List all available keys with the specified prefix.
     *
     * @param prefix The prefix to filter keys by
     * @return List of keys matching the prefix
     * @throws IOException If there's an error accessing S3
     */
    @Override
    public List<String> availableKeys(String prefix) throws IOException {
        String rootedKey = getRootedKey(prefix);
        if (!rootedKey.endsWith(PATH_SEPARATOR)) {
            rootedKey += PATH_SEPARATOR;
        }

        ListObjectsV2Request initialRequest = ListObjectsV2Request.builder()
                .bucket(bucket)
                .delimiter(PATH_SEPARATOR)
                .prefix(rootedKey).build();

        List<String> ret = new ArrayList<>();

        try {
            ListObjectsV2Response response = RetryUtils.retry(() -> limiter.call(() -> client.listObjectsV2(initialRequest)), null);
            while (true) {

                for (S3Object obj : response.contents()) {
                    if (obj.key().startsWith(rootedKey)) {
                        ret.add(obj.key().substring(rootedKey.length()));
                    }
                }

                for (CommonPrefix commonPrefixes : response.commonPrefixes()) {
                    if (commonPrefixes.prefix().startsWith(rootedKey)) {
                        ret.add(commonPrefixes.prefix().substring(rootedKey.length()));
                    }
                }

                if (response.isTruncated()) {
                    ListObjectsV2Request request = initialRequest.toBuilder()
                            .continuationToken(response.nextContinuationToken()).build();
                    response = RetryUtils.retry(() -> limiter.call(() -> client.listObjectsV2(request)), null);
                } else {
                    break;
                }
            }
        } catch (IOException | ProcessingStoppedException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to list key prefix \"" + rootedKey + "\"", e);
        }
        return ret;
    }

    /**
     * Upload data to S3 with a suggested key.
     *
     * @param key The suggested key for the data
     * @param data The data to upload
     * @return The actual key used for the uploaded data
     * @throws IOException If there's an error uploading the data
     */
    @Override
    public String upload(String key, byte[] data) throws IOException {
        String rootedKey = getRootedKey(key);

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(rootedKey)
                    .contentLength((long) data.length)
                    .build();
            RequestBody body = RequestBody.fromBytes(data);

            RetryUtils.retry(() -> limiter.call(() -> {
                client.putObject(request, body);

                debug(() -> log.debug("Uploaded \"{}/{}\" ({})", bucket, rootedKey, readableSize(data.length)));
                return null;
            }), null);
        } catch (IOException | ProcessingStoppedException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to upload object \"" + rootedKey + "\"", e);
        }

        return key;
    }

    /**
     * Download data from S3 using a key.
     *
     * @param key The key for the data to download
     * @return The downloaded data
     * @throws IOException If there's an error downloading the data
     */
    @Override
    public byte[] download(String key) throws IOException {
        String rootedKey = getRootedKey(key);

        try {
            return RetryUtils.retry(() -> limiter.call(() -> {
                try (ResponseInputStream<GetObjectResponse> obj = client.getObject(GetObjectRequest.builder()
                        .bucket(bucket).key(rootedKey).build())) {
                    byte[] data = IOUtils.readAllBytes(obj);
                    debug(() -> log.debug("Downloaded \"{}/{}\" ({})", bucket, rootedKey, readableSize(data.length)));
                    return data;
                }
            }), (exc) -> {
                if (exc instanceof S3Exception s3Exception)
                    return !s3Exception.awsErrorDetails().errorCode().equals("NoSuchKey");
                return true;
            });
        } catch (IOException | ProcessingStoppedException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to download object \"" + rootedKey + "\"", e);
        }
    }

    /**
     * Get a unique cache key for this provider.
     *
     * @return The cache key
     */
    @Override
    public String getCacheKey() {
        return bucket + PATH_SEPARATOR + root;
    }

    /**
     * Check if data exists at the specified key.
     *
     * @param key The key to check
     * @return True if data exists at the key, false otherwise
     * @throws IOException If there's an error checking for existence
     */
    @Override
    public boolean exists(String key) throws IOException {
        String rootedKey = getRootedKey(key);

        try {
            boolean ret = RetryUtils.retry(() -> limiter.call(() -> {
                try {
                    final HeadObjectResponse response = client.headObject(HeadObjectRequest.builder()
                            .bucket(bucket).key(rootedKey).build());
                    return response.deleteMarker() == null || !response.deleteMarker();
                } catch (S3Exception exc) {
                    if (exc.awsErrorDetails().errorCode().equals("NoSuchKey"))
                        return false;
                    throw exc;
                }
            }), null);
            debug(() -> log.debug("Exists \"{}\" ({})", key, ret));
            return ret;
        } catch (IOException | ProcessingStoppedException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to download object \"" + rootedKey + "\"", e);
        }
    }

    /**
     * Delete data at the specified key.
     *
     * @param key The key for the data to delete
     * @throws IOException If there's an error deleting the data
     */
    @Override
    public void delete(String key) throws IOException {
        String rootedKey = getRootedKey(key);

        try {
            RetryUtils.<Void>retry(() -> limiter.call(() -> {
                try {
                    client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(rootedKey).build());
                    return null;
                } catch (S3Exception exc) {
                    if (exc.awsErrorDetails().errorCode().equals("NoSuchKey"))
                        return null;
                    throw exc;
                }
            }), null);
            debug(() -> log.debug("Deleted \"{}\"", key));
        } catch (IOException | ProcessingStoppedException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to download object \"" + rootedKey + "\"", e);
        }
    }

    /**
     * Check if the provider credentials are valid.
     *
     * @param readonly Whether to check for read-only access
     * @throws IOException If the credentials are invalid or there's an error checking
     */
    @Override
    public void checkCredentials(boolean readonly) throws IOException {
        ListObjectsV2Request initialRequest = ListObjectsV2Request.builder()
                .bucket(bucket)
                .delimiter(PATH_SEPARATOR)
                .maxKeys(1)
                .prefix(root).build();
        try {
            ListObjectsV2Response response = client.listObjectsV2(initialRequest);
            if (readonly) {
                if (response.contents().isEmpty()) {
                    throw new IOException("No contents in read only S3 location");
                }
            }
        } catch (SdkException exc) {
            throw new IOException("Failed to access S3", exc);
        }
    }

    /**
     * Close the S3 client and release resources.
     *
     * @throws IOException If there's an error closing the client
     */
    @Override
    public void close() throws IOException {
        client.close();
    }
}
