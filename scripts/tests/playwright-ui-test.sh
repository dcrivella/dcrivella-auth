set -euo pipefail

readonly test_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
readonly repository_root="$(cd -- "${test_dir}/../.." && pwd -P)"
readonly subject="${repository_root}/scripts/playwright-ui.sh"
readonly test_root="$(mktemp -d)"

cleanup() {
  command rm -rf -- "${test_root}"
}
trap cleanup EXIT

fail() {
  echo "not ok - $*" >&2
  exit 1
}

run_case() {
  local name=$1
  local expected_status=$2
  local command_status=$3
  local case_dir="${test_root}/${name}"
  local temporary_parent="${case_dir}/temporary"
  local capture_file="${case_dir}/captured-paths"
  mkdir -p "${temporary_parent}"

  set +e
  case_output="$(
    DCRIVELLA_PLAYWRIGHT_UI_TMPDIR="${temporary_parent}" \
      CAPTURE_FILE="${capture_file}" \
      bash "${subject}" bash -c '
        printf "%s\n%s\n" \
          "$DCRIVELLA_PLAYWRIGHT_OUTPUT_DIR" \
          "$DCRIVELLA_PLAYWRIGHT_HTML_REPORT" >"$CAPTURE_FILE"
        mkdir -p "$DCRIVELLA_PLAYWRIGHT_OUTPUT_DIR" "$DCRIVELLA_PLAYWRIGHT_HTML_REPORT"
        touch "$DCRIVELLA_PLAYWRIGHT_OUTPUT_DIR/trace.zip"
        exit "$1"
      ' playwright-ui-stub "${command_status}" 2>&1
  )"
  case_status=$?
  set -e

  [[ "${case_status}" -eq "${expected_status}" ]] || fail "${name}: expected ${expected_status}, got ${case_status}: ${case_output}"
  [[ -r "${capture_file}" ]] || fail "${name}: wrapped command did not receive artifact paths"

  mapfile -t artifact_paths <"${capture_file}"
  [[ "${#artifact_paths[@]}" -eq 2 ]] || fail "${name}: expected two captured artifact paths"
  for artifact_path in "${artifact_paths[@]}"; do
    [[ "${artifact_path}" == "${temporary_parent}"/dcrivella-playwright-ui.*/** ]] ||
      fail "${name}: artifact path escaped the temporary parent: ${artifact_path}"
    [[ ! -e "${artifact_path}" ]] || fail "${name}: artifact path survived wrapper exit: ${artifact_path}"
  done
  [[ -z "$(find "${temporary_parent}" -mindepth 1 -print -quit)" ]] ||
    fail "${name}: temporary Playwright UI root survived wrapper exit"
}

run_case success 0 0
echo "ok - successful UI execution removes temporary traces and reports"

run_case failure 23 23
echo "ok - failed UI execution preserves status and removes temporary traces and reports"

set +e
missing_output="$(bash "${subject}" 2>&1)"
missing_status=$?
set -e
[[ "${missing_status}" -eq 2 ]] || fail "missing command returned ${missing_status}: ${missing_output}"
[[ "${missing_output}" == *"Playwright UI command is required"* ]] || fail "missing command error was not actionable"
echo "ok - missing wrapped command fails before creating artifacts"
