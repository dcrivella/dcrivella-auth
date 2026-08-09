#!/usr/bin/env bash

set -euo pipefail
set -E

if (($# > 1)); then
  echo "Usage: $0 [native|jvm]" >&2
  exit 2
fi

readonly requested_image_mode="${1:-native}"
readonly compose_project="dcrivella-auth-stack"
readonly k3d_cluster_name="dcrivella-auth"
export COMPOSE_PROJECT_NAME="${compose_project}"
readonly script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
readonly repository_root="$(cd -- "${script_dir}/.." && pwd -P)"
cd "${repository_root}"
IMAGE_MODE="${requested_image_mode}"
. scripts/image-mode.sh

if [[ "${IMAGE_MODE}" == jvm ]]; then
  readonly image_build_task="image:build:jvm"
  readonly compose_up_task="compose:up:jvm"
else
  readonly image_build_task="image:build"
  readonly compose_up_task="compose:up"
fi

readonly -a compose_command=(
  docker compose
  --project-name "${compose_project}"
  --env-file infra/compose/.env
  -f infra/compose/compose.yml
  -f infra/compose/compose.override.yml
)

if "${compose_command[@]}" config --quiet; then
  :
else
  status=$?
  echo "!! Compose configuration validation failed; no resources were changed." >&2
  exit "${status}"
fi

if active_k3d_containers="$(
  docker ps \
    --filter "label=k3d.cluster=${k3d_cluster_name}" \
    --format '{{.Names}}'
)"; then
  :
else
  status=$?
  echo "!! Could not inspect active containers for k3d cluster '${k3d_cluster_name}'." >&2
  exit "${status}"
fi

if [[ -n "${active_k3d_containers}" ]]; then
  echo "!! The ${k3d_cluster_name} k3d cluster has active containers:" >&2
  while IFS= read -r container; do
    printf '   %s\n' "${container}" >&2
  done <<<"${active_k3d_containers}"
  echo "!! Stop it first with: mise run k3d:cluster-stop" >&2
  exit 1
fi

cat <<EOF
!! Destructive Compose bootstrap
   Project: ${compose_project}
   Mode: ${IMAGE_MODE}
   Images:
     ${AUTH_SERVER_IMAGE}
     ${CLIENT_SERVER_IMAGE}
     ${RESOURCE_SERVER_IMAGE}
EOF

cat <<'EOF'

This rebuilds all application images, then recreates the Compose containers,
networks and services. The applications do not use a persistent data store.
EOF

printf "Continue? [y/N] "
if ! IFS= read -r confirmation; then
  confirmation=""
fi

case "${confirmation}" in
  y | Y) ;;
  *)
    echo "==> Fresh bootstrap cancelled; the Compose stack was not changed."
    exit 0
    ;;
esac

echo "==> Building all ${IMAGE_MODE} application images before deleting the current Compose environment"
if mise run "${image_build_task}"; then
  :
else
  status=$?
  echo "!! Image build failed; the current Compose stack was preserved." >&2
  exit "${status}"
fi

cleanup_started=false
on_error() {
  local status=$?
  trap - ERR
  if [[ "${cleanup_started}" == true ]]; then
    echo "!! Fresh Compose bootstrap failed after cleanup began." >&2
    echo "!! After correcting the reported error, recover with: mise run ${compose_up_task}" >&2
  fi
  exit "${status}"
}
trap on_error ERR

echo "==> Removing Compose containers and networks"
cleanup_started=true
"${compose_command[@]}" down --remove-orphans

echo "==> Creating a fresh Compose environment"
mise run "${compose_up_task}"

cleanup_started=false
trap - ERR
echo "==> Fresh Compose bootstrap completed"
