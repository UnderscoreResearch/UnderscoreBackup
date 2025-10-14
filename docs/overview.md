# Underscore Backup - Source Code Organization

This document provides an overview of the Underscore Backup project's source code organization and its major components.

## Project Overview

Underscore Backup is a serverless backup solution with client-side encryption that provides secure, efficient, and flexible backup capabilities. The project is built on Java 21 and supports multiple platforms including Linux, Windows, and macOS.

## Directory Structure

The project is organized into the following main directories:

- `/src` - Core Java source code
- `/webui` - Web-based user interface
- `/docs` - Documentation
- `/windows`, `/linux`, `/osx` - Platform-specific code and resources
- `/scripts` - Utility scripts
- `/integrationtests` - Integration test suite

## Core Components

### 1. Backup Engine (`/src/main/java/com/underscoreresearch/backup`)

The core backup engine is organized into several key packages:

#### Block Management (`/block`)
- Handles data blocks for storage and retrieval
- Implements block assignments for different file types and sizes
- Provides deduplication functionality
- Contains implementations for block formats and processing

#### Configuration (`/configuration`)
- Manages backup configuration settings
- Handles reading/writing configuration files
- Provides configuration models and validation

#### Encryption (`/encryption`)
- Implements encryption mechanisms including Post-Quantum encryption
- Supports multiple encryption algorithms (AES256, PQC)
- Contains encryptors for different security needs (Kyber, X25519)

#### Error Correction (`/errorcorrection`)
- Provides error correction capabilities for unreliable storage
- Implements Reed-Solomon error correction

#### File Management (`/file`)
- Handles file operations and tracking
- Implements change detection and polling
- Manages file metadata and versioning

#### I/O Operations (`/io`)
- Manages input/output operations to various destinations
- Implements storage backends for different providers:
  - Local file system
  - Cloud storage (S3, Wasabi, Backblaze B2, iDrive E2)
  - Dropbox
  - Windows Shares (SMB)

#### Manifest Management (`/manifest`)
- Tracks backup repository state
- Manages metadata about backed-up files
- Handles versioning and retention policies

#### Models (`/model`)
- Defines data models used throughout the application
- Provides serialization/deserialization capabilities

#### Service Integration (`/service`)
- Integrates with the optional Underscore Backup service
- Handles authentication and coordination
- Manages sharing capabilities

#### User Interface (`/ui`)
- Provides command-line tools for managing backups
- Includes web service components for the UI
- Implements UI helpers and command handlers

#### Utilities (`/utils`)
- Provides common utility functions
- Implements state management helpers

### 2. Web UI (`/webui`)

The web-based user interface is built with React and provides a complete management interface.

The UI is build into the `src/main/resources/webui` directory during compile time and embedded into the Java
as resources which are served by the Java web server based on [Takes framework](https://takes.org).

#### Components (`/webui/src/components`)
- UI components for configuration, monitoring, and restoration
- Implements file browsing and selection
- Provides settings management interfaces
- Handles authentication and security features

#### API Integration (`/webui/src/api`)
- Communicates with the Java backend
- Manages state and data flow

#### Utilities (`/webui/src/utils`)
- Helper functions for the UI

## Build and Deployment

See the separate `build.md` document for instructions on building the project and deploying it to different platforms.

## Extension Points

The system is designed to be extensible through plugins, particularly for:
- Encryption algorithms
- Error correction methods
- Storage destinations

This allows for future expansion of capabilities without changing the core architecture.
