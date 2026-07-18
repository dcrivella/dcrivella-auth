# Stack Commands

The repository uses [mise](https://mise.jdx.dev/) as its tool and task runner.
Run tasks from the repository root:

```zsh
mise install
mise tasks
mise run <task>
```

## Gradle Commands

- `mise run build` runs `./gradlew build`.
- `mise run test` runs `./gradlew test`.
- `mise run clean` runs `./gradlew clean`.
- `mise run format` runs `./gradlew spotlessApply`.
- `mise run lint` runs `./gradlew spotlessCheck`.
- `mise run dev:auth`, `dev:client` and `dev:resource` run each module with `bootRun`.

The Gradle wrapper remains the source of truth for the Gradle version; mise
manages the Java 25, k3d, standalone Kustomize and kubectl toolchain but does
not install Gradle separately.

## Container Images

`mise run image:build` builds all three native images sequentially. Individual
images can be built with:

- `mise run image:build:auth`
- `mise run image:build:client`
- `mise run image:build:resource`

Each task loads image tags from `infra/compose/.env` and invokes the module's
`bootBuildImage` task with `-PBP_NATIVE_IMAGE=true`, the resolved image name and
`-Pversion=<module image tag>`.

## Docker Compose

- `mise run compose:build-up` builds all images and starts the stack.
- `mise run compose:up` starts the stack with existing images.
- `mise run compose:down` stops the stack while preserving named volumes.
- `mise run compose:restart` stops and starts the stack.
- `mise run compose:logs` follows all logs.
- `mise run compose:logs:auth` follows the auth-server service.
- `mise run compose:logs:client` follows the client-server service.
- `mise run compose:logs:resource` follows the resource-server service.
- `mise run compose:logs:db` follows the Postgres service.
- `mise run compose:ps` shows service status.
- `mise run compose:check` prints image, Compose, wrapper and module diagnostics.
- `mise run compose:db-reset` deletes named volumes and starts a fresh stack.

The reset task waits three seconds after warning that it will delete the local
Postgres volume. Use `compose:down` when the database must be preserved.

All Compose tasks use both `infra/compose/compose.yml` and
`infra/compose/compose.override.yml`, with `infra/compose/.env`.

## k3d / Kubernetes

- `mise run k3d:build-up` builds images and runs the full k3d deployment.
- `mise run k3d:up` creates or selects the cluster, loads existing images and deploys.
- `mise run k3d:cluster-up` creates the cluster when it does not exist.
- `mise run k3d:cluster-start` starts the existing cluster.
- `mise run k3d:cluster-stop` stops the cluster without deleting it.
- `mise run k3d:cluster-down` deletes the cluster.
- `mise run k3d:load` imports the configured application images.
- `mise run k3d:render` renders the local Kustomize overlay to stdout.
- `mise run k3d:deploy` pipes the standalone Kustomize render into kubectl and waits for rollouts.
- `mise run k3d:logs` follows all workload logs.
- `mise run k3d:logs:auth` follows the auth-server workload.
- `mise run k3d:logs:client` follows the client-server workload.
- `mise run k3d:logs:resource` follows the resource-server workload.
- `mise run k3d:logs:db` follows the Postgres workload.
- `mise run k3d:ps` shows pods, services and PVCs.
- `mise run k3d:check` prints tool and path diagnostics.
- `mise run k3d:db-reset` deletes the Postgres PVC and recreates Postgres.

The local overlay reads `infra/db/init/001-provision_auth.sql` directly when it
generates the `postgres-init` ConfigMap. Standalone Kustomize is invoked with
`LoadRestrictionsNone` so this trusted repository-local file can be shared
without maintaining an overlay copy. The reset task warns and waits three
seconds before deleting Kubernetes data.

The k3d stack uses `http://host.k3d.internal:9000` as its issuer. On Linux, add
the host alias when required for browser redirects:

```zsh
echo "127.0.0.1 host.k3d.internal" | sudo tee -a /etc/hosts
```
