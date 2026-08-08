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
  [[ "${case_output}" == *"${expected}"* ]] ||
    fail "${case_name}: output does not contain '${expected}'; output: ${case_output}"
}

assert_output_excludes() {
  local unexpected=$1
  [[ "${case_output}" != *"${unexpected}"* ]] ||
    fail "${case_name}: output unexpectedly contains '${unexpected}'; output: ${case_output}"
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

  [[ "${count}" -eq "${expected_count}" ]] ||
    fail "${case_name}: expected '${expected}' ${expected_count} time(s), got ${count}; output: ${case_output}"
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

assert_order() {
  local previous=0
  local expected
  local line

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
  assert_log_excludes "rm <-rf>"
  assert_log_excludes "rmdir <--"
  assert_log_excludes "docker <run>"
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

    if [[ "${arguments}" == *" config --volumes"* ]]; then
      if (( TEST_COMPOSE_VOLUMES_STATUS == 0 )); then
        printf '%s\n' "${TEST_COMPOSE_VOLUMES}"
      fi
      return "${TEST_COMPOSE_VOLUMES_STATUS}"
    fi

    if [[ "${arguments}" == *" down -v --remove-orphans"* ]]; then
      if (( TEST_COMPOSE_DOWN_STATUS == 0 )); then
        command rm -f -- "${STACK_MARKER}"
      fi
      return "${TEST_COMPOSE_DOWN_STATUS}"
    fi

    return 91
  fi

  if [[ "${1:-}" == run ]]; then
    local fallback_data_dir="${HOME%/}/.k3d-dcrivella-auth/data"
    local arguments="$*"

    [[ "${arguments}" == *"type=bind,source=${fallback_data_dir},target=/data"* ]] || return 92
    [[ "${arguments}" == *"busybox:1.36"* ]] || return 93

    if (( TEST_DOCKER_RUN_STATUS == 0 )); then
      command rm -f -- "${fallback_data_dir}/protected"
    fi
    return "${TEST_DOCKER_RUN_STATUS}"
  fi

  return 94
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

rmdir() {
  log_call rmdir "$@"
  command rmdir "$@"
}

export -f docker k3d log_call rm rmdir

reset_case_config() {
  TEST_COMPOSE_CONFIG_STATUS=0
  TEST_COMPOSE_DOWN_STATUS=0
  TEST_COMPOSE_PRESENT=true
  TEST_COMPOSE_VOLUMES=db-data
  TEST_COMPOSE_VOLUMES_STATUS=0
  TEST_CLUSTER_EXISTS=true
  TEST_DATA_STATE=present
  TEST_DOCKER_RUN_STATUS=0
  TEST_HOME_VALUE=""
  TEST_K3D_DELETE_STATUS=0
  TEST_K3D_LIST_STATUS=0
  TEST_RM_FAIL=false
  TEST_CLUSTER_NAME=dcrivella-auth
}

prepare_data_path() {
  local state=$1
  local state_root="${case_home}/.k3d-dcrivella-auth"
  local symlink_target="${case_dir}/symlink-target"

  case "${state}" in
    absent)
      mkdir -p "${state_root}"
      ;;
    present)
      mkdir -p "${state_root}/data"
      : >"${state_root}/data/protected"
      ;;
    root-symlink)
      mkdir -p "${symlink_target}/data"
      : >"${symlink_target}/data/protected"
      ln -s "${symlink_target}" "${state_root}"
      ;;
    data-symlink)
      mkdir -p "${state_root}" "${symlink_target}"
      : >"${symlink_target}/protected"
      ln -s "${symlink_target}" "${state_root}/data"
      ;;
    data-file)
      mkdir -p "${state_root}"
      : >"${state_root}/data"
      ;;
    *)
      fail "unknown test data state: ${state}"
      ;;
  esac
}

run_case() {
  case_name=$1
  local mode=$2
  local answer=$3

  case_dir="${test_root}/${case_name}"
  case_home="${case_dir}/home"
  case_log="${case_dir}/calls.log"
  stack_marker="${case_dir}/compose-stack"
  cluster_marker="${case_dir}/k3d-cluster"
  mkdir -p "${case_home}"
  : >"${case_log}"

  if [[ "${TEST_COMPOSE_PRESENT}" == true ]]; then
    : >"${stack_marker}"
  fi
  if [[ "${TEST_CLUSTER_EXISTS}" == true ]]; then
    : >"${cluster_marker}"
  fi
  prepare_data_path "${TEST_DATA_STATE}"

  local effective_home="${TEST_HOME_VALUE:-${case_home}}"

  set +e
  case_output="$({
    printf '%s' "${answer}"
  } | HOME="${effective_home}" \
    CALL_LOG="${case_log}" \
    CLUSTER_MARKER="${cluster_marker}" \
    K3D_CLUSTER_NAME="${TEST_CLUSTER_NAME}" \
    STACK_MARKER="${stack_marker}" \
    TEST_CLUSTER_EXISTS="${TEST_CLUSTER_EXISTS}" \
    TEST_COMPOSE_CONFIG_STATUS="${TEST_COMPOSE_CONFIG_STATUS}" \
    TEST_COMPOSE_DOWN_STATUS="${TEST_COMPOSE_DOWN_STATUS}" \
    TEST_COMPOSE_VOLUMES="${TEST_COMPOSE_VOLUMES}" \
    TEST_COMPOSE_VOLUMES_STATUS="${TEST_COMPOSE_VOLUMES_STATUS}" \
    TEST_DOCKER_RUN_STATUS="${TEST_DOCKER_RUN_STATUS}" \
    TEST_K3D_DELETE_STATUS="${TEST_K3D_DELETE_STATUS}" \
    TEST_K3D_LIST_STATUS="${TEST_K3D_LIST_STATUS}" \
    TEST_RM_FAIL="${TEST_RM_FAIL}" \
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
[[ -e "${case_home}/.k3d-dcrivella-auth/data/protected" ]] || fail "${case_name}: data changed"
echo "ok - only y or Y confirms; cancellation makes no mutations"

reset_case_config
run_case confirmed-all all $'Y\n'
assert_status 0
assert_output_occurrences 1 "Continue? [y/N]"
assert_output_contains "Project: dcrivella-auth-stack"
assert_output_contains "- db-data"
assert_output_contains "Local Docker images and build caches are preserved"
assert_order \
  "${compose_prefix} <down> <-v> <--remove-orphans>" \
  "k3d <cluster> <delete> <dcrivella-auth>" \
  "rm <-rf> <--> <${case_home}/.k3d-dcrivella-auth/data>"
assert_log_excludes "<--rmi>"
assert_log_excludes "docker <image>"
assert_log_excludes "docker <rmi>"
assert_log_excludes "<prune>"
assert_log_excludes "mise <"
[[ ! -e "${stack_marker}" ]] || fail "${case_name}: Compose marker still exists"
[[ ! -e "${cluster_marker}" ]] || fail "${case_name}: cluster marker still exists"
[[ ! -e "${case_home}/.k3d-dcrivella-auth/data" ]] || fail "${case_name}: data directory still exists"
echo "ok - global mode confirms once and removes Compose before k3d without touching images"

reset_case_config
TEST_COMPOSE_PRESENT=false
TEST_CLUSTER_EXISTS=false
TEST_DATA_STATE=absent
run_case already-absent all $'y\n'
assert_status 0
assert_log_contains "${compose_prefix} <down> <-v> <--remove-orphans>"
assert_log_excludes "k3d <cluster> <delete>"
assert_log_excludes "rm <-rf>"
assert_output_contains "does not exist; skipping cluster deletion"
assert_output_contains "No persisted k3d data found"
echo "ok - already absent Compose and k3d environments succeed"

reset_case_config
TEST_COMPOSE_CONFIG_STATUS=17
run_case compose-preflight-failure all $'y\n'
assert_status 17
assert_output_contains "Preflight failed; no resources were changed"
assert_output_occurrences 0 "Continue? [y/N]"
assert_no_mutations
assert_log_excludes "k3d <cluster> <list>"
echo "ok - Compose configuration failure aborts all before confirmation"

reset_case_config
TEST_K3D_LIST_STATUS=19
run_case k3d-preflight-failure all $'y\n'
assert_status 19
assert_output_contains "Could not inspect k3d clusters"
assert_output_occurrences 0 "Continue? [y/N]"
assert_no_mutations
echo "ok - k3d inspection failure aborts all before mutations"

reset_case_config
TEST_COMPOSE_VOLUMES_STATUS=23
run_case volume-preflight-failure all $'y\n'
assert_status 23
assert_output_contains "Could not list the Compose volumes"
assert_output_occurrences 0 "Continue? [y/N]"
assert_no_mutations
echo "ok - Compose volume inspection failure aborts all before mutations"

reset_case_config
TEST_COMPOSE_DOWN_STATUS=41
run_case compose-partial-failure all $'y\n'
assert_failure
assert_order \
  "${compose_prefix} <down> <-v> <--remove-orphans>" \
  "k3d <cluster> <delete> <dcrivella-auth>" \
  "rm <-rf> <--> <${case_home}/.k3d-dcrivella-auth/data>"
assert_output_contains "repeat: mise run compose:nuke"
assert_output_excludes "repeat: mise run k3d:nuke"
[[ -e "${stack_marker}" ]] || fail "${case_name}: failed Compose marker unexpectedly changed"
[[ ! -e "${cluster_marker}" ]] || fail "${case_name}: k3d cleanup did not continue"
[[ ! -e "${case_home}/.k3d-dcrivella-auth/data" ]] || fail "${case_name}: k3d data cleanup did not continue"
echo "ok - partial Compose failure still runs k3d and reports the task to repeat"

reset_case_config
TEST_K3D_DELETE_STATUS=37
run_case k3d-partial-failure all $'y\n'
assert_failure
assert_output_contains "repeat: mise run k3d:nuke"
assert_output_excludes "repeat: mise run compose:nuke"
[[ ! -e "${stack_marker}" ]] || fail "${case_name}: Compose cleanup did not finish first"
[[ -e "${cluster_marker}" ]] || fail "${case_name}: failed cluster marker unexpectedly changed"
[[ -e "${case_home}/.k3d-dcrivella-auth/data/protected" ]] || fail "${case_name}: data changed after cluster deletion failure"
echo "ok - partial k3d failure reports its individual retry task"

reset_case_config
TEST_K3D_DELETE_STATUS=37
run_case cluster-delete-failure k3d $'y\n'
assert_status 37
assert_output_contains "persisted data at ${case_home}/.k3d-dcrivella-auth/data was preserved"
assert_log_contains "k3d <cluster> <delete> <dcrivella-auth>"
assert_log_excludes "rm <-rf>"
assert_log_excludes "docker <run>"
[[ -e "${case_home}/.k3d-dcrivella-auth/data/protected" ]] || fail "${case_name}: data changed after cluster deletion failure"
echo "ok - failed cluster deletion preserves the mounted data directory"

reset_case_config
TEST_CLUSTER_EXISTS=false
TEST_RM_FAIL=true
run_case bounded-fallback k3d $'y\n'
assert_status 0
assert_log_contains "rm <-rf> <--> <${case_home}/.k3d-dcrivella-auth/data>"
assert_log_contains "docker <run> <--rm> <--mount> <type=bind,source=${case_home}/.k3d-dcrivella-auth/data,target=/data> <busybox:1.36>"
assert_log_contains "rmdir <--> <${case_home}/.k3d-dcrivella-auth/data>"
assert_log_excludes "source=${case_home}/.k3d-dcrivella-auth,target=/data"
[[ ! -e "${case_home}/.k3d-dcrivella-auth/data" ]] || fail "${case_name}: fallback left the data directory"
echo "ok - busybox fallback is bounded to and removes only the exact data directory"

reset_case_config
TEST_DATA_STATE=root-symlink
run_case root-symlink all $'y\n'
assert_failure
assert_output_contains "symbolic link"
assert_output_contains "Preflight failed; no resources were changed"
assert_no_mutations
echo "ok - symlinked k3d state root is rejected during global preflight"

reset_case_config
TEST_DATA_STATE=data-symlink
run_case data-symlink k3d $'y\n'
assert_failure
assert_output_contains "symbolic link"
assert_no_mutations
echo "ok - symlinked k3d data path is rejected"

reset_case_config
TEST_DATA_STATE=data-file
run_case unexpected-data-type k3d $'y\n'
assert_failure
assert_output_contains "Expected the k3d data path to be a directory"
assert_no_mutations
echo "ok - unexpected k3d data path type is rejected"

reset_case_config
TEST_HOME_VALUE=relative-home
run_case relative-home k3d $'y\n'
assert_failure
assert_output_contains "HOME must be an absolute, non-root path"
assert_no_mutations
echo "ok - unexpected relative HOME path is rejected"

reset_case_config
TEST_CLUSTER_NAME=another-cluster
run_case wrong-cluster all $'y\n'
assert_failure
assert_output_contains "only supports cluster 'dcrivella-auth'"
assert_no_mutations
echo "ok - non-configured cluster names are rejected before global mutations"
