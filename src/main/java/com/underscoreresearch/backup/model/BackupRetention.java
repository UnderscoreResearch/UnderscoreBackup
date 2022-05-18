package com.underscoreresearch.backup.model;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.TreeSet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupRetention {
    private BackupTimespan retainDeleted;
    private BackupTimespan defaultFrequency;
    private TreeSet<BackupRetentionAdditional> older;

    private Integer maximumVersions;

    @JsonIgnore
    public boolean deletedImmediate() {
        BackupTimespan deletedTimespan = Optional.ofNullable(retainDeleted).orElse(new BackupTimespan());
        return deletedTimespan.isImmediate();
    }

    public boolean keepFile(BackupFile file, BackupFile previousFile, boolean deleted) {
        BackupTimespan deletedTimespan = Optional.ofNullable(retainDeleted).orElse(new BackupTimespan());
        if (deleted) {
            if (deletedTimespan.isImmediate())
                return false;

            if (!deletedTimespan.isForever()
                    && file.deletedToTime().isBefore(deletedTimespan.toTime())) {
                return false;
            }
        }

        if (previousFile == null) {
            return true;
        }

        LocalDateTime fileInstant = file.addedToTime();

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

        if (!frequency.isImmediate() && (frequency.isForever() || frequency.toTime(previousFile.addedToTime()).isBefore(fileInstant))) {
            return false;
        }
        return true;
    }
}