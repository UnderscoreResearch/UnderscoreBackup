package com.underscoreresearch.backup.manifest.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.underscoreresearch.backup.model.BackupActivePath;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PushActivePath {
    private String setId;
    private String path;
    private BackupActivePath activePath;
}
