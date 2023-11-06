package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.cli.web.RepairPost.executeAsyncOperation;

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
                            }
                            log.info("Waiting for rebuild to get to a checkpoint");
                            do {
                                try {
                                    thread.join();
                                } catch (InterruptedException ignored) {
                                }
                            } while (thread.isAlive());
                        }
                        try {
                            metadataRepository.close();
                        } catch (IOException e) {
                            log.error("Failed to close repository", e);
                        }
                    },
                    "OptimizingLogs");
            return messageJson(200, "Optimizing logs");
        }

        @Override
        protected String getBusyMessage() {
            return "Optimizing logs";
        }
    }
}
