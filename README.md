# dcrivella-auth
Spring Boot Authorization Server with GraalVM

This project provides an **OAuth2 Authorization Server** (with OIDC support) built on Spring Boot, with support for running on both a normal JVM and as a **GraalVM native image**.

## Project Structure
This repository is a single **Gradle multi-project build** with one root wrapper (`./gradlew`) and three application modules:

- **auth-server** → Spring Authorization Server (OIDC, OAuth2 flows)
- **client-server** → Example web client (PKCE, Thymeleaf UI)
- **resource-server** → Example API protected by JWT
- **infra** → Compose, k3d, Kubernetes manifests and shared local DB init scripts
- **postman** → Postman collection and environment

```text
dcrivella-auth/
├─ .mise.toml                      # tool versions and local development tasks
├─ auth-server/                    # Spring Authorization Server module; issues OAuth2/OIDC tokens
├─ client-server/                  # OAuth2/OIDC web client module; login UI and resource-server calls
├─ resource-server/                # JWT-protected API module; validates issuer, audience and scopes
├─ postman/                        # Postman collections for exercising the OAuth2/OIDC flows
├─ infra/                          # local runtime infrastructure shared by Compose and k3d
│  ├─ compose/                     # Docker Compose runtime definition
│  │  ├─ compose.yml               # base Compose stack: Postgres, auth, client and resource services
│  │  ├─ compose.override.yml      # local dev overrides: host ports and restart policy
│  │  └─ .env                      # image tags, ports, issuer, DB credentials and audience values
│  ├─ db/                          # database assets shared by all local runtime modes
│  │  └─ init/                     # scripts mounted into the Postgres init directory
│  │     └─ 001-provision_auth.sql # creates auth_server DB, auth schema and auth_user role
│  ├─ k3d/                         # local Kubernetes cluster definition
│  │  └─ cluster-config.yaml       # k3d cluster name, k3s image, ports, volume and kubeconfig behavior
│  ├─ k8s/                         # Kubernetes manifests
│  │  └─ overlays/                 # Kustomize environment overlays
│  │     └─ local/                 # local k3d overlay, one resource/service per YAML file
```

## Prerequisites

### Docker & Docker Compose v2
- macOS/Windows: Docker Desktop includes Compose v2

- Linux: install Docker Engine + Compose plugin

### mise

Install [mise](https://mise.jdx.dev/), then install the project toolchain:

```zsh
mise install
mise tasks
```

The project configuration tracks the current Temurin Java 25 patch and pins the
k3d, standalone Kustomize and kubectl versions used by the local Kubernetes
workflow. Gradle is not installed by mise because the repository uses its root
wrapper (`./gradlew`).

Docker Engine and Docker Compose v2 remain system prerequisites. k3d and
Kustomize and kubectl are only needed when using the optional Kubernetes runtime.

## Local Host Aliases

OIDC redirects happen in the browser, not only inside containers or pods. The Authorization Server issuer must use a hostname that works consistently for:

- services inside containers (service-to-service calls / token validation), where the **"iss"** claim in minted JWTs must exactly match the issuer that validators are configured with (**issuer-uri**). <br>
If you copy a token minted before (e.g., with iss = http://localhost:9000), the **resource-server** expecting http://host.docker.internal:9000 will reject it.
- the browser outside the runtime, for redirects to the Authorization Server.

Use the alias for the runtime you are starting:

```zsh
# Docker Compose issuer: http://host.docker.internal:9000
grep host.docker.internal /etc/hosts || echo "127.0.0.1 host.docker.internal" | sudo tee -a /etc/hosts

# k3d issuer: http://host.k3d.internal:9000
grep host.k3d.internal /etc/hosts || echo "127.0.0.1 host.k3d.internal" | sudo tee -a /etc/hosts
```

Verify them when needed:

```zsh
getent hosts host.docker.internal
getent hosts host.k3d.internal
```

## Quick Run With Docker Compose

The Compose stack uses:

```text
ISSUER_URL=http://host.docker.internal:9000
```

Start the stack from the repository root:
```zsh
mise run compose:build-up
```

- `mise run compose:build-up` → builds the images and then starts the Compose stack. Use this on a fresh checkout or after code changes.

- `mise run image:build` → only builds the images.

- `mise run compose:up` → only starts the Compose stack. Use this for later runs when the images already exist locally.

➡️ Open the client application in your browser:

http://localhost:8080
```text
Username: user
Password: pass
```

## Quick Run With k3d
The k3d option creates a local Kubernetes cluster and deploys the same services there:

- Postgres as a `StatefulSet`
- `auth-server`, `client-server` and `resource-server` as Kubernetes `Deployment`s
- NodePort services exposed through k3d port mappings

Do not run the Compose stack and k3d stack at the same time. Both modes expose the same host ports: `9000`, `8080`, `8081` and `5432`.

The k3d manifests use:

```text
http://host.k3d.internal:9000
```

Start the k3d stack from the repository root:

```zsh
mise run k3d:build-up
```

- `mise run k3d:build-up` → builds the images, creates the cluster if needed, imports images into k3d and deploys Kubernetes manifests.
- `mise run k3d:up` → creates the cluster if needed, imports already-built images and deploys manifests.
- `mise run k3d:render` → renders the local Kustomize overlay to stdout without applying it.
- `mise run k3d:cluster-stop` / `mise run k3d:cluster-start` → stop/start the existing cluster without deleting Kubernetes resources.
- `mise run k3d:cluster-down` → deletes the k3d cluster.

Open the client application in your browser:

http://localhost:8080

```text
Username: user
Password: pass
```

## Running Options

### JVM (normal)
To run normally with JDK 25 (same as pressing Run in IntelliJ):
```zsh
./gradlew :auth-server:bootRun
```

### GraalVM (native)
For native image compilation, install **GraalVM with Native Image Kit** (NIK):

```zsh
sdk install java 25.0.3.r25-nik
sdk use java 25.0.3.r25-nik
```

Check:
```zsh
java --version

openjdk 25.0.3 2026-04-21 LTS
OpenJDK Runtime Environment Liberica-NIK-25.0.3-1 (build 25.0.3+12-LTS)
OpenJDK 64-Bit Server VM Liberica-NIK-25.0.3-1 (build 25.0.3+12-LTS, mixed mode, sharing)
```

Understanding GraalVM versions:

- 25 Java language level (JDK 25)
- r25 GraalVM release train based on JDK 25
- nik Native Image Kit, required to build native binaries

#### Native Image Build
To compile as a native executable:
```zsh
./gradlew :auth-server:clean :auth-server:nativeCompile
```

Run it directly:
```zsh
./auth-server/build/native/nativeCompile/auth-server
```

Shortcut (compile + run in one step):
```zsh
./gradlew :auth-server:clean :auth-server:nativeRun
```

### Docker Build
This project uses Spring Boot's `bootBuildImage` task to produce OCI images with Cloud Native Buildpacks. The modules currently configure Paketo builder images. <br>
For a short explanation of Spring Boot build images, Paketo Buildpacks and Dockerfile alternatives, see [Container Images](docs/container-images.md).

By default, the Gradle task is configured to build native images, but you can choose to use JVM with a property.

- Build native image (default) → Banner will show: "JVM :: Substrate VM..."
    ```zsh
    ./gradlew :auth-server:clean :auth-server:bootBuildImage -Pversion=DEV-SNAPSHOT
    ```

- Build JVM image → Banner will show: "JVM :: OpenJDK 64-Bit Server VM..."
    ```zsh
    ./gradlew :auth-server:clean :auth-server:bootBuildImage -Pversion=DEV-SNAPSHOT -PBP_NATIVE_IMAGE=false
    ```

Run it:
```zsh
docker run --rm -p 9000:9000 \
  -e SPRING_PROFILES_ACTIVE=docker \
  dcrivella/auth-server:DEV-SNAPSHOT
```

## Gradle
The project uses a root Gradle multi-project build with one wrapper (`./gradlew`). <br>
Use module-qualified task names, for example `:auth-server:bootRun`, `:client-server:test` or `:resource-server:bootBuildImage`.

Check the wrapper version:
```zsh
./gradlew -v
```

## mise Tasks

The recommended local workflow uses `mise run <task>` from the repository root. Run `mise tasks` for the complete list. See [Stack Commands](docs/stack-commands.md) for the underlying Gradle, Compose and Kubernetes commands.

Format source and configuration files, or verify formatting without changing files:

```zsh
mise run format
mise run lint
```

The equivalent Gradle tasks are `./gradlew spotlessApply` and `./gradlew spotlessCheck`. Each module's `check` task also runs the Spotless check.

### Build Images

Use these commands to generate the Docker/OCI images used by both Compose and k3d.

- **mise run image:build:auth** → builds the `auth-server` image using Gradle’s `bootBuildImage` and Paketo Buildpacks.
- **mise run image:build:client** → builds the `client-server` image.
- **mise run image:build:resource** → builds the `resource-server` image.
- **mise run image:build** → builds all three images sequentially.

### Compose Stack

Use these commands to run the local stack with Docker Compose.

- **mise run compose:build-up** → builds images and starts the Compose stack.
- **mise run compose:up** / **compose:down** / **compose:restart** → control the stack lifecycle.
- **mise run compose:logs** → tails all logs; use `compose:logs:auth`, `:client`, `:resource` or `:db` for one service.
- **mise run compose:ps** → shows Compose container status.
- **mise run compose:db-reset** → deletes Postgres volumes and starts a fresh stack. <br> ⚠️ This wipes all local Compose data; use `compose:down` to preserve it.
- **mise run compose:check** → prints Compose diagnostics.

### k3d Cluster

Use these commands to run the local stack in a k3d Kubernetes cluster.

- **mise run k3d:build-up** → builds images, creates/starts the cluster, imports images and deploys Kubernetes manifests.
- **mise run k3d:up** → deploys using already-built images.
- **mise run k3d:render** → renders the local overlay with standalone Kustomize without applying it.
- **mise run k3d:cluster-stop** / **cluster-start** / **cluster-down** → control the cluster lifecycle.
- **mise run k3d:ps** → shows Kubernetes pods, services and PVCs.
- **mise run k3d:logs** → tails all workload logs; use `k3d:logs:auth`, `:client`, `:resource` or `:db` for one workload.
- **mise run k3d:db-reset** → deletes the Postgres PVC and recreates Postgres. <br> ⚠️ This wipes the k3d database.

## Project Notes

- [OAuth2 and OIDC Overview](docs/oauth2-oidc-overview.md) documents the OAuth2/OIDC behavior implemented by `auth-server`, `client-server` and `resource-server`.
- [Identity Providers and Multitenancy](docs/identity-providers-and-multitenancy.md) compares conceptual, not-yet-implemented Zitadel strategies for centralized identity, roles, tenant access metadata, enriched claims and cached domain resolution.
- [Browser Clients and Token Lifecycle](docs/browser-clients-and-token-lifecycle.md) covers refresh tokens, SPA/BFF tradeoffs, browser cookies, sessions, rotation, expiration and invalidation.
- [Container Images](docs/container-images.md) explains the repository's Paketo Buildpack flow and the historical Kaniko/current BuildKit comparison for Dockerfile builds.
- [Stack Commands](docs/stack-commands.md) maps every mise task to its Gradle, Docker Compose or Kubernetes behavior.

## Database Connection Details

- **Database name**: `auth_server`
- **Schema**: `auth`
- **User**: `auth_user`
- **Password**: `auth_pass`
- **Host**:
  - Inside Docker Compose → `db`
  - Inside k3d/Kubernetes → `db.dcrivella-auth.svc.cluster.local` or `db`
  - From host machine → `localhost` (or `127.0.0.1`)
- **Port**: `5432` (default PostgreSQL port)

### Connection Strings

- **JDBC (SQuirrel SQL, IntelliJ, etc.)** → `jdbc:postgresql://localhost:5432/auth_server?currentSchema=auth`

- **psql CLI (from host ZSH)** →
  ```zsh
  psql -h localhost -p 5432 -U auth_user -d auth_server
  ```

### Installing psql
If you don't have the PostgreSQL CLI (psql) installed, install it:

- macOS (Homebrew)

    ```zsh
    brew install libpq
    brew link --force libpq
    psql --version
    ```

- Linux (Debian/Ubuntu)
    ```zsh
    sudo apt install -y wget ca-certificates gnupg
    
    wget -qO- https://www.postgresql.org/media/keys/ACCC4CF8.asc | \
      gpg --dearmor | sudo tee /etc/apt/trusted.gpg.d/pgdg.gpg > /dev/null

    echo "deb http://apt.postgresql.org/pub/repos/apt noble-pgdg main" | \
      sudo tee /etc/apt/sources.list.d/pgdg.list

    sudo apt update

    sudo apt install -y postgresql-client-18

    psql --version
    ```
