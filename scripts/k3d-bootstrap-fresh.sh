#!/usr/bin/env bash

set -euo pipefail
set -E

readonly expected_cluster_name="dcrivella-auth"
readonly cluster_name="${K3D_CLUSTER_NAME:-${expected_cluster_name}}"

if [[ "${cluster_name}" != "${expected_cluster_name}" ]]; then
  echo "!! k3d:bootstrap:fresh only supports cluster '${expected_cluster_name}', not '${cluster_name}'." >&2
  exit 1
fi

readonly script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
readonly repository_root="$(cd -- "${script_dir}/.." && pwd -P)"
cd "${repository_root}"

: "${HOME:?HOME must be set}"
if [[ "${HOME}" != /* ]]; then
  echo "!! HOME must be an absolute path." >&2
  exit 1
fi

readonly data_root="${HOME%/}/.k3d-dcrivella-auth"
readonly data_dir="${data_root}/data"

if [[ -L "${data_root}" || -L "${data_dir}" ]]; then
  echo "!! Refusing to delete k3d data through a symbolic link: ${data_dir}" >&2
  exit 1
fi

if [[ -e "${data_root}" && ! -d "${data_root}" ]]; then
  echo "!! Expected the k3d state path to be a directory: ${data_root}" >&2
  exit 1
fi

if [[ -e "${data_dir}" && ! -d "${data_dir}" ]]; then
  echo "!! Expected the k3d data path to be a directory: ${data_dir}" >&2
  exit 1
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
   Data:    ${data_dir}

This permanently deletes the cluster and all persisted k3d data at that path.
EOF

printf "Continue? [y/N] "
if ! IFS= read -r confirmation; then
  confirmation=""
fi

case "${confirmation}" in
  y | Y) ;;
  *)
    echo "==> Fresh bootstrap cancelled; cluster and data were not changed."
    exit 0
    ;;
esac

echo "==> Building all application images before deleting the current environment"
if mise run image:build; then
  :
else
  status=$?
  echo "!! Image build failed; the current cluster and persistent data were preserved." >&2
  exit "${status}"
fi

if cluster_list="$(k3d cluster list)"; then
  :
else
  status=$?
  echo "!! Could not inspect k3d clusters; the current cluster and persistent data were preserved." >&2
  exit "${status}"
fi

recovery_required=false
on_error() {
  local status=$?
  trap - ERR
  if [[ "${recovery_required}" == true ]]; then
    echo "!! Fresh bootstrap failed after cleanup began." >&2
    echo "!! After correcting the reported error, recover with: mise run k3d:up" >&2
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

if [[ "${cluster_exists}" == true ]]; then
  recovery_required=true
  echo "==> Deleting k3d cluster ${cluster_name}"
  k3d cluster delete "${cluster_name}"
else
  echo "==> k3d cluster ${cluster_name} does not exist; skipping cluster deletion"
fi

recovery_required=true
if [[ ! -e "${data_dir}" ]]; then
  echo "==> No persisted k3d data found at ${data_dir}"
elif rm -rf -- "${data_dir}"; then
  echo "==> Removed persisted k3d data at ${data_dir}"
else
  echo "==> Normal removal failed; retrying the exact data directory with busybox:1.36"
  if [[ ! -e "${data_dir}" ]]; then
    echo "==> Persisted k3d data was removed"
  elif [[ ! -d "${data_dir}" || -L "${data_dir}" ]]; then
    echo "!! Refusing the Docker fallback because the validated data directory changed type." >&2
    false
  else
    docker run --rm \
      --mount "type=bind,source=${data_dir},target=/data" \
      busybox:1.36 \
      sh -ceu 'find /data -mindepth 1 -maxdepth 1 -exec rm -rf -- {} \; && [ -z "$(find /data -mindepth 1 -maxdepth 1 -print -quit)" ]'
    echo "==> Cleared persisted k3d data at ${data_dir}"
  fi
fi

echo "==> Creating a fresh k3d environment"
mise run k3d:up

recovery_required=false
trap - ERR
echo "==> Fresh k3d bootstrap completed"
