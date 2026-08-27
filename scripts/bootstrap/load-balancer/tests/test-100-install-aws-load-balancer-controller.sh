#!/usr/bin/env bash

# AWS Load Balancer Controller bootstrap의 공급망·owner·적용 순서 계약을 검증합니다.
set -euo pipefail

TEST_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly TEST_DIRECTORY
readonly TARGET_SCRIPT="${TEST_DIRECTORY}/../100-install-aws-load-balancer-controller.sh"
TEST_RUNTIME_DIRECTORY=""

# shellcheck source=../100-install-aws-load-balancer-controller.sh
source "${TARGET_SCRIPT}"

assert_equals() {
  local expected="$1"
  local actual="$2"
  local message="$3"

  if [[ "${actual}" != "${expected}" ]]; then
    printf 'FAIL: %s\nexpected: %s\nactual: %s\n' \
      "${message}" "${expected}" "${actual}" >&2
    exit 1
  fi
}

assert_contains() {
  local expected="$1"
  local actual="$2"
  local message="$3"

  if [[ "${actual}" != *"${expected}"* ]]; then
    printf 'FAIL: %s\nmissing: %s\nactual: %s\n' \
      "${message}" "${expected}" "${actual}" >&2
    exit 1
  fi
}

assert_array_contains() {
  local expected="$1"
  shift
  local actual

  for actual in "$@"; do
    if [[ "${actual}" == "${expected}" ]]; then
      return
    fi
  done
  printf 'FAIL: array에 필수 값이 없습니다: %s\n' "${expected}" >&2
  exit 1
}

test_pins_supported_releases_and_artifacts() {
  assert_equals "v1.36.2" "${KUBERNETES_VERSION}" \
    "현재 kubeadm cluster와 같은 Kubernetes exact version을 검증해야 합니다."
  assert_equals "3.21.4" "${HELM_VERSION}" \
    "Kubernetes 1.36 client를 포함하는 Helm 3 exact version을 고정해야 합니다."
  assert_equals \
    "b54c04b4e0b2540bbdc08c17a121dab70e9a2ed0de5705528fec68a5fd3b85a7" \
    "${HELM_ARCHIVE_SHA256}" \
    "검토한 Linux ARM64 Helm archive checksum을 고정해야 합니다."
  assert_equals "3.5.0" "${CONTROLLER_CHART_VERSION}" \
    "AWS Load Balancer Controller chart exact version을 고정해야 합니다."
  assert_equals \
    "45051f634b33e10baccb3354d0681b7de787c60445e599fa276e0c9aedd4ccd5" \
    "${CONTROLLER_CHART_SHA256}" \
    "검토한 official chart checksum을 고정해야 합니다."
  assert_equals \
    "public.ecr.aws/eks/aws-load-balancer-controller:v3.5.0@sha256:1ee3cc54fb62248bd56f2157f7be79baba2be8f0bbf2d926e2650d7ba710ee5f" \
    "${CONTROLLER_IMAGE}" \
    "ARM64 controller image를 exact digest로 고정해야 합니다."
  assert_equals "stg-car-community-k8s-node-role" "${EXPECTED_NODE_ROLE_NAME}" \
    "Controller AWS 권한을 포함한 staging node role만 허용해야 합니다."
}

test_requires_complete_external_command_inventory() {
  local command_name
  local -a current_dependencies=(
    awk cat chmod curl flock grep kubectl mkdir mktemp mv rm sha256sum tar uname
  )

  for command_name in "${current_dependencies[@]}"; do
    assert_array_contains "${command_name}" "${REQUIRED_COMMANDS[@]}"
  done
}

test_rejects_invalid_vpc_id() (
  local execution_status

  set +e
  (validate_vpc_id "vpc-guessed" >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: exact long-form VPC ID가 아닌 입력을 허용하면 안 됩니다." >&2
    exit 1
  fi

  validate_vpc_id "vpc-0123456789abcdef0"
)

test_ready_node_query_uses_live_confirmed_scalar_filter() (
  local test_root="$1"
  local argument_log="${test_root}/node-query-arguments"
  local arguments

  require_admin_kubeconfig_readable() { :; }
  require_exact_kubeadm_cluster_identity() { :; }
  kubectl_admin() {
    if [[ "$*" == "get --raw=/readyz" ]]; then
      printf '%s\n' ok
      return
    fi
    printf '%s\n' "$*" >"${argument_log}"
    printf '%s\n' 'ip-10-0-8-159|arm64|v1.36.2|True'
  }

  require_ready_single_node_cluster
  arguments="$(<"${argument_log}")"

  assert_equals "ip-10-0-8-159" "${TARGET_NODE_NAME}" \
    "검증된 singleton node 이름을 이후 mutation 대상으로 보존해야 합니다."
  assert_contains "--allow-missing-template-keys=false" "${arguments}" \
    "누락된 Ready field를 빈 성공으로 허용하면 안 됩니다."
  assert_contains \
    '.status.conditions[?(@.type=="Ready")].status' \
    "${arguments}" \
    "kubectl 1.36.2에서 확인한 scalar filter로 Ready status를 조회해야 합니다."
  if [[ "${arguments}" == *'{range .status.conditions'* ]]; then
    echo "FAIL: live에서 빈 성공한 nested JSONPath range를 다시 사용하면 안 됩니다." >&2
    exit 1
  fi
)

test_rejects_empty_duplicate_or_failed_ready_query() (
  local ready_fixture
  local execution_status

  require_admin_kubeconfig_readable() { :; }
  require_exact_kubeadm_cluster_identity() { :; }
  for ready_fixture in '' 'TrueTrue'; do
    kubectl_admin() {
      if [[ "$*" == "get --raw=/readyz" ]]; then
        printf '%s\n' ok
        return
      fi
      printf 'ip-10-0-8-159|arm64|v1.36.2|%s\n' "${ready_fixture}"
    }

    set +e
    (require_ready_single_node_cluster >/dev/null 2>&1)
    execution_status=$?
    set -e
    if [[ "${execution_status}" -eq 0 ]]; then
      echo "FAIL: Ready status가 없거나 중복된 node query를 허용하면 안 됩니다: ${ready_fixture:-빈 값}" >&2
      exit 1
    fi
  done

  kubectl_admin() {
    if [[ "$*" == "get --raw=/readyz" ]]; then
      printf '%s\n' ok
      return
    fi
    return 37
  }
  set +e
  (require_ready_single_node_cluster >/dev/null 2>&1)
  execution_status=$?
  set -e
  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: node query 실패를 정상 singleton cluster로 해석하면 안 됩니다." >&2
    exit 1
  fi
)

test_requires_exact_kubeadm_cluster_identity() (
  local execution_status

  kubectl_admin() {
    cat <<'EOF'
apiVersion: kubeadm.k8s.io/v1beta4
kind: ClusterConfiguration
clusterName: stg-car-community-k8s
kubernetesVersion: v1.36.2
EOF
  }
  require_exact_kubeadm_cluster_identity

  kubectl_admin() {
    cat <<'EOF'
apiVersion: kubeadm.k8s.io/v1beta4
kind: ClusterConfiguration
clusterName: another-cluster
kubernetesVersion: v1.36.2
EOF
  }
  set +e
  (require_exact_kubeadm_cluster_identity >/dev/null 2>&1)
  execution_status=$?
  set -e
  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: 다른 kubeadm cluster identity를 staging cluster로 허용하면 안 됩니다." >&2
    exit 1
  fi
)

test_requires_exact_instance_profile_role_and_identity() (
  local invalid_roles
  local execution_status

  for invalid_roles in \
    '' \
    'car-community-k8s-node-ecr-pull-role' \
    $'stg-car-community-k8s-node-role\nanother-role'; do
    set +e
    (set_expected_node_provider_id_from_imds \
      "${invalid_roles}" 'i-0123456789abcdef0' 'ap-northeast-2a' >/dev/null 2>&1)
    execution_status=$?
    set -e
    if [[ "${execution_status}" -eq 0 ]]; then
      echo "FAIL: old·zero·multi instance profile role을 허용하면 안 됩니다: ${invalid_roles:-0개}" >&2
      exit 1
    fi
  done

  set_expected_node_provider_id_from_imds \
    "${EXPECTED_NODE_ROLE_NAME}" 'i-0123456789abcdef0' 'ap-northeast-2a'
  assert_equals 'aws:///ap-northeast-2a/i-0123456789abcdef0' \
    "${EXPECTED_NODE_PROVIDER_ID}" \
    "같은 IMDSv2 identity로 instance target용 providerID를 만들어야 합니다."
)

test_imds_token_is_not_exposed_in_curl_arguments() (
  local test_root="$1"
  local argument_log="${test_root}/imds-curl-arguments"
  local header_log="${test_root}/imds-curl-header"
  local response
  local arguments

  curl() {
    printf '%s\n' "$*" >"${argument_log}"
    cat >"${header_log}"
    printf '%s\n' 'metadata-response'
  }

  response="$(fetch_imds_with_token \
    'sensitive-imdsv2-token' 'meta-data/instance-id')"
  arguments="$(<"${argument_log}")"

  assert_equals 'metadata-response' "${response}" \
    "IMDSv2 metadata response를 호출자에게 반환해야 합니다."
  assert_contains '--noproxy *' "${arguments}" \
    "link-local IMDS 요청을 proxy로 전달하면 안 됩니다."
  assert_contains '--header @-' "${arguments}" \
    "IMDSv2 token은 curl argv가 아니라 stdin header로 전달해야 합니다."
  if [[ "${arguments}" == *'sensitive-imdsv2-token'* ]]; then
    echo "FAIL: IMDSv2 token을 process argv에 노출하면 안 됩니다." >&2
    exit 1
  fi
  assert_equals \
    'X-aws-ec2-metadata-token: sensitive-imdsv2-token' \
    "$(<"${header_log}")" \
    "IMDSv2 token header를 정확히 전달해야 합니다."
)

test_download_rejects_checksum_mismatch() (
  local test_root="$1"
  local destination_path="${test_root}/artifact.tgz"
  local execution_status

  curl() {
    local argument
    local output_path=""
    local next_is_output=0

    for argument in "$@"; do
      if [[ "${next_is_output}" -eq 1 ]]; then
        output_path="${argument}"
        break
      fi
      if [[ "${argument}" == "--output" ]]; then
        next_is_output=1
      fi
    done
    printf '%s\n' "changed upstream bytes" >"${output_path}"
  }

  set +e
  (download_verified_artifact \
    "https://example.invalid/artifact.tgz" \
    "$(printf '0%.0s' {1..64})" \
    "${destination_path}" >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 || -e "${destination_path}" ]]; then
    echo "FAIL: checksum이 다른 artifact를 남기거나 실행 대상으로 허용하면 안 됩니다." >&2
    exit 1
  fi
)

test_download_uses_bounded_retry() (
  local test_root="$1"
  local destination_path="${test_root}/verified-artifact.tgz"
  local curl_arguments="${test_root}/curl-arguments"
  local expected_checksum

  expected_checksum="$(printf '%s\n' 'verified artifact' | sha256sum | awk '{print $1}')"

  curl() {
    local argument
    local output_path=""
    local next_is_output=0

    printf '%s\n' "$*" >"${curl_arguments}"
    for argument in "$@"; do
      if [[ "${next_is_output}" -eq 1 ]]; then
        output_path="${argument}"
        break
      fi
      if [[ "${argument}" == "--output" ]]; then
        next_is_output=1
      fi
    done
    printf '%s\n' 'verified artifact' >"${output_path}"
  }

  download_verified_artifact \
    "https://example.invalid/artifact.tgz" \
    "${expected_checksum}" \
    "${destination_path}"

  grep -Fq -- '--connect-timeout 15' "${curl_arguments}"
  grep -Fq -- '--max-time 300' "${curl_arguments}"
  grep -Fq -- '--retry 3' "${curl_arguments}"
  grep -Fq -- '--retry-delay 2' "${curl_arguments}"
  grep -Fq -- '--retry-max-time 300' "${curl_arguments}"
  if grep -Fq -- '--retry-all-errors' "${curl_arguments}"; then
    echo "FAIL: 비복구 HTTP 오류까지 재시도하면 안 됩니다." >&2
    exit 1
  fi
)

test_work_directory_creation_fails_closed() (
  local test_root="$1"
  local chmod_failure_directory="${test_root}/chmod-failure"
  local execution_status

  mktemp() { return 42; }
  set +e
  (create_work_directory >/dev/null 2>&1)
  execution_status=$?
  set -e
  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: mktemp 실패를 빈 작업 directory 성공으로 해석하면 안 됩니다." >&2
    exit 1
  fi

  mkdir -p "${chmod_failure_directory}"
  mktemp() { printf '%s\n' "${chmod_failure_directory}"; }
  chmod() { return 43; }
  set +e
  (create_work_directory >/dev/null 2>&1)
  execution_status=$?
  set -e
  if [[ "${execution_status}" -eq 0 || -d "${chmod_failure_directory}" ]]; then
    echo "FAIL: chmod 실패한 작업 directory를 남기거나 사용하면 안 됩니다." >&2
    exit 1
  fi
)

test_extracts_verified_helm_only_inside_work_directory() (
  local test_root="$1"
  local archive_path="${test_root}/helm.tgz"
  local extraction_directory="${test_root}/extracted"
  local environment_log="${test_root}/helm-version-environment"
  local helm_path
  local environment_fields

  : >"${archive_path}"
  tar() {
    mkdir -p "${extraction_directory}/linux-arm64"
    cat >"${extraction_directory}/linux-arm64/helm" <<'EOF'
#!/usr/bin/env bash
printf '%s|%s|%s\n' \
  "${HELM_CACHE_HOME:-}" \
  "${HELM_CONFIG_HOME:-}" \
  "${HELM_DATA_HOME:-}" \
  >"${HELM_VERSION_ENV_LOG}"
printf '%s\n' 'v3.21.4+gfixture'
EOF
    chmod 0755 "${extraction_directory}/linux-arm64/helm"
  }

  helm_path="$(HELM_VERSION_ENV_LOG="${environment_log}" \
    extract_verified_helm_client \
      "${archive_path}" "${extraction_directory}")"
  environment_fields="$(<"${environment_log}")"

  assert_equals "${extraction_directory}/linux-arm64/helm" "${helm_path}" \
    "검증한 Helm client는 작업 directory 안에서만 실행해야 합니다."
  [[ "${helm_path}" == "${test_root}"/* ]]
  assert_equals \
    "${extraction_directory}/runtime/cache|${extraction_directory}/runtime/config|${extraction_directory}/runtime/data" \
    "${environment_fields}" \
    "Helm version 확인도 temporary runtime home만 사용해야 합니다."
)

test_helm_extraction_fails_closed_on_mkdir_or_tar_failure() (
  local test_root="$1"
  local archive_path="${test_root}/helm-extraction-failure.tgz"
  local extraction_directory="${test_root}/helm-extraction-failure"
  local execution_status

  : >"${archive_path}"
  set +e
  (
    mkdir() { return 41; }
    tar() {
      /bin/mkdir -p "${extraction_directory}/linux-arm64"
      printf '#!/usr/bin/env bash\nprintf "v3.21.4+gfixture\\n"\n' \
        >"${extraction_directory}/linux-arm64/helm"
      /bin/chmod 0755 "${extraction_directory}/linux-arm64/helm"
    }
    extract_verified_helm_client \
      "${archive_path}" "${extraction_directory}" >/dev/null 2>&1
  )
  execution_status=$?
  set -e
  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: Helm extraction directory 생성 실패를 성공으로 덮으면 안 됩니다." >&2
    exit 1
  fi

  rm -rf -- "${extraction_directory}"
  set +e
  (
    tar() {
      mkdir -p "${extraction_directory}/linux-arm64"
      printf '#!/usr/bin/env bash\nprintf "v3.21.4+gfixture\\n"\n' \
        >"${extraction_directory}/linux-arm64/helm"
      chmod 0755 "${extraction_directory}/linux-arm64/helm"
      return 42
    }
    extract_verified_helm_client \
      "${archive_path}" "${extraction_directory}" >/dev/null 2>&1
  )
  execution_status=$?
  set -e
  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: 부분 Helm extraction 뒤 tar 실패를 성공으로 덮으면 안 됩니다." >&2
    exit 1
  fi
)

test_chart_extraction_fails_closed_on_mkdir_or_tar_failure() (
  local test_root="$1"
  local archive_path="${test_root}/chart-extraction-failure.tgz"
  local extraction_directory="${test_root}/chart-extraction-failure"
  local chart_directory="${extraction_directory}/aws-load-balancer-controller"
  local execution_status

  : >"${archive_path}"
  set +e
  (
    mkdir() { return 41; }
    tar() {
      /bin/mkdir -p "${chart_directory}/crds"
      : >"${chart_directory}/Chart.yaml"
      : >"${chart_directory}/crds/crds.yaml"
      : >"${chart_directory}/crds/gateway-crds.yaml"
    }
    extract_controller_chart \
      "${archive_path}" "${extraction_directory}" >/dev/null 2>&1
  )
  execution_status=$?
  set -e
  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: Chart extraction directory 생성 실패를 성공으로 덮으면 안 됩니다." >&2
    exit 1
  fi

  rm -rf -- "${extraction_directory}"
  set +e
  (
    tar() {
      mkdir -p "${chart_directory}/crds"
      : >"${chart_directory}/Chart.yaml"
      : >"${chart_directory}/crds/crds.yaml"
      : >"${chart_directory}/crds/gateway-crds.yaml"
      return 42
    }
    extract_controller_chart \
      "${archive_path}" "${extraction_directory}" >/dev/null 2>&1
  )
  execution_status=$?
  set -e
  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: 부분 Chart extraction 뒤 tar 실패를 성공으로 덮으면 안 됩니다." >&2
    exit 1
  fi
)

test_rejects_foreign_resource_owner() (
  local execution_status

  resource_exists() { return 0; }
  resource_owner_fields() {
    printf '%s\n' 'another-owner|another-release|kube-system'
  }

  set +e
  (require_helm_owned_if_present \
    "deployment/aws-load-balancer-controller" "kube-system" >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: 다른 owner의 controller resource를 자동 인수하면 안 됩니다." >&2
    exit 1
  fi
)

test_rejects_foreign_controller_version() (
  local execution_status

  resource_exists() { return 0; }
  resource_owner_fields() {
    printf '%s\n' "Helm|${CONTROLLER_RELEASE}|${CONTROLLER_NAMESPACE}"
  }
  kubectl_admin() {
    printf '%s\n' \
      'aws-load-balancer-controller-3.4.0|public.ecr.aws/eks/aws-load-balancer-controller:v3.4.0|1'
  }

  set +e
  (require_existing_deployment_matches_target_if_present >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: 기존 다른 controller version을 자동 upgrade·downgrade하면 안 됩니다." >&2
    exit 1
  fi
)

test_accepts_same_version_controller_on_rerun() (
  resource_exists() { return 0; }
  resource_owner_fields() {
    printf '%s\n' "Helm|${CONTROLLER_RELEASE}|${CONTROLLER_NAMESPACE}"
  }
  kubectl_admin() {
    printf '%s\n' \
      "aws-load-balancer-controller-${CONTROLLER_CHART_VERSION}|${CONTROLLER_IMAGE}|1"
  }

  require_existing_deployment_matches_target_if_present
)

test_accepts_exact_and_rejects_foreign_helm_release_inventory() (
  local test_root="$1"
  local fake_helm="${test_root}/inventory-helm/linux-arm64/helm"
  local exact_release
  local foreign_release
  local execution_status

  mkdir -p "${fake_helm%/*}"
  cat >"${fake_helm}" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "${HELM_LIST_OUTPUT}"
EOF
  chmod 0755 "${fake_helm}"
  resource_exists() { return 0; }

  exact_release='[{"name":"aws-load-balancer-controller","namespace":"kube-system","revision":"1","updated":"fixture","status":"deployed","chart":"aws-load-balancer-controller-3.5.0","app_version":"v3.5.0"}]'
  HELM_LIST_OUTPUT="${exact_release}" \
    require_release_and_deployment_consistent "${fake_helm}"

  foreign_release='[{"name":"aws-load-balancer-controller","namespace":"kube-system","revision":"1","updated":"fixture","status":"deployed","chart":"aws-load-balancer-controller-3.4.0","app_version":"v3.4.0"}]'
  set +e
  (HELM_LIST_OUTPUT="${foreign_release}" \
    require_release_and_deployment_consistent "${fake_helm}" >/dev/null 2>&1)
  execution_status=$?
  set -e
  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: 다른 chart version의 Helm release를 자동 upgrade하면 안 됩니다." >&2
    exit 1
  fi
)

test_first_install_skips_custom_resource_owner_query_before_crd_exists() (
  resource_exists() {
    if [[ "$1" == "ingressclassparams/alb" ]]; then
      fail "CRD가 없는데 custom resource kind를 조회했습니다."
    fi
    return 1
  }

  require_existing_resource_owners
)

test_rejects_hpa_when_fixed_replica_mode_is_required() (
  local execution_status

  resource_exists() {
    [[ "$1" == "horizontalpodautoscaler/aws-load-balancer-controller" ]]
  }

  set +e
  (require_controller_hpa_absent >/dev/null 2>&1)
  execution_status=$?
  set -e
  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: fixed replica mode에서 기존 foreign HPA를 허용하면 안 됩니다." >&2
    exit 1
  fi
)

test_crd_apply_waits_for_all_required_crds_to_be_established() (
  local test_root="$1"
  local action_log="${test_root}/crd-actions"
  local action_log_content
  local crd_name
  local execution_status
  local -a required_crds=(
    albtargetcontrolconfigs.elbv2.k8s.aws
    globalaccelerators.aga.k8s.aws
    ingressclassparams.elbv2.k8s.aws
    targetgroupbindings.elbv2.k8s.aws
    listenerruleconfigurations.gateway.k8s.aws
    loadbalancerconfigurations.gateway.k8s.aws
    targetgroupconfigurations.gateway.k8s.aws
  )

  kubectl_admin_long() { printf '%s\n' "$*" >>"${action_log}"; }
  apply_controller_crds "${test_root}/chart"
  action_log_content="$(<"${action_log}")"

  if [[ "$(awk 'NR == 1 { print $1 }' "${action_log}")" != "apply" ]]; then
    echo "FAIL: CRD manifest apply가 Established wait보다 먼저 실행돼야 합니다." >&2
    exit 1
  fi
  for crd_name in "${required_crds[@]}"; do
    assert_contains \
      "wait --for=condition=Established customresourcedefinition/${crd_name}" \
      "${action_log_content}" \
      "Helm 전에 required CRD Established를 기다려야 합니다."
  done
  if [[ "$(grep -Fc -- 'wait --for=condition=Established' "${action_log}")" -ne 7 ]]; then
    echo "FAIL: required CRD 7개 각각의 Established 상태를 확인해야 합니다." >&2
    exit 1
  fi

  kubectl_admin_long() {
    if [[ "$1" == "wait" ]]; then
      return 42
    fi
  }
  set +e
  (apply_controller_crds "${test_root}/chart" >/dev/null 2>&1)
  execution_status=$?
  set -e
  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: CRD Established wait 실패 뒤 Helm 단계로 진행하면 안 됩니다." >&2
    exit 1
  fi
)

test_node_provider_id_absent_exact_foreign_and_query_failure() (
  local test_root="$1"
  local patch_marker="${test_root}/provider-id-patched"
  local execution_status

  TARGET_NODE_NAME='ip-10-0-8-159'
  EXPECTED_NODE_PROVIDER_ID='aws:///ap-northeast-2a/i-0123456789abcdef0'

  kubectl_admin() {
    if [[ "$1" == 'get' ]]; then
      if [[ "$*" == *'--allow-missing-template-keys=false'* ]]; then
        return 88
      fi
      if [[ -f "${patch_marker}" ]]; then
        printf '%s\n' "${EXPECTED_NODE_PROVIDER_ID}"
      fi
      return
    fi
    if [[ "$1" == 'patch' ]]; then
      : >"${patch_marker}"
      return
    fi
    return 1
  }
  ensure_node_provider_id
  [[ -f "${patch_marker}" ]]

  kubectl_admin() {
    if [[ "$1" == 'get' ]]; then
      printf '%s\n' "${EXPECTED_NODE_PROVIDER_ID}"
      return
    fi
    fail "exact providerID 재실행에서 patch하면 안 됩니다."
  }
  ensure_node_provider_id

  kubectl_admin() {
    printf '%s\n' 'aws:///ap-northeast-2b/i-0fedcba9876543210'
  }
  set +e
  (ensure_node_provider_id >/dev/null 2>&1)
  execution_status=$?
  set -e
  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: 다른 nonempty node providerID를 자동 변경하면 안 됩니다." >&2
    exit 1
  fi

  kubectl_admin() { return 42; }
  set +e
  (ensure_node_provider_id >/dev/null 2>&1)
  execution_status=$?
  set -e
  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: node providerID query 실패 뒤 patch하면 안 됩니다." >&2
    exit 1
  fi
)

test_helm_upgrade_uses_exact_target_values() (
  local test_root="$1"
  local fake_helm="${test_root}/install-helm/linux-arm64/helm"
  local argument_log="${test_root}/helm-arguments"
  local arguments

  mkdir -p "${fake_helm%/*}"
  cat >"${fake_helm}" <<'EOF'
#!/usr/bin/env bash
printf 'KUBECONFIG=%s CACHE=%s CONFIG=%s DATA=%s ARGS=%s\n' \
  "${KUBECONFIG:-}" \
  "${HELM_CACHE_HOME:-}" \
  "${HELM_CONFIG_HOME:-}" \
  "${HELM_DATA_HOME:-}" \
  "$*" \
  >"${HELM_ARGUMENT_LOG}"
EOF
  chmod 0755 "${fake_helm}"

  HELM_ARGUMENT_LOG="${argument_log}" install_controller \
    "${fake_helm}" "${test_root}/controller.tgz" "vpc-0123456789abcdef0"
  arguments="$(<"${argument_log}")"

  assert_contains "KUBECONFIG=${ADMIN_KUBECONFIG}" "${arguments}" \
    "Helm install도 root 기본 context가 아닌 exact admin kubeconfig를 사용해야 합니다."
  assert_contains "CACHE=${test_root}/install-helm/runtime/cache" "${arguments}" \
    "Helm cache가 verified client의 temporary root 밖에 남으면 안 됩니다."
  assert_contains "CONFIG=${test_root}/install-helm/runtime/config" "${arguments}" \
    "Helm config가 verified client의 temporary root 밖에 남으면 안 됩니다."
  assert_contains "DATA=${test_root}/install-helm/runtime/data" "${arguments}" \
    "Helm data가 verified client의 temporary root 밖에 남으면 안 됩니다."
  assert_contains "ARGS=upgrade --install" "${arguments}" \
    "Helm install command를 exact wrapper로 실행해야 합니다."
  assert_contains "--kubeconfig ${ADMIN_KUBECONFIG}" "${arguments}" \
    "Helm install argv에도 exact admin kubeconfig를 고정해야 합니다."
  assert_contains "upgrade --install ${CONTROLLER_RELEASE}" "${arguments}" \
    "같은 목표 상태로 안전하게 재실행할 수 있어야 합니다."
  assert_contains "--namespace ${CONTROLLER_NAMESPACE}" "${arguments}" \
    "controller release namespace를 고정해야 합니다."
  assert_contains "clusterName=${CLUSTER_NAME}" "${arguments}" \
    "self-managed cluster 이름을 명시해야 합니다."
  assert_contains "region=${AWS_REGION}" "${arguments}" \
    "IMDS 추론 대신 region을 명시해야 합니다."
  assert_contains "vpcId=vpc-0123456789abcdef0" "${arguments}" \
    "검증한 runtime VPC ID를 명시해야 합니다."
  assert_contains "replicaCount=1" "${arguments}" \
    "현재 단일 node 규모에는 controller replica 하나를 사용해야 합니다."
  assert_contains "serviceAccount.create=true" "${arguments}" \
    "chart가 controller ServiceAccount를 소유해야 합니다."
  assert_contains "enableCertManager=false" "${arguments}" \
    "별도 cert-manager dependency를 만들면 안 됩니다."
  assert_contains "keepTLSSecret=true" "${arguments}" \
    "재실행 때 webhook TLS Secret을 불필요하게 교체하면 안 됩니다."
  if [[ "${arguments}" == *'enableManageBackendSecurityGroupRules='* ]]; then
    echo "FAIL: custom frontend SG가 없는 baseline에서 global backend SG rule override를 지정하면 안 됩니다." >&2
    exit 1
  fi
  assert_contains "image.tag=${CONTROLLER_IMAGE_TAG_WITH_DIGEST}" "${arguments}" \
    "controller image tag와 ARM64 digest를 함께 고정해야 합니다."
  assert_contains "--atomic" "${arguments}" \
    "부분 Helm release를 성공 상태로 남기면 안 됩니다."
  assert_contains "--wait" "${arguments}" \
    "controller rollout 전 Helm 단계를 완료하면 안 됩니다."
)

test_deployment_argument_query_reads_each_scalar_value() (
  RUNTIME_VPC_ID="vpc-0123456789abcdef0"

  kubectl_admin() {
    if [[ "$*" != *'jsonpath={range .spec.template.spec.containers[?(@.name=="aws-load-balancer-controller")].args[*]}{@}{"\n"}{end}'* ]]; then
      return 42
    fi
    printf '%s\n' \
      "--cluster-name=${CLUSTER_NAME}" \
      "--ingress-class=alb" \
      "--aws-region=${AWS_REGION}" \
      "--aws-vpc-id=${RUNTIME_VPC_ID}"
  }

  verify_deployment_arguments
)

test_helm_list_and_status_use_admin_kubeconfig() (
  local test_root="$1"
  local fake_helm="${test_root}/context-helm/linux-arm64/helm"
  local argument_log="${test_root}/helm-context-arguments"
  local arguments

  mkdir -p "${fake_helm%/*}"
  cat >"${fake_helm}" <<'EOF'
#!/usr/bin/env bash
printf 'KUBECONFIG=%s ARGS=%s\n' "${KUBECONFIG:-}" "$*" >>"${HELM_ARGUMENT_LOG}"
if [[ "$1" == 'list' ]]; then
  printf '%s\n' '[]'
fi
EOF
  chmod 0755 "${fake_helm}"

  resource_exists() { return 1; }
  HELM_ARGUMENT_LOG="${argument_log}" \
    require_release_and_deployment_consistent "${fake_helm}"
  HELM_ARGUMENT_LOG="${argument_log}" \
    require_helm_release_status "${fake_helm}"
  arguments="$(<"${argument_log}")"

  assert_contains \
    "KUBECONFIG=${ADMIN_KUBECONFIG} ARGS=list --namespace ${CONTROLLER_NAMESPACE}" \
    "${arguments}" \
    "Helm release discovery가 exact admin kubeconfig를 사용해야 합니다."
  assert_contains \
    "KUBECONFIG=${ADMIN_KUBECONFIG} ARGS=status ${CONTROLLER_RELEASE} --namespace ${CONTROLLER_NAMESPACE}" \
    "${arguments}" \
    "Helm release postcondition도 exact admin kubeconfig를 사용해야 합니다."
  if [[ "$(grep -Fc -- "--kubeconfig ${ADMIN_KUBECONFIG}" "${argument_log}")" -ne 2 ]]; then
    echo "FAIL: Helm list와 status 모두 explicit kubeconfig flag를 사용해야 합니다." >&2
    exit 1
  fi
)

test_main_preserves_required_mutation_order() (
  local test_root="$1"
  local action_log="${test_root}/actions"
  local fixture_work_directory="${test_root}/work"
  local provider_index
  local crd_apply_index
  local crd_established_index
  local helm_index
  local controller_verify_index
  local label_remove_index
  local label_verify_index
  local final_index

  parse_arguments() { RUNTIME_VPC_ID="vpc-0123456789abcdef0"; }
  run_preflight_checks() { printf '%s\n' preflight >>"${action_log}"; }
  create_work_directory() {
    mkdir -p "${fixture_work_directory}"
    printf '%s\n' "${fixture_work_directory}"
  }
  download_verified_artifact() {
    printf '%s\n' "download:$1" >>"${action_log}"
    : >"$3"
  }
  extract_verified_helm_client() {
    printf '%s\n' extract-helm >>"${action_log}"
    printf '%s\n' "${fixture_work_directory}/helm"
  }
  extract_controller_chart() {
    printf '%s\n' extract-chart >>"${action_log}"
    printf '%s\n' "${fixture_work_directory}/chart"
  }
  require_safe_existing_installation() { printf '%s\n' owner-check >>"${action_log}"; }
  ensure_node_provider_id() { printf '%s\n' ensure-provider-id >>"${action_log}"; }
  apply_controller_crds() {
    printf '%s\n' apply-crds wait-crds-established >>"${action_log}"
  }
  install_controller() { printf '%s\n' helm-upgrade >>"${action_log}"; }
  verify_controller_postconditions() { printf '%s\n' verify-controller >>"${action_log}"; }
  remove_external_load_balancer_exclusion_label() {
    printf '%s\n' remove-label >>"${action_log}"
  }
  verify_external_load_balancer_exclusion_label_absent() {
    printf '%s\n' verify-label >>"${action_log}"
  }

  main "vpc-0123456789abcdef0" >/dev/null

  provider_index="$(awk '$0 == "ensure-provider-id" { print NR }' "${action_log}")"
  crd_apply_index="$(awk '$0 == "apply-crds" { print NR }' "${action_log}")"
  crd_established_index="$(awk '$0 == "wait-crds-established" { print NR }' "${action_log}")"
  helm_index="$(awk '$0 == "helm-upgrade" { print NR }' "${action_log}")"
  controller_verify_index="$(awk '$0 == "verify-controller" { print NR }' "${action_log}")"
  label_remove_index="$(awk '$0 == "remove-label" { print NR }' "${action_log}")"
  label_verify_index="$(awk '$0 == "verify-label" { print NR }' "${action_log}")"
  final_index="$(awk 'END { print NR }' "${action_log}")"

  if [[ -z "${provider_index}" || -z "${crd_apply_index}" \
      || -z "${crd_established_index}" || -z "${helm_index}" \
      || -z "${controller_verify_index}" || -z "${label_remove_index}" \
      || -z "${label_verify_index}" ]]; then
    echo "FAIL: 필수 mutation·barrier action이 호출되지 않았습니다." >&2
    exit 1
  fi
  if ! (( provider_index < crd_apply_index \
      && crd_apply_index < crd_established_index \
      && crd_established_index < helm_index \
      && helm_index < controller_verify_index \
      && controller_verify_index < label_remove_index \
      && label_remove_index + 1 == label_verify_index \
      && label_verify_index == final_index )); then
    printf 'FAIL: providerID < CRD Established < Helm < controller 검증 < label-last 순서를 위반했습니다.\n%s\n' \
      "$(<"${action_log}")" >&2
    exit 1
  fi
)

test_main_cleans_ephemeral_work_directory_on_success_and_failure() {
  local test_root="$1"
  local success_directory="${test_root}/success-work"
  local failure_directory="${test_root}/failure-work"
  local execution_status

  run_main_case() (
    local work_directory="$1"
    local result="$2"

    parse_arguments() { RUNTIME_VPC_ID="vpc-0123456789abcdef0"; }
    run_preflight_checks() { :; }
    create_work_directory() {
      mkdir -p "${work_directory}"
      printf '%s\n' "${work_directory}"
    }
    download_verified_artifact() { : >"$3"; }
    extract_verified_helm_client() { printf '%s\n' "${work_directory}/helm"; }
    extract_controller_chart() { printf '%s\n' "${work_directory}/chart"; }
    require_safe_existing_installation() { :; }
    ensure_node_provider_id() { :; }
    apply_controller_crds() { :; }
    install_controller() { [[ "${result}" == 'success' ]]; }
    verify_controller_postconditions() { :; }
    remove_external_load_balancer_exclusion_label() { :; }
    verify_external_load_balancer_exclusion_label_absent() { :; }

    set -e
    main "vpc-0123456789abcdef0" >/dev/null
  )

  run_main_case "${success_directory}" success
  if [[ -e "${success_directory}" ]]; then
    echo "FAIL: 성공 뒤 ephemeral work directory를 남기면 안 됩니다." >&2
    exit 1
  fi

  set +e
  run_main_case "${failure_directory}" failure >/dev/null 2>&1
  execution_status=$?
  set -e
  if [[ "${execution_status}" -eq 0 || -e "${failure_directory}" ]]; then
    echo "FAIL: 실패 뒤 ephemeral work directory를 남기거나 성공으로 보고하면 안 됩니다." >&2
    exit 1
  fi
}

test_has_no_direct_aws_cli_or_embedded_application_ingress_manifest() {
  if grep -Eq -- '(^|[;&|()[:space:]])aws([[:space:]\\]|$)' \
      "${TARGET_SCRIPT}"; then
    echo "FAIL: controller bootstrap이 AWS CLI를 직접 호출하면 안 됩니다." >&2
    exit 1
  fi
  if grep -Eq -- 'kind:[[:space:]]*(Ingress|Service)([[:space:]]|$)' \
      "${TARGET_SCRIPT}"; then
    echo "FAIL: controller bootstrap에 application Ingress·Service manifest를 내장하면 안 됩니다." >&2
    exit 1
  fi
}

run_tests() {
  TEST_RUNTIME_DIRECTORY="$(mktemp -d)"
  trap '[[ -z "${TEST_RUNTIME_DIRECTORY:-}" ]] || rm -rf -- "${TEST_RUNTIME_DIRECTORY}"' EXIT

  test_pins_supported_releases_and_artifacts
  test_requires_complete_external_command_inventory
  test_rejects_invalid_vpc_id
  test_ready_node_query_uses_live_confirmed_scalar_filter "${TEST_RUNTIME_DIRECTORY}"
  test_rejects_empty_duplicate_or_failed_ready_query
  test_requires_exact_kubeadm_cluster_identity
  test_requires_exact_instance_profile_role_and_identity
  test_imds_token_is_not_exposed_in_curl_arguments "${TEST_RUNTIME_DIRECTORY}"
  test_download_rejects_checksum_mismatch "${TEST_RUNTIME_DIRECTORY}"
  test_download_uses_bounded_retry "${TEST_RUNTIME_DIRECTORY}"
  test_work_directory_creation_fails_closed "${TEST_RUNTIME_DIRECTORY}"
  test_extracts_verified_helm_only_inside_work_directory "${TEST_RUNTIME_DIRECTORY}"
  test_helm_extraction_fails_closed_on_mkdir_or_tar_failure "${TEST_RUNTIME_DIRECTORY}"
  test_chart_extraction_fails_closed_on_mkdir_or_tar_failure "${TEST_RUNTIME_DIRECTORY}"
  test_rejects_foreign_resource_owner
  test_rejects_foreign_controller_version
  test_accepts_same_version_controller_on_rerun
  test_accepts_exact_and_rejects_foreign_helm_release_inventory "${TEST_RUNTIME_DIRECTORY}"
  test_first_install_skips_custom_resource_owner_query_before_crd_exists
  test_rejects_hpa_when_fixed_replica_mode_is_required
  test_crd_apply_waits_for_all_required_crds_to_be_established "${TEST_RUNTIME_DIRECTORY}"
  test_node_provider_id_absent_exact_foreign_and_query_failure "${TEST_RUNTIME_DIRECTORY}"
  test_helm_upgrade_uses_exact_target_values "${TEST_RUNTIME_DIRECTORY}"
  test_deployment_argument_query_reads_each_scalar_value
  test_helm_list_and_status_use_admin_kubeconfig "${TEST_RUNTIME_DIRECTORY}"
  test_main_preserves_required_mutation_order "${TEST_RUNTIME_DIRECTORY}"
  test_main_cleans_ephemeral_work_directory_on_success_and_failure "${TEST_RUNTIME_DIRECTORY}"
  test_has_no_direct_aws_cli_or_embedded_application_ingress_manifest

  echo "PASS: 100-install-aws-load-balancer-controller.sh contract tests"
}

run_tests
