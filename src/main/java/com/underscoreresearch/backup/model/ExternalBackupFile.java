package com.underscoreresearch.backup.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonInclude;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class ExternalBackupFile {
    private Long added;
    private Long lastChanged;
    private Long length;
    private String path;

    public ExternalBackupFile(BackupFile file) {
        this.added = file.getAdded();
        this.lastChanged = file.getLastChanged();
        this.length = file.getLength();
        this.path = file.getPath();
    }
}
