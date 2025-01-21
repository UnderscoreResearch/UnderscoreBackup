package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.cli.web.RepairPost.executeAsyncOperation;
import static com.underscoreresearch.backup.manifest.implementation.ManifestManagerImpl.OPTIMIZING_LOG_OPERATION;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

import org.takes.Request;
import org.takes.Response;

import com.google.common.base.Strings;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.ManifestManager;

@Slf4j
public class OptimizePost extends BaseWrap {
    public OptimizePost() {
        super(new Implementation());
    }

    private static class Implementation extends ExclusiveImplementation {
        @Override
        public Response actualAct(Request req) {
            if (!Strings.isNullOrEmpty(InstanceFactory.getAdditionalSource())) {
                return messageJson(400, "Cannot optimize additional source");
            }
            InstanceFactory.reloadConfiguration(InstanceFactory.getAdditionalSource(),
                    InstanceFactory.getAdditionalSourceName(), null);

            ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);
            MetadataRepository metadataRepository = InstanceFactory.getInstance(MetadataRepository.class);
            LogConsumer logConsumer = InstanceFactory.getInstance(LogConsumer.class);
            executeAsyncOperation(() -> {
                        try {
                            manifestManager.optimizeLog(metadataRepository, logConsumer, false);
                        } catch (IOException e) {
                            log.error("Failed to optimize logs", e);
                        }
                    },
                    (thread, completed) -> {
                        try {
                            manifestManager.shutdown();
                        } catch (IOException e) {
                            log.error("Failed to cancel optimize", e);
                        }
                        if (!completed && manifestManager.isBusy()) {
                            try {
                                thread.join(1000);
                                if (!thread.isAlive())
                                    return;
                            } catch (InterruptedException ignored) {
                                Thread.currentThread().interrupt();
                            }
                            log.info("Waiting for rebuild to get to a checkpoint");
                            do {
                                try {
                                    thread.join();
                                } catch (InterruptedException ignored) {
                                    Thread.currentThread().interrupt();
                                }
                            } while (thread.isAlive());
                        }
                        try {
                            metadataRepository.close();
                        } catch (IOException e) {
                            log.error("Failed to close repository", e);
                        }
                    },
                    OPTIMIZING_LOG_OPERATION,
                    "OptimizingLogs");
            return messageJson(200, "Optimizing logs");
        }

        @Override
        protected String getBusyMessage() {
            return "Optimizing logs";
        }
    }
}
