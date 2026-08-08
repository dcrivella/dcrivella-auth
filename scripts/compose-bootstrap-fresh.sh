#!/usr/bin/env bash

set -euo pipefail
set -E

readonly compose_project="dcrivella-auth-stack"
readonly k3d_cluster_name="dcrivella-auth"
export COMPOSE_PROJECT_NAME="${compose_project}"
readonly script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
readonly repository_root="$(cd -- "${script_dir}/.." && pwd -P)"
cd "${repository_root}"

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

if compose_volumes="$("${compose_command[@]}" config --volumes)"; then
  :
else
  status=$?
  echo "!! Could not list the Compose volumes; no resources were changed." >&2
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
   Named volumes removed by Compose:
EOF

if [[ -n "${compose_volumes}" ]]; then
  while IFS= read -r volume; do
    printf '   - %s\n' "${volume}"
  done <<<"${compose_volumes}"
else
  echo "   - (none declared)"
fi

cat <<'EOF'

This rebuilds all application images, then recreates the Compose containers,
networks and named volumes. All data in the listed volumes is permanently lost.
EOF

printf "Continue? [y/N] "
if ! IFS= read -r confirmation; then
  confirmation=""
fi

case "${confirmation}" in
  y | Y) ;;
  *)
    echo "==> Fresh bootstrap cancelled; the Compose stack and volumes were not changed."
    exit 0
    ;;
esac

echo "==> Building all application images before deleting the current Compose environment"
if mise run image:build; then
  :
else
  status=$?
  echo "!! Image build failed; the current Compose stack and volumes were preserved." >&2
  exit "${status}"
fi

cleanup_started=false
on_error() {
  local status=$?
  trap - ERR
  if [[ "${cleanup_started}" == true ]]; then
    echo "!! Fresh Compose bootstrap failed after cleanup began." >&2
    echo "!! After correcting the reported error, recover with: mise run compose:up" >&2
  fi
  exit "${status}"
}
trap on_error ERR

echo "==> Removing Compose containers, networks and named volumes"
cleanup_started=true
"${compose_command[@]}" down -v --remove-orphans

echo "==> Creating a fresh Compose environment"
mise run compose:up

cleanup_started=false
trap - ERR
echo "==> Fresh Compose bootstrap completed"
