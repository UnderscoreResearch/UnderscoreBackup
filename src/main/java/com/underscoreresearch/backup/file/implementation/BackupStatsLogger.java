package com.underscoreresearch.backup.file.implementation;

import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Strings;
import com.underscoreresearch.backup.ui.helpers.RepositoryTrimmer;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.service.api.invoker.ApiException;
import com.underscoreresearch.backup.service.api.model.SourceStatsModel;
import com.underscoreresearch.backup.utils.log.StatusLine;
import com.underscoreresearch.backup.utils.log.StatusLogger;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.underscoreresearch.backup.configuration.CommandLineModule.MANIFEST_LOCATION;
import static com.underscoreresearch.backup.utils.log.LogUtil.formatTimestamp;
import static com.underscoreresearch.backup.utils.log.LogUtil.readableNumber;
import static com.underscoreresearch.backup.utils.log.LogUtil.readableSize;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

/**
 * BackupStatsLogger is responsible for logging backup statistics and errors.
 * It reads and writes statistics to a file, and provides methods to update and retrieve statistics.
 */
@Slf4j
public class BackupStatsLogger implements StatusLogger {
    private static final ObjectReader STATISTICS_READER = MAPPER.readerFor(RepositoryTrimmer.Statistics.class);
    private static final ObjectWriter STATISTICS_WRITER = MAPPER.writerFor(RepositoryTrimmer.Statistics.class);
    // All errors with custom fields in them should have the custom fields within \" characters
    // and that is redacted by this method. Should specifically include any paths logged anywhere in the app.
    private static final Pattern ERROR_REQUEST = Pattern.compile("[\u200E\"].*[\u200E\"]");
    private static File errorFile;
    private final String manifestPath;
    private final BackupConfiguration configuration;
    private final Map<String, Date> scheduledTimes = new HashMap<>();
    private RepositoryTrimmer.Statistics statistics;
    @Getter
    private boolean uploadRunning;
    @Getter
    @Setter
    private boolean downloadRunning;

    /**
     * Constructor for BackupStatsLogger.
     *
     * @param configuration The backup configuration
     * @param manifestPath The path to the manifest
     */
    public BackupStatsLogger(BackupConfiguration configuration, String manifestPath) {
        this.manifestPath = manifestPath;
        this.configuration = configuration;

        statistics = readStatistics();

        setErrorFile(manifestPath);
    }

    /**
     * Sets the error file path.
     *
     * @param manifestPath The path to the manifest
     */
    private static void setErrorFile(String manifestPath) {
        if (errorFile == null) {
            errorFile = new File(manifestPath, "error.txt");
        }
    }

    /**
     * Writes an encountered error to the error file.
     *
     * @param errorBytes The error message as a byte array
     */
    public static void writeEncounteredError(byte[] errorBytes) {
        try {
            ensureErrorFile();
            if (errorFile != null && !errorFile.exists()) {
                try (FileWriter fileWriter = new FileWriter(errorFile, StandardCharsets.UTF_8)) {
                    fileWriter.write(cleanError(new String(errorBytes, StandardCharsets.UTF_8)));
                }
            }
        } catch (IOException e) {
            log.warn("Failed to save last encountered error", e);
        }
    }

    /**
     * Cleans the error message by redacting sensitive information.
     *
     * @param error The error message
     * @return The cleaned error message
     */
    private static String cleanError(String error) {
        return ERROR_REQUEST.matcher(error).replaceAll("{REDACTED}");
    }

    /**
     * Extracts the last encountered error message.
     *
     * @return The last encountered error message, or null if not available
     */
    public static String extractEncounteredError() {
        ensureErrorFile();
        if (errorFile != null && errorFile.exists()) {
            try {
                String ret;
                try (FileInputStream reader = new FileInputStream(errorFile)) {
                    ret = new String(IOUtils.readAllBytes(reader), StandardCharsets.UTF_8);
                }
                IOUtils.deleteFile(errorFile);
                return ret;
            } catch (IOException e) {
                log.warn("Failed to read last encountered error", e);
            }
        }
        return null;
    }

    /**
     * Ensures that the error file is set up.
     */
    private static void ensureErrorFile() {
        if (errorFile == null) {
            try {
                setErrorFile(InstanceFactory.getInstance(MANIFEST_LOCATION));
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Gets the statistics file path.
     *
     * @return The statistics file
     */
    private File getStatisticsFile() {
        return new File(manifestPath, "statistics.json");
    }

    /**
     * Updates the statistics with the provided data.
     *
     * @param statistics The statistics to update
     */
    public void updateStats(RepositoryTrimmer.Statistics statistics) {
        if (manifestPath != null) {
            if (this.statistics != null) {
                statistics.setNeedActivation(this.statistics.isNeedActivation());
            }
            statistics.setTimestamp(Instant.now().toEpochMilli());
            storeStats(statistics);
        }
    }

    /**
     * Stores the statistics in a file.
     *
     * @param statistics The statistics to store
     */
    private void storeStats(RepositoryTrimmer.Statistics statistics) {
        try {
            this.statistics = statistics;
            STATISTICS_WRITER.writeValue(getStatisticsFile(), statistics);
        } catch (IOException e) {
            log.warn("Failed to save backup statistics", e);
        }
    }

    /**
     * Set if activation is needed.
     *
     * @param needsActivation True if activation is needed, false otherwise
     */
    public void setNeedsActivation(boolean needsActivation) {
        if (statistics != null) {
            statistics.setNeedActivation(needsActivation);
            storeStats(statistics);
        }
    }

    /**
     * Checks if validation is needed.
     *
     * @return True if validation is needed, false otherwise
     */
    public boolean isNeedValidation() {
        return statistics != null && statistics.isNeedValidation();
    }

    /**
     * Sets whether validation is needed.
     *
     * @param needValidation True if validation is needed, false otherwise
     */
    public void setNeedValidation(boolean needValidation) {
        if (statistics != null) {
            statistics.setNeedValidation(needValidation);
            storeStats(statistics);
        }
    }

    /**
     * Reads the statistics from the file.
     *
     * @return The statistics
     */
    private RepositoryTrimmer.Statistics readStatistics() {
        if (manifestPath != null) {
            try {
                File file = getStatisticsFile();
                if (file.exists()) {
                    return STATISTICS_READER.readValue(getStatisticsFile());
                }
            } catch (IOException e) {
                log.warn("Failed to read backup statistics", e);
            }
        }
        return statistics;
    }

    /**
     * Updates the future scheduled set times.
     *
     * @param newState The new scheduled times
     */
    public void updateScheduledTimes(Map<String, Date> newState) {
        synchronized (scheduledTimes) {
            scheduledTimes.clear();
            if (newState != null) {
                scheduledTimes.putAll(newState);
            }
        }
    }

    /**
     * Resets the status of the logger. NOP.
     */
    @Override
    public void resetStatus() {
    }

    /**
     * Sets the upload running status.
     *
     * @param uploadRunning True if upload is running, false otherwise
     */
    public void setUploadRunning(boolean uploadRunning) {
        if (this.uploadRunning && !uploadRunning) {
            if (statistics != null && configuration.getManifest() != null &&
                    (configuration.getManifest().getReportStats() == null || configuration.getManifest().getReportStats())) {
                ServiceManager serviceManager = InstanceFactory.getInstance(ServiceManager.class);
                if (!Strings.isNullOrEmpty(serviceManager.getSourceId())) {
                    SourceStatsModel stats = new SourceStatsModel();
                    stats.setFiles(statistics.getFiles());
                    stats.setFileVersions(statistics.getFileVersions());
                    stats.setBlocks(statistics.getBlocks());
                    stats.setBlockParts(statistics.getBlockParts());
                    stats.setDirectories(statistics.getDirectories());
                    stats.setDirectoryVersions(statistics.getDirectoryVersions());
                    stats.setTotalSize(statistics.getTotalSize());
                    stats.setTotalSizeLastVersion(statistics.getTotalSizeLastVersion());
                    stats.setRecentError(BackupStatsLogger.extractEncounteredError());
                    try {
                        serviceManager.callApi(null, (api) -> api.updateSourceStats(serviceManager.getSourceId(), stats));
                    } catch (ApiException e) {
                        log.warn("Failed to updated source stats", e);
                    }
                }
            }
        }
        this.uploadRunning = uploadRunning;
    }

    /**
     * Gets the backup status lines.
     *
     * @return List of status lines describing the current state based on the stats.
     */
    @Override
    public List<StatusLine> status() {
        List<StatusLine> ret = new ArrayList<>();

        if (InstanceFactory.getInstance(LockingMetadataRepository.class).isErrorsDetected()) {
            ret.add(new StatusLine(getClass(), "REPOSITORY_ERROR_DETECTED", "Detected corruption in local metadata repository",
                    null, InstanceFactory.getAdditionalSourceName()));
        }

        if (downloadRunning || uploadRunning || !Strings.isNullOrEmpty(InstanceFactory.getAdditionalSource())) {
            return ret;
        }
        synchronized (scheduledTimes) {
            ret.addAll(scheduledTimes
                    .entrySet()
                    .stream()
                    .map(item ->
                            new StatusLine(getClass(), "SCHEDULED_BACKUP_" + item.getKey(),
                                    String.format("Next run of set %d (%s)",
                                            indexOfSet(item.getKey()), item.getKey()),
                                    item.getValue().getTime(),
                                    formatTimestamp(item.getValue().getTime())))
                    .toList());

            if (statistics != null) {
                ret.add(new StatusLine(getClass(), "REPOSITORY_INFO_FILES",
                        "Total files in repository",
                        statistics.getFiles(),
                        readableNumber(statistics.getFiles())));
                ret.add(new StatusLine(getClass(), "REPOSITORY_INFO_CURRENT_FILES",
                        "Total current files in repository",
                        statistics.getFilesCurrent(),
                        readableNumber(statistics.getFilesCurrent())));
                ret.add(new StatusLine(getClass(), "REPOSITORY_INFO_FILE_VERSIONS",
                        "Total file versions in repository",
                        statistics.getFileVersions(),
                        readableNumber(statistics.getFileVersions())));
                ret.add(new StatusLine(getClass(), "REPOSITORY_INFO_TOTAL_SIZE",
                        "Total file size in repository",
                        statistics.getTotalSize(),
                        readableSize(statistics.getTotalSize())));
                ret.add(new StatusLine(getClass(), "REPOSITORY_INFO_CURRENT_SIZE",
                        "Total current file size in repository",
                        statistics.getCurrentSize(),
                        readableSize(statistics.getCurrentSize())));

                if (statistics.getBlocks() > 0) {
                    ret.add(new StatusLine(getClass(), "REPOSITORY_INFO_TOTAL_BLOCKS",
                            "Total blocks",
                            statistics.getBlocks(),
                            readableNumber(statistics.getBlocks())));
                    ret.add(new StatusLine(getClass(), "REPOSITORY_INFO_TOTAL_BLOCK_PARTS",
                            "Total block parts",
                            statistics.getBlockParts(),
                            readableNumber(statistics.getBlockParts())));
                }

                if (statistics.getTimestamp() != 0) {
                    ret.add(new StatusLine(getClass(), "REPOSITORY_INFO_TIMESTAMP",
                            "Last completed backup",
                            statistics.getTimestamp(),
                            formatTimestamp(statistics.getTimestamp())));
                }

                if (statistics.isNeedActivation()) {
                    ret.add(new StatusLine(getClass(), "SHARE_ACTIVATION_NEEDED",
                            "There are shares that need to be updated"));
                }
            }

            return ret;
        }
    }

    /**
     * Gets the type of the logger.
     *
     * @return The type of the logger
     */
    @Override
    public Type type() {
        return Type.PERMANENT;
    }

    /**
     * Gets the index of a set in the configuration by its id.
     *
     * @return Index of the set in the configuration, or -1 if not found
     */
    private int indexOfSet(String key) {
        for (int i = 0; i < configuration.getSets().size(); i++) {
            if (configuration.getSets().get(i).getId().equals(key)) {
                return i + 1;
            }
        }
        return -1;
    }
}
