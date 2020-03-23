package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

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
