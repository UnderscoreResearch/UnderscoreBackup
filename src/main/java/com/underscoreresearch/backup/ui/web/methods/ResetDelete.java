package com.underscoreresearch.backup.ui.web.methods;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import com.underscoreresearch.backup.ui.web.ExclusiveImplementation;
import com.underscoreresearch.backup.utils.log.ActivityAppender;
import lombok.extern.slf4j.Slf4j;
import org.takes.Request;
import org.takes.Response;

import java.io.File;

import static com.underscoreresearch.backup.configuration.CommandLineModule.CONFIG_FILE_LOCATION;
import static com.underscoreresearch.backup.configuration.CommandLineModule.KEY_FILE_NAME;
import static com.underscoreresearch.backup.configuration.CommandLineModule.MANIFEST_LOCATION;
import static com.underscoreresearch.backup.io.IOUtils.deleteContents;
import static com.underscoreresearch.backup.io.IOUtils.deleteFile;
import static com.underscoreresearch.backup.utils.log.LogUtil.debug;

/**
 * Web endpoint for resetting the backup system.
 * This class handles requests to delete all configuration and data files to reset the system.
 */
@Slf4j
public class ResetDelete extends BaseWrap {
    /**
     * Creates a new ResetDelete instance.
     */
    public ResetDelete() {
        super(new Implementation());
    }

    /**
     * Executes a task with exception handling.
     * This method runs a task and catches any exceptions that occur.
     *
     * @param task The task to execute
     */
    public static void executeShielded(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            debug(() -> log.debug("Error resetting", e));
        }
    }

    /**
     * Implementation class that handles reset requests.
     */
    private static class Implementation extends ExclusiveImplementation {
        /**
         * Processes a request to reset the system.
         * Deletes all configuration and data files.
         *
         * @param req The HTTP request
         * @return The HTTP response
         * @throws Exception If an error occurs during processing
         */
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

        /**
         * Gets the message to display when the system is busy.
         *
         * @return The busy message
         */
        @Override
        protected String getBusyMessage() {
            return "Deleting configuration";
        }
    }
}
