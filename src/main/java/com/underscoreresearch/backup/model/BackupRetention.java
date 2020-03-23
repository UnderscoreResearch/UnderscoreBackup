package com.underscoreresearch.backup.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.TreeSet;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupRetention {
    private BackupTimespan retainDeleted;
    private BackupTimespan defaultFrequency;
    private TreeSet<BackupRetentionAdditional> older;

    public boolean keepFile(BackupFile file, BackupFile previousFile, boolean deleted) {
        LocalDateTime fileInstant = file.toTime();

        BackupTimespan deletedTimespan = Optional.ofNullable(retainDeleted).orElse(new BackupTimespan());
        if (deleted && (deletedTimespan.isImmediate() || fileInstant.isBefore(deletedTimespan.toTime()))) {
            return false;
        }

        if (previousFile == null) {
            return true;
        }

        BackupTimespan frequency = defaultFrequency;
        if (older != null) {
            for (BackupRetentionAdditional ta : older) {
                if (fileInstant.isBefore(ta.getValidAfter().toTime()))
                    frequency = ta.getFrequency();
                else
                    break;
            }
        }

        if (frequency == null)
            frequency = new BackupTimespan();

        if (frequency.isImmediate() || frequency.toTime(previousFile.toTime()).isBefore(fileInstant)) {
            return false;
        }
        return true;
    }
}