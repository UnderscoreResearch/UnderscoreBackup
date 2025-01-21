package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.cli.helpers.BlockValidator.VALIDATE_BLOCKS_TASK;
import static com.underscoreresearch.backup.cli.web.RepairPost.executeAsyncOperation;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

import org.takes.Request;
import org.takes.Response;

import com.underscoreresearch.backup.cli.helpers.BlockValidator;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.ManifestManager;

@Slf4j
public class ValidateBlocksPost extends BaseWrap {
    public ValidateBlocksPost() {
        super(new Implementation());
    }

    private static class Implementation extends ExclusiveImplementation {
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

        @Override
        protected String getBusyMessage() {
            return "Validating block storage";
        }
    }
}
