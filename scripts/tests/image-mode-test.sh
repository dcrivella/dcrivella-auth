#!/usr/bin/env bash

set -euo pipefail

readonly test_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
readonly repository_root="$(cd -- "${test_dir}/../.." && pwd -P)"
readonly subject="${repository_root}/scripts/image-mode.sh"
readonly test_root="$(mktemp -d)"

cleanup() {
  command rm -rf -- "${test_root}"
}
trap cleanup EXIT

fail() {
  echo "not ok - $*" >&2
  exit 1
}

write_env() {
  local case_dir=$1
  local tag=$2
  mkdir -p "${case_dir}/infra/compose"
  {
    printf 'AUTH_SERVER_IMAGE_TAG=%s\n' "${tag}"
    printf 'CLIENT_SERVER_IMAGE_TAG=%s\n' "${tag}"
    printf 'RESOURCE_SERVER_IMAGE_TAG=%s\n' "${tag}"
  } >"${case_dir}/infra/compose/.env"
}

resolve_case() {
  local name=$1
  local mode=$2
  local tag=$3
  local case_dir="${test_root}/${name}"
  write_env "${case_dir}" "${tag}"

  set +e
  case_output="$(
    cd "${case_dir}"
    . "${subject}" "${mode}"
    printf '%s|%s|%s|%s|%s' \
      "${IMAGE_MODE}" \
      "${BP_NATIVE_IMAGE}" \
      "${AUTH_SERVER_IMAGE}" \
      "${CLIENT_SERVER_IMAGE}" \
      "${RESOURCE_SERVER_IMAGE}"
  )"
  case_status=$?
  set -e
}

assert_resolved() {
  local expected=$1
  [[ "${case_status}" -eq 0 ]] || fail "expected success, got ${case_status}: ${case_output}"
  [[ "${case_output}" == "${expected}" ]] || fail "expected '${expected}', got '${case_output}'"
}

resolve_case native native 1.0.0-native
assert_resolved "native|true|dcrivella/auth-server:1.0.0-native|dcrivella/client-server:1.0.0-native|dcrivella/resource-server:1.0.0-native"
echo "ok - native mode preserves configured native tags and enables native image builds"

resolve_case jvm-from-native jvm 1.0.0-native
assert_resolved "jvm|false|dcrivella/auth-server:1.0.0-jvm|dcrivella/client-server:1.0.0-jvm|dcrivella/resource-server:1.0.0-jvm"
echo "ok - JVM mode resolves JVM tags and disables native image builds"

resolve_case jvm-from-base jvm 2.0.0
assert_resolved "jvm|false|dcrivella/auth-server:2.0.0-jvm|dcrivella/client-server:2.0.0-jvm|dcrivella/resource-server:2.0.0-jvm"
echo "ok - JVM suffix is appended to an unsuffixed tag"

resolve_case jvm-idempotent jvm 2.0.0-jvm
assert_resolved "jvm|false|dcrivella/auth-server:2.0.0-jvm|dcrivella/client-server:2.0.0-jvm|dcrivella/resource-server:2.0.0-jvm"
echo "ok - JVM suffix is not duplicated"

dash_dir="${test_root}/dash-jvm"
write_env "${dash_dir}" "1.0.0-native"
dash_output="$(
  cd "${dash_dir}"
  IMAGE_MODE=jvm dash -c '
    . "$1"
    printf "%s|%s|%s" "$IMAGE_MODE" "$BP_NATIVE_IMAGE" "$AUTH_SERVER_IMAGE"
  ' image-mode-test "${subject}"
)"
[[ "${dash_output}" == "jvm|false|dcrivella/auth-server:1.0.0-jvm" ]] || fail "dash resolved the wrong mode: ${dash_output}"
echo "ok - environment-selected JVM mode works when dash sources the helper"

invalid_dir="${test_root}/invalid"
mkdir -p "${invalid_dir}/infra/compose"
mutation_marker="${invalid_dir}/sourced-env"
{
  printf ': > "%s"\n' "${mutation_marker}"
  printf 'AUTH_SERVER_IMAGE_TAG=1.0.0-native\n'
  printf 'CLIENT_SERVER_IMAGE_TAG=1.0.0-native\n'
  printf 'RESOURCE_SERVER_IMAGE_TAG=1.0.0-native\n'
} >"${invalid_dir}/infra/compose/.env"

set +e
invalid_output="$(cd "${invalid_dir}" && . "${subject}" invalid 2>&1)"
invalid_status=$?
set -e

[[ "${invalid_status}" -eq 2 ]] || fail "invalid mode returned ${invalid_status}: ${invalid_output}"
[[ "${invalid_output}" == *"Expected 'native' or 'jvm'"* ]] || fail "invalid mode output was not actionable: ${invalid_output}"
[[ ! -e "${mutation_marker}" ]] || fail "invalid mode sourced the environment file"
echo "ok - invalid mode fails before environment resolution"
