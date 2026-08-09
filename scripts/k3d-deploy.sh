set -euo pipefail

readonly script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
readonly repository_root="$(cd -- "${script_dir}/.." && pwd -P)"
cd "${repository_root}"

. scripts/image-mode.sh

readonly namespace="${K8S_NAMESPACE:-dcrivella-auth}"
readonly overlay_dir="${repository_root}/infra/k8s/overlays/local"
readonly -a deployments=(auth-server client-server resource-server)
declare -Ar selected_images=(
  [auth-server]="${AUTH_SERVER_IMAGE}"
  [client-server]="${CLIENT_SERVER_IMAGE}"
  [resource-server]="${RESOURCE_SERVER_IMAGE}"
)
declare -A previous_generations=()
declare -A previous_images=()

namespace_exists=false
if namespace_resource="$(kubectl get namespace "${namespace}" --ignore-not-found -o name)"; then
  [[ -n "${namespace_resource}" ]] && namespace_exists=true
else
  status=$?
  echo "!! Could not inspect namespace '${namespace}' before deployment." >&2
  exit "${status}"
fi

if [[ "${namespace_exists}" == true ]]; then
  for deployment in "${deployments[@]}"; do
    if current_state="$(
      kubectl -n "${namespace}" get "deployment/${deployment}" --ignore-not-found \
        -o 'jsonpath={.metadata.generation}{"|"}{.spec.template.spec.containers[0].image}'
    )"; then
      :
    else
      status=$?
      echo "!! Could not inspect deployment '${deployment}' before applying manifests." >&2
      exit "${status}"
    fi

    if [[ -n "${current_state}" ]]; then
      previous_generations["${deployment}"]="${current_state%%|*}"
      previous_images["${deployment}"]="${current_state#*|}"
    fi
  done
fi

readonly render_dir="$(mktemp -d)"
cleanup() {
  if [[ -n "${render_dir:-}" && -d "${render_dir}" ]]; then
    command rm -rf -- "${render_dir}"
  fi
}
trap cleanup EXIT

ln -s "${overlay_dir}" "${render_dir}/local"
(
  cd "${render_dir}"
  kustomize create --resources local
  kustomize edit set image \
    "${AUTH_SERVER_IMAGE_NAME}=${AUTH_SERVER_IMAGE}" \
    "${CLIENT_SERVER_IMAGE_NAME}=${CLIENT_SERVER_IMAGE}" \
    "${RESOURCE_SERVER_IMAGE_NAME}=${RESOURCE_SERVER_IMAGE}"
)

kustomize build --load-restrictor LoadRestrictionsNone "${render_dir}" | kubectl apply -f -

restart_targets=()
for deployment in "${deployments[@]}"; do
  previous_generation="${previous_generations["${deployment}"]:-}"
  previous_image="${previous_images["${deployment}"]:-}"
  if [[ -z "${previous_generation}" || "${previous_image}" != "${selected_images["${deployment}"]}" ]]; then
    continue
  fi

  current_generation="$(kubectl -n "${namespace}" get "deployment/${deployment}" -o 'jsonpath={.metadata.generation}')"
  if [[ "${current_generation}" == "${previous_generation}" ]]; then
    restart_targets+=("deployment/${deployment}")
  fi
done

if ((${#restart_targets[@]} > 0)); then
  echo "==> Restarting unchanged deployments to activate rebuilt same-tag images"
  kubectl -n "${namespace}" rollout restart "${restart_targets[@]}"
else
  echo "==> Applied manifests already started every required rollout; no additional restart needed"
fi

kubectl -n "${namespace}" rollout status deployment/auth-server --timeout=180s
kubectl -n "${namespace}" rollout status deployment/resource-server --timeout=180s
kubectl -n "${namespace}" rollout status deployment/client-server --timeout=180s
echo "==> k3d stack is up. Open http://localhost:8080"
