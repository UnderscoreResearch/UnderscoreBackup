# IO Implementation Package

This package contains concrete implementations of the IO interfaces defined in the parent package, providing support for various storage destinations.

## Files

- **DownloadSchedulerImpl.java**: Implementation of the download scheduler.
- **DropboxIOProvider.java**: IO provider for Dropbox storage.
- **FileIOProvider.java**: IO provider for local file storage.
- **S3IOProvider.java**: IO provider for S3-compatible storage (AWS S3, Wasabi, Backblaze B2, iDrive E2).
- **SMBIOProvider.java**: IO provider for Windows Shares (SMB).
- **SchedulerImpl.java**: Base implementation for schedulers.
- **UnderscoreBackupProvider.java**: IO provider for the Underscore Backup service.
- **UploadSchedulerImpl.java**: Implementation of the upload scheduler.
- **Utils.java**: Utility functions for IO implementations.

These implementations provide the concrete functionality for transferring data to and from various storage destinations. The plugin-based architecture allows users to choose from multiple storage options or even use multiple destinations simultaneously for increased redundancy.
