package com.underscoreresearch.backup.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Optional;

/**
 * Represents an additional retention policy for older files.
 * Contains information about when the policy applies and how frequently to keep versions.
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupRetentionAdditional implements Comparable<BackupRetentionAdditional> {
    /**
     * The time after which this retention policy applies.
     */
    private BackupTimespan validAfter;
    
    /**
     * The frequency for keeping versions.
     */
    private BackupTimespan frequency;

    /**
     * Compares this retention policy to another for sorting.
     * Policies are sorted in reverse order of validAfter time.
     * 
     * @param o The retention policy to compare to
     * @return A negative integer, zero, or a positive integer as this policy is greater than, equal to, or less than the specified policy
     */
    @Override
    public int compareTo(BackupRetentionAdditional o) {
        return -Optional.of(validAfter).orElse(new BackupTimespan()).toInstant()
                .compareTo(Optional.of(o.validAfter).orElse(new BackupTimespan()).toInstant());
    }
}
