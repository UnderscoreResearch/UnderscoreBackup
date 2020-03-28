package com.underscoreresearch.backup.manifest.model;

import java.util.NavigableSet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonInclude;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BackupDirectory {
    private String path;
    private Long timestamp;
    private NavigableSet<String> files;
}
