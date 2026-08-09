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
- `mise run test:all` runs Gradle unit/integration/architecture tests and the local runtime automation tests.
- `mise run test:scripts` runs six local, non-destructive shell test harnesses with stubbed external commands; it does not modify a real Compose or k3d runtime and is not run by GitHub Actions.
- `mise run ci:system` runs preflight, smoke, M2M and browser end-to-end tests against an already-running Compose stack.
- `mise run ci:all` mirrors all GitHub Actions CI phases, including `ci:system`; it requires an already-running Compose stack but does not manage its lifecycle or include `test:scripts`.
- `mise run clean` runs `./gradlew clean`.
- `mise run format` runs `./gradlew spotlessApply`.
- `mise run lint` runs `./gradlew spotlessCheck`.
- `mise run dev:auth`, `dev:client` and `dev:resource` run each module with `bootRun`.

The Gradle wrapper remains the source of truth for the Gradle version; mise
manages Java 25, Node 24.19.0, k3d, standalone Kustomize and kubectl but does
not install Gradle separately.

`gradle.properties` enables the configuration cache with incompatibilities set
to fail. It also pins the Kotlin compiler's embedded idempotence-check rate to
its documented default (`1000`) so equivalent Gradle invocations can reuse the
same cache entry across daemons.

## Browser OAuth Tests

Browser execution task names start with the selected runtime (`mock`, `compose`,
or `k3d`). The `playwright` prefix remains reserved for tool operations.

- `mise run playwright:install` installs the isolated npm dependencies and Chromium for Playwright 1.62.1.
- `mise run mock:playwright` builds a normal client-server JAR and runs the three browser scenarios headlessly against mock OIDC and resource HTTP systems; Docker is not used.
- `mise run mock:playwright:ui` builds the same JAR and opens the same mock scenarios in the interactive Playwright UI.
- `mise run compose:playwright` and `mise run compose:playwright:ui` run the same real browser flow headlessly or in Playwright UI against an already-running Compose stack and verify the Docker issuer.
- `mise run k3d:playwright` and `mise run k3d:playwright:ui` run the same real browser flow headlessly or in Playwright UI against an already-running k3d stack and verify the k3d issuer.
- `mise run playwright:report` opens the most recent HTML report.

Commands without `:ui` execute automatically in headless mode. The matching `:ui` commands pass `--ui` to Playwright, opening its interactive test explorer and visible Chromium without changing the profile, URLs, preflight or scenarios.

The four real tasks only preflight and test the selected runtime. They never build images or start, restart, seed, stop or remove services. Run only the task matching the runtime that currently owns ports 9000, 8080 and 8081. Browser traces, screenshots, videos and automatic page snapshots are disabled in both modes because the client home page displays live token values.

## Container Images

The commands without a suffix remain the native-image flow. They build the
`1.0.0-native` tags sequentially:

- `mise run image:build:auth`
- `mise run image:build:client`
- `mise run image:build:resource`
- `mise run image:build`

The JVM equivalents build the `1.0.0-jvm` tags:

- `mise run image:build:auth:jvm`
- `mise run image:build:client:jvm`
- `mise run image:build:resource:jvm`
- `mise run image:build:jvm`

`scripts/image-mode.sh` accepts only `native` or `jvm`, loads the configured
native tags from `infra/compose/.env`, and exports one consistent set of image
references for Gradle, Compose, k3d import and Kubernetes deployment. It passes
`BP_NATIVE_IMAGE=true` for native builds and `false` for JVM builds. A trailing
`-jvm` is never duplicated.

Use JVM images for the faster local edit/build/run loop. Use native images when
the purpose is specifically to validate GraalVM compilation and native runtime
behavior.

## Runtime Removal Semantics

| Command | Runtime result | Recreation | Images and build caches |
| --- | --- | --- | --- |
| `compose:down` | Removes Compose containers and project networks | No | Preserved |
| `k3d:cluster-down` | Deletes the cluster | No | Preserved |
| `compose:bootstrap:fresh` / `k3d:bootstrap:fresh` | Removes the selected runtime | Yes, after rebuilding application images | Preserved, with application images rebuilt |
| `compose:nuke` / `k3d:nuke` | Removes the selected runtime | No | Preserved |
| `nuke` | Removes both runtimes | No | Preserved |

Every `nuke` task validates and displays all selected targets before requiring
interactive `[y/N]` confirmation. Only `y` or `Y` proceeds; there is no flag or
environment-variable bypass.

## Docker Compose

- `mise run compose:build-up` builds native images and starts the stack.
- `mise run compose:build-up:jvm` builds JVM images and starts the stack.
- `mise run compose:bootstrap:fresh` rebuilds native images, removes the fixed Compose project, then recreates the complete environment.
- `mise run compose:bootstrap:fresh:jvm` performs the same guarded recreation with JVM images.
- `mise run compose:nuke` removes the fixed Compose project without rebuilding or recreating it.
- `mise run compose:up` starts the stack with existing native images.
- `mise run compose:up:jvm` starts the stack with existing JVM images.
- `mise run compose:down` stops and removes the stack.
- `mise run compose:restart` stops and starts the stack.
- `mise run compose:logs` follows all logs.
- `mise run compose:logs:auth` follows the auth-server service.
- `mise run compose:logs:client` follows the client-server service.
- `mise run compose:logs:resource` follows the resource-server service.
- `mise run compose:ps` shows service status.
- `mise run compose:check` prints image, Compose, wrapper and module diagnostics.

`compose:bootstrap:fresh` is the destructive full-runtime option. It validates
the fixed `dcrivella-auth-stack` configuration, refuses to proceed while
containers from the `dcrivella-auth` k3d cluster are running, and requires
interactive `[y/N]` confirmation. The confirmation displays the selected mode
and all three image references. It builds those images before removing anything,
then runs Compose `down --remove-orphans` and the matching `compose:up` task,
recreating the containers and networks. If recreation fails after cleanup
begins, recover with `compose:up` or `compose:up:jvm` for the selected mode.

`compose:nuke` uses the same fixed `dcrivella-auth-stack` project, environment
file and two Compose files. Before confirmation it validates the resolved
configuration. Once confirmed, it runs `down --remove-orphans` and stops:
containers, project networks and orphans are removed, while images and caches remain. An
already absent project is a successful no-op from the user's perspective.

All Compose tasks use both `infra/compose/compose.yml` and
`infra/compose/compose.override.yml`, with `infra/compose/.env`.

## k3d / Kubernetes

- `mise run k3d:build-up` builds native images and runs the full k3d deployment.
- `mise run k3d:build-up:jvm` builds JVM images and runs the full k3d deployment.
- `mise run k3d:bootstrap:fresh` rebuilds native images, deletes the fixed cluster, then recreates the full k3d environment.
- `mise run k3d:bootstrap:fresh:jvm` performs the same guarded recreation with JVM images.
- `mise run k3d:nuke` deletes the fixed cluster without rebuilding or recreating it.
- `mise run k3d:up` creates or selects the cluster, loads existing native images and deploys.
- `mise run k3d:up:jvm` creates or selects the cluster, loads existing JVM images and deploys.
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
- `mise run k3d:ps` shows pods and services.
- `mise run k3d:check` prints tool and path diagnostics.

`k3d:build-up` is the non-destructive rebuild/deploy option: it retains the
existing cluster. `k3d:bootstrap:fresh` is the destructive option for a
completely new runtime. It only accepts the configured
`dcrivella-auth` cluster, refuses to proceed while this project's Compose
services are active, builds all three images before deleting anything, and
requires an interactive `[y/N]` confirmation. Once confirmed, it deletes the
cluster before delegating recreation and rollout waits to the matching `k3d:up`
task. Deployment renders the selected native or JVM images before applying the
manifests. A new deployment or image-tag change receives only its required
rollout; an unchanged deployment is restarted only when needed to activate an
image rebuilt under the same tag.

The client and resource init containers wait for the exact configured
`ISSUER_URL`, including the `host.k3d.internal` alias. They therefore do not
start while the internal auth service is reachable but the issuer address used
by Spring Security is still unavailable through k3d DNS.

The existing `compose:smoke`, `compose:e2e`, `k3d:smoke` and `k3d:e2e` tasks
operate against whichever mode is already running; they do not need image-mode
variants.

`k3d:nuke` only accepts cluster `dcrivella-auth`. Its preflight inspects the
fixed cluster before confirmation, then deletes it when present. An already
absent cluster is a successful no-op.

`mise run nuke` performs the Compose and k3d preflights before its single
confirmation, then removes Compose first and k3d second. If one runtime fails
after mutation begins, cleanup of the other is still attempted; the command
returns non-zero and identifies `compose:nuke` or `k3d:nuke` as the task to
repeat. Already absent runtimes are successful, and no nuke mode uses
`--rmi` or a prune command.

Compose remains independent and provides equivalent `compose:bootstrap:fresh`
and `compose:nuke` tasks. Each destructive bootstrap blocks while the other
runtime is active because it recreates services; the individual nuke tasks do
not start services and therefore do not block the other runtime.

All OAuth clients, users, authorizations, signing keys and tasks are in memory,
so neither runtime creates a database or persistent application volume.

The k3d stack uses `http://host.k3d.internal:9000` as its issuer. On Linux, add
the host alias when required for browser redirects:

```zsh
echo "127.0.0.1 host.k3d.internal" | sudo tee -a /etc/hosts
```
