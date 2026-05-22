# Codex Instructions

## Project Overview

This repository is a Java 25 / Spring Boot 4 OAuth2 learning project built as a Gradle Kotlin DSL multi-project.

Modules:

- `auth-server`: Spring Authorization Server with OIDC, OAuth2 flows, Postgres persistence, Flyway and token audience customization.
- `client-server`: Example OAuth2/OIDC web client using authorization code + PKCE, Thymeleaf and WebClient calls to the resource server.
- `resource-server`: Example JWT-protected API with issuer and audience validation.
- `stack`: Docker Compose stack and helper makefile for Postgres plus the three application services.
- `postman`: Local Postman collection and environment.

## Required Tools

- Use the root Gradle wrapper: `./gradlew`.
- Java toolchain is Java 25.
- Docker Compose v2 is used by the stack.
- The root `Makefile` delegates to `stack/makefile`.

## Common Commands

- Run all tests: `./gradlew test`
- Run one module's tests: `./gradlew :auth-server:test`, `./gradlew :client-server:test` or `./gradlew :resource-server:test`
- Run the auth server locally: `./gradlew :auth-server:bootRun`
- Run the client locally: `./gradlew :client-server:bootRun`
- Run the resource server locally: `./gradlew :resource-server:bootRun`
- Build Docker images and start the stack: `make build up`
- Start an already-built stack: `make up`
- Stop the stack: `make down`
- Tail stack logs: `make logs`
- Reset the local Postgres volume: `make db-reset`

Use module-qualified Gradle task names when possible.

## Local Runtime Notes

- The auth server runs on port `9000`.
- The client server runs on port `8080`.
- The resource server runs on port `8081`.
- Docker/local issuer consistency matters. The default issuer used by client/resource configuration is `http://host.docker.internal:9000`.
- On Linux, `host.docker.internal` may need to be mapped to `127.0.0.1` in `/etc/hosts` for local browser and container flows.

## Security Rules

- Treat OAuth2/OIDC, JWT, issuer, audience, redirect URI and scope changes as security-sensitive.
- Keep the token issuer exactly aligned with the value configured in resource server `issuer-uri`; mismatches will make JWT validation fail.
- Keep resource audience values aligned with `auth.token.client-audiences` in `auth-server` and `spring.security.oauth2.resourceserver.jwt.audience` in `resource-server`.
- Do not weaken authentication, authorization, CSRF, redirect URI, issuer or audience checks unless the user explicitly asks and the tradeoff is documented.
- Prefer Spring Security and Spring Authorization Server APIs over custom protocol logic.
- If changing scopes or authorities, verify how JWT `scope`/`scp` claims map to `SCOPE_` authorities.

## Coding Conventions

- Follow the existing package structure under `cloud.dcrivella`.
- Keep changes small and module-focused.
- Prefer constructor/configuration style already used in the touched module.
- Do not add new dependencies unless they are clearly needed.
- Keep comments useful and sparse; avoid narrating obvious Java code.
- Keep configuration in YAML consistent between default and `application-docker.yml` profiles when the setting affects both runtime modes.
- Use the Gradle version catalog in `gradle/libs.versions.toml` for dependency or plugin changes.

## Testing Guidance

- Add or update tests when changing security behavior, token claims, controllers, clients or configuration binding.
- For narrow changes, run the affected module test task.
- For cross-module auth flow or shared configuration changes, run `./gradlew test`.
- Native image and Docker image builds are slower; run them only when the change touches GraalVM/runtime hints, Docker/Paketo configuration or the user asks for full verification.

## Database And Migrations

- The auth server uses Postgres with schema `auth`.
- Flyway migrations belong under `auth-server/src/main/resources/db/migration`.
- Do not edit an existing migration that may already have been applied; add a new migration instead.
- Local Docker database credentials are documented in `README.md` and `stack/.env`.

## Git And Workspace

- Do not revert unrelated user changes.
- Before editing, inspect the relevant files and preserve existing style.
- Avoid broad refactors unless the user requests them.
- If a command may wipe local state, such as `make db-reset`, explain that before running it.
