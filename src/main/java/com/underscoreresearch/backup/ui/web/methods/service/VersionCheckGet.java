package com.underscoreresearch.backup.ui.web.methods.service;

import com.underscoreresearch.backup.ui.desktop.UIHandler;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import com.underscoreresearch.backup.ui.web.ExclusiveImplementation;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.service.api.model.ReleaseResponse;
import com.underscoreresearch.backup.machinestate.MachineState;
import lombok.extern.slf4j.Slf4j;
import org.takes.Request;
import org.takes.Response;

import java.io.IOException;

/**
 * Endpoint for checking for new versions.
 * This class handles checking for new versions of the backup software and optionally upgrading.
 */
@Slf4j
public class VersionCheckGet extends BaseWrap {
    
    /**
     * Constructor for the VersionCheckGet endpoint.
     */
    public VersionCheckGet() {
        super(new Implementation());
    }

    /**
     * Checks for a new version of the software and optionally upgrades.
     * 
     * @param forceCheck Whether to force a check even if the cache is still valid
     * @return The release response if a new version is available, null otherwise
     */
    public static ReleaseResponse checkNewVersion(boolean forceCheck) {
        ReleaseResponse version = InstanceFactory.getInstance(ServiceManager.class).checkVersion(forceCheck);
        if (version != null) {
            UIHandler.displayInfoMessage(String.format("New version %s available:\n\n%s",
                    version.getVersion(), version.getName()));
            BackupConfiguration configuration = InstanceFactory.getInstance(BackupConfiguration.class);
            if (configuration.getManifest().getAutomaticUpgrade() == null || configuration.getManifest().getAutomaticUpgrade()) {
                MachineState state = InstanceFactory.getInstance(MachineState.class);
                if (state.supportsAutomaticUpgrade()) {
                    try {
                        log.info("Upgrading to version {}", version.getVersion());
                        state.upgrade(version);
                    } catch (IOException e) {
                        log.warn("Failed to upgrade to version {}", version.getVersion(), e);
                    }
                }
            }
        }
        return version;
    }

    /**
     * Implementation of the exclusive implementation that handles the HTTP request.
     */
    private static class Implementation extends ExclusiveImplementation {
        
        /**
         * Processes the HTTP request to check for a new version.
         * 
         * @param req The HTTP request
         * @return Response indicating whether a new version is available
         * @throws Exception If there's an error processing the request
         */
        @Override
        public Response actualAct(Request req) throws Exception {
            ReleaseResponse response = checkNewVersion(true);
            if (response != null) {
                return messageJson(200, String.format("New version available %s", response.getVersion()));
            } else {
                UIHandler.displayInfoMessage("No updates available");
                return messageJson(200, "No updates available");
            }
        }

        /**
         * Returns the message to display when the system is busy.
         * 
         * @return The busy message
         */
        @Override
        protected String getBusyMessage() {
            return "Deleting service token";
        }
    }
}
