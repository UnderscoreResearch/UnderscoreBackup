package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.base.Strings;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents the manifest for the backup system.
 * Contains configuration settings for the backup system, including destinations,
 * synchronization settings, and scheduling options.
 */

@Data
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class BackupManifest {
    /**
     * The primary destination for the backup manifest.
     */
    private String destination;
    
    /**
     * Additional destinations for the backup manifest.
     */
    private List<String> additionalDestinations;
    
    /**
     * Maximum size of unsynced data before forcing a sync.
     */
    private Integer maximumUnsyncedSize;
    
    /**
     * Maximum time in seconds before forcing a sync.
     */
    private Integer maximumUnsyncedSeconds;

    /**
     * Whether authentication is required for the web UI.
     */
    private Boolean authenticationRequired;
    
    /**
     * Whether to run backups interactively.
     */
    private Boolean interactiveBackup;
    
    /**
     * Whether to pause backups when running on battery power.
     */
    private Boolean pauseOnBattery;
    
    /**
     * Whether to hide notifications.
     */
    private Boolean hideNotifications;
    
    /**
     * Whether this is the initial setup.
     */
    private Boolean initialSetup;
    
    /**
     * Whether to ignore file permissions during backup.
     */
    private Boolean ignorePermissions;
    
    /**
     * Whether to check for new versions.
     */
    private Boolean versionCheck;
    
    /**
     * Whether to automatically upgrade to new versions.
     */
    private Boolean automaticUpgrade;
    
    /**
     * Whether to report usage statistics.
     */
    private Boolean reportStats;
    
    /**
     * Time to randomize schedule execution to avoid peak loads.
     */
    private BackupTimespan scheduleRandomize;

    /**
     * Schedule for trimming old backups.
     */
    private String trimSchedule;
    
    /**
     * Schedule for optimizing the backup repository.
     */
    private String optimizeSchedule;

    /**
     * Legacy setter for local location.
     * This is a no-op and exists for backward compatibility.
     * 
     * @param str The local location string
     */
    public void setLocalLocation(String str) {
        // Intentional Nop
    }

    /**
     * Legacy setter for config user.
     * If a config user was previously set, authentication is now required.
     * 
     * @param str The config user string
     */
    public void setConfigUser(String str) {
        setConfigPassword(str);
    }

    /**
     * Legacy setter for config password.
     * If a config password is set, authentication is now required.
     * 
     * @param str The config password string
     */
    public void setConfigPassword(String str) {
        if (!Strings.isNullOrEmpty(str)) {
            authenticationRequired = true;
        }
    }
}
