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

## Runtime Removal Semantics

| Command | Runtime and data result | Recreation | Images and build caches |
| --- | --- | --- | --- |
| `compose:down` | Removes Compose containers and project networks; preserves named volumes | No | Preserved |
| `k3d:cluster-down` | Deletes the cluster; preserves `$HOME/.k3d-dcrivella-auth/data` | No | Preserved |
| `compose:bootstrap:fresh` / `k3d:bootstrap:fresh` | Removes the selected runtime and its persistent data | Yes, after rebuilding application images | Preserved, with application images rebuilt |
| `compose:nuke` / `k3d:nuke` | Removes the selected runtime and its persistent data | No | Preserved |
| `nuke` | Removes both runtimes and both persistent data stores | No | Preserved |

Every `nuke` task validates and displays all selected targets before requiring
interactive `[y/N]` confirmation. Only `y` or `Y` proceeds; there is no flag or
environment-variable bypass.

## Docker Compose

- `mise run compose:build-up` builds all images and starts the stack.
- `mise run compose:bootstrap:fresh` rebuilds images, removes the fixed Compose project and its declared volumes, then recreates the complete environment.
- `mise run compose:nuke` removes the fixed Compose project and its declared volumes without rebuilding or recreating it.
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

`compose:build-up` is the non-destructive rebuild option and preserves the
database. The reset task is the quick database-only path: it waits three seconds
after warning, removes the local Postgres volume and restarts with existing
images. Use `compose:down` when the database must be preserved.

`compose:bootstrap:fresh` is the destructive full-runtime option. It validates
the fixed `dcrivella-auth-stack` configuration, lists its declared volumes
(currently `db-data`), refuses to proceed while containers from the
`dcrivella-auth` k3d cluster are running, and requires interactive `[y/N]`
confirmation. It builds all three images before removing anything, then runs
Compose `down -v --remove-orphans` and `compose:up`, recreating the containers,
networks and database. If recreation fails after cleanup begins, recover with
`mise run compose:up` after correcting the reported error.

`compose:nuke` uses the same fixed `dcrivella-auth-stack` project, environment
file and two Compose files. Before confirmation it validates the resolved
configuration and lists declared volumes (currently `db-data`). Once confirmed,
it runs `down -v --remove-orphans` and stops: containers, project networks,
orphans and declared volumes are removed, while images and caches remain. An
already absent project is a successful no-op from the user's perspective.

All Compose tasks use both `infra/compose/compose.yml` and
`infra/compose/compose.override.yml`, with `infra/compose/.env`.

## k3d / Kubernetes

- `mise run k3d:build-up` builds images and runs the full k3d deployment.
- `mise run k3d:bootstrap:fresh` rebuilds images, deletes the fixed cluster and its persisted data, then recreates the full k3d environment.
- `mise run k3d:nuke` deletes the fixed cluster and its persisted data without rebuilding or recreating it.
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

`k3d:build-up` is the non-destructive rebuild/deploy option: it retains the
existing cluster and database. `k3d:bootstrap:fresh` is the destructive option
for a completely new environment. It only accepts the configured
`dcrivella-auth` cluster, refuses to proceed while this project's Compose
services are active, builds all three images before deleting anything, and
requires an interactive `[y/N]` confirmation. Once confirmed, it deletes the
cluster and all content under `$HOME/.k3d-dcrivella-auth/data`, including
orphaned PVC data, before delegating recreation and rollout waits to `k3d:up`.

`k3d:nuke` only accepts cluster `dcrivella-auth`. Its preflight inspects the
cluster and rejects symbolic links or unexpected types at
`$HOME/.k3d-dcrivella-auth/data`. Once confirmed, it deletes the cluster when
present and only then removes that exact data directory. A failed cluster
deletion preserves data that may still be mounted. If normal removal encounters
root-owned content, the fallback mounts only that directory into `busybox:1.36`
for cleanup. The parent `$HOME/.k3d-dcrivella-auth` directory may remain empty.

`mise run nuke` performs the Compose and k3d preflights before its single
confirmation, then removes Compose first and k3d second. If one runtime fails
after mutation begins, cleanup of the other is still attempted; the command
returns non-zero and identifies `compose:nuke` or `k3d:nuke` as the task to
repeat. Already absent runtimes and data are successful, and no nuke mode uses
`--rmi` or a prune command.

Compose remains independent and provides equivalent `compose:bootstrap:fresh`
and `compose:nuke` tasks. Each destructive bootstrap blocks while the other
runtime is active because it recreates services; the individual nuke tasks do
not start services and therefore do not block the other runtime.
`compose:db-reset` continues to reset only the Compose database runtime.

The k3d stack uses `http://host.k3d.internal:9000` as its issuer. On Linux, add
the host alias when required for browser redirects:

```zsh
echo "127.0.0.1 host.k3d.internal" | sudo tee -a /etc/hosts
```
