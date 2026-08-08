#!/usr/bin/env bash

set -euo pipefail

readonly test_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
readonly repository_root="$(cd -- "${test_dir}/../.." && pwd -P)"
readonly subject="${repository_root}/scripts/k3d-bootstrap-fresh.sh"
readonly test_root="$(mktemp -d)"

cleanup() {
  command rm -rf -- "${test_root}"
}
trap cleanup EXIT

fail() {
  echo "not ok - $*" >&2
  exit 1
}

assert_status() {
  local expected=$1
  [[ "${case_status}" -eq "${expected}" ]] ||
    fail "${case_name}: expected status ${expected}, got ${case_status}; output: ${case_output}"
}

assert_failure() {
  [[ "${case_status}" -ne 0 ]] || fail "${case_name}: expected a non-zero status"
}

assert_log_contains() {
  local expected=$1
  local log
  log="$(<"${case_log}")"
  [[ "${log}" == *"${expected}"* ]] ||
    fail "${case_name}: log does not contain '${expected}'; log: ${log}"
}

assert_log_excludes() {
  local unexpected=$1
  local log
  log="$(<"${case_log}")"
  [[ "${log}" != *"${unexpected}"* ]] ||
    fail "${case_name}: log unexpectedly contains '${unexpected}'; log: ${log}"
}

assert_output_contains() {
  local expected=$1
  [[ "${case_output}" == *"${expected}"* ]] ||
    fail "${case_name}: output does not contain '${expected}'; output: ${case_output}"
}

assert_order() {
  local previous=0
  local expected line
  for expected in "$@"; do
    line="$(awk -v expected="${expected}" 'index($0, expected) { print NR; exit }' "${case_log}")"
    [[ -n "${line}" ]] || fail "${case_name}: missing ordered call '${expected}'"
    ((line > previous)) || fail "${case_name}: call '${expected}' is out of order"
    previous=${line}
  done
}

log_call() {
  local command=$1
  shift
  {
    printf '%s' "${command}"
    printf ' <%s>' "$@"
    printf '\n'
  } >>"${CALL_LOG}"
}

docker() {
  log_call docker "$@"

  if [[ "${1:-}" == compose ]]; then
    if [[ "${TEST_COMPOSE_ACTIVE}" == true ]]; then
      printf 'db\nauth-server\n'
    fi
    return "${TEST_COMPOSE_STATUS}"
  fi

  if [[ "${1:-}" == run ]]; then
    local fallback_data_dir="${HOME%/}/.k3d-dcrivella-auth/data"
    local arguments="$*"
    [[ "${arguments}" == *"type=bind,source=${fallback_data_dir},target=/data"* ]] || return 91
    [[ "${arguments}" == *"busybox:1.36"* ]] || return 92
    command rm -f -- "${fallback_data_dir}/protected"
    return "${TEST_DOCKER_RUN_STATUS}"
  fi

  return 93
}

k3d() {
  log_call k3d "$@"

  if [[ "${1:-} ${2:-}" == "cluster list" ]]; then
    printf 'NAME SERVERS AGENTS LOADBALANCER\n'
    if [[ "${TEST_CLUSTER_EXISTS}" == true ]]; then
      printf 'dcrivella-auth 1/1 0/0 true\n'
    fi
    return "${TEST_K3D_LIST_STATUS}"
  fi

  if [[ "${1:-} ${2:-}" == "cluster delete" ]]; then
    return "${TEST_K3D_DELETE_STATUS}"
  fi

  return 94
}

mise() {
  log_call mise "$@"

  if [[ "${1:-} ${2:-}" == "run image:build" ]]; then
    return "${TEST_BUILD_STATUS}"
  fi

  if [[ "${1:-} ${2:-}" == "run k3d:up" ]]; then
    return "${TEST_UP_STATUS}"
  fi

  return 95
}

rm() {
  log_call rm "$@"
  local target="${!#}"
  if [[ "${TEST_RM_FAIL}" == true && "${target}" == "${HOME%/}/.k3d-dcrivella-auth/data" ]]; then
    return 1
  fi
  command rm "$@"
}

export -f docker k3d log_call mise rm

run_case() {
  case_name=$1
  local answer=$2
  local compose_active=$3
  local build_status=$4
  local cluster_exists=$5
  local rm_fails=$6
  local cluster_name=${7:-dcrivella-auth}
  local up_status=${8:-0}

  case_home="${test_root}/${case_name}/home"
  case_log="${test_root}/${case_name}/calls.log"
  mkdir -p "${case_home}/.k3d-dcrivella-auth/data"
  : >"${case_log}"
  : >"${case_home}/.k3d-dcrivella-auth/data/protected"

  set +e
  case_output="$({
    printf '%s' "${answer}"
  } | HOME="${case_home}" \
    CALL_LOG="${case_log}" \
    K3D_CLUSTER_NAME="${cluster_name}" \
    TEST_BUILD_STATUS="${build_status}" \
    TEST_CLUSTER_EXISTS="${cluster_exists}" \
    TEST_COMPOSE_ACTIVE="${compose_active}" \
    TEST_COMPOSE_STATUS=0 \
    TEST_DOCKER_RUN_STATUS=0 \
    TEST_K3D_DELETE_STATUS=0 \
    TEST_K3D_LIST_STATUS=0 \
    TEST_RM_FAIL="${rm_fails}" \
    TEST_UP_STATUS="${up_status}" \
    bash "${subject}" 2>&1)"
  case_status=$?
  set -e
}

run_case compose-active $'y\n' true 0 true false
assert_failure
assert_output_contains "mise run compose:down"
assert_log_contains "docker <compose> <--env-file> <infra/compose/.env> <-f> <infra/compose/compose.yml> <-f> <infra/compose/compose.override.yml> <ps> <--status> <running> <--services>"
assert_log_excludes "mise <"
assert_log_excludes "k3d <"
assert_log_excludes "rm <"
[[ -e "${case_home}/.k3d-dcrivella-auth/data/protected" ]] || fail "${case_name}: data changed"
echo "ok - active Compose blocks before mutations"

run_case declined $'n\n' false 0 true false
assert_status 0
assert_output_contains "Fresh bootstrap cancelled"
assert_log_excludes "mise <"
assert_log_excludes "k3d <"
assert_log_excludes "rm <"
[[ -e "${case_home}/.k3d-dcrivella-auth/data/protected" ]] || fail "${case_name}: data changed"
echo "ok - negative confirmation cancels without mutations"

run_case build-failure $'y\n' false 23 true false
assert_status 23
assert_output_contains "current cluster and persistent data were preserved"
assert_log_contains "mise <run> <image:build>"
assert_log_excludes "k3d <"
assert_log_excludes "rm <"
[[ -e "${case_home}/.k3d-dcrivella-auth/data/protected" ]] || fail "${case_name}: data changed"
echo "ok - failed image build preserves cluster and data"

run_case cluster-absent $'y\n' false 0 false false
assert_status 0
assert_output_contains "does not exist; skipping cluster deletion"
assert_log_excludes "k3d <cluster> <delete>"
assert_order "mise <run> <image:build>" "k3d <cluster> <list>" "rm <-rf> <-->" "mise <run> <k3d:up>"
[[ ! -e "${case_home}/.k3d-dcrivella-auth/data" ]] || fail "${case_name}: data directory still exists"
echo "ok - absent cluster is skipped safely"

run_case confirmed $'y\n' false 0 true false
assert_status 0
assert_order "mise <run> <image:build>" "k3d <cluster> <delete> <dcrivella-auth>" "rm <-rf> <-->" "mise <run> <k3d:up>"
echo "ok - confirmed bootstrap keeps the required operation order"

run_case fallback $'y\n' false 0 false true
assert_status 0
assert_log_contains "rm <-rf> <--> <${case_home}/.k3d-dcrivella-auth/data>"
assert_log_contains "docker <run> <--rm> <--mount> <type=bind,source=${case_home}/.k3d-dcrivella-auth/data,target=/data> <busybox:1.36>"
assert_log_excludes "source=${case_home}/.k3d-dcrivella-auth,target=/data"
[[ ! -e "${case_home}/.k3d-dcrivella-auth/data/protected" ]] || fail "${case_name}: fallback left protected content"
echo "ok - Docker fallback is bounded to the validated data directory"

run_case recreate-failure $'y\n' false 0 true false dcrivella-auth 31
assert_status 31
assert_output_contains "recover with: mise run k3d:up"
assert_order "k3d <cluster> <delete> <dcrivella-auth>" "rm <-rf> <-->" "mise <run> <k3d:up>"
echo "ok - failures after cleanup return recovery guidance"

run_case wrong-cluster $'y\n' false 0 true false another-cluster
assert_failure
assert_output_contains "only supports cluster 'dcrivella-auth'"
assert_log_excludes "docker <"
assert_log_excludes "mise <"
assert_log_excludes "k3d <"
assert_log_excludes "rm <"
echo "ok - non-configured cluster names are rejected"
