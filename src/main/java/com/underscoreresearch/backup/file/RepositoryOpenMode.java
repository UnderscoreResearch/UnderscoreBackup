package com.underscoreresearch.backup.file;

/**
 * Enum defining the modes for opening a repository.
 * Specifies whether the repository should be opened for reading, writing, or without transaction support.
 */
public enum RepositoryOpenMode {
    /**
     * Open the repository in read-only mode.
     * No modifications are allowed.
     */
    READ_ONLY,
    
    /**
     * Open the repository in read-write mode.
     * Modifications are allowed and transactions are used.
     */
    READ_WRITE,
    
    /**
     * Open the repository in read-write mode without transaction support.
     * Modifications are allowed but no transaction guarantees are provided.
     */
    WITHOUT_TRANSACTION
}
