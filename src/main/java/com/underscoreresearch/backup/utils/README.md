# Underscore Backup Utilities

This package contains utility classes that provide common functionality used throughout the Underscore Backup application. These utilities support the core features mentioned in the main project README, such as efficient handling of large backup sets, low resource usage, and reliable operation across multiple platforms.

## Package Overview

The utilities package provides foundational components that enable Underscore Backup to efficiently manage very large backup sets with millions of files while maintaining low resource requirements. These utilities handle critical aspects like concurrency control, error recovery, serialization, and task scheduling.

## Core Components

### Concurrency and Resource Management

- `AccessLock.java` - Provides a file-based locking mechanism to ensure exclusive or shared access to resources across multiple processes or threads, preventing data corruption during concurrent operations
- `SingleTaskScheduler.java` - Implements a scheduler that ensures only one instance of a task is running at any given time, helping to maintain the application's low resource requirements

### Error Handling and Recovery

- `RetryUtils.java` - Implements retry logic with exponential backoff for operations that may fail temporarily due to network issues or other transient errors, particularly important for reliable interaction with remote storage destinations
- `ProcessingStoppedException.java` - Exception class used to signal when a processing operation has been intentionally stopped, allowing for clean handling of user-initiated cancellations and system shutdowns

### Data Management

- `SerializationUtils.java` - Provides utilities for serializing and deserializing data structures used throughout the application, supporting efficient storage and retrieval of backup metadata

## Subpackages

### `log` Package

The `log` package contains specialized logging utilities that support the monitoring and reporting capabilities of Underscore Backup. These components are essential for providing users with visibility into backup operations, system state, and activity.

See the [log README](log/README.md) for detailed information about the logging components.
