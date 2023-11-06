package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.cli.web.RepairPost.executeAsyncOperation;

import java.io.IOException;

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
                    "DefragRepository");
            return messageJson(200, "Defragging repository");
        }

        @Override
        protected String getBusyMessage() {
            return "Defragging repository";
        }
    }
}
