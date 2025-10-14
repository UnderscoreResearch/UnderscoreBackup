# Service Methods Package

This package contains web endpoints that interact with the Underscore Backup service. These endpoints facilitate the optional integration with the free service that helps coordinate backup sources, encryption key recovery, and sharing of backup data.

## Service Integration Overview

As mentioned in the main project README, Underscore Backup ties into a free service that provides:
- Coordination of backup sources
- Optional encryption key recovery
- Coordination of sharing backup data

The endpoints in this package implement the client-side functionality needed to interact with this service. However, all online services are optional, and users can run the backup software without an online account.

## Key Components

### Region Management

- `BestRegionGet.java` - Determines the best AWS region for service operations based on latency and availability. This helps ensure optimal performance when interacting with the service.

### Secret Management

These endpoints handle the secure storage and retrieval of sensitive information:

- `CreateSecretPut.java` - Creates a new secret in the service, used for storing sensitive configuration data
- `DeleteSecretPost.java` - Deletes a secret from the service when it's no longer needed
- `GetSecretPost.java` - Retrieves a secret from the service, with proper authentication

### Authentication and Authorization

- `GenerateTokenPost.java` - Generates authentication tokens for secure service communication
- `TokenDelete.java` - Deletes authentication tokens when they're no longer needed

### Source Management

These endpoints manage backup sources in the service:

- `SourcesGet.java` - Retrieves backup sources from the service
- `SourcesPost.java` - Creates new backup sources in the service
- `SourcesPut.java` - Updates existing backup sources in the service

### Sharing

- `SharesGet.java` - Retrieves information about shared backup data, enabling the selective sharing feature mentioned in the main README

### Support and Updates

- `SupportBundlePost.java` - Creates and uploads support bundles for troubleshooting
- `VersionCheckGet.java` - Checks for software updates to ensure users have the latest features and security fixes

## Security Considerations

Since these endpoints interact with an external service and handle sensitive information:

1. All communication is encrypted using TLS
2. Authentication tokens are used to verify identity
3. Sensitive data is encrypted before transmission
4. The service follows the principle of least privilege

## Implementation Details

The service endpoints follow RESTful design principles and handle:
- Authentication with the service
- Error handling and retries
- Rate limiting compliance
- Data validation

## Optional Nature

In line with the project's philosophy, these service integrations are entirely optional. The code is designed to gracefully handle scenarios where:
- The service is unavailable
- The user chooses not to use the service
- The user wants to switch between using and not using the service

This ensures users have complete control over their backup strategy and data privacy.
