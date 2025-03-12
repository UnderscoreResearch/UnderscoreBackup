package com.underscoreresearch.backup.cli.web;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.utils.ActivityAppender;
import lombok.extern.slf4j.Slf4j;
import org.takes.Request;
import org.takes.Response;

import java.io.File;

import static com.underscoreresearch.backup.configuration.CommandLineModule.CONFIG_FILE_LOCATION;
import static com.underscoreresearch.backup.configuration.CommandLineModule.KEY_FILE_NAME;
import static com.underscoreresearch.backup.configuration.CommandLineModule.MANIFEST_LOCATION;
import static com.underscoreresearch.backup.io.IOUtils.deleteContents;
import static com.underscoreresearch.backup.io.IOUtils.deleteFile;
import static com.underscoreresearch.backup.utils.LogUtil.debug;

@Slf4j
public class ResetDelete extends BaseWrap {
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

    private static class Implementation extends ExclusiveImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {
            InstanceFactory.reloadConfiguration(null);
            executeShielded(() -> InstanceFactory.getInstance(ServiceManager.class).reset());
            executeShielded(() -> deleteFile(new File(InstanceFactory.getInstance(CONFIG_FILE_LOCATION))));
            executeShielded(() -> new File(InstanceFactory.getInstance(KEY_FILE_NAME)));

            executeShielded(() -> InstanceFactory.reloadConfiguration(null));
            executeShielded(() -> deleteContents(new File(InstanceFactory.getInstance(MANIFEST_LOCATION))));

            ActivityAppender.resetLogging();

            return messageJson(200, "Ok");
        }

        @Override
        protected String getBusyMessage() {
            return "Deleting configuration";
        }
    }
}
