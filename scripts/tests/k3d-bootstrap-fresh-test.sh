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
  [[ "${log}" == *"${expected}"* ]] || fail "${case_name}: log does not contain '${expected}'; log: ${log}"
}

assert_log_excludes() {
  local unexpected=$1
  local log
  log="$(<"${case_log}")"
  [[ "${log}" != *"${unexpected}"* ]] || fail "${case_name}: log unexpectedly contains '${unexpected}'; log: ${log}"
}

assert_output_contains() {
  local expected=$1
  [[ "${case_output}" == *"${expected}"* ]] || fail "${case_name}: output does not contain '${expected}'; output: ${case_output}"
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
      printf 'auth-server\n'
    fi
    return "${TEST_COMPOSE_STATUS}"
  fi
  return 91
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
  return 92
}

mise() {
  log_call mise "$@"
  if [[ "${1:-}" == run && ("${2:-}" == image:build || "${2:-}" == image:build:jvm) ]]; then
    return "${TEST_BUILD_STATUS}"
  fi
  if [[ "${1:-}" == run && ("${2:-}" == k3d:up || "${2:-}" == k3d:up:jvm) ]]; then
    return "${TEST_UP_STATUS}"
  fi
  return 93
}

export -f docker k3d log_call mise

run_case() {
  case_name=$1
  local answer=$2
  local compose_active=$3
  local build_status=$4
  local cluster_exists=$5
  local cluster_name=${6:-dcrivella-auth}
  local up_status=${7:-0}
  local delete_status=${8:-0}
  local image_mode=${9:-}
  local -a subject_args=()
  if [[ -n "${image_mode}" ]]; then
    subject_args+=("${image_mode}")
  fi

  local case_dir="${test_root}/${case_name}"
  case_log="${case_dir}/calls.log"
  mkdir -p "${case_dir}"
  : >"${case_log}"

  set +e
  case_output="$({
    printf '%s' "${answer}"
  } | CALL_LOG="${case_log}" \
    K3D_CLUSTER_NAME="${cluster_name}" \
    TEST_BUILD_STATUS="${build_status}" \
    TEST_CLUSTER_EXISTS="${cluster_exists}" \
    TEST_COMPOSE_ACTIVE="${compose_active}" \
    TEST_COMPOSE_STATUS=0 \
    TEST_K3D_DELETE_STATUS="${delete_status}" \
    TEST_K3D_LIST_STATUS=0 \
    TEST_UP_STATUS="${up_status}" \
    bash "${subject}" "${subject_args[@]}" 2>&1)"
  case_status=$?
  set -e
}

run_case invalid-mode '' false 0 true dcrivella-auth 0 0 invalid
assert_status 2
assert_output_contains "Unsupported image mode 'invalid'"
assert_log_excludes "docker <"
assert_log_excludes "mise <"
assert_log_excludes "k3d <"
echo "ok - invalid image mode fails before external commands"

run_case compose-active $'y\n' true 0 true
assert_failure
assert_output_contains "mise run compose:down"
assert_log_contains "docker <compose> <--env-file> <infra/compose/.env> <-f> <infra/compose/compose.yml> <-f> <infra/compose/compose.override.yml> <ps> <--status> <running> <--services>"
assert_log_excludes "mise <"
assert_log_excludes "k3d <"
echo "ok - active Compose blocks before mutations"

run_case declined $'n\n' false 0 true
assert_status 0
assert_output_contains "Fresh bootstrap cancelled"
assert_log_excludes "mise <"
assert_log_excludes "k3d <"
echo "ok - negative confirmation cancels without mutations"

run_case build-failure $'y\n' false 23 true
assert_status 23
assert_output_contains "current cluster was preserved"
assert_log_contains "mise <run> <image:build>"
assert_log_excludes "k3d <"
echo "ok - failed image build preserves the cluster"

run_case cluster-absent $'y\n' false 0 false
assert_status 0
assert_output_contains "does not exist; skipping cluster deletion"
assert_log_excludes "k3d <cluster> <delete>"
assert_order "mise <run> <image:build>" "k3d <cluster> <list>" "mise <run> <k3d:up>"
echo "ok - absent cluster is recreated normally"

run_case confirmed $'y\n' false 0 true
assert_status 0
assert_output_contains "Mode: native"
assert_output_contains "dcrivella/auth-server:1.0.0-native"
assert_order "mise <run> <image:build>" "k3d <cluster> <list>" "k3d <cluster> <delete> <dcrivella-auth>" "mise <run> <k3d:up>"
echo "ok - confirmed bootstrap keeps the required operation order"

run_case jvm-confirmed $'y\n' false 0 true dcrivella-auth 0 0 jvm
assert_status 0
assert_output_contains "Mode: jvm"
assert_output_contains "dcrivella/auth-server:1.0.0-jvm"
assert_order "mise <run> <image:build:jvm>" "k3d <cluster> <list>" "k3d <cluster> <delete> <dcrivella-auth>" \
  "mise <run> <k3d:up:jvm>"
echo "ok - JVM build completes before deletion and selects JVM up"

run_case jvm-declined $'n\n' false 0 true dcrivella-auth 0 0 jvm
assert_status 0
assert_log_excludes "mise <run> <image:build:jvm>"
assert_log_excludes "k3d <"
echo "ok - JVM cancellation leaves the cluster unchanged"

run_case delete-failure $'y\n' false 0 true dcrivella-auth 0 29
assert_status 29
assert_output_contains "recover with: mise run k3d:up"
assert_log_excludes "mise <run> <k3d:up>"
echo "ok - cluster deletion failure returns recovery guidance"

run_case recreate-failure $'y\n' false 0 true dcrivella-auth 31
assert_status 31
assert_output_contains "recover with: mise run k3d:up"
assert_order "k3d <cluster> <delete> <dcrivella-auth>" "mise <run> <k3d:up>"
echo "ok - recreation failure returns recovery guidance"

run_case jvm-recreate-failure $'y\n' false 0 true dcrivella-auth 31 0 jvm
assert_status 31
assert_output_contains "recover with: mise run k3d:up:jvm"
assert_order "mise <run> <image:build:jvm>" "k3d <cluster> <delete> <dcrivella-auth>" "mise <run> <k3d:up:jvm>"
echo "ok - JVM recreation failure selects JVM recovery guidance"

run_case wrong-cluster $'y\n' false 0 true another-cluster
assert_failure
assert_output_contains "only supports cluster 'dcrivella-auth'"
assert_log_excludes "docker <"
assert_log_excludes "mise <"
assert_log_excludes "k3d <"
echo "ok - non-configured cluster names are rejected"
