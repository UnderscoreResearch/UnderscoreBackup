package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents bandwidth limits for the backup system.
 * Contains information about maximum upload and download speeds.
 */

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class BackupLimits {
    /**
     * Maximum upload bandwidth in bytes per second.
     */
    private Long maximumUploadBytesPerSecond;
    
    /**
     * Maximum download bandwidth in bytes per second.
     */
    private Long maximumDownloadBytesPerSecond;
}
