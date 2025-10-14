package com.underscoreresearch.backup.ui.web.methods;

import com.underscoreresearch.backup.ui.helpers.RepositoryTrimmer;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.implementation.BackupStatsLogger;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import com.underscoreresearch.backup.ui.web.ExclusiveImplementation;
import lombok.extern.slf4j.Slf4j;
import org.takes.Request;
import org.takes.Response;

import java.io.IOException;

import static com.underscoreresearch.backup.ui.helpers.RepositoryTrimmer.TRIMMING_REPOSITORY_TASK;
import static com.underscoreresearch.backup.ui.web.methods.RepairPost.executeAsyncOperation;

/**
 * Web endpoint for trimming the backup repository.
 * This class handles requests to optimize the repository by removing unused blocks
 * and cleaning up old data.
 */
@Slf4j
public class TrimPost extends BaseWrap {
    /**
     * Creates a new TrimPost instance.
     */
    public TrimPost() {
        super(new Implementation());
    }

    /**
     * Implementation class that handles repository trimming requests.
     */
    private static class Implementation extends ExclusiveImplementation {
        /**
         * Processes a request to trim the repository.
         * Initiates the repository trimming process in a separate thread.
         *
         * @param req The HTTP request
         * @return The HTTP response
         */
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

        /**
         * Gets the message to display when the system is busy.
         *
         * @return The busy message
         */
        @Override
        protected String getBusyMessage() {
            return "Optimizing repository";
        }
    }
}
