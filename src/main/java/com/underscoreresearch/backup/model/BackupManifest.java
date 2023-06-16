package com.underscoreresearch.backup.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonInclude;

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

    private String configUser;
    private String configPassword;
    private Boolean interactiveBackup;
    private Boolean pauseOnBattery;
    private Boolean hideNotifications;
    private Boolean initialSetup;
    private Boolean ignorePermissions;
    private Boolean versionCheck;
    private BackupTimespan scheduleRandomize;

    private String trimSchedule;
    private String optimizeSchedule;

    private void setLocalLocation(String str) {
        // Intentional Nop
    }
}
