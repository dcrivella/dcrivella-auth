# Stack Commands

The root `Makefile` is a small wrapper. It delegates to `stack/makefile` so local commands can be run from the repository root.

Use `make` for normal local work. Use this page when you want to see the underlying Gradle or Docker Compose command.

For every supported target, the root `Makefile` runs:

```zsh
make -C stack -f makefile <target>
```

## Build Commands

`make build` runs:

```zsh
make build-auth
make build-client
make build-resource
```

`make build-auth` runs:

```zsh
cd ..
./gradlew :auth-server:clean :auth-server:bootBuildImage \
  -PBP_NATIVE_IMAGE=true \
  -Pspring-boot.build-image.imageName=$AUTH_SERVER_IMAGE \
  -Pversion=$AUTH_SERVER_IMAGE_TAG
```

`make build-client` runs:

```zsh
cd ..
./gradlew :client-server:clean :client-server:bootBuildImage \
  -PBP_NATIVE_IMAGE=true \
  -Pspring-boot.build-image.imageName=$CLIENT_SERVER_IMAGE \
  -Pversion=$CLIENT_SERVER_IMAGE_TAG
```

`make build-resource` runs:

```zsh
cd ..
./gradlew :resource-server:clean :resource-server:bootBuildImage \
  -PBP_NATIVE_IMAGE=true \
  -Pspring-boot.build-image.imageName=$RESOURCE_SERVER_IMAGE \
  -Pversion=$RESOURCE_SERVER_IMAGE_TAG
```

## Docker Compose Commands

The Compose commands run from the `stack/` directory.

`make up` runs:

```zsh
docker compose up -d --remove-orphans
```

`make up` does not build images. Use it for later runs when the images already exist locally. For a fresh checkout or after code changes, run `make build up`.

`make down` runs:

```zsh
docker compose down
```

`make restart` runs:

```zsh
make down
make up
```

`make logs` follows logs for the whole stack and runs:

```zsh
docker compose logs -f
```

`make logs-auth` runs:

```zsh
docker compose logs -f auth-server
```

`make logs-client` runs:

```zsh
docker compose logs -f client-server
```

`make logs-resource` runs:

```zsh
docker compose logs -f resource-server
```

`make logs-db` runs:

```zsh
docker compose logs -f db
```

`make ps` runs:

```zsh
docker compose ps
```

`make db-reset` runs:

```zsh
docker compose down -v
docker compose up -d --remove-orphans
```

This deletes the local Postgres volume.

## Diagnostics

`make check` prints the resolved image names, Docker Compose version, root Gradle wrapper status and module directory checks.
