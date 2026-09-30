# StockFlow

Multi-warehouse order and inventory management system built with Java 17, Spring Boot, PostgreSQL, Flyway, and Docker Compose.

## Current milestone

The project foundation is in place: application configuration, PostgreSQL container, and the first Flyway migration for users and roles. The next milestone implements JWT authentication and role-based authorization.

## Local setup

1. Use the defaults in `src/main/resources/application.yml`, or export `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, and `DB_PASSWORD` in your shell/run configuration if you need different local database values. `.env.example` is only a reference file; Spring Boot does not load `.env` automatically.
2. Start PostgreSQL with `docker compose up -d`.
3. Run the application with `./mvnw spring-boot:run` on macOS/Linux, `.\mvnw.cmd spring-boot:run` on Windows, or `mvn spring-boot:run` with Maven installed.

The health endpoint is `GET /api/v1/health`.
