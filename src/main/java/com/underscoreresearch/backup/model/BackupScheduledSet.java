package com.underscoreresearch.backup.model;

import java.util.Date;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BackupScheduledSet {
    @JsonIgnore
    @Getter
    @Setter
    private String setId;
    @Getter
    @Setter
    private String schedule;
    private long scheduledMillis;

    public BackupScheduledSet(String setId, String schedule, Date scheduledAt) {
        this.setId = setId;
        this.schedule = schedule;
        this.scheduledMillis = scheduledAt.getTime();
    }

    @JsonIgnore
    public Date getScheduledAt() {
        return new Date(scheduledMillis);
    }

    @JsonIgnore
    public void setScheduledAt(Date scheduledAt) {
        scheduledAt.getTime();
    }
}
