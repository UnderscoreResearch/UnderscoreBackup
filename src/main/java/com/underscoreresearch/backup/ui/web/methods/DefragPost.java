package com.underscoreresearch.backup.ui.web.methods;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import com.underscoreresearch.backup.ui.web.ExclusiveImplementation;
import lombok.extern.slf4j.Slf4j;
import org.takes.Request;
import org.takes.Response;

import java.io.IOException;

import static com.underscoreresearch.backup.ui.web.methods.RepairPost.executeAsyncOperation;
import static com.underscoreresearch.backup.file.implementation.LockingMetadataRepository.COMPACT_TASK;

/**
 * Web endpoint for defragmenting the backup repository.
 * This class handles requests to compact the metadata repository to improve performance.
 */
@Slf4j
public class DefragPost extends BaseWrap {
    /**
     * Creates a new DefragPost instance.
     */
    public DefragPost() {
        super(new Implementation());
    }

    /**
     * Implementation class that handles defragmentation requests.
     */
    private static class Implementation extends ExclusiveImplementation {
        /**
         * Processes a request to defragment the repository.
         * Initiates the compaction process in a separate thread.
         *
         * @param req The HTTP request
         * @return The HTTP response
         */
        @Override
        public Response actualAct(Request req) {
            InstanceFactory.reloadConfiguration(InstanceFactory.getAdditionalSource(),
                    InstanceFactory.getAdditionalSourceName(), null);

            MetadataRepository metadataRepository = InstanceFactory.getInstance(MetadataRepository.class);
            executeAsyncOperation(() -> {
                        try {
                            metadataRepository.compact();
                        } catch (IOException e) {
                            log.error("Failed to defrag repository", e);
                        }
                    },
                    (thread, completed) -> {
                        try {
                            metadataRepository.close();
                        } catch (IOException e) {
                            log.error("Failed to close repository", e);
                        }
                    },
                    COMPACT_TASK,
                    "DefragRepository");
            return messageJson(200, "Defragging repository");
        }

        /**
         * Gets the message to display when the system is busy.
         *
         * @return The busy message
         */
        @Override
        protected String getBusyMessage() {
            return "Defragging repository";
        }
    }
}
