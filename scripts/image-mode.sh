#!/usr/bin/env sh

# Source this file from the repository root to resolve one consistent set of
# image references for Gradle, Compose and k3d.

image_mode="${IMAGE_MODE:-${1:-native}}"

case "${image_mode}" in
  native)
    bp_native_image=true
    ;;
  jvm)
    bp_native_image=false
    ;;
  *)
    echo "!! Unsupported image mode '${image_mode}'. Expected 'native' or 'jvm'." >&2
    return 2 2>/dev/null || exit 2
    ;;
esac

if [ ! -r infra/compose/.env ]; then
  echo "!! Image environment file 'infra/compose/.env' is not readable." >&2
  return 1 2>/dev/null || exit 1
fi

. infra/compose/.env

: "${AUTH_SERVER_IMAGE_TAG:?AUTH_SERVER_IMAGE_TAG must be set in infra/compose/.env}"
: "${CLIENT_SERVER_IMAGE_TAG:?CLIENT_SERVER_IMAGE_TAG must be set in infra/compose/.env}"
: "${RESOURCE_SERVER_IMAGE_TAG:?RESOURCE_SERVER_IMAGE_TAG must be set in infra/compose/.env}"

image_mode_jvm_tag() {
  case "$1" in
    *-jvm)
      printf '%s' "$1"
      ;;
    *-native)
      printf '%s-jvm' "${1%-native}"
      ;;
    *)
      printf '%s-jvm' "$1"
      ;;
  esac
}

if [ "${image_mode}" = jvm ]; then
  AUTH_SERVER_IMAGE_TAG="$(image_mode_jvm_tag "${AUTH_SERVER_IMAGE_TAG}")"
  CLIENT_SERVER_IMAGE_TAG="$(image_mode_jvm_tag "${CLIENT_SERVER_IMAGE_TAG}")"
  RESOURCE_SERVER_IMAGE_TAG="$(image_mode_jvm_tag "${RESOURCE_SERVER_IMAGE_TAG}")"
fi

AUTH_SERVER_IMAGE_NAME="${AUTH_SERVER_IMAGE_NAME:-dcrivella/auth-server}"
CLIENT_SERVER_IMAGE_NAME="${CLIENT_SERVER_IMAGE_NAME:-dcrivella/client-server}"
RESOURCE_SERVER_IMAGE_NAME="${RESOURCE_SERVER_IMAGE_NAME:-dcrivella/resource-server}"

AUTH_SERVER_IMAGE="${AUTH_SERVER_IMAGE_NAME}:${AUTH_SERVER_IMAGE_TAG}"
CLIENT_SERVER_IMAGE="${CLIENT_SERVER_IMAGE_NAME}:${CLIENT_SERVER_IMAGE_TAG}"
RESOURCE_SERVER_IMAGE="${RESOURCE_SERVER_IMAGE_NAME}:${RESOURCE_SERVER_IMAGE_TAG}"

IMAGE_MODE="${image_mode}"
BP_NATIVE_IMAGE="${bp_native_image}"

export IMAGE_MODE BP_NATIVE_IMAGE
export AUTH_SERVER_IMAGE_NAME AUTH_SERVER_IMAGE_TAG AUTH_SERVER_IMAGE
export CLIENT_SERVER_IMAGE_NAME CLIENT_SERVER_IMAGE_TAG CLIENT_SERVER_IMAGE
export RESOURCE_SERVER_IMAGE_NAME RESOURCE_SERVER_IMAGE_TAG RESOURCE_SERVER_IMAGE
