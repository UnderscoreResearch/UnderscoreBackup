package com.underscoreresearch.backup.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.base.Strings;

@Data
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class BackupManifest {
    private String destination;
    private List<String> additionalDestinations;
    private Integer maximumUnsyncedSize;
    private Integer maximumUnsyncedSeconds;

    private Boolean authenticationRequired;
    private Boolean interactiveBackup;
    private Boolean pauseOnBattery;
    private Boolean hideNotifications;
    private Boolean initialSetup;
    private Boolean ignorePermissions;
    private Boolean versionCheck;
    private Boolean automaticUpgrade;
    private BackupTimespan scheduleRandomize;

    private String trimSchedule;
    private String optimizeSchedule;

    public void setLocalLocation(String str) {
        // Intentional Nop
    }

    // If you previously had a UI user and password you get require authentication now.
    public void setConfigUser(String str) {
        setConfigPassword(str);
    }

    public void setConfigPassword(String str) {
        if (!Strings.isNullOrEmpty(str)) {
            authenticationRequired = true;
        }
    }
}
