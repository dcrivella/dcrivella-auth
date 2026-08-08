#!/usr/bin/env bash

set -euo pipefail

readonly compose_project="dcrivella-auth-stack"
readonly expected_cluster_name="dcrivella-auth"

usage() {
  echo "Usage: $0 {compose|k3d|all}" >&2
}

if (( $# != 1 )); then
  usage
  exit 2
fi

readonly mode=$1
selected_compose=false
selected_k3d=false

case "${mode}" in
  compose)
    selected_compose=true
    ;;
  k3d)
    selected_k3d=true
    ;;
  all)
    selected_compose=true
    selected_k3d=true
    ;;
  *)
    usage
    exit 2
    ;;
esac

readonly selected_compose selected_k3d
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

compose_volumes=""
cluster_exists=false
data_root=""
data_dir=""

preflight_compose() {
  local status

  if "${compose_command[@]}" config --quiet; then
    :
  else
    status=$?
    echo "!! Compose configuration validation failed." >&2
    return "${status}"
  fi

  if compose_volumes="$("${compose_command[@]}" config --volumes)"; then
    :
  else
    status=$?
    echo "!! Could not list the Compose volumes." >&2
    return "${status}"
  fi
}

validate_k3d_target() {
  local requested_cluster_name="${K3D_CLUSTER_NAME:-${expected_cluster_name}}"

  if [[ "${requested_cluster_name}" != "${expected_cluster_name}" ]]; then
    echo "!! k3d nuke only supports cluster '${expected_cluster_name}', not '${requested_cluster_name}'." >&2
    return 1
  fi

  if [[ -z "${HOME:-}" ]]; then
    echo "!! HOME must be set before removing k3d data." >&2
    return 1
  fi

  if [[ "${HOME}" != /* || "${HOME}" == / ]]; then
    echo "!! HOME must be an absolute, non-root path." >&2
    return 1
  fi

  data_root="${HOME%/}/.k3d-dcrivella-auth"
  data_dir="${data_root}/data"

  if [[ -L "${data_root}" || -L "${data_dir}" ]]; then
    echo "!! Refusing to delete k3d data through a symbolic link: ${data_dir}" >&2
    return 1
  fi

  if [[ -e "${data_root}" && ! -d "${data_root}" ]]; then
    echo "!! Expected the k3d state path to be a directory: ${data_root}" >&2
    return 1
  fi

  if [[ -e "${data_dir}" && ! -d "${data_dir}" ]]; then
    echo "!! Expected the k3d data path to be a directory: ${data_dir}" >&2
    return 1
  fi
}

inspect_k3d_cluster() {
  local cluster_list
  local listed_cluster
  local status

  if cluster_list="$(k3d cluster list)"; then
    :
  else
    status=$?
    echo "!! Could not inspect k3d clusters." >&2
    return "${status}"
  fi

  cluster_exists=false
  while read -r listed_cluster _; do
    if [[ "${listed_cluster}" == "${expected_cluster_name}" ]]; then
      cluster_exists=true
      break
    fi
  done <<<"${cluster_list}"
}

preflight_k3d() {
  if validate_k3d_target; then
    :
  else
    return $?
  fi

  inspect_k3d_cluster
}

print_compose_targets() {
  cat <<EOF
Compose:
  Project: ${compose_project}
  Removes: project containers, networks, orphan containers and declared volumes
  Declared volumes:
EOF

  if [[ -n "${compose_volumes}" ]]; then
    while IFS= read -r volume; do
      printf '    - %s\n' "${volume}"
    done <<<"${compose_volumes}"
  else
    echo "    - (none declared)"
  fi
}

print_k3d_targets() {
  local cluster_state="already absent"
  local data_state="already absent"

  if [[ "${cluster_exists}" == true ]]; then
    cluster_state="present"
  fi

  if [[ -e "${data_dir}" ]]; then
    data_state="present"
  fi

  cat <<EOF
k3d:
  Cluster: ${expected_cluster_name} (${cluster_state})
  Data:    ${data_dir} (${data_state})
EOF
}

print_targets() {
  echo "!! Destructive local environment removal"

  if [[ "${selected_compose}" == true ]]; then
    print_compose_targets
  fi

  if [[ "${selected_k3d}" == true ]]; then
    print_k3d_targets
  fi

  cat <<'EOF'

The selected runtimes and persistent data will be permanently removed and will
not be recreated. Local Docker images and build caches are preserved.
EOF
}

confirm_nuke() {
  local confirmation=""

  printf "Continue? [y/N] "
  if ! IFS= read -r confirmation; then
    confirmation=""
  fi

  case "${confirmation}" in
    y | Y)
      return 0
      ;;
    *)
      echo "==> Nuke cancelled; no resources were changed."
      return 1
      ;;
  esac
}

nuke_compose() {
  local status

  echo "==> Removing Compose project ${compose_project} and its declared volumes"
  if "${compose_command[@]}" down -v --remove-orphans; then
    echo "==> Compose runtime and data removed"
    return 0
  else
    status=$?
    echo "!! Compose cleanup failed." >&2
    return "${status}"
  fi
}

remove_k3d_data() {
  local status

  if [[ ! -e "${data_dir}" && ! -L "${data_dir}" ]]; then
    echo "==> No persisted k3d data found at ${data_dir}"
    return 0
  fi

  if rm -rf -- "${data_dir}"; then
    echo "==> Removed persisted k3d data at ${data_dir}"
    return 0
  fi

  echo "==> Normal removal failed; retrying the exact data directory with busybox:1.36"

  if [[ ! -e "${data_dir}" && ! -L "${data_dir}" ]]; then
    echo "==> Persisted k3d data was removed"
    return 0
  fi

  if [[ -L "${data_dir}" || ! -d "${data_dir}" ]]; then
    echo "!! Refusing the Docker fallback because the validated data directory changed type." >&2
    return 1
  fi

  if docker run --rm \
    --mount "type=bind,source=${data_dir},target=/data" \
    busybox:1.36 \
    sh -ceu 'find /data -mindepth 1 -maxdepth 1 -exec rm -rf -- {} \; && [ -z "$(find /data -mindepth 1 -maxdepth 1 -print -quit)" ]'; then
    :
  else
    status=$?
    echo "!! Docker fallback failed for ${data_dir}." >&2
    return "${status}"
  fi

  if [[ -L "${data_dir}" || ! -d "${data_dir}" ]]; then
    echo "!! The k3d data directory changed type during fallback cleanup." >&2
    return 1
  fi

  if rmdir -- "${data_dir}"; then
    echo "==> Removed persisted k3d data at ${data_dir}"
    return 0
  else
    status=$?
    echo "!! Cleared k3d data but could not remove the data directory itself: ${data_dir}" >&2
    return "${status}"
  fi
}

nuke_k3d() {
  local status

  if validate_k3d_target; then
    :
  else
    status=$?
    echo "!! k3d cleanup stopped before cluster deletion." >&2
    return "${status}"
  fi

  if inspect_k3d_cluster; then
    :
  else
    status=$?
    echo "!! k3d cleanup stopped before cluster deletion; persistent data was preserved." >&2
    return "${status}"
  fi

  if [[ "${cluster_exists}" == true ]]; then
    echo "==> Deleting k3d cluster ${expected_cluster_name}"
    if k3d cluster delete "${expected_cluster_name}"; then
      :
    else
      status=$?
      echo "!! Cluster deletion failed; persisted data at ${data_dir} was preserved." >&2
      return "${status}"
    fi
  else
    echo "==> k3d cluster ${expected_cluster_name} does not exist; skipping cluster deletion"
  fi

  if validate_k3d_target; then
    :
  else
    status=$?
    echo "!! Cluster cleanup completed, but persisted data was not removed." >&2
    return "${status}"
  fi

  if remove_k3d_data; then
    echo "==> k3d runtime and data removed"
    return 0
  else
    status=$?
    echo "!! k3d persistent data cleanup failed." >&2
    return "${status}"
  fi
}

nuke_all() {
  local compose_status=0
  local k3d_status=0

  if nuke_compose; then
    :
  else
    compose_status=$?
  fi

  if nuke_k3d; then
    :
  else
    k3d_status=$?
  fi

  if (( compose_status == 0 && k3d_status == 0 )); then
    echo "==> Compose and k3d runtimes and data removed"
    return 0
  fi

  echo "!! Global nuke completed with failures." >&2
  if (( compose_status != 0 )); then
    echo "!! After correcting the error, repeat: mise run compose:nuke" >&2
  fi
  if (( k3d_status != 0 )); then
    echo "!! After correcting the error, repeat: mise run k3d:nuke" >&2
  fi
  return 1
}

if [[ "${selected_compose}" == true ]]; then
  if preflight_compose; then
    :
  else
    status=$?
    echo "!! Preflight failed; no resources were changed." >&2
    exit "${status}"
  fi
fi

if [[ "${selected_k3d}" == true ]]; then
  if preflight_k3d; then
    :
  else
    status=$?
    echo "!! Preflight failed; no resources were changed." >&2
    exit "${status}"
  fi
fi

print_targets
if ! confirm_nuke; then
  exit 0
fi

case "${mode}" in
  compose)
    if nuke_compose; then
      exit 0
    else
      exit $?
    fi
    ;;
  k3d)
    if nuke_k3d; then
      exit 0
    else
      exit $?
    fi
    ;;
  all)
    if nuke_all; then
      exit 0
    else
      exit $?
    fi
    ;;
esac
