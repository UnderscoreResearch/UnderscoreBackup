package com.underscoreresearch.backup.model;

import java.util.Optional;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.jetbrains.annotations.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupRetentionAdditional implements Comparable<BackupRetentionAdditional> {
    private BackupTimespan validAfter;
    private BackupTimespan frequency;

    @Override
    public int compareTo(@NotNull BackupRetentionAdditional o) {
        return -Optional.of(validAfter).orElse(new BackupTimespan()).toInstant()
                .compareTo(Optional.of(o.validAfter).orElse(new BackupTimespan()).toInstant());
    }
}
