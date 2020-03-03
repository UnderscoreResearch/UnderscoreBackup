package com.underscoreresearch.backup.io.implementation;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.LogUtil.readableSize;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.apache.logging.log4j.util.Strings;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.retry.PredefinedRetryPolicies;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.underscoreresearch.backup.io.IOIndex;
import com.underscoreresearch.backup.io.IOPlugin;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.utils.RetryUtils;

@IOPlugin(("S3"))
@Slf4j
public class S3IOPRovider implements IOIndex, IOProvider {
    private final BackupDestination destination;
    private final AmazonS3 client;
    private final String root;
    private final String bucket;

    public S3IOPRovider(BackupDestination destination) {
        this.destination = destination;

        BasicAWSCredentials credentials = new BasicAWSCredentials(destination.getPrincipal(),
                destination.getCredential());
        String region = destination.getProperty("region", "us-east-1");

        client = AmazonS3ClientBuilder.standard()
                .withRegion(region)
                .withClientConfiguration(new ClientConfiguration()
                        .withRetryPolicy(PredefinedRetryPolicies.NO_RETRY_POLICY))
                .withCredentials(new AWSStaticCredentialsProvider(credentials)).build();

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
    }

    private String getRootedKey(String key) {
        if (!key.startsWith(PATH_SEPARATOR)) {
            return root + PATH_SEPARATOR + key;
        } else {
            return root + key;
        }
    }

    @Override
    public List<String> availableKeys(String prefix) throws IOException {
        String rootedKey = getRootedKey(prefix);
        if (!rootedKey.endsWith(PATH_SEPARATOR)) {
            rootedKey += PATH_SEPARATOR;
        }

        ListObjectsV2Request request = new ListObjectsV2Request()
                .withBucketName(bucket)
                .withDelimiter(PATH_SEPARATOR)
                .withPrefix(rootedKey);

        List<String> ret = new ArrayList<>();

        try {
            ListObjectsV2Result response = RetryUtils.retry(() -> client.listObjectsV2(request), null);
            while (true) {

                for (S3ObjectSummary obj : response.getObjectSummaries()) {
                    if (obj.getKey().startsWith(rootedKey)) {
                        ret.add(obj.getKey().substring(rootedKey.length()));
                    }
                }

                for (String commonPrefixes : response.getCommonPrefixes()) {
                    if (commonPrefixes.startsWith(rootedKey)) {
                        ret.add(commonPrefixes.substring(rootedKey.length()));
                    }
                }

                if (response.isTruncated()) {
                    request.withContinuationToken(response.getNextContinuationToken());
                    response = RetryUtils.retry(() -> client.listObjectsV2(request), null);
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            throw new IOException("Failed to list key prefix " + rootedKey, e);
        }
        return ret;
    }

    @Override
    public String upload(String key, byte[] data) throws IOException {
        String rootedKey = getRootedKey(key);

        try {
            RetryUtils.retry(() -> {
                try (ByteArrayInputStream stream = new ByteArrayInputStream(data)) {
                    ObjectMetadata metadata = new ObjectMetadata();
                    metadata.setContentLength(data.length);
                    client.putObject(bucket, rootedKey, stream, metadata);
                }

                debug(() -> log.debug("Uploaded {}/{} ({})", bucket, rootedKey, readableSize(data.length)));
                return null;
            }, null);
        } catch (Exception e) {
            throw new IOException("Failed to upload object " + rootedKey, e);
        }

        return key;
    }

    @Override
    public byte[] download(String key) throws IOException {
        String rootedKey = getRootedKey(key);

        try {
            return RetryUtils.retry(() -> {
                try (S3Object obj = client.getObject(bucket, rootedKey)) {
                    try (S3ObjectInputStream str = obj.getObjectContent()) {
                        byte[] data = IOUtils.readAllBytes(str);
                        debug(() -> log.debug("Downloaded {}/{} ({})", bucket, rootedKey, readableSize(data.length)));
                        return data;
                    }
                }
            }, (exc) -> {
                if (exc instanceof AmazonS3Exception) {
                    return !((AmazonS3Exception) exc).getErrorCode().equals("NoSuchKey");
                }
                return true;
            });
        } catch (Exception e) {
            throw new IOException("Failed to download object " + rootedKey, e);
        }
    }
}
