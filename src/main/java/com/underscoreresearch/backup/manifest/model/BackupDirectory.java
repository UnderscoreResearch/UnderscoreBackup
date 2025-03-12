package com.underscoreresearch.backup.manifest.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.NavigableSet;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BackupDirectory {
    private String path;
    private Long added;
    private String permissions;
    private NavigableSet<String> files;
    private Long deleted;
}
