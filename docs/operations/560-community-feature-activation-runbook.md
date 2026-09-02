# Community 기능 활성화 runbook

이 절차는 capability-off 상태의 Community 선행 조건을 확인하고 cursor HMAC Secret을
생성·수렴시킨 뒤 별도 GitOps 변경으로 Community route를 활성화하고 Swagger 노출을
확인하는 전용 수명주기를 소유합니다. 전체 제품 수용은 별도 qualification owner가 필요합니다.

## 실행 계약

| 항목 | 계약 |
| --- | --- |
| 언제 | Community 구현이 capability-off로 배포된 뒤 staging 기능을 활성화할 때 사용합니다. |
| 누가 | Local Secret source·target SSH 권한과 repository 변경 권한을 가진 운영자가 실행합니다. |
| 어디서 | Secret 입력과 Git 변경은 local repository terminal, live 확인은 target EC2에서 수행합니다. |
| 입력 | Local `.env`의 `STG_COMMUNITY_CURSOR_HMAC_SECRET`, `<KEY_PATH>`, `<EC2_SSH_HOST>` |
| 완료·재개 | Secret과 capability-off gate를 확인하고 activation revision에서 Community OpenAPI path가 노출되면 완료합니다. 실패하면 activation commit만 되돌리고 Secret과 data는 보존합니다. |

## 1. Capability-off 배포 gate

먼저 [420 GitOps 배포와 rollback](420-gitops-deployment-and-rollback-runbook.md)에서 capability-off
commit의 exact revision이 `Succeeded|Synced|Healthy`, condition 없음, Migration PreSync
`Succeeded`, runtime imageID가 승인한 digest와 같고 Deployment rollout·Pod Ready임을 확인합니다.
이어 target EC2에서 rendered workload 계약을 한 번에 확인합니다.

```bash
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get deployment/car-community -n car-community \
  -o jsonpath='{.spec.template.spec.containers[?(@.name=="application")].env[?(@.name=="COMMUNITY_ENABLED")].value}{"|"}{.spec.replicas}{"|"}{.spec.strategy.type}{"|"}{.spec.strategy.rollingUpdate.maxSurge}{"|"}{.spec.strategy.rollingUpdate.maxUnavailable}{"|"}{.spec.template.spec.serviceAccountName}{"|"}{.spec.template.spec.automountServiceAccountToken}{"|"}{.spec.template.spec.volumes[?(@.name=="aws-web-identity-token")].projected.sources[0].serviceAccountToken.audience}{"|"}{.spec.template.spec.volumes[?(@.name=="aws-web-identity-token")].projected.sources[0].serviceAccountToken.expirationSeconds}{"|"}{.spec.template.spec.volumes[?(@.name=="aws-web-identity-token")].projected.sources[0].serviceAccountToken.path}{"\n"}'

test -z "$(sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get hpa -n car-community -o name)"
```

첫 출력은 정확히 다음과 같아야 합니다.

```text
false|1|RollingUpdate|1|0|car-community-application|false|sts.amazonaws.com|3600|token
```

[430 Application 기능 qualification](430-application-functional-qualification-runbook.md)의 기존
Auth/API smoke도 cleanup까지 성공해야 합니다.

다음 두 prerequisite는 현재 repository에 실행 가능한 canonical qualification owner가 없으므로
이 runbook이 성공을 대신 주장하지 않습니다.

- `flyway_schema_history`의 V7·V10 checksum과 기존 `auth_event.event_type` 분포를 읽어
  [001 Auth Event Taxonomy 재설계](../work-items/improvements/2026/001-auth-event-taxonomy-redesign.md)의
  지속 DB 호환성 처분을 완료해야 합니다.
- AWS Organizations·Account의 effective Block Public Access, identity bucket의 설정과 공개
  policy가 discovery와 JWKS 두 object만 허용하는지 검증할 별도 infrastructure owner가 필요합니다.

두 owner가 완료됐다는 evidence가 없으면 Secret을 준비할 수는 있지만 기능 flag를 `true`로
바꾸지 않습니다. 상위 AWS policy나 기존 schema가 source와 다르면 guard를 완화하거나 migration
validation을 우회하지 않고 해당 owner의 재개점으로 돌아갑니다.

## 2. AWS identity·private storage gate

현재 repository에는 AWS resource를 생성하거나 issuer를 전환하는 반복 operation이 없습니다.
따라서 이 절은 이미 준비된 resource의 자격만 검증합니다. Resource가 없으면 임의 생성·policy
완화 없이 중단하고, 별도 승인된 AWS provisioning·issuer 전환 Procedure를 먼저 구현합니다.

Target EC2에서 다음 child Bash를 시작하고 전체 block을 한 번에 실행합니다. Node identity에는
S3 권한을 추가하지 않으며, Application ServiceAccount token으로 얻은 임시 credential만
staging prefix를 읽고 qualification object를 생성·삭제합니다.

```bash
sudo bash --noprofile --norc <<'COMMUNITY_AWS_GATE'
set -euo pipefail
set +x
export KUBECONFIG=/etc/kubernetes/admin.conf

for command_name in aws base64 curl jq kubectl tr; do
  command -v "${command_name}" >/dev/null
done

work_directory="$(mktemp -d)"
chmod 0700 "${work_directory}"
access_key=''
credentials_json=''
qualification_key=''
secret_key=''
session_token=''
cleanup() {
  if [[ -n "${qualification_key}" && -n "${access_key}" ]] \
      && declare -F aws_as_application >/dev/null; then
    aws_as_application s3api delete-object \
      --bucket "${media_bucket}" --key "${qualification_key}" >/dev/null \
      || printf 'QUALIFICATION_OBJECT_REQUIRES_CLEANUP=%s\n' "${qualification_key}" >&2
  fi
  unset access_key secret_key session_token credentials_json
  rm -rf -- "${work_directory}"
}
trap cleanup EXIT

community_config_name="$(kubectl get deployment/car-community -n car-community \
  -o jsonpath='{.spec.template.spec.containers[?(@.name=="application")].env[?(@.name=="COMMUNITY_MEDIA_BUCKET")].valueFrom.configMapKeyRef.name}')"
media_bucket="$(kubectl get "configmap/${community_config_name}" -n car-community \
  -o jsonpath='{.data.COMMUNITY_MEDIA_BUCKET}')"
media_region="$(kubectl get "configmap/${community_config_name}" -n car-community \
  -o jsonpath='{.data.COMMUNITY_MEDIA_REGION}')"
media_role_arn="$(kubectl get "configmap/${community_config_name}" -n car-community \
  -o jsonpath='{.data.COMMUNITY_MEDIA_ROLE_ARN}')"
test -n "${media_bucket}"
test -n "${media_region}"
test -n "${media_role_arn}"

token_path="${work_directory}/service-account.jwt"
payload_path="${work_directory}/payload.json"
kubectl create token car-community-application -n car-community \
  --audience=sts.amazonaws.com --duration=10m >"${token_path}"
chmod 0600 "${token_path}"

token_payload="$(cut -d. -f2 <"${token_path}")"
case $((${#token_payload} % 4)) in
  2) token_payload="${token_payload}==" ;;
  3) token_payload="${token_payload}=" ;;
  0) ;;
  *) exit 1 ;;
esac
printf '%s' "${token_payload}" | tr '_-' '/+' | base64 -d >"${payload_path}"
jq -e '.sub == "system:serviceaccount:car-community:car-community-application" and ((.aud == "sts.amazonaws.com") or ((.aud | type) == "array" and (.aud | index("sts.amazonaws.com")) != null))' \
  "${payload_path}" >/dev/null
issuer="$(jq -er '.iss' "${payload_path}")"
curl --fail --silent --show-error "${issuer}/.well-known/openid-configuration" \
  >"${work_directory}/discovery.json"
jwks_uri="$(jq -er '.jwks_uri' "${work_directory}/discovery.json")"
curl --fail --silent --show-error "${jwks_uri}" \
  | jq -e '.keys | length > 0' >/dev/null

credentials_json="$(env -i HOME="${HOME}" PATH="${PATH}" AWS_DEFAULT_REGION="${media_region}" \
  aws sts assume-role-with-web-identity \
    --role-arn "${media_role_arn}" \
    --role-session-name car-community-activation-gate \
    --web-identity-token "file://${token_path}" \
    --duration-seconds 900 \
    --query Credentials --output json)"
access_key="$(jq -er '.AccessKeyId' <<<"${credentials_json}")"
secret_key="$(jq -er '.SecretAccessKey' <<<"${credentials_json}")"
session_token="$(jq -er '.SessionToken' <<<"${credentials_json}")"

aws_as_application() {
  env -i HOME="${HOME}" PATH="${PATH}" \
    AWS_DEFAULT_REGION="${media_region}" \
    AWS_ACCESS_KEY_ID="${access_key}" \
    AWS_SECRET_ACCESS_KEY="${secret_key}" \
    AWS_SESSION_TOKEN="${session_token}" \
    aws "$@"
}

aws_as_application s3api list-objects-v2 \
  --bucket "${media_bucket}" --prefix staging/ --max-items 1 >/dev/null
qualification_key="staging/activation-qualification-$(cat /proc/sys/kernel/random/uuid)"
aws_as_application s3api put-object \
  --bucket "${media_bucket}" --key "${qualification_key}" \
  --server-side-encryption AES256 --body /dev/null >/dev/null
aws_as_application s3api head-object \
  --bucket "${media_bucket}" --key "${qualification_key}" >/dev/null
aws_as_application s3api delete-object \
  --bucket "${media_bucket}" --key "${qualification_key}" >/dev/null
aws_as_application s3api list-objects-v2 \
  --bucket "${media_bucket}" --prefix "${qualification_key}" \
  --query KeyCount --output text | grep -Fx 0

echo 'COMMUNITY_AWS_IDENTITY_STORAGE=PASS'
COMMUNITY_AWS_GATE
```

`COMMUNITY_AWS_IDENTITY_STORAGE=PASS`가 없거나 cleanup 전 어느 명령이든 실패하면 flag를
바꾸지 않습니다. Qualification object 삭제 결과가 불확실하면 같은 exact key의 존재 여부를
확인해 삭제한 뒤 이 절 전체를 재실행합니다.

## 3. Secret rollout 계약 적용

[150 EC2 실행 artifact 전달](150-ec2-execution-artifact-transfer-runbook.md)로 현재 commit의
script와 tracked GitOps manifest를 target EC2에 전달합니다. Target EC2에서 bootstrap이 소유하는
Application manifest의 Community rollout annotation ignore를 server-side apply합니다.

```bash
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl apply --server-side --field-manager=car-community-bootstrap \
  -f /home/ubuntu/car-community-scripts/bootstrap/gitops/manifests/car-community-stg-application.yaml

sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get application/car-community-stg -n argocd \
  -o jsonpath='{range .spec.ignoreDifferences[*].jsonPointers[*]}{.}{"\n"}{end}' \
| grep -F '/spec/template/metadata/annotations/car-community.io~1community-secret-resource-version'
```

Apply가 실패하거나 마지막 명령이 exact pointer를 찾지 못하면 Secret operation을 시작하지
않습니다. Tracked manifest와 live Application owner를 대조한 뒤 같은 단계에서 재개합니다.

## 4. Community cursor Secret 생성·수렴

**실행 위치:** Local repository terminal

**시작 directory:** Repository root

`.env`를 검증하고 xtrace를 끈 현재 shell에 값을 읽습니다.

```bash
set +x
test -f .env
test ! -L .env
test "$(stat -f '%u' .env)" = "$(id -u)"
test "$(stat -f '%Lp' .env)" = '600'
. ./.env
typeset +x STG_COMMUNITY_CURSOR_HMAC_SECRET
test -n "${STG_COMMUNITY_CURSOR_HMAC_SECRET}"
```

같은 terminal에서 값 하나만 NUL-delimited stdin으로 전달합니다.

```bash
printf '%s\0' "${STG_COMMUNITY_CURSOR_HMAC_SECRET}" \
| env -i HOME="${HOME}" PATH="${PATH}" ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
  'sudo -n /home/ubuntu/car-community-scripts/operations/application/reconcile-community-secret.sh'
```

Exit code `0`과 다음 완료 메시지 중 하나를 확인합니다.

```text
[완료] Community Secret을 적용하고 새 runtime Pod Ready를 확인했습니다.
[완료] Community Secret과 runtime Pod가 이미 목표 상태입니다.
```

Operation은 줄바꿈 없는 32 bytes 이상, 인증 HMAC Secret과 다른 값을 요구합니다. Secret이
없으면 bootstrap owner와 approved schema로 생성하고, 있으면 owner·type·exact schema가 일치할
때만 같은 field manager로 수렴시킵니다. 실제 값은 argument·log·영구 file에 남기지 않습니다.

## 5. 기능 flag GitOps 변경

1절의 두 prerequisite owner가 완료되고 1절의 capability-off gate와 2~4절이 성공한 뒤에만
[540 Application 환경변수·Secret 변경](540-application-environment-and-secret-change-runbook.md)에
변경할 환경변수 이름 `COMMUNITY_ENABLED`를 입력해 Git-managed 경로를 실행합니다. 변경은
`false`에서 `true`로 바꾸는 단일 GitOps 결과 단위로 유지하고 다른 설정·resource 변경과
섞지 않습니다.

Candidate render와 target server-side dry-run, review·merge 뒤
[420 GitOps 배포와 rollback](420-gitops-deployment-and-rollback-runbook.md)에서 activation commit의
exact revision과 Deployment rollout·Ready를 확인합니다.

## 6. Swagger 노출 확인

Activation revision 배포 뒤 target EC2에서 live flag를 확인합니다.

```bash
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get deployment/car-community -n car-community \
  -o jsonpath='{range .spec.template.spec.containers[?(@.name=="application")].env[?(@.name=="COMMUNITY_ENABLED")]}{.value}{"\n"}{end}' \
| grep -Fx true
```

기존 관리자 계정으로 `/swagger-ui.html`에 로그인한 같은 origin의 browser console에서 실행합니다.

```javascript
const required = [
  "/api/v1/boards/free/posts",
  "/api/v1/posts/{postId}",
  "/api/v1/media/images",
  "/api/v1/notifications",
  "/api/v1/admin/community/reports",
];
const response = await fetch("/v3/api-docs", { credentials: "same-origin" });
if (!response.ok) throw new Error(`OpenAPI HTTP ${response.status}`);
const paths = Object.keys((await response.json()).paths ?? {});
const missing = required.filter((path) => !paths.includes(path));
if (missing.length) throw new Error(`missing Community paths: ${missing.join(", ")}`);
console.log("COMMUNITY_SWAGGER_PATHS=PASS");
```

`COMMUNITY_SWAGGER_PATHS=PASS`가 이 절차의 terminal 기능 증거입니다. 게시글·대화·반응·신고·
inbox·이미지의 R1~R6 전체 수용은 test member/admin credential, DB·S3 test data와 cleanup이라는
독립 mutation 수명주기입니다. 현재 canonical qualification Procedure가 없으므로 이를 Swagger
활성화 완료로 과장하지 않습니다. 별도 Community 기능 qualification owner를 구현·검증하기
전에는 전체 제품 수용 상태를 `미검증`으로 유지합니다.

## 7. 실패와 rollback

Secret operation이 실패하면 같은 값으로 같은 command를 다시 실행합니다. Secret apply 뒤
rollout만 실패해도 current `resourceVersion`부터 재개합니다.

Activation 뒤 기능 수용이 실패하면 `COMMUNITY_ENABLED=true` 변경 commit만 revert해 같은
render·dry-run·GitOps 배포 경로로 `false`를 복구합니다. 이미 만든 Secret, schema, data와
object는 삭제하지 않고 원인을 forward-fix한 뒤 1절부터 재검증합니다.

## 8. 완료 확인

- Capability-off 배포 gate와 별도 migration·AWS infrastructure prerequisite owner가 모두 완료됐습니다.
- Live Application이 Community Secret rollout annotation을 ignore하며 sync에도 같은 예외를 적용합니다.
- Community Secret owner·schema·value invariant와 이를 읽는 새 Pod Ready를 확인했습니다.
- Activation commit exact revision이 Synced·Healthy이고 환경변수 값이 `true`인 새 Pod가 Ready입니다.
- Application ServiceAccount의 STS 교환과 private media staging prefix put·head·delete가 성공했습니다.
- Auth/API qualification과 Community 대표 OpenAPI path 다섯 개의 노출이 성공했습니다.
- 전체 R1~R6 제품 기능은 별도 qualification Procedure가 생기기 전까지 `미검증`으로 구분했습니다.
