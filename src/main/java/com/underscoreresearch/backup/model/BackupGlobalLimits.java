package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Represents global limits for the backup system.
 * Extends BackupLimits to add thread limits in addition to bandwidth limits.
 */

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class BackupGlobalLimits extends BackupLimits {
    /**
     * Maximum number of threads to use for uploads.
     */
    private Integer maximumUploadThreads;
    
    /**
     * Maximum number of threads to use for downloads.
     */
    private Integer maximumDownloadThreads;

    /**
     * Constructor that initializes a BackupGlobalLimits with bandwidth and thread limits.
     * 
     * @param maximumUploadBytesPerSecond The maximum upload bandwidth in bytes per second
     * @param maximumDownloadBytesPerSecond The maximum download bandwidth in bytes per second
     * @param maximumUploadThreads The maximum number of upload threads
     * @param maximumDownloadThreads The maximum number of download threads
     */
    public BackupGlobalLimits(Long maximumUploadBytesPerSecond, Long maximumDownloadBytesPerSecond,
                              Integer maximumUploadThreads, Integer maximumDownloadThreads) {
        super(maximumUploadBytesPerSecond, maximumDownloadBytesPerSecond);
        this.maximumUploadThreads = maximumUploadThreads;
        this.maximumDownloadThreads = maximumDownloadThreads;
    }
}
