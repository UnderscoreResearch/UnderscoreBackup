package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class ExternalBackupFile {
    private Long added;
    private Long lastChanged;
    private Long deleted;
    private Long length;
    private String path;

    public ExternalBackupFile(BackupFile file) {
        this.added = file.getAdded();
        this.lastChanged = file.getLastChanged();
        this.deleted = file.getDeleted();
        this.length = file.getLength();
        this.path = file.getPath();
    }
}
