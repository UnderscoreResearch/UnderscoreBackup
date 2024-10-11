package com.underscoreresearch.backup.cli.web.service;

import static com.underscoreresearch.backup.manifest.implementation.ServiceManagerImpl.sendApiFailureOn;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

import org.takes.Request;
import org.takes.Response;

import com.underscoreresearch.backup.cli.ui.UIHandler;
import com.underscoreresearch.backup.cli.web.BaseWrap;
import com.underscoreresearch.backup.cli.web.ExclusiveImplementation;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.service.api.model.ReleaseResponse;
import com.underscoreresearch.backup.utils.state.MachineState;

@Slf4j
public class VersionCheckGet extends BaseWrap {
    public VersionCheckGet() {
        super(new Implementation());
    }

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

    private static class Implementation extends ExclusiveImplementation {
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

        @Override
        protected String getBusyMessage() {
            return "Deleting service token";
        }
    }
}
