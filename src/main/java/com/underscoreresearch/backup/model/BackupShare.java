package com.underscoreresearch.backup.model;

import java.util.HashSet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

@Data
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class BackupShare {
    private String name;
    private String targetEmail;
    private BackupDestination destination;
    private BackupFileSpecification contents;

    @JsonIgnore
    public BackupActivatedShare activatedShare(String sourceId, String shareId) {
        return BackupActivatedShare.builder()
                .share(toBuilder().destination(getDestination().shareDestination(sourceId, shareId)).build())
                .usedDestinations(new HashSet<>())
                .build();
    }
}
