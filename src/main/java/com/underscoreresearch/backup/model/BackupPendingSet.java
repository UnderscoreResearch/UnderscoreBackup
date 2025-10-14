package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Represents a backup set that is pending execution.
 * Contains information about a backup set that is scheduled to run,
 * including its ID, schedule, and scheduled time.
 */

@NoArgsConstructor
@Builder(toBuilder = true)
@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BackupPendingSet {
    /**
     * The ID of the backup set that is pending.
     */
    private String setId;
    
    /**
     * The schedule for the backup set.
     */
    private String schedule;
    
    /**
     * The time when the backup set is scheduled to run.
     */
    private Date scheduledAt;
}
