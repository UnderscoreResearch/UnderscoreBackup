package com.underscoreresearch.backup.ui.web.methods;

import com.underscoreresearch.backup.ui.helpers.BlockValidator;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import com.underscoreresearch.backup.ui.web.ExclusiveImplementation;
import lombok.extern.slf4j.Slf4j;
import org.takes.Request;
import org.takes.Response;

import java.io.IOException;

import static com.underscoreresearch.backup.ui.helpers.BlockValidator.VALIDATE_BLOCKS_TASK;
import static com.underscoreresearch.backup.ui.web.methods.RepairPost.executeAsyncOperation;

/**
 * Web endpoint for validating backup blocks.
 * This class handles requests to validate the integrity of backup blocks and storage.
 */
@Slf4j
public class ValidateBlocksPost extends BaseWrap {
    /**
     * Creates a new ValidateBlocksPost instance.
     */
    public ValidateBlocksPost() {
        super(new Implementation());
    }

    /**
     * Implementation class that handles block validation requests.
     */
    private static class Implementation extends ExclusiveImplementation {
        /**
         * Processes a request to validate blocks.
         * Initiates the block validation process in a separate thread.
         *
         * @param req The HTTP request
         * @return The HTTP response
         */
        @Override
        public Response actualAct(Request req) {
            InstanceFactory.reloadConfiguration(InstanceFactory.getAdditionalSource(),
                    InstanceFactory.getAdditionalSourceName(), null);

            BlockValidator validator = InstanceFactory.getInstance(BlockValidator.class);
            ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);
            MetadataRepository metadataRepository = InstanceFactory.getInstance(MetadataRepository.class);
            executeAsyncOperation(() -> {
                        try {
                            validator.validateBlocks(true, null);
                            validator.validateStorage(true, null);
                        } catch (IOException e) {
                            log.error("Failed to validate blocks", e);
                        }
                    },
                    (thread, completed) -> {
                        try {
                            manifestManager.shutdown();
                            metadataRepository.close();
                        } catch (IOException e) {
                            log.error("Failed to validate blocks", e);
                        }
                    },
                    VALIDATE_BLOCKS_TASK,
                    "ValidateBlocks");
            return messageJson(200, "Validating block storage");
        }

        /**
         * Gets the message to display when the system is busy.
         *
         * @return The busy message
         */
        @Override
        protected String getBusyMessage() {
            return "Validating block storage";
        }
    }
}
