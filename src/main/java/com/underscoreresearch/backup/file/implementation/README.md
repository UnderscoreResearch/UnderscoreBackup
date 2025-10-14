# File Implementation Package

This package contains concrete implementations of the file interfaces defined in the parent package.

## Files

- **AclPermissionManager.java**: ACL-based file permission manager.
- **BackupStatsLogger.java**: Logger for backup statistics.
- **ContinuousBackupImpl.java**: Implementation of continuous backup functionality.
- **FileChangeWatcherImpl.java**: Implementation of file change watching.
- **FileConsumerImpl.java**: Implementation of file data consumption.
- **FileScannerImpl.java**: Implementation of file system scanning.
- **FileSystemAccessImpl.java**: Implementation of file system access.
- **LockingMetadataRepository.java**: Thread-safe metadata repository implementation.
- **LogFileRepositoryImpl.java**: Implementation of the log file repository.
- **MapdbMetadataRepositoryStorage.java**: MapDB-based metadata repository storage.
- **NullRepository.java**: Null implementation of repository interfaces.
- **PermissionFileSystemAccess.java**: Permission-aware file system access.
- **PosixPermissionManager.java**: POSIX-based file permission manager.
- **RepositoryUpgrader.java**: Handles repository format upgrades.
- **ScannerSchedulerImpl.java**: Implementation of scanner scheduling.
- **WindowsFileSystemAccess.java**: Windows-specific file system access.

These implementations provide the concrete functionality for interacting with the file system, managing file metadata, and tracking changes to files. They handle platform-specific details and provide a consistent interface to the rest of the backup system.
