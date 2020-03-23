package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class BackupFile implements Comparable<BackupFile> {
    private Long lastChanged;
    private Long length;
    private String path;
    private List<BackupLocation> locations;

    @Override
    public int compareTo(@NotNull BackupFile backupFile) {
        int pathCompare = path.compareTo(backupFile.path);
        if (pathCompare != 0)
            return pathCompare;
        if (lastChanged == backupFile.lastChanged)
            return 0;
        if (lastChanged == null)
            return -1;
        if (backupFile.lastChanged == null)
            return 1;
        return lastChanged.compareTo(backupFile.lastChanged);
    }

    @JsonIgnore
    public LocalDateTime toTime() {
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
