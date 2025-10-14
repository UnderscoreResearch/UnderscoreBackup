# File Package

This package contains components for file system interaction, file scanning, and metadata management in the Underscore Backup system.

## Core Files

- **CloseableLock.java**: Closeable lock implementation.
- **CloseableMap.java**: Closeable map implementation.
- **CloseableSortedMap.java**: Closeable sorted map implementation.
- **CloseableStream.java**: Closeable stream implementation.
- **ContinuousBackup.java**: Interface for continuous backup functionality.
- **FileChangeWatcher.java**: Interface for watching file system changes.
- **FileConsumer.java**: Interface for consuming file data.
- **FilePermissionManager.java**: Interface for managing file permissions.
- **FileScanner.java**: Interface for scanning file systems.
- **FileSystemAccess.java**: Interface for accessing the file system.
- **LogFileRepository.java**: Interface for the log file repository.
- **MapSerializer.java**: Interface for serializing maps.
- **MetadataRepository.java**: Interface for the metadata repository.
- **MetadataRepositoryStorage.java**: Interface for metadata repository storage.
- **PathNormalizer.java**: Utility for normalizing file paths.
- **RepositoryOpenMode.java**: Enum for repository open modes.
- **ScannerScheduler.java**: Interface for scheduling file scans.

## Subpackages

- **changepoller**: Contains implementations for detecting file system changes.
- **implementation**: Contains concrete implementations of the file interfaces.

The file package is responsible for interacting with the local file system, detecting changes to files, and managing metadata about backed-up files. It provides the foundation for the backup system's ability to efficiently track and back up changes to files.
