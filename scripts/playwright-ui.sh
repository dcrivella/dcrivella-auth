set -euo pipefail

if (($# == 0)); then
  echo "!! Playwright UI command is required." >&2
  exit 2
fi

readonly temporary_parent="${DCRIVELLA_PLAYWRIGHT_UI_TMPDIR:-${TMPDIR:-/tmp}}"
if [[ ! -d "${temporary_parent}" || -L "${temporary_parent}" ]]; then
  echo "!! Playwright UI temporary parent must be a real directory: ${temporary_parent}" >&2
  exit 2
fi

readonly output_root="$(mktemp -d "${temporary_parent%/}/dcrivella-playwright-ui.XXXXXX")"
cleanup() {
  case "${output_root}" in
    "${temporary_parent%/}"/dcrivella-playwright-ui.*)
      [[ ! -e "${output_root}" ]] || command rm -rf -- "${output_root}"
      ;;
    *)
      echo "!! Refusing to remove unexpected Playwright UI path: ${output_root}" >&2
      return 1
      ;;
  esac
}
trap cleanup EXIT

export DCRIVELLA_PLAYWRIGHT_OUTPUT_DIR="${output_root}/test-results"
export DCRIVELLA_PLAYWRIGHT_HTML_REPORT="${output_root}/playwright-report"

echo "==> Playwright UI artifacts are temporary and will be removed when the explorer closes"
"$@"
