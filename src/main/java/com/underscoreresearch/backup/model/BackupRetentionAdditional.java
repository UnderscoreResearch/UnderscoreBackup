package com.underscoreresearch.backup.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Optional;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupRetentionAdditional implements Comparable<BackupRetentionAdditional> {
    private BackupTimespan validAfter;
    private BackupTimespan frequency;

    @Override
    public int compareTo(BackupRetentionAdditional o) {
        return -Optional.of(validAfter).orElse(new BackupTimespan()).toInstant()
                .compareTo(Optional.of(o.validAfter).orElse(new BackupTimespan()).toInstant());
    }
}
