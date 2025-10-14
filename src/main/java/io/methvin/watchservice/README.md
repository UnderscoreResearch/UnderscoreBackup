# Methvin WatchService

This package contains a file system watching implementation that provides improved performance over the default Java WatchService on some platforms. It is used by Underscore Backup to efficiently monitor file system changes.

Key components include:

- **AbstractWatchKey**: Base implementation of the WatchKey interface
- **AbstractWatchService**: Base implementation of the WatchService interface
- **MacOSXWatchService**: macOS-specific implementation using native APIs
- **PollWatchService**: Fallback implementation using polling

This is a third-party library integrated into the Underscore Backup codebase to provide efficient file system change detection across different platforms.
