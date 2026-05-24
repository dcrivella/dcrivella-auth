# Stack Commands

The root `Makefile` is a small wrapper. It delegates to `infra/makefile` so local commands can be run from the repository root.

Use `make` for normal local work. Use this page when you want to see the underlying Gradle or Docker Compose command.

For every supported target, the root `Makefile` runs:

```zsh
make -C infra -f makefile <target>
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

The Compose commands are defined in `infra/makefile` and run Docker Compose with files from `infra/compose`.

`make compose-build-up` runs:

```zsh
make build
make compose-up
```

`make compose-up` runs:

```zsh
docker compose up -d --remove-orphans
```

`make compose-up` does not build images. Use it for later runs when the images already exist locally. For a fresh checkout or after code changes, run `make compose-build-up`.

`make compose-down` runs:

```zsh
docker compose down
```

`make compose-restart` runs:

```zsh
make compose-down
make compose-up
```

`make compose-logs` follows logs for the whole Compose stack and runs:

```zsh
docker compose logs -f
```

`make compose-logs-auth` runs:

```zsh
docker compose logs -f auth-server
```

`make compose-logs-client` runs:

```zsh
docker compose logs -f client-server
```

`make compose-logs-resource` runs:

```zsh
docker compose logs -f resource-server
```

`make compose-logs-db` runs:

```zsh
docker compose logs -f db
```

`make compose-ps` runs:

```zsh
docker compose ps
```

`make compose-db-reset` runs:

```zsh
docker compose down -v
docker compose up -d --remove-orphans
```

This deletes the local Postgres volume.

The shorter legacy targets `make up`, `make down`, `make logs`, `make ps`, `make db-reset` and `make check` still map to the Compose workflow.

## Diagnostics

`make check` prints the resolved image names, Docker Compose version, root Gradle wrapper status and module directory checks.

## k3d / Kubernetes Commands

The k3d commands also run through the root `Makefile`, but create a local Kubernetes cluster instead of using Docker Compose. They require `k3d` and `kubectl` with Kustomize support for `kubectl apply -k`.

`make k3d-build-up` runs:

```zsh
make build
make k3d-up
```

`make k3d-up` runs:

```zsh
make k3d-cluster-up
make k3d-load
make k3d-deploy
```

`make k3d-cluster-up` creates the k3d cluster from:

```zsh
infra/k3d/cluster-config.yaml
```

It does not deploy application manifests by itself. Application deployment happens in `make k3d-deploy`.

`make k3d-load` imports the locally built application images into the k3d cluster:

```zsh
k3d image import $AUTH_SERVER_IMAGE $CLIENT_SERVER_IMAGE $RESOURCE_SERVER_IMAGE -c dcrivella-auth
```

`make k3d-deploy` applies the local Kustomize overlay:

```zsh
kubectl apply -k infra/k8s/overlays/local
```

Before applying the overlay, the Make target copies `infra/db/init/001-provision_auth.sql` into the overlay so Kustomize can generate the `postgres-init` ConfigMap without maintaining a second SQL source. After applying the overlay, the target sets the Deployment image tags from `infra/compose/.env`.

`make k3d-cluster-stop` stops the existing k3d cluster without deleting Kubernetes objects or PVCs.

`make k3d-cluster-start` starts the existing k3d cluster and switches kubectl to its context.

`make k3d-cluster-down` deletes the k3d cluster.

`make k3d-db-reset` deletes the k3d Postgres PVC and recreates Postgres. This wipes the k3d database.

The k3d stack uses `http://host.k3d.internal:9000` as the issuer URL so pods can resolve the Authorization Server through k3d's cluster DNS. Add this host mapping for browser redirects:

```zsh
echo "127.0.0.1 host.k3d.internal" | sudo tee -a /etc/hosts
```
