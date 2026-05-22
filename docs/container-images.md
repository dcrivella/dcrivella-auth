# Container Images

This project builds container images with Spring Boot's `bootBuildImage` Gradle task.

`bootBuildImage` is provided by the Spring Boot Gradle plugin.

The task builds an OCI image using Cloud Native Buildpacks. In this repository, each application module configures Paketo builder images:

- native image builds use `paketobuildpacks/builder-jammy-buildpackless-tiny`
- JVM image builds use `paketobuildpacks/builder-jammy-tiny`

No Dockerfile is required for this buildpack flow.

## Paketo vs Kaniko

Paketo is a buildpack system. It builds an image by detecting the app type and assembling a runtime image automatically. In this project, Spring Boot's `bootBuildImage` task uses Paketo Buildpacks and does not require a Dockerfile.

Kaniko is a Dockerfile builder. It builds an image by executing the steps in a Dockerfile, usually inside CI without needing a Docker daemon.

Main difference:

```text
Paketo: app source/JAR -> buildpacks -> image
Kaniko: Dockerfile + context -> image
```

Use Paketo when you want convention-based Java/Spring images with less image-maintenance work.

Use Kaniko when you need full Dockerfile control or your CI cannot run a Docker daemon but still needs Dockerfile builds.

## How This Repo Builds Images

The root `Makefile` delegates image builds to `stack/makefile`. The stack makefile runs module-qualified Gradle tasks such as:

```zsh
./gradlew :auth-server:clean :auth-server:bootBuildImage \
  -PBP_NATIVE_IMAGE=true \
  -Pspring-boot.build-image.imageName=dcrivella/auth-server:DEV-SNAPSHOT
```

The `BP_NATIVE_IMAGE` project property controls whether the image is native or JVM-based:

- `-PBP_NATIVE_IMAGE=true` builds a native image
- `-PBP_NATIVE_IMAGE=false` builds a JVM image

For local development, the common path is:

```zsh
make build up
```

That builds the images and starts the Docker Compose stack.
