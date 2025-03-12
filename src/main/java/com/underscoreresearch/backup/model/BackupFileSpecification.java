package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BackupFileSpecification extends BackupFileSelection {

    @JsonCreator
    @Builder
    public BackupFileSpecification(@JsonProperty("roots") List<BackupSetRoot> roots,
                                   @JsonProperty("exclusions") List<String> exclusions) {
        super(roots, exclusions);
    }
}
