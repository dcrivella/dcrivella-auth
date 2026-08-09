#!/usr/bin/env bash

set -euo pipefail

test_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
readonly test_dir
repository_root="$(cd -- "${test_dir}/../.." && pwd -P)"
readonly repository_root
readonly subject="${repository_root}/scripts/actions.sh"
test_root="$(mktemp -d)"
readonly test_root

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
  [[ "${log}" != *"${unexpected}"* ]] ||
    fail "${case_name}: log unexpectedly contains '${unexpected}'; log: ${log}"
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

assert_no_runtime_mutations() {
  assert_log_excludes "act[token="
  assert_log_excludes "<down> <--remove-orphans>"
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

act() {
  local token_state=unset
  if [[ -v GITHUB_TOKEN ]]; then
    token_state="set"
  fi
  log_call "act[token=${token_state}]" "$@"
  return "${TEST_ACT_STATUS}"
}

docker() {
  local arguments="$*"
  log_call docker "$@"

  if [[ "${1:-}" == info ]]; then
    return "${TEST_DOCKER_INFO_STATUS}"
  fi

  if [[ "${1:-} ${2:-}" == "compose version" ]]; then
    return "${TEST_COMPOSE_VERSION_STATUS}"
  fi

  if [[ "${1:-}" == compose ]]; then
    if [[ "${arguments}" == *" config --quiet"* ]]; then
      return "${TEST_COMPOSE_CONFIG_STATUS}"
    fi
    if [[ "${arguments}" == *" down --remove-orphans"* ]]; then
      return "${TEST_COMPOSE_DOWN_STATUS}"
    fi
    return 91
  fi

  if [[ "${1:-}" == ps && "${arguments}" == *"com.docker.compose.project=dcrivella-auth-stack"* ]]; then
    if [[ "${TEST_COMPOSE_PRESENT}" == true ]]; then
      printf 'compose-container\n'
    fi
    return "${TEST_COMPOSE_PS_STATUS}"
  fi

  if [[ "${1:-}" == ps && "${arguments}" == *"k3d.cluster=dcrivella-auth"* ]]; then
    if [[ "${TEST_K3D_ACTIVE}" == true ]]; then
      printf 'k3d-container\n'
    fi
    return "${TEST_K3D_PS_STATUS}"
  fi

  return 92
}

export -f act docker log_call

reset_case_config() {
  TEST_ACT_STATUS=0
  TEST_COMPOSE_CONFIG_STATUS=0
  TEST_COMPOSE_DOWN_STATUS=0
  TEST_COMPOSE_PRESENT=false
  TEST_COMPOSE_PS_STATUS=0
  TEST_COMPOSE_VERSION_STATUS=0
  TEST_DOCKER_INFO_STATUS=0
  TEST_K3D_ACTIVE=false
  TEST_K3D_PS_STATUS=0
}

run_case() {
  case_name=$1
  shift
  local case_dir="${test_root}/${case_name}"
  case_log="${case_dir}/calls.log"
  mkdir -p "${case_dir}"
  : >"${case_log}"

  set +e
  case_output="$(
    CALL_LOG="${case_log}" \
      GITHUB_TOKEN=must-not-reach-act \
      TEST_ACT_STATUS="${TEST_ACT_STATUS}" \
      TEST_COMPOSE_CONFIG_STATUS="${TEST_COMPOSE_CONFIG_STATUS}" \
      TEST_COMPOSE_DOWN_STATUS="${TEST_COMPOSE_DOWN_STATUS}" \
      TEST_COMPOSE_PRESENT="${TEST_COMPOSE_PRESENT}" \
      TEST_COMPOSE_PS_STATUS="${TEST_COMPOSE_PS_STATUS}" \
      TEST_COMPOSE_VERSION_STATUS="${TEST_COMPOSE_VERSION_STATUS}" \
      TEST_DOCKER_INFO_STATUS="${TEST_DOCKER_INFO_STATUS}" \
      TEST_K3D_ACTIVE="${TEST_K3D_ACTIVE}" \
      TEST_K3D_PS_STATUS="${TEST_K3D_PS_STATUS}" \
      bash "${subject}" "$@" 2>&1
  )"
  case_status=$?
  set -e
}

readonly act_prefix="act[token=unset] <workflow_dispatch> <--input> <force_all=true>"
readonly compose_prefix="docker <compose> <--project-name> <dcrivella-auth-stack> <--env-file> <infra/compose/.env> <-f> <infra/compose/compose.yml> <-f> <infra/compose/compose.override.yml>"

reset_case_config
run_case invalid-mode unsupported
assert_status 2
assert_output_contains "Usage: scripts/actions.sh"
assert_no_runtime_mutations
run_case missing-job-id job
assert_status 2
assert_no_runtime_mutations
run_case extra-job-id job repository-quality unexpected
assert_status 2
assert_no_runtime_mutations
echo "ok - invalid modes and job arguments fail before external commands"

reset_case_config
run_case list list
assert_status 0
assert_log_contains "${act_prefix} <--list>"
assert_log_excludes "docker <"
run_case graph graph
assert_status 0
assert_log_contains "${act_prefix} <--graph>"
assert_log_excludes "docker <"
run_case dry-run dry-run
assert_status 0
assert_log_contains "${act_prefix} <--dryrun>"
assert_log_excludes "docker <"
run_case ordinary-job job repository-quality
assert_status 0
assert_log_contains "${act_prefix} <--job> <repository-quality>"
assert_log_excludes "docker <"
echo "ok - inspection modes and an ordinary job bypass runtime preflight"

reset_case_config
run_case compose-runtime-job job compose-system-tests-jvm
assert_status 0
assert_order \
  "docker <info>" \
  "${compose_prefix} <config> <--quiet>" \
  "${act_prefix} <--job> <compose-system-tests-jvm>" \
  "${compose_prefix} <down> <--remove-orphans>"
echo "ok - the Compose system-test job receives runtime protection and teardown"

reset_case_config
run_case aggregate-runtime-job job ci-success
assert_status 0
assert_order \
  "docker <info>" \
  "${act_prefix} <--job> <ci-success>" \
  "${compose_prefix} <down> <--remove-orphans>"
echo "ok - the aggregate CI job receives runtime protection and teardown"

reset_case_config
TEST_DOCKER_INFO_STATUS=17
run_case docker-inspection-failure all
assert_failure
assert_output_contains "Docker Engine is not available"
assert_no_runtime_mutations
reset_case_config
TEST_COMPOSE_CONFIG_STATUS=19
run_case compose-config-failure all
assert_failure
assert_output_contains "Compose configuration is invalid"
assert_no_runtime_mutations
reset_case_config
TEST_COMPOSE_PS_STATUS=21
run_case compose-inspection-failure all
assert_failure
assert_output_contains "Could not inspect existing"
assert_no_runtime_mutations
reset_case_config
TEST_K3D_PS_STATUS=23
run_case k3d-inspection-failure all
assert_failure
assert_output_contains "Could not inspect active"
assert_no_runtime_mutations
echo "ok - Docker, Compose and runtime inspection failures abort before act"

reset_case_config
TEST_COMPOSE_PRESENT=true
run_case compose-present all
assert_failure
assert_output_contains "already has containers"
assert_output_contains "mise run compose:down"
assert_no_runtime_mutations
echo "ok - existing Compose containers block the ephemeral workflow stack"

reset_case_config
TEST_K3D_ACTIVE=true
run_case k3d-active all
assert_failure
assert_output_contains "k3d cluster 'dcrivella-auth' is active"
assert_output_contains "mise run k3d:cluster-stop"
assert_no_runtime_mutations
echo "ok - an active k3d cluster blocks the ephemeral workflow stack"

reset_case_config
run_case successful-all all
assert_status 0
assert_output_contains "build JVM application images"
assert_output_contains "images and build caches were preserved"
assert_order \
  "docker <ps> <--all> <--quiet> <--filter> <label=com.docker.compose.project=dcrivella-auth-stack>" \
  "docker <ps> <--quiet> <--filter> <label=k3d.cluster=dcrivella-auth>" \
  "${act_prefix}" \
  "${compose_prefix} <down> <--remove-orphans>"
assert_log_excludes "<--rmi>"
assert_log_excludes "<prune>"
echo "ok - a successful full workflow always tears down only the temporary stack"

reset_case_config
TEST_ACT_STATUS=29
TEST_COMPOSE_DOWN_STATUS=37
run_case act-and-teardown-failure all
assert_status 29
assert_output_contains "failed to remove the temporary Compose stack"
assert_order "${act_prefix}" "${compose_prefix} <down> <--remove-orphans>"
echo "ok - an act failure still tears down and retains the act status"

reset_case_config
TEST_COMPOSE_DOWN_STATUS=41
run_case teardown-failure all
assert_status 41
assert_output_contains "failed to remove the temporary Compose stack"
assert_order "${act_prefix}" "${compose_prefix} <down> <--remove-orphans>"
echo "ok - teardown failure becomes the status only after act succeeds"
