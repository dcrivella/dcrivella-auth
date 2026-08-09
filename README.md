# dcrivella-auth
Spring Boot Authorization Server with GraalVM

This project provides an **OAuth2 Authorization Server** (with OIDC support) built on Spring Boot, with support for running on both a normal JVM and as a **GraalVM native image**.

## Project Structure
This repository is a single **Gradle multi-project build** with one root wrapper (`./gradlew`) and three application modules:

- **auth-server** → Spring Authorization Server (OIDC, OAuth2 flows)
- **client-server** → Example web client (PKCE, Thymeleaf UI)
- **resource-server** → Example API protected by JWT
- **architecture-tests** → Cross-module static dependency and convention checks
- **system-tests** → Black-box smoke and M2M end-to-end tests for active runtimes
- **playwright** → Browser login, consent, protected tasks and OIDC logout tests
- **infra** → Compose, k3d and Kubernetes manifests for the three applications
- **postman** → Postman collection and environment

```text
dcrivella-auth/
├─ .mise.toml                      # tool versions and local development tasks
├─ auth-server/                    # Spring Authorization Server module; issues OAuth2/OIDC tokens
├─ client-server/                  # OAuth2/OIDC web client module; login UI and resource-server calls
├─ resource-server/                # JWT-protected API module; validates issuer, audience and scopes
├─ architecture-tests/             # cross-module ArchUnit rules
├─ system-tests/                   # runtime-independent black-box test source set
├─ playwright/                     # isolated mock and real browser OAuth suites
├─ docs/                           # architecture, runtime and testing documentation
├─ postman/                        # Postman collections for exercising the OAuth2/OIDC flows
├─ infra/                          # local runtime infrastructure shared by Compose and k3d
│  ├─ compose/                     # Docker Compose runtime definition
│  │  ├─ compose.yml               # base Compose stack: auth, client and resource services
│  │  ├─ compose.override.yml      # local dev overrides: host ports and restart policy
│  │  └─ .env                      # image tags, ports, issuer and audience values
│  ├─ k3d/                         # local Kubernetes cluster definition
│  │  └─ cluster-config.yaml       # k3d cluster name, k3s image, ports and kubeconfig behavior
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

The project configuration tracks the current Temurin Java 25 patch and pins
act 0.2.89, Node 24.19.0, k3d, standalone Kustomize and kubectl. Gradle is not
installed by mise because the repository uses its root wrapper (`./gradlew`).

The Gradle configuration cache is enabled in strict mode. The project pins the
embedded Kotlin/IntelliJ idempotence-check rate to its documented default so
the cache key remains stable across Gradle daemons; cache incompatibilities
still fail the build instead of being ignored.

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
- `mise run compose:build-up:jvm` → uses the faster JVM image flow for local development.

- `mise run compose:bootstrap:fresh` → rebuilds all images, then deletes and recreates the fixed `dcrivella-auth-stack` Compose project. It refuses to run while the `dcrivella-auth` k3d cluster is active and requires `[y/N]` confirmation.
- `mise run compose:bootstrap:fresh:jvm` → runs the same guarded bootstrap with JVM images.

- `mise run compose:nuke` → deletes that fixed Compose project without rebuilding or recreating it. Local images and build caches are preserved, and `[y/N]` confirmation is required.

- `mise run image:build` → only builds the images.

- `mise run compose:up` → only starts the Compose stack. Use this for later runs when the images already exist locally.
- `mise run compose:up:jvm` → starts existing JVM images without rebuilding them.

- `mise run compose:down` → removes the Compose containers and networks.
- `mise run compose:bootstrap:fresh` → deletes and recreates the complete Compose environment.
- `mise run compose:nuke` → deletes the Compose environment without recreating it or starting any services.

➡️ Open the client application in your browser:

http://localhost:8080
```text
Username: user
Password: pass
```

On the consent screen, select `api.read` before submitting. If it is not
granted, login still succeeds but `/tasks` correctly returns `403` because the
access token contains no `SCOPE_api.read` authority.

Consent is authorization-server state, not browser state. See
[User Consent](docs/oauth2-oidc-overview.md#user-consent) for what survives
logout and token expiration, and why restarting `auth-server` clears consent in
this project.

## Quick Run With k3d
The k3d option creates a local Kubernetes cluster and deploys the same services there:

- `auth-server`, `client-server` and `resource-server` as Kubernetes `Deployment`s
- NodePort services exposed through k3d port mappings

Do not run the Compose stack and k3d stack at the same time. Both modes expose the same host ports: `9000`, `8080` and `8081`.

The k3d manifests use:

```text
http://host.k3d.internal:9000
```

Start the k3d stack from the repository root:

```zsh
mise run k3d:build-up
```

- `mise run k3d:build-up` → builds the images, creates the cluster if needed, imports images into k3d and deploys Kubernetes manifests.
- `mise run k3d:build-up:jvm` → performs the same flow with the faster JVM images.
- `mise run k3d:bootstrap:fresh` → rebuilds all images, then deletes and recreates the fixed `dcrivella-auth` cluster. It refuses to run while this project's Compose services are active and requires `[y/N]` confirmation.
- `mise run k3d:bootstrap:fresh:jvm` → runs the same guarded bootstrap with JVM images.
- `mise run k3d:up` → creates the cluster if needed, imports already-built images and deploys manifests.
- `mise run k3d:up:jvm` → imports and deploys existing JVM images without rebuilding them.
- `mise run k3d:render` → renders the local Kustomize overlay to stdout without applying it.
- `mise run k3d:cluster-stop` / `mise run k3d:cluster-start` → stop/start the existing cluster without deleting Kubernetes resources.
- `mise run k3d:cluster-down` → deletes the k3d cluster.
- `mise run k3d:nuke` → deletes the fixed cluster without rebuilding or recreating the environment. Local images and build caches are preserved, and `[y/N]` confirmation is required.

- `mise run k3d:build-up` → provides the normal, non-destructive rebuild/deploy flow.
- `mise run k3d:bootstrap:fresh` → recreates the cluster after rebuilding images.
- `mise run k3d:nuke` → removes the cluster without recreating it or starting services.
- `mise run nuke` → preflights both runtimes, asks for confirmation once, removes Compose before k3d, continues with the other cleanup after a partial failure, reports whether to repeat `mise run compose:nuke` or `mise run k3d:nuke`, and preserves Docker images and build caches.

Open the client application in your browser:

http://localhost:8080

```text
Username: user
Password: pass
```

### Run the resource server locally with mirrord

Mirrord is supported only for `ResourceServerApplication`. The auth server and
client server remain in k3d so the browser-facing OIDC session, redirect URI and
logout flow always use the deployed applications.

With the `dcrivella-auth` cluster running:

1. Select `.mirrord/resource-server.json` as the existing mirrord configuration.
   Do not ask the plugin to create a new targetless configuration.
2. Run `ResourceServerApplication` with an IntelliJ Application or Spring Boot
   run configuration, not a Gradle run configuration.
3. Leave IntelliJ's **Active profiles** field empty. Mirrord imports the `docker`
   profile, public `ISSUER_URL`, internal `JWK_SET_URI` and audience from the
   resource-server pod. It overrides only `SERVER_PORT`, using local port `18081`
   while stealing the Deployment's public port `8081`.
4. Continue browsing at `http://localhost:8080`. Do not open port `18081`
   directly; it belongs only to the mirrord bridge.

The token issuer remains `http://host.k3d.internal:9000`, so the JWT `iss` claim
is checked against the same public identity used by the browser and client pod.
The local process obtains signing keys separately from
`http://auth-server:9000/oauth2/jwks` through mirrord's cluster network. Signature,
issuer, audience and `SCOPE_api.read` validation all remain enabled.

Without the mirrord session, the resource-server pod handles the client's
`/tasks` call. While the session is active, the same traffic is stolen by the
local `ResourceServerApplication`. Stopping it returns traffic to the pod; no
Deployment is replaced by a `pause` or BusyBox container.

## Running Options

### JVM (normal)
To run normally with JDK 25 (same as pressing Run in IntelliJ):
```zsh
./gradlew :auth-server:bootRun
```

### GraalVM (native)
For native image compilation, install **GraalVM with Native Image Kit** (NIK):

```zsh
sdk install java 25.0.4.r25-nik
sdk use java 25.0.4.r25-nik
```

Check:
```zsh
java --version

openjdk 25.0.4 2026-07-21 LTS
OpenJDK Runtime Environment Liberica-NIK-25.0.4-1 (build 25.0.4+10-LTS)
OpenJDK 64-Bit Server VM Liberica-NIK-25.0.4-1 (build 25.0.4+10-LTS, mixed mode, sharing)
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

The test levels, mocking rules, BDD comment convention, source sets and current
OAuth persistence boundary are documented in [Testing Strategy](docs/testing-strategy.md).

```zsh
mise run test              # fast unit, web slice and standard architecture tests
mise run test:integration  # Spring and external HTTP boundary integration suites
mise run test:all          # unit, slice, integration and all architecture suites
mise run coverage          # test + integration coverage reports with JaCoCo
mise run test:mutation     # focused test-strength reports with PIT
mise run mock:playwright      # mock browser scenarios, headless
mise run mock:playwright:ui   # same mock scenarios in Playwright UI

# Require the corresponding runtime to be active already:
mise run compose:playwright      # Compose browser scenarios, headless
mise run compose:playwright:ui   # same Compose scenarios in Playwright UI
mise run k3d:playwright          # k3d browser scenarios, headless
mise run k3d:playwright:ui       # same k3d scenarios in Playwright UI
```

Run `mise run actions:list`, `actions:graph` or `actions:dry-run` to inspect the
real GitHub Actions workflow locally. `mise run actions:job <job-id>` selects a
job, while `mise run actions:all` executes every forced branch and owns an
ephemeral JVM Compose stack. Unlike that act-backed flow, `mise run ci:all`
runs the equivalent commands directly against a Compose stack that must already
be active. See [Stack Commands](docs/stack-commands.md#github-actions-locally)
for lifecycle guards and act's local-runner limitations.

Install the Chromium binary once with `mise run playwright:install`. Playwright
1.62.1 is isolated under `playwright/`. Browser execution task names start with
the selected runtime (`mock`, `compose` or `k3d`); `playwright:install` and
`playwright:report` remain tool operations. Commands without `:ui` run
automatically in headless mode; the matching `:ui` commands open the interactive
Playwright test explorer and visible Chromium while preserving the same profile,
preflight, URLs and three scenarios. The mock and real browser boundaries,
covered consent scenarios and token-safe artifact policy are documented in
[Testing Strategy](docs/testing-strategy.md).

Playwright uses the dedicated `playwright` / `playwright-pass` account so its
stored consent and single-login session do not affect the manual `user` / `pass`
account. UI timeline artifacts are temporary and are removed when the explorer
closes.

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

These commands run Spotless. Each module's `check` task runs the same formatting
check; integration and black-box tests remain opt-in.

### Build Images

Use these commands to generate the Docker/OCI images used by both Compose and k3d.

- **mise run image:build:auth**, **:client**, **:resource** → build each native image using Gradle’s `bootBuildImage` and Paketo Buildpacks.
- **mise run image:build** → builds all three `1.0.0-native` images sequentially.
- **mise run image:build:auth:jvm**, **:client:jvm**, **:resource:jvm** → build the corresponding JVM image.
- **mise run image:build:jvm** → builds all three `1.0.0-jvm` images sequentially.

JVM images are the faster development path. Native images remain the dedicated
GraalVM compilation/runtime validation path.

### Compose Stack

Use these commands to run the local stack with Docker Compose.

- **mise run compose:build-up** / **compose:build-up:jvm** → build native/JVM images and start the Compose stack.
- **mise run compose:bootstrap:fresh** → rebuilds images and, after confirmation, recreates the `dcrivella-auth-stack` containers and networks. It is blocked while the `dcrivella-auth` k3d cluster is active.
- **mise run compose:nuke** → after confirmation, removes the fixed project with `down --remove-orphans` without recreating its containers or starting services. Images and build caches are preserved.
- **mise run compose:up** / **compose:down** / **compose:restart** → control the stack lifecycle.
- **mise run compose:logs** → tails all logs; use `compose:logs:auth`, `:client` or `:resource` for one service.
- **mise run compose:ps** → shows Compose container status.
- **mise run compose:preflight** → checks system-test URLs and confirms that all three Compose services are reachable without changing them.
- **mise run compose:smoke** → verifies core availability and security scenarios against the already-running Compose stack.
- **mise run compose:e2e** → crosses the real Compose services: the authorization server issues a token and the resource server validates it before returning tasks.
- **mise run compose:check** → prints Compose diagnostics.

- **mise run compose:bootstrap:fresh** → deletes the runtime and then recreates it.
- **mise run compose:nuke** → deletes the runtime without recreating it.

### k3d Cluster

Use these commands to run the local stack in a k3d Kubernetes cluster.

- **mise run k3d:build-up** / **k3d:build-up:jvm** → build native/JVM images, create/start the cluster, import images and deploy Kubernetes manifests.
- **mise run k3d:bootstrap:fresh** → rebuilds images and, after confirmation, deletes and recreates the `dcrivella-auth` cluster. It is blocked while this project's Compose stack is active.
- **mise run k3d:up** → deploys using already-built images.
- **mise run k3d:render** → renders the local overlay with standalone Kustomize without applying it.
- **mise run k3d:cluster-stop** / **cluster-start** / **cluster-down** → control the cluster lifecycle.
- **mise run k3d:nuke** → after confirmation, deletes the fixed cluster without recreating it or starting services. Images and build caches are preserved.
- **mise run k3d:ps** → shows Kubernetes pods and services.
- **mise run k3d:preflight** → checks system-test URLs and confirms that all three k3d services are reachable without changing them.
- **mise run k3d:smoke** → verifies core availability and security scenarios against the already-running k3d stack.
- **mise run k3d:e2e** → crosses the real k3d services: the authorization server issues a token and the resource server validates it before returning tasks.
- **mise run k3d:logs** → tails all workload logs; use `k3d:logs:auth`, `:client` or `:resource` for one workload.

- **mise run k3d:cluster-down** → deletes the cluster.
- **mise run k3d:bootstrap:fresh** → deletes and recreates the runtime and refuses to run while Compose is active.
- **mise run k3d:nuke** → deletes the runtime without rebuilding, recreating or starting services.

Use **mise run nuke** to remove both runtimes with one confirmation. It
preflights every selected target before mutation, orders Compose before k3d,
continues after a partial cleanup failure, and preserves images and build caches.

## Project Notes

- [OAuth2 and OIDC Overview](docs/oauth2-oidc-overview.md) documents the OAuth2/OIDC behavior implemented by `auth-server`, `client-server` and `resource-server`.
- [Identity Providers and Multitenancy](docs/identity-providers-and-multitenancy.md) compares conceptual, not-yet-implemented Zitadel strategies for centralized identity, roles, tenant access metadata, enriched claims and cached domain resolution.
- [Browser Clients and Token Lifecycle](docs/browser-clients-and-token-lifecycle.md) covers refresh tokens, SPA/BFF tradeoffs, browser cookies, sessions, rotation, expiration and invalidation.
- [Container Images](docs/container-images.md) explains the repository's Paketo Buildpack flow and the historical Kaniko/current BuildKit comparison for Dockerfile builds.
- [Stack Commands](docs/stack-commands.md) maps every mise task to its Gradle, Docker Compose or Kubernetes behavior.
