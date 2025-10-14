# JNA Integration for WatchService

This package contains Java Native Access (JNA) integration components for the Methvin WatchService. These components enable the WatchService to use native operating system APIs for efficient file system monitoring.

Key components include:

- **CarbonAPI**: JNA interface to macOS Carbon API for file system events
- **CFStringRef**: JNA wrapper for Core Foundation string references
- **FSEventStreamRef**: JNA wrapper for File System Event stream references

These classes provide the native API bindings that allow the WatchService to use platform-specific file system monitoring capabilities, resulting in improved performance and reduced resource usage compared to pure Java implementations.
