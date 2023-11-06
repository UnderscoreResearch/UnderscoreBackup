package com.underscoreresearch.backup.model;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonInclude;

@NoArgsConstructor
@Builder(toBuilder = true)
@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BackupPendingSet {
    private String setId;
    private String schedule;
    private Date scheduledAt;
}
