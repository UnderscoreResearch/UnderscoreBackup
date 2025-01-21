package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.cli.helpers.RepositoryTrimmer.TRIMMING_REPOSITORY_TASK;
import static com.underscoreresearch.backup.cli.web.RepairPost.executeAsyncOperation;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

import org.takes.Request;
import org.takes.Response;

import com.underscoreresearch.backup.cli.helpers.RepositoryTrimmer;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.implementation.BackupStatsLogger;
import com.underscoreresearch.backup.manifest.ManifestManager;

@Slf4j
public class TrimPost extends BaseWrap {
    public TrimPost() {
        super(new Implementation());
    }

    private static class Implementation extends ExclusiveImplementation {
        @Override
        public Response actualAct(Request req) {
            InstanceFactory.reloadConfiguration(InstanceFactory.getAdditionalSource(),
                    InstanceFactory.getAdditionalSourceName(), null);

            RepositoryTrimmer trimmer = InstanceFactory.getInstance(RepositoryTrimmer.class);
            ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);
            MetadataRepository metadataRepository = InstanceFactory.getInstance(MetadataRepository.class);
            executeAsyncOperation(() -> {
                        try {
                            RepositoryTrimmer.Statistics statistics = trimmer.trimRepository(null);
                            InstanceFactory.getInstance(BackupStatsLogger.class).updateStats(statistics);
                        } catch (IOException e) {
                            log.error("Failed to trim repository", e);
                        }
                    },
                    (thread, completed) -> {
                        try {
                            manifestManager.shutdown();
                            metadataRepository.close();
                        } catch (IOException e) {
                            log.error("Failed to close repository", e);
                        }
                    },
                    TRIMMING_REPOSITORY_TASK,
                    "TrimRepository");
            return messageJson(200, "Optimizing repository");
        }

        @Override
        protected String getBusyMessage() {
            return "Optimizing repository";
        }
    }
}
