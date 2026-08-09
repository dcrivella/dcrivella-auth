set -euo pipefail

readonly test_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
readonly repository_root="$(cd -- "${test_dir}/../.." && pwd -P)"
readonly subject="${repository_root}/scripts/k3d-deploy.sh"
readonly test_root="$(mktemp -d)"

cleanup() {
  command rm -rf -- "${test_root}"
}
trap cleanup EXIT

fail() {
  echo "not ok - $*" >&2
  exit 1
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

log_call() {
  local command=$1
  shift
  {
    printf '%s' "${command}"
    printf ' <%s>' "$@"
    printf '\n'
  } >>"${CALL_LOG}"
}

state_for() {
  local deployment=$1
  local phase=$2
  case "${deployment}:${phase}" in
    auth-server:before) printf '%s' "${TEST_AUTH_BEFORE}" ;;
    auth-server:after) printf '%s' "${TEST_AUTH_AFTER}" ;;
    client-server:before) printf '%s' "${TEST_CLIENT_BEFORE}" ;;
    client-server:after) printf '%s' "${TEST_CLIENT_AFTER}" ;;
    resource-server:before) printf '%s' "${TEST_RESOURCE_BEFORE}" ;;
    resource-server:after) printf '%s' "${TEST_RESOURCE_AFTER}" ;;
    *) return 90 ;;
  esac
}

kustomize() {
  log_call kustomize "$@"
  if [[ "${1:-}" == build ]]; then
    printf '%s\n' 'apiVersion: v1' 'kind: List' 'items: []'
  fi
}

kubectl() {
  log_call kubectl "$@"

  if [[ "${1:-} ${2:-}" == "get namespace" ]]; then
    [[ "${TEST_NAMESPACE_EXISTS}" == true ]] && printf 'namespace/%s' "${3}"
    return 0
  fi

  if [[ "${1:-}" == -n && "${3:-}" == get && "${4:-}" == deployment/* ]]; then
    local deployment="${4#deployment/}"
    local count_file="${CALL_COUNTS}/${deployment}"
    local count=0
    [[ -r "${count_file}" ]] && count="$(<"${count_file}")"
    count=$((count + 1))
    printf '%s' "${count}" >"${count_file}"
    if ((count == 1)); then
      state_for "${deployment}" before
    else
      local after_state
      after_state="$(state_for "${deployment}" after)"
      printf '%s' "${after_state%%|*}"
    fi
    return 0
  fi

  if [[ "${1:-} ${2:-}" == "apply -f" ]]; then
    while IFS= read -r _; do :; done
    return 0
  fi

  if [[ "${1:-}" == -n && "${3:-} ${4:-}" == "rollout status" ]]; then
    return 0
  fi

  if [[ "${1:-}" == -n && "${3:-} ${4:-}" == "rollout restart" ]]; then
    return 0
  fi

  return 91
}

export -f kubectl kustomize log_call state_for

run_case() {
  case_name=$1
  local mode=$2
  local namespace_exists=$3
  local auth_before=$4
  local auth_after=$5
  local client_before=$6
  local client_after=$7
  local resource_before=$8
  local resource_after=$9
  local case_dir="${test_root}/${case_name}"
  case_log="${case_dir}/calls.log"
  mkdir -p "${case_dir}/counts"
  : >"${case_log}"

  set +e
  case_output="$(
    CALL_LOG="${case_log}" \
      CALL_COUNTS="${case_dir}/counts" \
      IMAGE_MODE="${mode}" \
      TEST_AUTH_AFTER="${auth_after}" \
      TEST_AUTH_BEFORE="${auth_before}" \
      TEST_CLIENT_AFTER="${client_after}" \
      TEST_CLIENT_BEFORE="${client_before}" \
      TEST_NAMESPACE_EXISTS="${namespace_exists}" \
      TEST_RESOURCE_AFTER="${resource_after}" \
      TEST_RESOURCE_BEFORE="${resource_before}" \
      bash "${subject}" 2>&1
  )"
  case_status=$?
  set -e

  [[ "${case_status}" -eq 0 ]] || fail "${case_name}: expected success, got ${case_status}: ${case_output}"
}

rendered="$(command kustomize build --load-restrictor LoadRestrictionsNone "${repository_root}/infra/k8s/overlays/local")"
issuer_wait_count="$(printf '%s\n' "${rendered}" | rg -F -c '${ISSUER_URL}/.well-known/openid-configuration')"
[[ "${issuer_wait_count}" -eq 2 ]] || fail "expected both dependent deployments to wait for the configured issuer URL"
[[ "${rendered}" != *'wget -q --spider http://auth-server:9000/'* ]] || fail "render still waits for the internal service URL"
[[ "${rendered}" == *'ISSUER_URL: http://host.k3d.internal:9000'* ]] || fail "render does not retain the public issuer URL"
[[ "${rendered}" == *'JWK_SET_URI: http://auth-server:9000/oauth2/jwks'* ]] || fail "render does not expose the internal JWKS URI"
jwk_reference_count="$(printf '%s\n' "${rendered}" | rg -F -c 'key: JWK_SET_URI')"
[[ "${jwk_reference_count}" -eq 1 ]] || fail "expected only resource-server to import the internal JWKS URI"
echo "ok - render keeps the public issuer and gives resource-server the internal JWKS URI"

run_case fresh native false '' '' '' '' '' ''
assert_log_contains "kustomize <edit> <set> <image> <dcrivella/auth-server=dcrivella/auth-server:1.0.0-native>"
assert_log_contains "kubectl <apply> <-f> <->"
assert_log_excludes "rollout> <restart>"
echo "ok - fresh deployments start directly with selected images and skip a redundant restart"

native_auth='7|dcrivella/auth-server:1.0.0-native'
native_client='7|dcrivella/client-server:1.0.0-native'
native_resource='7|dcrivella/resource-server:1.0.0-native'
run_case same-tag native true "${native_auth}" "${native_auth}" "${native_client}" "${native_client}" "${native_resource}" \
  "${native_resource}"
assert_log_contains "rollout> <restart> <deployment/auth-server> <deployment/client-server> <deployment/resource-server>"
echo "ok - unchanged same-tag deployments restart to activate newly imported images"

jvm_auth='8|dcrivella/auth-server:1.0.0-jvm'
jvm_client='8|dcrivella/client-server:1.0.0-jvm'
jvm_resource='8|dcrivella/resource-server:1.0.0-jvm'
run_case changed-tag jvm true "${native_auth}" "${jvm_auth}" "${native_client}" "${jvm_client}" "${native_resource}" "${jvm_resource}"
assert_log_contains "dcrivella/auth-server=dcrivella/auth-server:1.0.0-jvm"
assert_log_excludes "rollout> <restart>"
echo "ok - image-tag changes rely on the rollout created by the manifest update"

changed_generation_auth='8|dcrivella/auth-server:1.0.0-native'
changed_generation_client='8|dcrivella/client-server:1.0.0-native'
changed_generation_resource='8|dcrivella/resource-server:1.0.0-native'
run_case changed-template native true "${native_auth}" "${changed_generation_auth}" "${native_client}" \
  "${changed_generation_client}" "${native_resource}" "${changed_generation_resource}"
assert_log_excludes "rollout> <restart>"
echo "ok - deployment template changes do not receive a second rollout"
