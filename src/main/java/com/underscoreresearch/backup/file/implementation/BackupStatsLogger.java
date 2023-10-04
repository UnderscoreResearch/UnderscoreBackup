package com.underscoreresearch.backup.file.implementation;

import static com.underscoreresearch.backup.utils.LogUtil.formatTimestamp;
import static com.underscoreresearch.backup.utils.LogUtil.readableSize;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Strings;
import com.underscoreresearch.backup.cli.helpers.RepositoryTrimmer;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.utils.StatusLine;
import com.underscoreresearch.backup.utils.StatusLogger;

@Slf4j
public class BackupStatsLogger implements StatusLogger {
    private static final ObjectReader STATISTICS_READER = MAPPER.readerFor(RepositoryTrimmer.Statistics.class);
    private static final ObjectWriter STATISTICS_WRITER = MAPPER.writerFor(RepositoryTrimmer.Statistics.class);
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
    }

    private File getStatisticsFile() {
        return new File(manifestPath, "statistics.json");
    }

    public void updateStats(RepositoryTrimmer.Statistics statistics) {
        if (manifestPath != null) {
            try {
                if (this.statistics != null) {
                    statistics.setNeedActivation(this.statistics.isNeedActivation());
                }
                STATISTICS_WRITER.writeValue(getStatisticsFile(), statistics);
                this.statistics = statistics;
            } catch (IOException e) {
                log.warn("Failed to save backup statistics", e);
            }
        }
    }

    public void setNeedsActivation(boolean needsActivation) {
        if (statistics != null) {
            statistics.setNeedActivation(needsActivation);
            updateStats(statistics);
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
