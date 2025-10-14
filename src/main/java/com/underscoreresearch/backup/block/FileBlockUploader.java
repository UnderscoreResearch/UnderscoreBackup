package com.underscoreresearch.backup.block;

import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockUploadCompletion;
import com.underscoreresearch.backup.model.BackupCompletion;
import com.underscoreresearch.backup.model.BackupData;
import com.underscoreresearch.backup.model.BackupSet;

import java.util.Set;

/**
 * Interface for uploading file blocks to storage destinations.
 * Handles the process of storing blocks in backup destinations.
 */
public interface FileBlockUploader {
    /**
     * Upload a block to the destinations specified in the backup set.
     * 
     * @param set The backup set containing destination information
     * @param unencryptedData The unencrypted data to upload
     * @param blockHash The hash of the block for identification
     * @param format The format of the block
     * @param completionFuture Callback for when the upload is complete
     */
    void uploadBlock(BackupSet set,
                     BackupData unencryptedData,
                     String blockHash,
                     String format,
                     BackupCompletion completionFuture);

    /**
     * Upload a block to specific required destinations.
     * 
     * @param requiredDestinations Set of destination identifiers where the block should be uploaded
     * @param existingBlock Existing block information if this is an update
     * @param unencryptedData The unencrypted data to upload
     * @param blockHash The hash of the block for identification
     * @param format The format of the block
     * @param completionFuture Callback for when the upload is complete
     */
    void uploadBlock(Set<String> requiredDestinations,
                     BackupBlock existingBlock,
                     BackupData unencryptedData,
                     String blockHash,
                     String format,
                     BackupBlockUploadCompletion completionFuture);
}
