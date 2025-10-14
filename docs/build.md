# Building Underscore Backup

This document outlines the requirements and steps to build Underscore Backup from source.

## Prerequisites

### Required Software

- **Java Development Kit (JDK) 21** - Amazon Corretto 21 is recommended
- **Gradle** - Build automation tool (wrapper included in the repository)
- **Node.js and npm** - Required for building the web UI
- **Perl** - Used for some build scripts (Use Strawberry Perl on Windows)

### Platform-Specific Requirements

#### Windows
- Inno Setup 6 - Required for creating Windows installers
- Windows SDK - For signing executables (optional)

#### Linux
- dpkg tools - For building Debian packages
- rpm tools - For building RPM packages

#### macOS
- Xcode Command Line Tools

## Building the Application

### Create a platform-specific installer as well as running all available unit and integration tests

Run the following command in the root directory of the project:

```
# On Linux/macOS
./builddist.pl

# On Windows (Must be executed from CMD, not Git Bash or other shells)
builddist.pl
```

### Building the Web UI

The web UI is automatically built during the Java build process if it doesn't exist. If you need to build it separately:

```bash
cd webui
npm install
npm run build
```

## Testing

### Running Unit Tests

```bash
# On Linux/macOS
./gradlew test

# On Windows
gradlew.bat test
```

### Running Integration Tests

```bash
# On Linux/macOS
./gradlew integrationTest

# On Windows
gradlew.bat integrationTest
```

### Running All Tests

```bash
# On Linux/macOS
./gradlew allDistTest

# On Windows
gradlew.bat allDistTest
```

## Memory Requirements

The application is designed to run with minimal memory requirements:
- 256MB heap memory is sufficient for normal operation
- Additional memory may be required during build time

## Notes for Developers

- The application uses Lombok for reducing boilerplate code. Ensure your IDE has Lombok plugin installed.
- The build process automatically handles versioning through the `version.properties` file.
- The application follows a plugin-based architecture for encryption, error correction, and destination I/O.
