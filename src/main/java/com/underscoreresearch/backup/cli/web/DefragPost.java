package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.cli.web.RepairPost.executeAsyncOperation;
import static com.underscoreresearch.backup.file.implementation.LockingMetadataRepository.COMPACT_TASK;

import java.io.IOException;

import com.underscoreresearch.backup.cli.commands.InteractiveCommand;
import lombok.extern.slf4j.Slf4j;

import org.takes.Request;
import org.takes.Response;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;

@Slf4j
public class DefragPost extends BaseWrap {
    public DefragPost() {
        super(new Implementation());
    }

    private static class Implementation extends ExclusiveImplementation {
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

        @Override
        protected String getBusyMessage() {
            return "Defragging repository";
        }
    }
}
