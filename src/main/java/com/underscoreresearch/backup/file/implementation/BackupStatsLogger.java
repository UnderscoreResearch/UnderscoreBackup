package com.underscoreresearch.backup.file.implementation;

import static com.underscoreresearch.backup.configuration.CommandLineModule.MANIFEST_LOCATION;
import static com.underscoreresearch.backup.utils.LogUtil.formatTimestamp;
import static com.underscoreresearch.backup.utils.LogUtil.readableSize;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Strings;
import com.underscoreresearch.backup.cli.helpers.RepositoryTrimmer;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.service.api.invoker.ApiException;
import com.underscoreresearch.backup.service.api.model.SourceStatsModel;
import com.underscoreresearch.backup.utils.StatusLine;
import com.underscoreresearch.backup.utils.StatusLogger;

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
    @Setter
    private boolean uploadRunning;
    @Getter
    @Setter
    private boolean downloadRunning;

    public BackupStatsLogger(BackupConfiguration configuration, String manifestPath) {
        this.manifestPath = manifestPath;
        this.configuration = configuration;

        statistics = readStatistics();

        setErrorFile(manifestPath);
    }

    private static void setErrorFile(String manifestPath) {
        if (errorFile == null) {
            errorFile = new File(manifestPath, "error.txt");
        }
    }

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

    private static String cleanError(String error) {
        return ERROR_REQUEST.matcher(error).replaceAll("{REDACTED}");
    }

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

    private static void ensureErrorFile() {
        if (errorFile == null) {
            try {
                setErrorFile(InstanceFactory.getInstance(MANIFEST_LOCATION));
            } catch (Exception ignored) {
            }
        }
    }

    private File getStatisticsFile() {
        return new File(manifestPath, "statistics.json");
    }

    public void updateStats(RepositoryTrimmer.Statistics statistics, boolean complete) {
        if (manifestPath != null) {
            if (this.statistics != null) {
                statistics.setNeedActivation(this.statistics.isNeedActivation());
            }
            storeStats(statistics);
            if (complete && configuration.getManifest() != null &&
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
    }

    private void storeStats(RepositoryTrimmer.Statistics statistics) {
        try {
            this.statistics = statistics;
            STATISTICS_WRITER.writeValue(getStatisticsFile(), statistics);
        } catch (IOException e) {
            log.warn("Failed to save backup statistics", e);
        }
    }

    public void setNeedsActivation(boolean needsActivation) {
        if (statistics != null) {
            statistics.setNeedActivation(needsActivation);
            storeStats(statistics);
        }
    }

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

    public void updateScheduledTimes(Map<String, Date> newState) {
        synchronized (scheduledTimes) {
            scheduledTimes.clear();
            if (newState != null) {
                scheduledTimes.putAll(newState);
            }
        }
    }

    @Override
    public void resetStatus() {
    }

    @Override
    public List<StatusLine> status() {
        List<StatusLine> ret = new ArrayList<>();

        if (InstanceFactory.getInstance(MetadataRepository.class).isErrorsDetected()) {
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
                        statistics.getFiles()));
                ret.add(new StatusLine(getClass(), "REPOSITORY_INFO_FILE_VERSIONS",
                        "Total file versions in repository",
                        statistics.getFileVersions()));
                ret.add(new StatusLine(getClass(), "REPOSITORY_INFO_TOTAL_SIZE",
                        "Total file size in repository",
                        statistics.getTotalSize(),
                        readableSize(statistics.getTotalSize())));
                ret.add(new StatusLine(getClass(), "REPOSITORY_INFO_TOTAL_SIZE_LAST_VERSION",
                        "Total file last version size in repository",
                        statistics.getTotalSizeLastVersion(),
                        readableSize(statistics.getTotalSizeLastVersion())));

                if (statistics.getBlocks() > 0) {
                    ret.add(new StatusLine(getClass(), "REPOSITORY_INFO_TOTAL_BLOCKS",
                            "Total blocks",
                            statistics.getBlocks()));
                    ret.add(new StatusLine(getClass(), "REPOSITORY_INFO_TOTAL_BLOCK_PARTS",
                            "Total block parts",
                            statistics.getBlockParts()));
                }

                if (statistics.isNeedActivation()) {
                    ret.add(new StatusLine(getClass(), "SHARE_ACTIVATION_NEEDED",
                            "There are shares that need to be updated"));
                }
            }

            return ret;
        }
    }

    @Override
    public Type type() {
        return Type.PERMANENT;
    }

    private int indexOfSet(String key) {
        for (int i = 0; i < configuration.getSets().size(); i++) {
            if (configuration.getSets().get(i).getId().equals(key)) {
                return i + 1;
            }
        }
        return -1;
    }
}
