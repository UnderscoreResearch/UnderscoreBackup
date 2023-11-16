package com.underscoreresearch.backup.utils;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.encryption.EncryptionKey;
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

public class SerializationUtils {
    public static ObjectMapper MAPPER = new ObjectMapper()
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);

    public static final ObjectReader BACKUP_CONFIGURATION_READER = MAPPER
            .readerFor(BackupConfiguration.class);
    public static final ObjectWriter BACKUP_CONFIGURATION_WRITER = MAPPER
            .writerFor(BackupConfiguration.class);
    public static final ObjectReader BACKUP_DESTINATION_READER = MAPPER
            .readerFor(BackupDestination.class);
    public static final ObjectWriter BACKUP_DESTINATION_WRITER = MAPPER
            .writerFor(BackupDestination.class);

    public static final ObjectReader ENCRYPTION_KEY_READER = MAPPER
            .readerFor(EncryptionKey.class);
    public static final ObjectWriter ENCRYPTION_KEY_WRITER = MAPPER
            .writerFor(EncryptionKey.class);

    public static final ObjectReader BACKUP_BLOCK_READER = MAPPER
            .readerFor(BackupBlock.class);
    public static final ObjectWriter BACKUP_BLOCK_WRITER = MAPPER
            .writerFor(BackupBlock.class);
    public static final ObjectReader BACKUP_FILE_READER = MAPPER
            .readerFor(BackupFile.class);
    public static final ObjectWriter BACKUP_FILE_WRITER = MAPPER
            .writerFor(BackupFile.class);
    public static final ObjectReader BACKUP_DIRECTORY_READER = MAPPER
            .readerFor(BackupDirectory.class);
    public static final ObjectWriter BACKUP_DIRECTORY_WRITER = MAPPER
            .writerFor(BackupDirectory.class);

    public static final ObjectReader PUSH_ACTIVE_PATH_READER = MAPPER
            .readerFor(PushActivePath.class);
    public static final ObjectWriter PUSH_ACTIVE_PATH_WRITER = MAPPER
            .writerFor(PushActivePath.class);
    public static final ObjectReader BACKUP_FILE_PART_READER = MAPPER
            .readerFor(BackupFilePart.class);
    public static final ObjectWriter BACKUP_FILE_PART_WRITER = MAPPER
            .writerFor(BackupFilePart.class);

    public static final ObjectWriter EXTERNAL_BACKUP_FILES_WRITER = MAPPER
            .writerFor(new TypeReference<List<ExternalBackupFile>>() {
            });

    public static final ObjectReader BACKUP_ACTIVE_PATH_READER = MAPPER.readerFor(BackupActivePath.class);
    public static final ObjectWriter BACKUP_ACTIVE_PATH_WRITER = MAPPER.writerFor(BackupActivePath.class);
    public static final ObjectReader BACKUP_PENDING_SET_READER = MAPPER.readerFor(BackupPendingSet.class);
    public static final ObjectWriter BACKUP_PENDING_SET_WRITER = MAPPER.writerFor(BackupPendingSet.class);
    public static final ObjectReader BACKUP_PARTIAL_FILE_READER = MAPPER.readerFor(BackupPartialFile.class);
    public static final ObjectWriter BACKUP_PARTIAL_FILE_WRITER = MAPPER.writerFor(BackupPartialFile.class);
    public static final ObjectReader BACKUP_BLOCK_ADDITIONAL_READER = MAPPER.readerFor(BackupBlockAdditional.class);
    public static final ObjectWriter BACKUP_BLOCK_ADDITIONAL_WRITER = MAPPER.writerFor(BackupBlockAdditional.class);

    public static final ObjectReader BACKUP_ACTIVATED_SHARE_READER = MAPPER.readerFor(BackupActivatedShare.class);
    public static final ObjectWriter BACKUP_ACTIVATED_SHARE_WRITER = MAPPER.writerFor(BackupActivatedShare.class);
}
