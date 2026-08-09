# Container Images

This project builds container images with Spring Boot's `bootBuildImage` Gradle task.

`bootBuildImage` is provided by the Spring Boot Gradle plugin.

The task builds an OCI image using Cloud Native Buildpacks. In this repository, each application module configures Paketo builder images:

- native image builds use `paketobuildpacks/builder-jammy-buildpackless-tiny`
- JVM image builds use `paketobuildpacks/builder-jammy-tiny`

No Dockerfile is required for this buildpack flow.

## Paketo, Kaniko And BuildKit

Paketo is a buildpack system. It builds an image by detecting the app type and assembling a runtime image automatically. In this project, Spring Boot's `bootBuildImage` task uses Paketo Buildpacks and does not require a Dockerfile.

Kaniko is a Dockerfile builder. It builds an image by executing the steps in a Dockerfile, usually inside CI without needing a Docker daemon. The [Kaniko repository](https://github.com/GoogleContainerTools/kaniko) was archived on June 3, 2025, so this comparison is retained for historical context rather than as a recommendation for new build pipelines.

[BuildKit](https://docs.docker.com/build/buildkit/) is a current alternative when a build must be driven by a Dockerfile. It is the build backend used by modern Docker Build workflows and can also run independently.

Main difference:

```text
Paketo: app source/JAR -> buildpacks -> image
Kaniko: Dockerfile + context -> image (historical; project archived)
BuildKit: Dockerfile + context -> image
```

Use Paketo when you want convention-based Java/Spring images with less image-maintenance work.

For new Dockerfile-based builds, evaluate BuildKit and the security/runtime constraints of the target CI environment. Existing Kaniko pipelines can still explain why this historical comparison appears in older architecture discussions, but Kaniko is no longer actively maintained.

## How This Repo Builds Images

The mise image tasks run module-qualified Gradle tasks such as:

```zsh
./gradlew :auth-server:clean :auth-server:bootBuildImage \
  -PBP_NATIVE_IMAGE=true \
  -Pspring-boot.build-image.imageName=dcrivella/auth-server:DEV-SNAPSHOT \
  -Pversion=DEV-SNAPSHOT
```

The `PBP_NATIVE_IMAGE` project property controls whether the image is native or JVM-based. The `version` property must match the image tag loaded by the mise task:

- `-PBP_NATIVE_IMAGE=true` builds a native image
- `-PBP_NATIVE_IMAGE=false` builds a JVM image

The public image tags are explicit:

- native: `dcrivella/<module>:1.0.0-native`
- JVM: `dcrivella/<module>:1.0.0-jvm`

For the fastest local development loop, use the JVM flow:

```zsh
mise run compose:build-up:jvm
```

That builds JVM images and starts the Docker Compose stack. Use
`mise run k3d:build-up:jvm` for the equivalent k3d flow. The unsuffixed
`compose:build-up` and `k3d:build-up` commands remain the GraalVM native-image
validation paths.

Smoke and end-to-end commands are mode-independent. Run the existing
`compose:smoke`/`compose:e2e` or `k3d:smoke`/`k3d:e2e` task against the active
runtime.
