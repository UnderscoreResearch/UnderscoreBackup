package com.underscoreresearch.backup.cli;

import com.google.common.base.Strings;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptorFactory;
import com.underscoreresearch.backup.encryption.Hash;
import com.underscoreresearch.backup.errorcorrection.ErrorCorrectorFactory;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.io.IOIndex;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.model.BackupFileSelection;
import com.underscoreresearch.backup.model.BackupManifest;
import com.underscoreresearch.backup.model.BackupRetention;
import com.underscoreresearch.backup.model.BackupRetentionAdditional;
import com.underscoreresearch.backup.model.BackupSet;
import com.underscoreresearch.backup.model.BackupSetRoot;
import com.underscoreresearch.backup.model.BackupShare;
import com.underscoreresearch.backup.model.BackupTimeUnit;
import com.underscoreresearch.backup.model.BackupTimespan;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.underscoreresearch.backup.encryption.encryptors.NoneEncryptor.NONE_ENCRYPTION;
import static com.underscoreresearch.backup.encryption.encryptors.PQCEncryptor.PQC_ENCRYPTION;
import static com.underscoreresearch.backup.errorcorrection.implementation.NoneErrorCorrector.NONE;
import static com.underscoreresearch.backup.io.IOUtils.createDirectory;
import static com.underscoreresearch.backup.utils.LogUtil.debug;

@Slf4j
public class ConfigurationValidator {
    private static final String DEFAULT_ENCRYPTION = PQC_ENCRYPTION;
    private static final String DEFAULT_ERROR_CORRECTION = NONE;
    private static final int DEFAULT_UNSYNCED_SIZE = 8 * 1024 * 1024;
    private static final Pattern INVALID_CHARACTERS = Pattern.compile("[/:\\\\]");

    public static void validateConfiguration(BackupConfiguration configuration, boolean readOnly, boolean source) {
        validateSets(configuration, source);
        validateDestinations(configuration);
        validateSources(configuration);
        validateShares(configuration);
        validateManifest(configuration, readOnly, source);
    }

    private static void validateShares(BackupConfiguration configuration) {
        boolean allowNonPqcShares = configuration.getDestinations().values().stream().anyMatch((destination) ->
                !PQC_ENCRYPTION.equals(destination.getEncryption()));

        if (configuration.getShares() != null) {
            validateUniqueness("Shares", configuration.getShares().keySet());
            for (Map.Entry<String, BackupShare> entry : configuration.getShares().entrySet()) {
                validateDestination(entry.getValue().getName(), entry.getValue().getDestination());
                if (!entry.getValue().getDestination().getErrorCorrection().equals(DEFAULT_ERROR_CORRECTION)) {
                    throw new IllegalArgumentException("Share \"" + entry.getValue().getName() +
                            "\" destination must not use error correction");
                }
                if (invalidFilenameValue(entry.getValue().getName())) {
                    throw new IllegalArgumentException("Share has missing or invalid name");
                }
                if (!allowNonPqcShares && !PQC_ENCRYPTION.equals(entry.getValue().getDestination().getEncryption())) {
                    throw new IllegalArgumentException("Share \"" + entry.getValue().getName() +
                            "\" destination must use PQC encryption because your destinations do");
                }
                if (entry.getValue().getDestination().getEncryption().equals(NONE_ENCRYPTION)) {
                    throw new IllegalArgumentException("Share \"" + entry.getValue().getName() +
                            "\" destination must use encryption");
                }
                try {
                    if (entry.getKey().length() != 52)
                        throw new IllegalArgumentException();

                    Hash.decodeBytes(entry.getKey());
                } catch (IllegalArgumentException exc) {
                    throw new IllegalArgumentException("Invalid public key \"" + entry.getKey() +
                            "\" for share \"" + entry.getValue().getName() + "\"");
                }
                validateContents("Share contents " + entry.getValue().getName(), entry.getValue().getContents());
            }
        }
    }

    private static void validateSources(BackupConfiguration configuration) {
        if (configuration.getAdditionalSources() != null) {
            validateUniqueness("Additional sources", configuration.getAdditionalSources().keySet());
            for (Map.Entry<String, BackupDestination> entry : configuration.getAdditionalSources().entrySet()) {
                validateDestination(entry.getKey(), entry.getValue());
                if (!entry.getValue().getErrorCorrection().equals(DEFAULT_ERROR_CORRECTION)) {
                    throw new IllegalArgumentException("Additional source \"" + entry.getKey() +
                            "\" destination must not use error correction");
                }
                if (invalidFilenameValue(entry.getKey())) {
                    throw new IllegalArgumentException("Additional source \"" + entry.getKey() + "\" has missing or invalid name");
                }
            }
        }
    }

    private static void validateUniqueness(String type, Collection<String> keySet) {
        Set<String> checkSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        checkSet.addAll(keySet);
        if (checkSet.size() != keySet.size()) {
            throw new IllegalArgumentException(type + " does not have case insensitively unique names");
        }
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

        int randomizeScheduleSeconds = configuration.getProperty("randomizeScheduleSeconds", 0);
        if (randomizeScheduleSeconds > 0 && configuration.getManifest().getScheduleRandomize() == null) {
            log.info("Moved randomizeScheduleSeconds property to manifest property");
            configuration.getManifest().setScheduleRandomize(
                    BackupTimespan.builder().unit(BackupTimeUnit.SECONDS).duration(randomizeScheduleSeconds).build());
            configuration.getProperties().remove("randomizeScheduleSeconds");
        }

        if (manifest.getDestination() == null) {
            throw new IllegalArgumentException("Missing manifest destination name");
        }

        BackupDestination destination = configuration.getDestinations().get(manifest.getDestination());
        if (destination == null) {
            throw new IllegalArgumentException("Destination \"" + manifest.getDestination()
                    + "\" used in manifest is not defined");
        }

        if (!(IOProviderFactory.getProvider(destination) instanceof IOIndex)) {
            throw new IllegalArgumentException("This destination does not support listing files and can not be used as metadata destination");
        }

        if (configuration.getManifest().getAdditionalDestinations() != null) {
            for (String additionalDestination : configuration.getManifest().getAdditionalDestinations()) {
                if (!configuration.getDestinations().containsKey(additionalDestination)) {
                    throw new IllegalArgumentException("Additional manifest destination \"" + additionalDestination
                            + "\" is not defined");
                }
                if (!(IOProviderFactory.getProvider(configuration.getDestinations().get(additionalDestination)) instanceof IOIndex)) {
                    throw new IllegalArgumentException(
                            String.format("The destination \"%s\" does not support listing files and can not be used as metadata destination",
                                    additionalDestination));
                }
            }
        }

        if (EncryptorFactory.requireStorage(destination.getEncryption())) {
            throw new IllegalArgumentException("Encryption for destination used by metadata must not require storage");
        }

        if (!destination.getErrorCorrection().equals(DEFAULT_ERROR_CORRECTION)) {
            throw new IllegalArgumentException("Manifest destination must not use error correction");
        }

        String local = InstanceFactory.getInstance(CommandLineModule.DEFAULT_MANIFEST_LOCATION);

        File file = new File(local);
        if (!file.isDirectory()) {
            if (readOnly) {
                throw new IllegalArgumentException("Repository does not exist, run backup or rebuild-repository first");
            }
            log.warn("Local location for backup metadata does not exist.");
            createDirectory(file, true);
        } else {
            file = new File(file, "db");
            if (file.exists() && !file.isDirectory()) {
                throw new IllegalArgumentException("Repository \"" + file + "\" exists but is not a directory");
            }

            file = new File(file, "logs");
            if (file.exists() && !file.isDirectory()) {
                throw new IllegalArgumentException("Repository \"" + file + "\" does exist but is not a directory");
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
        if (configuration.getDestinations() == null || configuration.getDestinations().isEmpty()) {
            throw new IllegalArgumentException("No destinations are defined");
        }

        for (Map.Entry<String, BackupDestination> entry : configuration.getDestinations().entrySet()) {
            validateDestination(entry.getKey(), entry.getValue());
        }
    }

    private static void validateDestination(String name, BackupDestination destination) {
        if (destination == null) {
            throw new IllegalArgumentException("Missing destination for \"" + name + "\"");
        }
        if (destination.getErrorCorrection() == null) {
            debug(() -> log.debug("Error correction missing on \"" + name + "\" defaulting to \""
                    + DEFAULT_ERROR_CORRECTION + "\""));
            destination.setErrorCorrection(DEFAULT_ERROR_CORRECTION);
        } else if (!ErrorCorrectorFactory.hasCorrector(destination.getErrorCorrection())) {
            throw new IllegalArgumentException("Invalid error corrector \"" + destination.getErrorCorrection() + "\"");
        }
        if (destination.getEncryption() == null) {
            destination.setEncryption(DEFAULT_ENCRYPTION);
            debug(() -> log.debug("Encryption missing on \"" + name + "\" defaulting to \""
                    + DEFAULT_ENCRYPTION + "\""));
        } else if (!EncryptorFactory.hasEncryptor(destination.getEncryption())) {
            throw new IllegalArgumentException("Invalid encryptor \"" + destination.getErrorCorrection() + "\"");
        }

        if (!IOProviderFactory.hasProvider(destination)) {
            throw new IllegalArgumentException("Unsupported backup destination type \"" + destination.getType() + "\"");
        }
    }

    private static void validateSets(BackupConfiguration configuration, boolean source) {
        if (configuration.getSets() == null) {
            configuration.setSets(new ArrayList<>());
        }
        validateUniqueness("Sets", configuration.getSets().stream().map(BackupSet::getId)
                .collect(Collectors.toList()));
        HashSet<String> existingSetIds = new HashSet<>();
        for (BackupSet backupSet : configuration.getSets()) {
            if (Strings.isNullOrEmpty(backupSet.getId())) {
                throw new IllegalArgumentException("Backup set missing id");
            }

            if (invalidFilenameValue(backupSet.getId())) {
                throw new IllegalArgumentException("Invalid backup set ID");
            }

            if (!existingSetIds.add(backupSet.getId())) {
                throw new IllegalArgumentException("Backup set id \"" + backupSet.getId() + "\" is not unique");
            }

            if (backupSet.getDestinations() == null || backupSet.getDestinations().isEmpty()) {
                throw new IllegalArgumentException("Backup set \"" + backupSet.getId() + "\" is missing destination");
            }

            for (String destination : backupSet.getDestinations()) {
                if (!configuration.getDestinations().containsKey(destination)) {
                    throw new IllegalArgumentException("Destination \"" + destination + "\" used in backup set \""
                            + backupSet.getId() + "\" is not defined");
                }
            }

            if (backupSet.getRetention() == null) {
                backupSet.setRetention(new BackupRetention());
            } else {
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
                            throw new IllegalArgumentException("Missing validAfter of retention in set \""
                                    + backupSet.getId() + "\"");

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

            validateContents("Backup set " + backupSet.getId(), backupSet);

            if (Strings.isNullOrEmpty(backupSet.getSchedule()) && !source) {
                debug(() -> log.debug("Backup set \"" + backupSet.getId()
                        + "\" is missing a schedule so will only be scanned once on startup"));
            }
        }
    }

    private static void validateContents(String name, BackupFileSelection backupSet) {
        if (backupSet == null)
            throw new IllegalArgumentException("\"" + name + "\" missing definition");
        if (backupSet.getRoots() == null || backupSet.getRoots().isEmpty()) {
            throw new IllegalArgumentException("\"" + name + "\" does not have a single root defined");
        }

        for (BackupSetRoot root : backupSet.getRoots()) {
            if (Strings.isNullOrEmpty(root.getPath())) {
                throw new IllegalArgumentException("\"" + name + "\" missing root path");
            }

            if (!root.getNormalizedPath().equals(PathNormalizer.ROOT)) {
                File file = new File(root.getPath());
                if (file.exists()) {
                    try {
                        String rootFile = file.toPath().toRealPath().toString();
                        String existingRoot = file.toPath().toString();
                        if (!existingRoot.equals(rootFile)) {
                            log.warn("\"{}\" root \"{}\" changed to \"{}\"", name, existingRoot, rootFile);
                            root.setNormalizedPath(PathNormalizer.normalizePath(rootFile));
                        }
                    } catch (IOException e) {
                        throw new IllegalArgumentException("\"" + name + "\" can't access root \"" + root.getPath() + "\"");
                    }
                }
            }
        }
    }

    private static boolean invalidFilenameValue(String val) {
        return val == null ||
                val.equals(".") ||
                val.equals("..") ||
                INVALID_CHARACTERS.matcher(val).find();
    }
}
