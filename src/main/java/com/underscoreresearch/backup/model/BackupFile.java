package com.underscoreresearch.backup.model;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class BackupFile implements Comparable<BackupFile> {
    private Long added;
    private Long deleted;
    private Long lastChanged;
    private Long length;
    private String path;
    private List<BackupLocation> locations;

    @Override
    public int compareTo(BackupFile backupFile) {
        int pathCompare = path.compareTo(backupFile.path);
        if (pathCompare != 0)
            return pathCompare;
        if (added == backupFile.added)
            return 0;
        if (added == null)
            return -1;
        if (backupFile.added == null)
            return 1;
        return added.compareTo(backupFile.added);
    }

    @JsonIgnore
    public LocalDateTime addedToTime() {
        if (added != null) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(added), OffsetDateTime.now().getOffset());
        }
        return null;
    }

    @JsonIgnore
    public LocalDateTime deletedToTime() {
        if (deleted != null) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(deleted), OffsetDateTime.now().getOffset());
        }
        return null;
    }

    @JsonIgnore
    public LocalDateTime lastChangedToTime() {
        if (lastChanged != null) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(lastChanged), OffsetDateTime.now().getOffset());
        }
        return null;
    }

    @JsonIgnore
    public boolean isDirectory() {
        return path.endsWith(PATH_SEPARATOR);
    }
}
