# Underscore Backup Web UI

This package contains the implementation of Underscore Backup's web-based user interface. The web UI provides a fully-fledged interface for initial setup, monitoring, and restore operations as mentioned in the main project README.

## Package Overview

The web UI is built as a modern web application with a Java backend that serves both the static frontend assets and provides REST API endpoints for all backup operations. The UI is designed to work across all supported platforms (Linux, Windows, and MacOS).

## Core Components

### Main Web Server and Authentication

- `WebServer.java` - The main web server implementation that handles HTTP requests, configures routes, and manages the embedded Jetty server
- `ApiAuth.java` - Handles authentication for the web API endpoints, ensuring secure access to backup operations
- `PsAuthedContent.java` - Manages authenticated content and sessions

### Base Classes and Utilities

- `BaseImplementation.java` - Base class for implementing web endpoints with common functionality
- `ExclusiveImplementation.java` - Extension of BaseImplementation that ensures exclusive access to resources
- `BaseWrap.java` - Wrapper class for web responses
- `LoggingTake.java` - Logging utility for web requests
- `StrippedPrefixClasspath.java` - Utility for handling classpath resources with stripped prefixes

### Encryption Key Management

- `PrivateKeyRequest.java` - Handles requests related to private encryption keys
- `AdditionalPrivateKeyRequest.java` - Manages additional private keys for advanced encryption scenarios
- `DestinationDecoder.java` - Utility for decoding destination information for backup operations

## Subpackages

### `methods` Package

The `methods` package contains implementations of all HTTP endpoints that handle specific backup operations. Each class typically represents a single API endpoint with a specific responsibility.

Key functionality groups:
- Configuration and setup endpoints
- Authentication endpoints
- Backup operation endpoints
- File management endpoints
- Restore operation endpoints
- System operation endpoints
- Sharing endpoints

See the [methods README](methods/README.md) for detailed information.
