#!/usr/bin/env bash

set -euo pipefail
set -E

if (($# > 1)); then
  echo "Usage: $0 [native|jvm]" >&2
  exit 2
fi

readonly requested_image_mode="${1:-native}"
readonly expected_cluster_name="dcrivella-auth"
readonly cluster_name="${K3D_CLUSTER_NAME:-${expected_cluster_name}}"

if [[ "${cluster_name}" != "${expected_cluster_name}" ]]; then
  echo "!! k3d:bootstrap:fresh only supports cluster '${expected_cluster_name}', not '${cluster_name}'." >&2
  exit 1
fi

readonly script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
readonly repository_root="$(cd -- "${script_dir}/.." && pwd -P)"
cd "${repository_root}"
IMAGE_MODE="${requested_image_mode}"
. scripts/image-mode.sh

if [[ "${IMAGE_MODE}" == jvm ]]; then
  readonly image_build_task="image:build:jvm"
  readonly k3d_up_task="k3d:up:jvm"
else
  readonly image_build_task="image:build"
  readonly k3d_up_task="k3d:up"
fi

readonly -a compose_command=(
  docker compose
  --env-file infra/compose/.env
  -f infra/compose/compose.yml
  -f infra/compose/compose.override.yml
)

if compose_services="$("${compose_command[@]}" ps --status running --services)"; then
  :
else
  status=$?
  echo "!! Could not inspect this project's Compose stack." >&2
  exit "${status}"
fi

if [[ -n "${compose_services}" ]]; then
  echo "!! The dcrivella-auth Compose stack has active services:" >&2
  while IFS= read -r service; do
    printf '   %s\n' "${service}" >&2
  done <<<"${compose_services}"
  echo "!! Stop it first with: mise run compose:down" >&2
  exit 1
fi

cat <<EOF
!! Destructive k3d bootstrap
   Cluster: ${cluster_name}
   Mode: ${IMAGE_MODE}
   Images:
     ${AUTH_SERVER_IMAGE}
     ${CLIENT_SERVER_IMAGE}
     ${RESOURCE_SERVER_IMAGE}

This deletes the cluster after rebuilding the application images, then recreates it.
The applications do not use a persistent data store.
EOF

printf "Continue? [y/N] "
if ! IFS= read -r confirmation; then
  confirmation=""
fi

case "${confirmation}" in
  y | Y) ;;
  *)
    echo "==> Fresh bootstrap cancelled; the cluster was not changed."
    exit 0
    ;;
esac

echo "==> Building all ${IMAGE_MODE} application images before deleting the current environment"
if mise run "${image_build_task}"; then
  :
else
  status=$?
  echo "!! Image build failed; the current cluster was preserved." >&2
  exit "${status}"
fi

if cluster_list="$(k3d cluster list)"; then
  :
else
  status=$?
  echo "!! Could not inspect k3d clusters; the current cluster was preserved." >&2
  exit "${status}"
fi

recovery_required=false
on_error() {
  local status=$?
  trap - ERR
  if [[ "${recovery_required}" == true ]]; then
    echo "!! Fresh bootstrap failed after cleanup began." >&2
    echo "!! After correcting the reported error, recover with: mise run ${k3d_up_task}" >&2
  fi
  exit "${status}"
}
trap on_error ERR

cluster_exists=false
while read -r listed_cluster _; do
  if [[ "${listed_cluster}" == "${cluster_name}" ]]; then
    cluster_exists=true
    break
  fi
done <<<"${cluster_list}"

recovery_required=true
if [[ "${cluster_exists}" == true ]]; then
  echo "==> Deleting k3d cluster ${cluster_name}"
  k3d cluster delete "${cluster_name}"
else
  echo "==> k3d cluster ${cluster_name} does not exist; skipping cluster deletion"
fi

echo "==> Creating a fresh k3d environment"
mise run "${k3d_up_task}"

recovery_required=false
trap - ERR
echo "==> Fresh k3d bootstrap completed"
