package com.underscoreresearch.backup.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.manifest.model.PushActivePath;
import com.underscoreresearch.backup.model.BackupActivatedShare;
import com.underscoreresearch.backup.model.BackupActivePath;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockAdditional;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupFilePart;
import com.underscoreresearch.backup.model.BackupPartialFile;
import com.underscoreresearch.backup.model.BackupPendingSet;
import com.underscoreresearch.backup.model.ExternalBackupFile;

import java.util.List;

/**
 * Utility class providing Jackson ObjectMapper, ObjectReader, and ObjectWriter instances
 * for serializing and deserializing various backup model classes.
 * This centralizes the serialization configuration and provides type-specific readers and writers
 * for efficient and consistent JSON processing throughout the application.
 */
public class SerializationUtils {
    /**
     * Shared ObjectMapper configured to exclude null properties.
     */
    public static ObjectMapper MAPPER = new ObjectMapper()
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);

    /**
     * Reader for deserializing BackupConfiguration objects.
     */
    public static final ObjectReader BACKUP_CONFIGURATION_READER = MAPPER
            .readerFor(BackupConfiguration.class);
    
    /**
     * Writer for serializing BackupConfiguration objects.
     */
    public static final ObjectWriter BACKUP_CONFIGURATION_WRITER = MAPPER
            .writerFor(BackupConfiguration.class);
    
    /**
     * Reader for deserializing BackupDestination objects.
     */
    public static final ObjectReader BACKUP_DESTINATION_READER = MAPPER
            .readerFor(BackupDestination.class);
    
    /**
     * Writer for serializing BackupDestination objects.
     */
    public static final ObjectWriter BACKUP_DESTINATION_WRITER = MAPPER
            .writerFor(BackupDestination.class);

    /**
     * Reader for deserializing BackupBlock objects.
     */
    public static final ObjectReader BACKUP_BLOCK_READER = MAPPER
            .readerFor(BackupBlock.class);
    
    /**
     * Writer for serializing BackupBlock objects.
     */
    public static final ObjectWriter BACKUP_BLOCK_WRITER = MAPPER
            .writerFor(BackupBlock.class);
    
    /**
     * Reader for deserializing BackupFile objects.
     */
    public static final ObjectReader BACKUP_FILE_READER = MAPPER
            .readerFor(BackupFile.class);
    
    /**
     * Writer for serializing BackupFile objects.
     */
    public static final ObjectWriter BACKUP_FILE_WRITER = MAPPER
            .writerFor(BackupFile.class);
    
    /**
     * Reader for deserializing BackupDirectory objects.
     */
    public static final ObjectReader BACKUP_DIRECTORY_READER = MAPPER
            .readerFor(BackupDirectory.class);
    
    /**
     * Writer for serializing BackupDirectory objects.
     */
    public static final ObjectWriter BACKUP_DIRECTORY_WRITER = MAPPER
            .writerFor(BackupDirectory.class);

    /**
     * Reader for deserializing PushActivePath objects.
     */
    public static final ObjectReader PUSH_ACTIVE_PATH_READER = MAPPER
            .readerFor(PushActivePath.class);
    
    /**
     * Writer for serializing PushActivePath objects.
     */
    public static final ObjectWriter PUSH_ACTIVE_PATH_WRITER = MAPPER
            .writerFor(PushActivePath.class);
    
    /**
     * Reader for deserializing BackupFilePart objects.
     */
    public static final ObjectReader BACKUP_FILE_PART_READER = MAPPER
            .readerFor(BackupFilePart.class);
    
    /**
     * Writer for serializing BackupFilePart objects.
     */
    public static final ObjectWriter BACKUP_FILE_PART_WRITER = MAPPER
            .writerFor(BackupFilePart.class);

    /**
     * Writer for serializing lists of ExternalBackupFile objects.
     */
    public static final ObjectWriter EXTERNAL_BACKUP_FILES_WRITER = MAPPER
            .writerFor(new TypeReference<List<ExternalBackupFile>>() {
            });

    /**
     * Reader for deserializing BackupActivePath objects.
     */
    public static final ObjectReader BACKUP_ACTIVE_PATH_READER = MAPPER.readerFor(BackupActivePath.class);
    
    /**
     * Writer for serializing BackupActivePath objects.
     */
    public static final ObjectWriter BACKUP_ACTIVE_PATH_WRITER = MAPPER.writerFor(BackupActivePath.class);
    
    /**
     * Reader for deserializing BackupPendingSet objects.
     */
    public static final ObjectReader BACKUP_PENDING_SET_READER = MAPPER.readerFor(BackupPendingSet.class);
    
    /**
     * Writer for serializing BackupPendingSet objects.
     */
    public static final ObjectWriter BACKUP_PENDING_SET_WRITER = MAPPER.writerFor(BackupPendingSet.class);
    
    /**
     * Reader for deserializing BackupPartialFile objects.
     */
    public static final ObjectReader BACKUP_PARTIAL_FILE_READER = MAPPER.readerFor(BackupPartialFile.class);
    
    /**
     * Writer for serializing BackupPartialFile objects.
     */
    public static final ObjectWriter BACKUP_PARTIAL_FILE_WRITER = MAPPER.writerFor(BackupPartialFile.class);
    
    /**
     * Reader for deserializing BackupBlockAdditional objects.
     */
    public static final ObjectReader BACKUP_BLOCK_ADDITIONAL_READER = MAPPER.readerFor(BackupBlockAdditional.class);
    
    /**
     * Writer for serializing BackupBlockAdditional objects.
     */
    public static final ObjectWriter BACKUP_BLOCK_ADDITIONAL_WRITER = MAPPER.writerFor(BackupBlockAdditional.class);

    /**
     * Reader for deserializing BackupActivatedShare objects.
     */
    public static final ObjectReader BACKUP_ACTIVATED_SHARE_READER = MAPPER.readerFor(BackupActivatedShare.class);
    
    /**
     * Writer for serializing BackupActivatedShare objects.
     */
    public static final ObjectWriter BACKUP_ACTIVATED_SHARE_WRITER = MAPPER.writerFor(BackupActivatedShare.class);
}
