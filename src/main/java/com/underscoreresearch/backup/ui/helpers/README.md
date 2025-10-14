# CLI Helpers Package

This package contains helper classes that support the command-line interface commands.

## Files

- **BlockValidator.java**: Validates the integrity of blocks in the backup repository.
- **DestinationBlockProcessor.java**: Processes blocks in backup destinations.
- **DirectoryCache.java**: Caches directory information for improved performance.
- **RepositoryTrimmer.java**: Handles trimming of unused data from the repository.
- **RestoreDirectoryPermissions.java**: Manages directory permissions during restore operations.
- **RestoreExecutor.java**: Executes the restore process.

These helper classes provide common functionality used by multiple commands, abstracting complex operations into reusable components.
