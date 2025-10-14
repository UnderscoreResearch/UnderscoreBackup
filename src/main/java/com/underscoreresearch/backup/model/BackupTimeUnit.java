package com.underscoreresearch.backup.model;

/**
 * Enum representing units of time for backup operations.
 * Used in timespans for retention policies, scheduling, and other time-based operations.
 */
public enum BackupTimeUnit {
    /**
     * Seconds unit.
     */
    SECONDS,
    
    /**
     * Minutes unit.
     */
    MINUTES,
    
    /**
     * Hours unit.
     */
    HOURS,
    
    /**
     * Days unit.
     */
    DAYS,
    
    /**
     * Weeks unit.
     */
    WEEKS,
    
    /**
     * Months unit.
     */
    MONTHS,
    
    /**
     * Years unit.
     */
    YEARS,
    
    /**
     * Special unit representing an infinite time.
     */
    FOREVER
}
