#!/usr/bin/env bash

set -uo pipefail

readonly compose_project="dcrivella-auth-stack"
readonly k3d_cluster="dcrivella-auth"
readonly -a compose_command=(
  docker compose
  --project-name "${compose_project}"
  --env-file infra/compose/.env
  -f infra/compose/compose.yml
  -f infra/compose/compose.override.yml
)

usage() {
  cat <<'EOF'
Usage: scripts/actions.sh <list|graph|dry-run|all>
       scripts/actions.sh job <job-id>
EOF
}

fail() {
  echo "Error: $*" >&2
  return 1
}

run_act() {
  unset GITHUB_TOKEN
  act workflow_dispatch --input force_all=true "$@"
}

preflight_runtime() {
  local compose_containers
  local k3d_containers

  if ! command -v docker >/dev/null 2>&1; then
    fail "Docker is required for jobs that run the ephemeral Compose stack."
    return 1
  fi

  if ! docker info >/dev/null 2>&1; then
    fail "Docker Engine is not available."
    return 1
  fi

  if ! docker compose version >/dev/null 2>&1; then
    fail "Docker Compose v2 is not available."
    return 1
  fi

  if ! "${compose_command[@]}" config --quiet >/dev/null; then
    fail "The dcrivella-auth-stack Compose configuration is invalid."
    return 1
  fi

  if ! compose_containers="$(
    docker ps --all --quiet --filter "label=com.docker.compose.project=${compose_project}"
  )"; then
    fail "Could not inspect existing ${compose_project} containers."
    return 1
  fi

  if [[ -n "${compose_containers}" ]]; then
    fail "Compose project '${compose_project}' already has containers; remove them with 'mise run compose:down' before running this job."
    return 1
  fi

  if ! k3d_containers="$(
    docker ps --quiet --filter "label=k3d.cluster=${k3d_cluster}"
  )"; then
    fail "Could not inspect active ${k3d_cluster} k3d containers."
    return 1
  fi

  if [[ -n "${k3d_containers}" ]]; then
    fail "k3d cluster '${k3d_cluster}' is active; stop it with 'mise run k3d:cluster-stop' before running this job."
    return 1
  fi
}

# shellcheck disable=SC2329 # Invoked indirectly by the EXIT trap below.
cleanup_runtime() {
  local workflow_status=$1
  local teardown_status=0

  trap - EXIT INT TERM HUP
  echo "==> Removing the temporary ${compose_project} Compose stack"
  "${compose_command[@]}" down --remove-orphans || teardown_status=$?

  if ((teardown_status != 0)); then
    echo "Error: failed to remove the temporary Compose stack (status ${teardown_status})." >&2
  else
    echo "==> Temporary Compose stack removed; images and build caches were preserved"
  fi

  if ((workflow_status != 0)); then
    exit "${workflow_status}"
  fi
  exit "${teardown_status}"
}

if (($# == 0)); then
  usage >&2
  exit 2
fi

mode=$1
shift
needs_runtime=false
act_arguments=()

case "${mode}" in
  list)
    (($# == 0)) || {
      usage >&2
      exit 2
    }
    act_arguments+=(--list)
    ;;
  graph)
    (($# == 0)) || {
      usage >&2
      exit 2
    }
    act_arguments+=(--graph)
    ;;
  dry-run)
    (($# == 0)) || {
      usage >&2
      exit 2
    }
    act_arguments+=(--dryrun)
    ;;
  job)
    (($# == 1)) || {
      usage >&2
      exit 2
    }
    readonly job_id=$1
    act_arguments+=(--job "${job_id}")
    if [[ "${job_id}" == compose-system-tests-jvm || "${job_id}" == ci-success ]]; then
      needs_runtime=true
    fi
    ;;
  all)
    (($# == 0)) || {
      usage >&2
      exit 2
    }
    needs_runtime=true
    ;;
  *)
    usage >&2
    exit 2
    ;;
esac

if ! command -v act >/dev/null 2>&1; then
  echo "Error: act is not installed; run 'mise install'." >&2
  exit 1
fi

if [[ "${needs_runtime}" == false ]]; then
  run_act "${act_arguments[@]}"
  exit $?
fi

preflight_runtime || exit $?

echo "==> act will build JVM application images and create a temporary ${compose_project} Compose stack"
echo "==> The stack will be removed afterwards; Docker images and build caches will be preserved"

trap 'cleanup_runtime "$?"' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP

run_act "${act_arguments[@]}"
exit $?
