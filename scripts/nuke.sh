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

cluster_exists=false

preflight_compose() {
  local status

  if "${compose_command[@]}" config --quiet; then
    return 0
  else
    status=$?
    echo "!! Compose configuration validation failed." >&2
    return "${status}"
  fi
}

validate_k3d_target() {
  local requested_cluster_name="${K3D_CLUSTER_NAME:-${expected_cluster_name}}"

  if [[ "${requested_cluster_name}" != "${expected_cluster_name}" ]]; then
    echo "!! k3d nuke only supports cluster '${expected_cluster_name}', not '${requested_cluster_name}'." >&2
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
  validate_k3d_target && inspect_k3d_cluster
}

print_targets() {
  local cluster_state="already absent"

  echo "!! Destructive local runtime removal"
  if [[ "${selected_compose}" == true ]]; then
    cat <<EOF
Compose:
  Project: ${compose_project}
  Removes: project containers, networks and orphan containers
EOF
  fi

  if [[ "${selected_k3d}" == true ]]; then
    if [[ "${cluster_exists}" == true ]]; then
      cluster_state="present"
    fi
    cat <<EOF
k3d:
  Cluster: ${expected_cluster_name} (${cluster_state})
EOF
  fi

  cat <<'EOF'

The selected runtimes will be removed and will not be recreated. The
applications do not use a persistent data store. Local images and build caches
are preserved.
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

  echo "==> Removing Compose project ${compose_project}"
  if "${compose_command[@]}" down --remove-orphans; then
    echo "==> Compose runtime removed"
    return 0
  else
    status=$?
    echo "!! Compose cleanup failed." >&2
    return "${status}"
  fi
}

nuke_k3d() {
  local status

  if validate_k3d_target && inspect_k3d_cluster; then
    :
  else
    status=$?
    echo "!! k3d cleanup stopped before cluster deletion." >&2
    return "${status}"
  fi

  if [[ "${cluster_exists}" == false ]]; then
    echo "==> k3d cluster ${expected_cluster_name} does not exist; nothing to remove"
    return 0
  fi

  echo "==> Deleting k3d cluster ${expected_cluster_name}"
  if k3d cluster delete "${expected_cluster_name}"; then
    echo "==> k3d runtime removed"
    return 0
  else
    status=$?
    echo "!! Cluster deletion failed." >&2
    return "${status}"
  fi
}

nuke_all() {
  local compose_status=0
  local k3d_status=0

  nuke_compose || compose_status=$?
  nuke_k3d || k3d_status=$?

  if (( compose_status == 0 && k3d_status == 0 )); then
    echo "==> Compose and k3d runtimes removed"
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

if [[ "${selected_compose}" == true ]] && ! preflight_compose; then
  echo "!! Preflight failed; no resources were changed." >&2
  exit 1
fi

if [[ "${selected_k3d}" == true ]] && ! preflight_k3d; then
  echo "!! Preflight failed; no resources were changed." >&2
  exit 1
fi

print_targets
if ! confirm_nuke; then
  exit 0
fi

case "${mode}" in
  compose)
    nuke_compose
    ;;
  k3d)
    nuke_k3d
    ;;
  all)
    nuke_all
    ;;
esac
