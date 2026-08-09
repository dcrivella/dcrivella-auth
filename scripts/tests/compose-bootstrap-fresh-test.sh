#!/usr/bin/env bash

set -euo pipefail

readonly test_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
readonly repository_root="$(cd -- "${test_dir}/../.." && pwd -P)"
readonly subject="${repository_root}/scripts/compose-bootstrap-fresh.sh"
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
    local arguments="$*"
    if [[ "${arguments}" == *" config --quiet"* ]]; then
      return "${TEST_CONFIG_STATUS}"
    fi

    if [[ "${arguments}" == *" down --remove-orphans"* ]]; then
      command rm -f -- "${STACK_MARKER}"
      return "${TEST_DOWN_STATUS}"
    fi

    return 91
  fi

  if [[ "${1:-}" == ps ]]; then
    if [[ "${TEST_K3D_ACTIVE}" == true ]]; then
      printf 'k3d-dcrivella-auth-server-0\n'
    fi
    return "${TEST_DOCKER_PS_STATUS}"
  fi

  return 92
}

mise() {
  log_call mise "$@"

  if [[ "${1:-}" == run && ("${2:-}" == image:build || "${2:-}" == image:build:jvm) ]]; then
    return "${TEST_BUILD_STATUS}"
  fi

  if [[ "${1:-}" == run && ("${2:-}" == compose:up || "${2:-}" == compose:up:jvm) ]]; then
    return "${TEST_UP_STATUS}"
  fi

  return 93
}

export -f docker log_call mise

run_case() {
  case_name=$1
  local answer=$2
  local k3d_active=$3
  local build_status=$4
  local down_status=$5
  local up_status=$6
  local stack_exists=$7
  local image_mode=${8:-}
  local -a subject_args=()
  if [[ -n "${image_mode}" ]]; then
    subject_args+=("${image_mode}")
  fi

  local case_dir="${test_root}/${case_name}"
  case_log="${case_dir}/calls.log"
  stack_marker="${case_dir}/stack"
  mkdir -p "${case_dir}"
  : >"${case_log}"
  if [[ "${stack_exists}" == true ]]; then
    : >"${stack_marker}"
  fi

  set +e
  case_output="$({
    printf '%s' "${answer}"
  } | CALL_LOG="${case_log}" \
    STACK_MARKER="${stack_marker}" \
    TEST_BUILD_STATUS="${build_status}" \
    TEST_CONFIG_STATUS=0 \
    TEST_DOCKER_PS_STATUS=0 \
    TEST_DOWN_STATUS="${down_status}" \
    TEST_K3D_ACTIVE="${k3d_active}" \
    TEST_UP_STATUS="${up_status}" \
    bash "${subject}" "${subject_args[@]}" 2>&1)"
  case_status=$?
  set -e
}

readonly compose_prefix="docker <compose> <--project-name> <dcrivella-auth-stack> <--env-file> <infra/compose/.env> <-f> <infra/compose/compose.yml> <-f> <infra/compose/compose.override.yml>"

run_case invalid-mode '' false 0 0 0 true invalid
assert_status 2
assert_output_contains "Unsupported image mode 'invalid'"
assert_log_excludes "docker <"
assert_log_excludes "mise <"
[[ -e "${stack_marker}" ]] || fail "${case_name}: stack marker changed"
echo "ok - invalid image mode fails before external commands"

run_case k3d-active $'y\n' true 0 0 0 true
assert_failure
assert_output_contains "mise run k3d:cluster-stop"
assert_log_contains "docker <ps> <--filter> <label=k3d.cluster=dcrivella-auth> <--format> <{{.Names}}>"
assert_log_excludes "mise <run> <image:build>"
assert_log_excludes "<down> <--remove-orphans>"
[[ -e "${stack_marker}" ]] || fail "${case_name}: stack marker changed"
echo "ok - active k3d blocks before build or cleanup"

run_case declined $'n\n' false 0 0 0 true
assert_status 0
assert_output_contains "Fresh bootstrap cancelled"
assert_log_excludes "mise <run> <image:build>"
assert_log_excludes "<down> <--remove-orphans>"
[[ -e "${stack_marker}" ]] || fail "${case_name}: stack marker changed"
echo "ok - negative confirmation cancels without mutations"

run_case build-failure $'y\n' false 23 0 0 true
assert_status 23
assert_output_contains "current Compose stack was preserved"
assert_log_contains "mise <run> <image:build>"
assert_log_excludes "<down> <--remove-orphans>"
assert_log_excludes "mise <run> <compose:up>"
[[ -e "${stack_marker}" ]] || fail "${case_name}: stack marker changed"
echo "ok - failed image build preserves the stack"

run_case stack-absent $'y\n' false 0 0 0 false
assert_status 0
assert_order \
  "${compose_prefix} <config> <--quiet>" \
  "docker <ps> <--filter> <label=k3d.cluster=dcrivella-auth>" \
  "mise <run> <image:build>" \
  "${compose_prefix} <down> <--remove-orphans>" \
  "mise <run> <compose:up>"
assert_output_contains "Fresh Compose bootstrap completed"
echo "ok - absent Compose stack is recreated normally"

run_case confirmed $'y\n' false 0 0 0 true
assert_status 0
assert_output_contains "Project: dcrivella-auth-stack"
assert_output_contains "Mode: native"
assert_output_contains "dcrivella/auth-server:1.0.0-native"
assert_order \
  "mise <run> <image:build>" \
  "${compose_prefix} <down> <--remove-orphans>" \
  "mise <run> <compose:up>"
[[ ! -e "${stack_marker}" ]] || fail "${case_name}: stack marker still exists"
echo "ok - confirmed bootstrap validates and keeps the required operation order"

run_case jvm-confirmed $'y\n' false 0 0 0 true jvm
assert_status 0
assert_output_contains "Mode: jvm"
assert_output_contains "dcrivella/auth-server:1.0.0-jvm"
assert_order \
  "mise <run> <image:build:jvm>" \
  "${compose_prefix} <down> <--remove-orphans>" \
  "mise <run> <compose:up:jvm>"
[[ ! -e "${stack_marker}" ]] || fail "${case_name}: stack marker still exists"
echo "ok - JVM build completes before cleanup and selects JVM up"

run_case jvm-declined $'n\n' false 0 0 0 true jvm
assert_status 0
assert_log_excludes "mise <run> <image:build:jvm>"
assert_log_excludes "<down> <--remove-orphans>"
[[ -e "${stack_marker}" ]] || fail "${case_name}: stack marker changed"
echo "ok - JVM cancellation leaves the stack unchanged"

run_case down-failure $'y\n' false 0 29 0 true
assert_status 29
assert_output_contains "recover with: mise run compose:up"
assert_log_contains "${compose_prefix} <down> <--remove-orphans>"
assert_log_excludes "mise <run> <compose:up>"
echo "ok - down failure returns recovery guidance"

run_case recreate-failure $'y\n' false 0 0 31 true
assert_status 31
assert_output_contains "recover with: mise run compose:up"
assert_order \
  "${compose_prefix} <down> <--remove-orphans>" \
  "mise <run> <compose:up>"
[[ ! -e "${stack_marker}" ]] || fail "${case_name}: stack marker still exists"
echo "ok - recreation failure returns recovery guidance"

run_case jvm-recreate-failure $'y\n' false 0 0 31 true jvm
assert_status 31
assert_output_contains "recover with: mise run compose:up:jvm"
assert_order \
  "mise <run> <image:build:jvm>" \
  "${compose_prefix} <down> <--remove-orphans>" \
  "mise <run> <compose:up:jvm>"
echo "ok - JVM recreation failure selects JVM recovery guidance"
