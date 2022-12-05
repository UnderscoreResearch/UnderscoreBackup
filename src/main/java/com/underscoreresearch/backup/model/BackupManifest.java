package com.underscoreresearch.backup.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonInclude;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class BackupManifest {
    private String destination;
    private Integer maximumUnsyncedSize;
    private Integer maximumUnsyncedSeconds;

    private String configUser;
    private String configPassword;
    private Boolean interactiveBackup;
    private Boolean pauseOnBattery;
    private Boolean hideNotifications;
    private BackupTimespan scheduleRandomize;

    private String trimSchedule;
    private String optimizeSchedule;

    private void setLocalLocation(String str) {
        // Intentional Nop
    }
}
