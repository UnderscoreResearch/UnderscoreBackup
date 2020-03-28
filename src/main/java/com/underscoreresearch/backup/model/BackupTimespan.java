package com.underscoreresearch.backup.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupTimespan {
    private static final LocalDateTime IMMEDIATE = LocalDateTime.MIN;
    private long duration;
    private BackupTimeUnit unit;

    @JsonIgnore
    public LocalDateTime toTime() {
        return toTime(LocalDateTime.now());
    }

    @JsonIgnore
    public Instant toInstant() {
        LocalDateTime time = toTime(LocalDateTime.now());
        if (IMMEDIATE.equals(time)) {
            return Instant.EPOCH;
        }
        return time.toInstant(OffsetDateTime.now().getOffset());
    }

    @JsonIgnore
    public boolean isImmediate() {
        return duration == 0;
    }

    @JsonIgnore
    public long toEpochMilli() {
        return toInstant().toEpochMilli();
    }

    @JsonIgnore
    public LocalDateTime toTime(LocalDateTime now) {
        if (duration == 0) {
            return IMMEDIATE;
        }
        switch (unit) {
            case YEARS:
                return now.minus(Period.ofYears((int) duration));
            case MONTHS:
                return now.minus(Period.ofMonths((int) duration));
            case WEEKS:
                return now.minus(Period.ofWeeks((int) duration));
            case DAYS:
                return now.minus(Period.ofDays((int) duration));
            case HOURS:
                return now.minus(duration, ChronoUnit.HOURS);
            case MINUTES:
                return now.minus(duration, ChronoUnit.MINUTES);
            case SECONDS:
                return now.minus(duration, ChronoUnit.SECONDS);
            default:
                throw new IllegalArgumentException("Unknown time unit: " + unit);
        }
    }
}