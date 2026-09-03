# Application 환경변수·Secret 변경 runbook

이 절차는 최초 Application bootstrap 뒤 **변경할 환경변수 이름**을 입력받아 실제 source,
owner, consumer와 실행 가능한 전용 절차를 찾는 공용 진입점입니다. 현재 변수·Secret 목록과
특정 provider의 수명주기는 이 문서가 소유하지 않습니다.

## 실행 계약

| 항목 | 계약 |
| --- | --- |
| 언제 | Application 환경변수 계약이나 값을 추가·변경할 때 사용합니다. |
| 누가 | Repository 변경 권한과 target 환경 확인 권한을 가진 운영자가 실행합니다. Secret 값 변경은 발견한 credential owner가 수행합니다. |
| 어디서 | Source 확인은 local repository terminal, live 확인은 target cluster에 접근 가능한 terminal에서 수행합니다. |
| 입력 | `<ENV_NAME>`은 변경할 환경변수 이름입니다. 환경·overlay·Application·resource identity는 현재 source에서 발견합니다. |
| 완료·재개 | Git-managed 값은 배포와 직접 기능 검증까지, Secret 값은 전용 Procedure handoff 완료까지 확인합니다. 전용 owner가 없으면 live mutation 없이 중단합니다. |

## 1. 변경할 환경변수의 owner와 consumer 발견

### 1.1 Repository source와 render 확인

**실행 위치:** Local repository terminal

**시작 directory:** Repository root

```bash
TARGET_ENV_NAME='<ENV_NAME>'

rg -n -- "${TARGET_ENV_NAME}" \
  src/main/resources deploy scripts .env.example \
  docs/development docs/operations
```

검색 결과에서 Application property binding과 기본값, rendered value source, 값을 생성·변경하는
owner와 직접 기능 검증 entrypoint를 기록합니다. `envFrom`이면 owner source에서 해당 key를
포함하는 ConfigMap 또는 Secret 이름도 기록합니다. 환경변수를 선언한 base manifest 이름만으로
target overlay를 추측하지 않습니다. 모든 Kustomization을 render해 해당 consumer를 포함하는
후보를 찾고, 그 경로를 source로 지정한 Application을 연결합니다.

```bash
TARGET_VALUE_RESOURCE_NAME='<envFrom resource 이름; direct env이면 빈 값>'

while IFS= read -r kustomization_path; do
  candidate_path="${kustomization_path%/kustomization.yaml}"
  candidate_render="$(kubectl kustomize "${candidate_path}" 2>/dev/null)" || continue
  if printf '%s\n' "${candidate_render}" | rg -q -F -- "name: ${TARGET_ENV_NAME}" \
      || { test -n "${TARGET_VALUE_RESOURCE_NAME}" \
        && printf '%s\n' "${candidate_render}" \
          | rg -q -F -- "name: ${TARGET_VALUE_RESOURCE_NAME}"; }; then
    printf 'overlayCandidate=%s\n' "${candidate_path}"
    rg -n -C 8 -F -- "path: ${candidate_path}" scripts deploy || true
  fi
done < <(find deploy -type f -name kustomization.yaml -print | sort)
```

`overlayCandidate` 가운데 환경별 최종 overlay이면서 이를 가리키는 Application source가 정확히
하나인 경로를 선택합니다. Application 연결이 없거나 둘 이상이라 target environment,
Application 이름·Namespace를 하나로 고정할 수 없으면 변경하지 않습니다. 선택한 Application
manifest에서 destination Namespace와 target revision도 함께 기록합니다.

```bash
TARGET_OVERLAY_PATH='<SOURCE에서 발견한 overlay 경로>'

kubectl kustomize "${TARGET_OVERLAY_PATH}" | less
```

`less`에서 `/<ENV_NAME>`과 `/envFrom:`을 검색합니다. 발견한 모든 resource의 `kind`,
`metadata.name`, `metadata.namespace`, container, value source와 selector를 기록합니다.
Consumer나 owner를 하나라도 식별할 수 없으면 값을 변경하지 않습니다.

### 1.2 Live identity 확인

Secret-backed target일 때만 실행합니다. `<NAMESPACE>`와 `<SECRET_NAME>`은 render에서,
expected owner와 key schema는 값을 생성하는 source에서 가져옵니다.

**실행 위치:** Target cluster에 접근 가능한 terminal

**시작 directory:** 제한 없음

```bash
TARGET_NAMESPACE='<NAMESPACE>'
TARGET_SECRET_NAME='<SECRET_NAME>'

sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get "secret/${TARGET_SECRET_NAME}" -n "${TARGET_NAMESPACE}" \
  --ignore-not-found \
  -o go-template='name={{.metadata.name}}{{"\n"}}namespace={{.metadata.namespace}}{{"\n"}}managedBy={{index .metadata.labels "app.kubernetes.io/managed-by"}}{{"\n"}}type={{.type}}{{"\n"}}{{range $key, $_ := .data}}key={{$key}}{{"\n"}}{{end}}'
```

이 명령은 Secret 값을 출력하지 않습니다. Resource가 있으면 source의 owner·type·key schema와
모두 같아야 합니다. Resource가 없으면 **최초 생성까지 명시적으로 소유하는 전용
entrypoint**가 있는지 확인합니다. 기존 값 변경만 지원하는 절차나 전체 bootstrap을 임의로
재사용하지 않습니다.

Kafka 역할별 Secret·mTLS·identity rotation은 이 공용 절차에 복제하지 않고 [380 Kafka lifecycle](380-kafka-lifecycle-runbook.md)로 인계합니다.

## 2. 변경 경로 선택

| Source에서 확인한 변경 | 실행 경로 |
| --- | --- |
| Application code·property binding 변경 | [200 개발 작업 워크플로우](../workflow/200-development-workflow.md)로 구현·검증한 뒤 image 게시와 GitOps 배포 |
| Git-tracked literal·ConfigMap 변경 | [3절](#3-git-managed-값-변경과-배포) |
| Secret 값 변경 또는 누락 Secret 생성 | [000 운영 문서 목차](000-index.md)에서 발견한 전용 Procedure로 handoff하고 이 공용 절차 종료 |
| 새 환경변수 계약 | Application·manifest·`.env.example`·local development 문서를 함께 개발·검증한 뒤 1절부터 재실행 |

Secret-backed target에 전용 Procedure가 없거나 그 Procedure가 현재 상태의 생성·변경을
지원하지 않으면 live 값을 출력하거나 `kubectl edit`, 임의 patch, Secret 삭제·재생성,
bootstrap 재실행으로 우회하지 않습니다. 필요한 target 전용 입력 검증, 동시성, rollout,
직접 기능 검증과 rollback을 canonical operation으로 먼저 구현·검증한 뒤 router에 연결합니다.

## 3. Git-managed 값 변경과 배포

### 3.1 Baseline과 candidate render

**실행 위치:** Local repository terminal

**시작 directory:** Repository root

```bash
CONFIG_RENDER_DIRECTORY="$(mktemp -d /tmp/application-config-change.XXXXXX)"
trap 'rm -rf -- "${CONFIG_RENDER_DIRECTORY}"' EXIT INT TERM

kubectl kustomize "${TARGET_OVERLAY_PATH}" \
  > "${CONFIG_RENDER_DIRECTORY}/before.yaml"

# 여기서 source를 수정합니다.

kubectl kustomize "${TARGET_OVERLAY_PATH}" \
  > "${CONFIG_RENDER_DIRECTORY}/after.yaml"
diff -u "${CONFIG_RENDER_DIRECTORY}/before.yaml" \
  "${CONFIG_RENDER_DIRECTORY}/after.yaml" | less
```

Diff에서 입력한 환경변수와 1절에서 발견한 consumer만 바뀌었는지 확인합니다. ConfigMap
이름이 그대로인데 consumer Pod template diff가 없으면 변경 값이 새 Pod에 반영되지 않으므로
적용하지 말고 Git-owned rollout 계약을 먼저 구현합니다.

### 3.2 Target server-side dry-run

**실행 위치:** 3.1을 실행한 같은 local shell session

**시작 directory:** Repository root

```bash
env -i HOME="${HOME}" PATH="${PATH}" \
  ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
  'sudo -n KUBECONFIG=/etc/kubernetes/admin.conf kubectl apply --dry-run=server -f -' \
  < "${CONFIG_RENDER_DIRECTORY}/after.yaml"
```

Baseline·candidate diff에 없는 `configured`·`created` 또는 error가 하나라도 있으면 적용하지
않습니다. 확인 뒤 임시 render를 정리합니다.

```bash
rm -rf -- "${CONFIG_RENDER_DIRECTORY}"
trap - EXIT INT TERM
```

### 3.3 GitOps 배포와 exact revision 확인

[300 커밋 워크플로우](../workflow/300-commit-workflow.md)로 변경을 커밋하고 review·merge한 뒤
[420 GitOps 배포와 rollback](420-gitops-deployment-and-rollback-runbook.md)의 exact revision,
Application 상태와 모든 consumer rollout을 확인합니다. Target Application 이름·Namespace와
workload identity는 1절에서 발견한 값을 그대로 사용합니다.

직접 기능 검증 entrypoint까지 성공해야 완료입니다. Ready 상태만으로 설정의 기능 성공을
대신하지 않습니다.

### 3.4 실패와 rollback

배포 또는 직접 기능 검증이 실패하면 live resource를 patch하지 않습니다. 문제 설정 commit만
되돌리는 revert 또는 forward-fix를 다시 render·server-side dry-run하고 같은 GitOps 경로로
배포합니다. 새 revision에서 모든 consumer와 직접 기능 검증을 반복합니다.

## 4. 완료 확인

- 입력한 환경변수의 property binding, owner, value source와 모든 consumer를 source에서 확인했습니다.
- Secret-backed target은 값 없이 live identity를 확인하고 현재 상태를 지원하는 전용 Procedure로 handoff했습니다.
- Git-managed target은 candidate render, server-side dry-run, exact revision, consumer와 직접 기능을 확인했습니다.
- 새 계약이면 `.env.example`과 local development 문서까지 동기화했습니다.
- 특정 provider·resource·변수의 전용 lifecycle이나 현재 inventory를 이 공용 문서에 추가하지 않았습니다.
