package com.underscoreresearch.backup.model;

import java.time.Duration;
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
    private static final LocalDateTime IMMEDIATE = LocalDateTime.of(3000, 1, 1, 0, 0);
    private static final LocalDateTime FOREVER = LocalDateTime.MIN;
    private long duration;
    private BackupTimeUnit unit;

    @JsonIgnore
    public LocalDateTime toTime() {
        return toTime(LocalDateTime.now());
    }

    @JsonIgnore
    public Instant toInstant() {
        LocalDateTime time = toTime(LocalDateTime.now());
        if (FOREVER.equals(time)) {
            return Instant.EPOCH;
        }
        return time.toInstant(OffsetDateTime.now().getOffset());
    }

    @JsonIgnore
    public boolean isImmediate() {
        if (unit == BackupTimeUnit.FOREVER)
            return false;
        else
            return duration == 0;
    }

    @JsonIgnore
    public boolean isForever() {
        return unit == BackupTimeUnit.FOREVER;
    }

    @JsonIgnore
    public long toEpochMilli() {
        return toInstant().toEpochMilli();
    }

    @JsonIgnore
    public LocalDateTime toTime(LocalDateTime now) {
        if (unit == BackupTimeUnit.FOREVER) {
            return FOREVER;
        }
        if (duration == 0) {
            return IMMEDIATE;
        }

        return switch (unit) {
            case YEARS -> now.minus(Period.ofYears((int) duration));
            case MONTHS -> now.minus(Period.ofMonths((int) duration));
            case WEEKS -> now.minus(Period.ofWeeks((int) duration));
            case DAYS -> now.minus(Period.ofDays((int) duration));
            case HOURS -> now.minus(duration, ChronoUnit.HOURS);
            case MINUTES -> now.minus(duration, ChronoUnit.MINUTES);
            case SECONDS -> now.minus(duration, ChronoUnit.SECONDS);
            default -> throw new IllegalArgumentException("Unknown time unit: " + unit);
        };
    }

    public Duration toDuration() {
        if (unit == null)
            return null;

        return switch (unit) {
            case FOREVER, YEARS, MONTHS -> null;
            case WEEKS -> Duration.ofDays(7 * duration);
            case DAYS -> Duration.ofDays(duration);
            case HOURS -> Duration.ofHours(duration);
            case MINUTES -> Duration.ofMinutes(duration);
            case SECONDS -> Duration.ofSeconds(duration);
            default -> throw new IllegalArgumentException("Unknown time unit: " + unit);
        };
    }
}