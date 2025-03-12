package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;

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
                .share(toBuilder().destination(getDestination().sourceShareDestination(sourceId, shareId)).build())
                .usedDestinations(new HashSet<>())
                .build();
    }
}
