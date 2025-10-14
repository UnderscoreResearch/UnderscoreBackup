package com.underscoreresearch.backup.model;

/**
 * Enum representing the type of backup filter.
 * Defines whether a filter includes or excludes paths.
 */
public enum BackupFilterType {
    /**
     * Filter includes paths.
     */
    INCLUDE,
    
    /**
     * Filter excludes paths.
     */
    EXCLUDE
}
