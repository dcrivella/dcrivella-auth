#!/usr/bin/env bash

set -euo pipefail

readonly test_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
readonly repository_root="$(cd -- "${test_dir}/../.." && pwd -P)"
readonly subject="${repository_root}/scripts/nuke.sh"
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

assert_output_contains() {
  local expected=$1
  [[ "${case_output}" == *"${expected}"* ]] || fail "${case_name}: output does not contain '${expected}'; output: ${case_output}"
}

assert_output_excludes() {
  local unexpected=$1
  [[ "${case_output}" != *"${unexpected}"* ]] || fail "${case_name}: output unexpectedly contains '${unexpected}'; output: ${case_output}"
}

assert_output_occurrences() {
  local expected_count=$1
  local expected=$2
  local count=0
  local remainder="${case_output}"
  while [[ "${remainder}" == *"${expected}"* ]]; do
    remainder="${remainder#*"${expected}"}"
    ((count += 1))
  done
  [[ "${count}" -eq "${expected_count}" ]] || fail "${case_name}: expected '${expected}' ${expected_count} time(s), got ${count}"
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

assert_no_mutations() {
  assert_log_excludes "<down>"
  assert_log_excludes "k3d <cluster> <delete>"
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
    local arguments="$*"
    if [[ "${arguments}" == *" config --quiet"* ]]; then
      return "${TEST_COMPOSE_CONFIG_STATUS}"
    fi
    if [[ "${arguments}" == *" down --remove-orphans"* ]]; then
      if (( TEST_COMPOSE_DOWN_STATUS == 0 )); then
        command rm -f -- "${STACK_MARKER}"
      fi
      return "${TEST_COMPOSE_DOWN_STATUS}"
    fi
  fi
  return 91
}

k3d() {
  log_call k3d "$@"
  if [[ "${1:-} ${2:-}" == "cluster list" ]]; then
    if (( TEST_K3D_LIST_STATUS == 0 )); then
      printf 'NAME SERVERS AGENTS LOADBALANCER\n'
      if [[ "${TEST_CLUSTER_EXISTS}" == true ]]; then
        printf 'dcrivella-auth 1/1 0/0 true\n'
      fi
    fi
    return "${TEST_K3D_LIST_STATUS}"
  fi
  if [[ "${1:-} ${2:-}" == "cluster delete" ]]; then
    if (( TEST_K3D_DELETE_STATUS == 0 )); then
      command rm -f -- "${CLUSTER_MARKER}"
    fi
    return "${TEST_K3D_DELETE_STATUS}"
  fi
  return 92
}

export -f docker k3d log_call

reset_case_config() {
  TEST_COMPOSE_CONFIG_STATUS=0
  TEST_COMPOSE_DOWN_STATUS=0
  TEST_COMPOSE_PRESENT=true
  TEST_CLUSTER_EXISTS=true
  TEST_K3D_DELETE_STATUS=0
  TEST_K3D_LIST_STATUS=0
  TEST_CLUSTER_NAME=dcrivella-auth
}

run_case() {
  case_name=$1
  local mode=$2
  local answer=$3
  local case_dir="${test_root}/${case_name}"
  case_log="${case_dir}/calls.log"
  stack_marker="${case_dir}/compose-stack"
  cluster_marker="${case_dir}/k3d-cluster"
  mkdir -p "${case_dir}"
  : >"${case_log}"
  if [[ "${TEST_COMPOSE_PRESENT}" == true ]]; then
    : >"${stack_marker}"
  fi
  if [[ "${TEST_CLUSTER_EXISTS}" == true ]]; then
    : >"${cluster_marker}"
  fi

  set +e
  case_output="$({
    printf '%s' "${answer}"
  } | CALL_LOG="${case_log}" \
    CLUSTER_MARKER="${cluster_marker}" \
    K3D_CLUSTER_NAME="${TEST_CLUSTER_NAME}" \
    STACK_MARKER="${stack_marker}" \
    TEST_CLUSTER_EXISTS="${TEST_CLUSTER_EXISTS}" \
    TEST_COMPOSE_CONFIG_STATUS="${TEST_COMPOSE_CONFIG_STATUS}" \
    TEST_COMPOSE_DOWN_STATUS="${TEST_COMPOSE_DOWN_STATUS}" \
    TEST_K3D_DELETE_STATUS="${TEST_K3D_DELETE_STATUS}" \
    TEST_K3D_LIST_STATUS="${TEST_K3D_LIST_STATUS}" \
    bash "${subject}" "${mode}" 2>&1)"
  case_status=$?
  set -e
}

readonly compose_prefix="docker <compose> <--project-name> <dcrivella-auth-stack> <--env-file> <infra/compose/.env> <-f> <infra/compose/compose.yml> <-f> <infra/compose/compose.override.yml>"

reset_case_config
run_case cancelled all $'yes\n'
assert_status 0
assert_output_contains "Nuke cancelled; no resources were changed"
assert_output_occurrences 1 "Continue? [y/N]"
assert_no_mutations
[[ -e "${stack_marker}" ]] || fail "${case_name}: Compose marker changed"
[[ -e "${cluster_marker}" ]] || fail "${case_name}: cluster marker changed"
echo "ok - only y or Y confirms; cancellation makes no mutations"

reset_case_config
run_case confirmed-all all $'Y\n'
assert_status 0
assert_output_occurrences 1 "Continue? [y/N]"
assert_output_contains "Project: dcrivella-auth-stack"
assert_output_contains "Local images and build caches"
assert_order "${compose_prefix} <down> <--remove-orphans>" "k3d <cluster> <delete> <dcrivella-auth>"
assert_log_excludes "<--rmi>"
assert_log_excludes "<prune>"
[[ ! -e "${stack_marker}" ]] || fail "${case_name}: Compose marker still exists"
[[ ! -e "${cluster_marker}" ]] || fail "${case_name}: cluster marker still exists"
echo "ok - global mode confirms once and removes Compose before k3d without touching images"

reset_case_config
TEST_COMPOSE_PRESENT=false
TEST_CLUSTER_EXISTS=false
run_case already-absent all $'y\n'
assert_status 0
assert_log_contains "${compose_prefix} <down> <--remove-orphans>"
assert_log_excludes "k3d <cluster> <delete>"
assert_output_contains "does not exist; nothing to remove"
echo "ok - already absent Compose and k3d runtimes succeed"

reset_case_config
TEST_COMPOSE_CONFIG_STATUS=17
run_case compose-preflight-failure all $'y\n'
assert_failure
assert_output_contains "Preflight failed; no resources were changed"
assert_output_occurrences 0 "Continue? [y/N]"
assert_no_mutations
assert_log_excludes "k3d <cluster> <list>"
echo "ok - Compose configuration failure aborts all before confirmation"

reset_case_config
TEST_K3D_LIST_STATUS=19
run_case k3d-preflight-failure all $'y\n'
assert_failure
assert_output_contains "Could not inspect k3d clusters"
assert_output_occurrences 0 "Continue? [y/N]"
assert_no_mutations
echo "ok - k3d inspection failure aborts all before mutations"

reset_case_config
TEST_COMPOSE_DOWN_STATUS=41
run_case compose-partial-failure all $'y\n'
assert_failure
assert_order "${compose_prefix} <down> <--remove-orphans>" "k3d <cluster> <delete> <dcrivella-auth>"
assert_output_contains "repeat: mise run compose:nuke"
assert_output_excludes "repeat: mise run k3d:nuke"
[[ -e "${stack_marker}" ]] || fail "${case_name}: failed Compose marker unexpectedly changed"
[[ ! -e "${cluster_marker}" ]] || fail "${case_name}: k3d cleanup did not continue"
echo "ok - partial Compose failure still runs k3d and reports the task to repeat"

reset_case_config
TEST_K3D_DELETE_STATUS=37
run_case k3d-partial-failure all $'y\n'
assert_failure
assert_output_contains "repeat: mise run k3d:nuke"
assert_output_excludes "repeat: mise run compose:nuke"
[[ ! -e "${stack_marker}" ]] || fail "${case_name}: Compose cleanup did not finish first"
[[ -e "${cluster_marker}" ]] || fail "${case_name}: failed cluster marker unexpectedly changed"
echo "ok - partial k3d failure reports its individual retry task"

reset_case_config
TEST_CLUSTER_NAME=another-cluster
run_case wrong-cluster all $'y\n'
assert_failure
assert_output_contains "only supports cluster 'dcrivella-auth'"
assert_no_mutations
echo "ok - non-configured cluster names are rejected before global mutations"
