package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.configuration.CommandLineModule.CONFIG_FILE_LOCATION;
import static com.underscoreresearch.backup.configuration.CommandLineModule.KEY_FILE_NAME;
import static com.underscoreresearch.backup.configuration.CommandLineModule.MANIFEST_LOCATION;
import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.io.File;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import org.takes.Request;
import org.takes.Response;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.utils.ActivityAppender;

@Slf4j
public class ResetDelete extends JsonWrap {
    private static ObjectWriter WRITER = MAPPER.writerFor(KeyResponse.class);

    public ResetDelete() {
        super(new Implementation());
    }

    public static void executeShielded(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            debug(() -> log.debug("Error resetting", e));
        }
    }

    public static void deleteContents(File file) {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                if (!child.getName().startsWith(".")) {
                    if (child.isDirectory()) {
                        deleteContents(child);
                    }
                    child.delete();
                }
            }
        }
    }

    @Data
    @AllArgsConstructor
    public static class KeyResponse {
        private Boolean specified;
    }

    private static class Implementation extends BaseImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {
            executeShielded(() -> new File(InstanceFactory.getInstance(CONFIG_FILE_LOCATION)).delete());
            executeShielded(() -> new File(InstanceFactory.getInstance(KEY_FILE_NAME)).delete());

            executeShielded(() -> InstanceFactory.reloadConfiguration(null, null));
            executeShielded(() -> deleteContents(new File(InstanceFactory.getInstance(MANIFEST_LOCATION))));

            ActivityAppender.resetLogging();

            return messageJson(200, "Ok");
        }
    }
}
