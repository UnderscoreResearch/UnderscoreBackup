# File Change Poller Package

This package contains implementations for detecting changes to files in the file system, enabling continuous backup functionality.

## Files

- **BaseWatcherChangePoller.java**: Base implementation for file change pollers.
- **FileChangePoller.java**: Interface for file change polling.
- **FsChangePoller.java**: File system change poller implementation.
- **OsxChangePoller.java**: macOS-specific file change poller implementation.
- **WindowsFileChangePoller.java**: Windows-specific file change poller implementation.

These change pollers monitor the file system for changes to files and directories, allowing the backup system to respond quickly to changes and maintain an up-to-date backup. Different implementations are provided for different operating systems to take advantage of platform-specific file system notification mechanisms.
