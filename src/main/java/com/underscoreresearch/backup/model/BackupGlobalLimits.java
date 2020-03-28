package com.underscoreresearch.backup.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import com.fasterxml.jackson.annotation.JsonInclude;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class BackupGlobalLimits extends BackupLimits {
    private Integer maximumUploadThreads;
    private Integer maximumDownloadThreads;

    public BackupGlobalLimits(Long maximumUploadBytesPerSecond, Long maximumDownloadBytesPerSecond,
                              Integer maximumUploadThreads, Integer maximumDownloadThreads) {
        super(maximumUploadBytesPerSecond, maximumDownloadBytesPerSecond);
        this.maximumUploadThreads = maximumUploadThreads;
        this.maximumDownloadThreads = maximumDownloadThreads;
    }
}
