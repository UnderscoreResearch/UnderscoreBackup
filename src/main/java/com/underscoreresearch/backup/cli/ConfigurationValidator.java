package com.underscoreresearch.backup.cli;

import static com.underscoreresearch.backup.utils.LogUtil.debug;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import com.google.common.base.Strings;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptorFactory;
import com.underscoreresearch.backup.errorcorrection.ErrorCorrectorFactory;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.io.IOIndex;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.model.BackupManifest;
import com.underscoreresearch.backup.model.BackupRetention;
import com.underscoreresearch.backup.model.BackupRetentionAdditional;
import com.underscoreresearch.backup.model.BackupSet;
import com.underscoreresearch.backup.model.BackupSetRoot;
import com.underscoreresearch.backup.model.BackupTimespan;

@Slf4j
public class ConfigurationValidator {
    private static final String DEFAULT_ENCRYPTION = "AES256";
    private static final String DEFAULT_ERROR_CORRECTION = "NONE";
    private static final int DEFAULT_UNSYNCED_SIZE = 8 * 1024 * 1024;

    public static void validateConfiguration(BackupConfiguration configuration, boolean readOnly, boolean source) {
        validateSets(configuration, source);
        validateDestinations(configuration);
        validateManifest(configuration, readOnly, source);
    }

    private static void validateManifest(BackupConfiguration configuration, boolean readOnly, boolean source) {
        if (source) {
            if (configuration.getManifest() == null) {
                configuration.setManifest(new BackupManifest());
            }
            return;
        }

        BackupManifest manifest = configuration.getManifest();
        if (manifest == null) {
            throw new IllegalArgumentException("Missing manifest section from configuration file");
        }

        if (manifest.getDestination() == null) {
            throw new IllegalArgumentException("Missing manifest destination name");
        }

        BackupDestination destination = configuration.getDestinations().get(manifest.getDestination());
        if (destination == null) {
            throw new IllegalArgumentException("Destination " + manifest.getDestination()
                    + " used in manifest is not defined");
        }

        if (!(IOProviderFactory.getProvider(destination) instanceof IOIndex)) {
            throw new IllegalArgumentException("This destination does not support listing files and can not be used as metadata destination");
        }

        if (EncryptorFactory.requireStorage(destination.getEncryption())) {
            throw new IllegalArgumentException("Encryption for destination used by metadata ust not require storage");
        }

        if (!destination.getErrorCorrection().equals("NONE")) {
            throw new IllegalArgumentException("Manifest destination must not use error correction");
        }

        String local = manifest.getLocalLocation();
        if (Strings.isNullOrEmpty(local)) {
            local = InstanceFactory.getInstance(CommandLineModule.DEFAULT_MANIFEST_LOCATION);
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
            } else if (!ErrorCorrectorFactory.hasCorrector(destination.getErrorCorrection())) {
                throw new IllegalArgumentException("Invalid error corrector " + destination.getErrorCorrection());
            }
            if (destination.getEncryption() == null) {
                destination.setEncryption(DEFAULT_ENCRYPTION);
                debug(() -> log.debug("Encryption missing on " + entry.getKey() + " defaulting to "
                        + DEFAULT_ENCRYPTION));
            } else if (!EncryptorFactory.hasEncryptor(destination.getEncryption())) {
                throw new IllegalArgumentException("Invalid encryptor " + destination.getErrorCorrection());
            }

            if (!IOProviderFactory.hasProvider(entry.getValue())) {
                throw new IllegalArgumentException("Unsupported backup destination type " + entry.getKey());
            }
        }
    }

    private static void validateSets(BackupConfiguration configuration, boolean source) {
        if (configuration.getSets() == null) {
            configuration.setSets(new ArrayList<>());
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

            if (backupSet.getRoots() == null || backupSet.getRoots().size() == 0) {
                throw new IllegalArgumentException("Backup set " + backupSet.getId() + " does not have a single root defined");
            }


            for (BackupSetRoot root : backupSet.getRoots()) {
                if (Strings.isNullOrEmpty(root.getPath())) {
                    throw new IllegalArgumentException("Backup set " + backupSet.getId() + "missing root");
                }

                File file = new File(root.getPath());
                if (file.exists()) {
                    try {
                        String rootFile = file.toPath().toRealPath().toString();
                        String existingRoot = file.toPath().toString();
                        if (!existingRoot.equals(rootFile)) {
                            log.warn("Backup set root " + existingRoot + " changed to " + rootFile);
                            root.setNormalizedPath(PathNormalizer.normalizePath(rootFile));
                        }
                    } catch (IOException e) {
                        throw new IllegalArgumentException("Can't access root " + root.getPath() + " of backup set");
                    }
                }
            }

            if (Strings.isNullOrEmpty(backupSet.getSchedule()) && !source) {
                debug(() -> log.debug("Backup set " + backupSet.getId()
                        + " is missing a schedule so will only be scanned once on startup"));
            }
        }
    }
}
