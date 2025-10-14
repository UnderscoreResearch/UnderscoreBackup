# Web Methods Package

This package contains the implementation of all HTTP endpoints for the Underscore Backup web UI. Each class typically represents a single API endpoint that handles specific functionality in the backup system.

## Endpoint Naming Convention

The classes follow a naming convention that indicates both the functionality and the HTTP method:
- `*Get.java` - Endpoints that handle HTTP GET requests
- `*Post.java` - Endpoints that handle HTTP POST requests
- `*Put.java` - Endpoints that handle HTTP PUT requests
- `*Delete.java` - Endpoints that handle HTTP DELETE requests
- `*Options.java` - Endpoints that handle HTTP OPTIONS requests

## Functional Categories

### Configuration and Setup

- `ConfigurationGet.java` / `ConfigurationPost.java` - Get and update backup configuration
- `GenerateKeyPut.java` - Generate encryption keys
- `KeyPost.java` / `KeyChangePost.java` - Manage encryption keys
- `AdditionalKeyPut.java` - Add an additional encryption key
- `AdditionalKeysPost.java` - Fetch all additional encryption keys
- `RemoteConfigurationGet.java` - Get configuration from remote sources
- `SourceSelectPost.java` - Select backup sources

### Authentication and System Health

- `AuthEndpointGet.java` / `AuthPost.java` - Handle user authentication
- `PingGet.java` / `PingPost.java` / `PingOptions.java` - Health check endpoints
- `StateGet.java` - Get system state information

### Backup Operations

- `BackupPauseGet.java` - Pause/resume backup operations
- `DefragPost.java` - Defragment backup repositories
- `OptimizePost.java` - Optimize backup logs
- `TrimPost.java` - Trim backup data
- `ValidateBlocksPost.java` - Validate backup blocks
- `RepairPost.java` - Repair backup repositories
- `RestartSetsPost.java` - Restart backup sets
- `ActivityGet.java` - Get activity information

### File Management

- `ListBackupFilesGet.java` - List files in backup
- `ListBackupVersionsGet.java` - List versions of backed up files
- `ListLocalFilesGet.java` - List local files
- `SearchBackupFilesGet.java` - Search for files in backup
- `BackupFilesDelete.java` - Delete files from backup
- `RebuildAvailableGet.java` - Check if rebuild is available

### Restore Operations

- `RestorePost.java` - Restore files from backup
- `RemoteRestorePost.java` - Restore files from remote backup
- `BackupDownloadPost.java` - Download files from backup

### System Operations

- `ShutdownGet.java` - Shutdown the backup system
- `ResetDelete.java` - Reset the backup system

### Sharing

- `ActiveSharesGet.java` - Get active shares
- `ActivateSharesPost.java` - Activate non activated shares

## Implementation Details

Most endpoint implementations follow a common pattern:
1. Validate the request and authenticate the user
2. Process the request by interacting with the appropriate backup system components
3. Return a response with the result or error information

The endpoints are designed to be stateless where possible, with state maintained in the core backup system components rather than in the web layer.

## Subpackages

### `service` Package

The `service` package contains endpoints that interact with the optional Underscore Backup service. See the [service README](service/README.md) for detailed information.
