# Codex Instructions

## Project Overview

This repository is a Java 25 / Spring Boot 4 OAuth2 learning project built as a Gradle Kotlin DSL multi-project.

Modules:

- `auth-server`: Spring Authorization Server with OIDC, OAuth2 flows, Postgres persistence, Flyway and token audience customization.
- `client-server`: Example OAuth2/OIDC web client using authorization code + PKCE, Thymeleaf and WebClient calls to the resource server.
- `resource-server`: Example JWT-protected API with issuer and audience validation.
- `infra`: Docker Compose and k3d infrastructure for Postgres plus the three application services.
- `postman`: Local Postman collection and environment.

## Required Tools

- Use the root Gradle wrapper: `./gradlew`.
- Java toolchain is Java 25.
- Docker Compose v2 is used by the stack.
- Use `mise install` for the Java, k3d, Kustomize and kubectl toolchain.
- Use `mise run <task>` for project and stack workflows.

## Common Commands

- Run all tests: `./gradlew test`
- Format source and configuration files: `./gradlew spotlessApply` or `mise run format`
- Check formatting: `./gradlew spotlessCheck` or `mise run lint`
- Run one module's tests: `./gradlew :auth-server:test`, `./gradlew :client-server:test` or `./gradlew :resource-server:test`
- Run the auth server locally: `./gradlew :auth-server:bootRun`
- Run the client locally: `./gradlew :client-server:bootRun`
- Run the resource server locally: `./gradlew :resource-server:bootRun`
- Build Docker images and start the stack: `mise run compose:build-up`
- Start an already-built stack: `mise run compose:up`
- Stop the stack: `mise run compose:down`
- Tail stack logs: `mise run compose:logs`
- Reset the local Postgres volume: `mise run compose:db-reset`

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
- Local Docker database credentials are documented in `README.md` and `infra/compose/.env`.

## Git And Workspace

- Do not revert unrelated user changes.
- Before editing, inspect the relevant files and preserve existing style.
- Avoid broad refactors unless the user requests them.
- If a command may wipe local state, such as `mise run compose:db-reset`, explain that before running it.


<!-- headroom:rtk-instructions -->
# RTK (Rust Token Killer) - Token-Optimized Commands

When running shell commands, **always prefix with `rtk`**. This reduces context
usage by 60-90% with zero behavior change. If rtk has no filter for a command,
it passes through unchanged — so it is always safe to use.

## Key Commands
```bash
# Git (59-80% savings)
rtk git status          rtk git diff            rtk git log

# Files & Search (60-75% savings)
rtk ls <path>           rtk read <file>         rtk grep <pattern>
rtk find <pattern>      rtk diff <file>

# Test (90-99% savings) — shows failures only
rtk pytest tests/       rtk cargo test          rtk test <cmd>

# Build & Lint (80-90% savings) — shows errors only
rtk tsc                 rtk lint                rtk cargo build
rtk prettier --check    rtk mypy                rtk ruff check

# Analysis (70-90% savings)
rtk err <cmd>           rtk log <file>          rtk json <file>
rtk summary <cmd>       rtk deps                rtk env

# GitHub (26-87% savings)
rtk gh pr view <n>      rtk gh run list         rtk gh issue list

# Infrastructure (85% savings)
rtk docker ps           rtk kubectl get         rtk docker logs <c>

# Package managers (70-90% savings)
rtk pip list            rtk pnpm install        rtk npm run <script>
```

## Rules
- In command chains, prefix each segment: `rtk git add . && rtk git commit -m "msg"`
- For debugging, use raw command without rtk prefix
- `rtk proxy <cmd>` runs command without filtering but tracks usage
<!-- /headroom:rtk-instructions -->
