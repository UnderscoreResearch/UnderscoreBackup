# Manifest Package

This package contains components for managing the backup manifest, which tracks the state of the backup repository and provides access to backup contents.

## Core Files

- **BackupContentsAccess.java**: Interface for accessing backup contents.
- **BackupSearchAccess.java**: Interface for searching backup contents.
- **BaseManifestManager.java**: Base interface for manifest managers.
- **LogConsumer.java**: Interface for consuming log entries.
- **ManifestManager.java**: Interface for managing the backup manifest.
- **ServiceManager.java**: Interface for interacting with the Underscore Backup service.
- **ShareActivateMetadataRepository.java**: Interface for activating shared repositories.
- **ShareManifestManager.java**: Interface for managing shared manifests.

## Subpackages

- **implementation**: Contains concrete implementations of the manifest interfaces.
- **model**: Contains data models used by the manifest system.

The manifest package is responsible for tracking the state of the backup repository, including which files are backed up, their versions, and their locations in the backup storage. It provides interfaces for accessing and searching the backup contents, as well as for managing shared backups.
