package com.underscoreresearch.backup.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.fasterxml.jackson.annotation.JsonInclude;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode(exclude = "status")
public class BackupActiveFile {
    @Setter(AccessLevel.PRIVATE)
    private String path;
    private BackupActiveStatus status;

    public BackupActiveFile(String path) {
        this.path = path;
    }
}
