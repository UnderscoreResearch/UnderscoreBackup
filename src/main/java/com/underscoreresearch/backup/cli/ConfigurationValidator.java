package com.underscoreresearch.backup.cli;

import com.google.common.base.Strings;
import com.underscoreresearch.backup.encryption.EncryptorFactory;
import com.underscoreresearch.backup.errorcorrection.ErrorCorrectorFactory;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.io.IOIndex;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.model.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.SystemUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;

import static com.underscoreresearch.backup.utils.LogUtil.debug;

@Slf4j
public class ConfigurationValidator {
    private static final String DEFAULT_LOCAL_PATH = "/var/cache/underscorebackup";
    private static final String DEFAULT_LOCAL_WINDOWS_PATH = "C:\\UnderscoreBackup";
    private static final String DEFAULT_ENCRYPTION = "AES256";
    private static final String DEFAULT_ERROR_CORRECTION = "NONE";
    private static final int DEFAULT_UNSYNCED_SIZE = 32 * 1024 * 1024;

    public static void validateConfiguration(BackupConfiguration configuration, boolean readOnly) {
        validateSets(configuration);
        validateDestinations(configuration);
        validateManifest(configuration, readOnly);
    }

    private static void validateManifest(BackupConfiguration configuration, boolean readOnly) {
        BackupManifest manifest = configuration.getManifest();
        if (manifest == null) {
            throw new IllegalArgumentException("Missing manifest section from configuration file");
        }

        BackupDestination destination = configuration.getDestinations().get(manifest.getDestination());
        if (destination == null) {
            throw new IllegalArgumentException("Destination " + manifest.getDestination()
                    + " used in manifest is not defined");
        }

        if (!(IOProviderFactory.getProvider(destination) instanceof IOIndex)) {
            throw new IllegalArgumentException("This destination does not support listing files and can not be used as metadata destination");
        }

        if (!destination.getErrorCorrection().equals("NONE")) {
            throw new IllegalArgumentException("Manifest destination must not use error correction");
        }

        String local = manifest.getLocalLocation();
        if (Strings.isNullOrEmpty(local)) {
            debug(() -> log.debug("Missing localLocation in manifest config. Defaulting to " + DEFAULT_LOCAL_PATH));
            if (SystemUtils.IS_OS_WINDOWS) {
                local = DEFAULT_LOCAL_WINDOWS_PATH;
            } else {
                local = DEFAULT_LOCAL_PATH;
            }
            manifest.setLocalLocation(local);
        }

        File file = new File(local);
        if (!file.isDirectory()) {
            if (readOnly) {
                throw new IllegalArgumentException("Repository does not exist, run backup or rebuild-repository first");
            }
            log.warn("Local location for backup metadata does not exist.");
            file.mkdirs();
        } else {
            file = new File(file, "db");
            if (file.exists() && !file.isDirectory()) {
                throw new IllegalArgumentException("Repository " + file.toString() + " exists but is not a directory");
            }

            file = new File(file, "logs");
            if (file.exists() && !file.isDirectory()) {
                throw new IllegalArgumentException("Repository " + file.toString() + " does exist but is not a directory");
            }
        }

        if (manifest.getMaximumUnsyncedSize() == null) {
            debug(() -> log.debug("Missing maximumUnsyncedSize, defaulting to " + DEFAULT_UNSYNCED_SIZE));
            manifest.setMaximumUnsyncedSize(DEFAULT_UNSYNCED_SIZE);
        } else if (manifest.getMaximumUnsyncedSize() <= 0) {
            throw new IllegalArgumentException("Default maximumUnsyncedSize must be more than 0");
        }

        if (manifest.getMaximumUnsyncedSeconds() != null && manifest.getMaximumUnsyncedSeconds() <= 0) {
            throw new IllegalArgumentException("Default maximumUnsyncedSeconds must be more than 0");
        }
    }

    private static void validateDestinations(BackupConfiguration configuration) {
        if (configuration.getDestinations() == null || configuration.getDestinations().size() == 0) {
            throw new IllegalArgumentException("No destinations are defined");
        }

        for (Map.Entry<String, BackupDestination> entry : configuration.getDestinations().entrySet()) {
            BackupDestination destination = entry.getValue();
            if (destination.getErrorCorrection() == null) {
                debug(() -> log.debug("Error correction missing on " + entry.getKey() + " defaulting to "
                        + DEFAULT_ERROR_CORRECTION));
                destination.setErrorCorrection(DEFAULT_ERROR_CORRECTION);
            } else {
                ErrorCorrectorFactory.getCorrector(destination.getErrorCorrection());
            }
            if (destination.getEncryption() == null) {
                destination.setEncryption(DEFAULT_ENCRYPTION);
                debug(() -> log.debug("Encryption missing on " + entry.getKey() + " defaulting to "
                        + DEFAULT_ENCRYPTION));
            } else {
                EncryptorFactory.getEncryptor(destination.getEncryption());
            }

            if (IOProviderFactory.getProvider(entry.getValue()) == null) {
                throw new IllegalArgumentException("Can't create backup destination " + entry.getKey());
            }
        }
    }

    private static void validateSets(BackupConfiguration configuration) {
        if (configuration.getSets() == null || configuration.getSets().size() == 0) {
            throw new IllegalArgumentException("No backup sets are defined");
        }

        HashSet<String> existingSetIds = new HashSet<>();
        for (BackupSet backupSet : configuration.getSets()) {
            if (Strings.isNullOrEmpty(backupSet.getId())) {
                throw new IllegalArgumentException("Backup set missing id");
            }

            if (!existingSetIds.add(backupSet.getId())) {
                throw new IllegalArgumentException("Backup set id " + backupSet.getId() + " is not unique");
            }

            if (backupSet.getDestinations() == null || backupSet.getDestinations().size() == 0) {
                throw new IllegalArgumentException("Backup set " + backupSet.getId() + "missing destination");
            }

            for (String destination : backupSet.getDestinations()) {
                if (!configuration.getDestinations().containsKey(destination)) {
                    throw new IllegalArgumentException("Destination " + destination + " used in backup set "
                            + backupSet.getId() + " is not defined");
                }
            }

            if (backupSet.getRetention() == null) {
                backupSet.setRetention(new BackupRetention());
            } else if (backupSet.getRetention() != null) {
                long previousFrequency = 0;

                if (backupSet.getRetention().getDefaultFrequency() == null) {
                    backupSet.getRetention().setDefaultFrequency(new BackupTimespan());
                }

                if (backupSet.getRetention().getRetainDeleted() == null) {
                    backupSet.getRetention().setRetainDeleted(new BackupTimespan());
                }

                previousFrequency = backupSet.getRetention().getDefaultFrequency().toEpochMilli();

                if (backupSet.getRetention().getOlder() != null) {

                    for (BackupRetentionAdditional older : backupSet.getRetention().getOlder()) {
                        if (older.getValidAfter() == null)
                            throw new IllegalArgumentException("Missing validAfter of retention in set "
                                    + backupSet.getId());

                        if (older.getFrequency() == null) {
                            older.setFrequency(new BackupTimespan());
                        }
                        long frequency = older.getFrequency().toEpochMilli();
                        if (previousFrequency < frequency) {
                            throw new IllegalArgumentException("Older frequencies must have longer frequencies than the previous records");
                        }
                        previousFrequency = frequency;
                    }
                }
            }

            if (Strings.isNullOrEmpty(backupSet.getRoot())) {
                throw new IllegalArgumentException("Backup set " + backupSet.getId() + "missing root");
            }

            File file = new File(backupSet.getRoot());
            if (file.exists()) {
                if (!file.isDirectory()) {
                    throw new IllegalArgumentException("Backup set root " + backupSet.getRoot() + "is not directory");
                }

                try {
                    String root = file.toPath().toRealPath().toString();
                    String existingRoot = file.toPath().toString();
                    if (!existingRoot.equals(root)) {
                        log.warn("Backup set root " + existingRoot + " changed to " + root);
                        backupSet.setNormalizedRoot(PathNormalizer.normalizePath(root));
                    }
                } catch (IOException e) {
                    throw new IllegalArgumentException("Can't access root " + backupSet.getRoot() + " of backup set");
                }
            }

            if (Strings.isNullOrEmpty(backupSet.getSchedule())) {
                debug(() -> log.debug("Backup set " + backupSet.getId()
                        + " is missing a schedule so will only be scanned once on startup"));
            }
        }
    }
}
