# URL Shortener Application Documentation

## Overview
This project implements a URL shortener service using Java 17 and Spring Boot. It provides a REST API to create short URLs and redirect to original URLs. The application uses PostgreSQL for data persistence and Flyway for database migrations.

## Features
- **POST /api/shorten**: Accepts a JSON payload with an original URL, validates it, and returns an 8-character case-sensitive Base62 short code.
- **GET /{shortCode}**: Redirects the client to the original URL with an HTTP 302 status if the short code exists.
- Unique short codes are generated using Java's `SecureRandom` ensuring secure randomness.
- Collision management allows up to 5 retries when generating short codes.
- URL validation ensures only valid HTTP or HTTPS URLs are shortened.
- Proper HTTP response codes:
  - 200 OK for successful shortening
  - 302 Found for redirection
  - 400 Bad Request for invalid URLs
  - 404 Not Found if short code does not exist
  - 409 Conflict if unable to generate a unique short code after retries
  - 500 Internal Server Error for unexpected errors
- Persistence layer uses Spring Data JPA interacting with PostgreSQL.
- Flyway manages database schema migrations.
- OpenAPI / Swagger UI is integrated for API documentation.
- Spring Boot Actuator provides health and info endpoints.
- Unit and integration tests cover the core functionality.

## Architecture
- **Entity**: `UrlMapping` representing a mapping between an 8-character short code and an original URL.
- **Repository**: `UrlMappingRepository` for CRUD operations on mappings.
- **Service**: `UrlShortenerService` encapsulates business logic—URL validation, short code generation with retry, lookup.
- **Controller**: `UrlShortenerController` exposes REST endpoints, handles request validation and responses.

## Database
- Table `urls` with columns:
  - `short_code` (VARCHAR(8), primary key)
  - `original_url` (TEXT)
  - `created_at` (TIMESTAMP WITH TIME ZONE)
- Migration SQL located at `src/main/resources/db/migration/V1__Create_urls_table.sql`.

## Build and Dependencies
- Built with Gradle.
- Java 17 compatibility.
- Dependencies:
  - Spring Boot Web, Data JPA, Actuator
  - Flyway Core for migrations
  - PostgreSQL driver
  - Springdoc OpenAPI for API docs

## API Documentation
- OpenAPI spec available under `/v3/api-docs`.
- Swagger UI accessible at `/swagger-ui.html`.
- API includes descriptions, response codes, and request/response schemas.

## Health and Monitoring
- Actuator endpoints exposed: `/actuator/health`, `/actuator/info`.
- Health endpoint shows application and database connectivity status.

## Error Handling
- Invalid input URLs cause HTTP 400.
- Nonexistent short codes cause HTTP 404.
- Collision exhaustion returns HTTP 409.
- Unexpected errors return HTTP 500.

## Testing
- Comprehensive unit tests verify service logic like URL validation, short code generation, and repository interactions.
- Integration tests cover controller endpoints and full request handling.

## Limitations and Scope
- No analytics, redirect counters, authentication, rate limiting, updates, or deletions as per requirements.
- Uses sensible defaults for configuration.

## Running the Application
1. Ensure PostgreSQL is running and accessible with the configured credentials.
2. Build the project using Gradle.
3. Run the Spring Boot application.
4. Access Swagger UI for interactive API exploration.

---