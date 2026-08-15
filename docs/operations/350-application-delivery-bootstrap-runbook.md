# Application delivery 최초 bootstrap runbook

Argo CD Core 설치가 끝난 staging cluster에 application DB role과 Git 밖 Secret을 준비하고,
현재 private `car-community` repository를 read-only GitHub App으로 연결해 최초
`car-community-stg` Application을 `Synced`·`Healthy`로 만드는 절차입니다.

이 Runbook은 최초 연결만 소유합니다. 이후 image digest 배포·rollback은
[420 GitOps 배포와 rollback](420-gitops-deployment-and-rollback-runbook.md), GitHub App
private key 교체는
[520 Repository credential 교체](520-argocd-repository-credential-rotation-runbook.md)가
소유합니다. 같은 repository 사용·read-only 인증·AppProject·자동 sync의 작동 원리와
선택 이유는 [Argo CD platform](../project/infrastructure/150-argocd-platform.md)에서
확인합니다.

## 실행 계약과 순서

다음 단계는 서로 다른 상태와 재개점을 가지므로 한꺼번에 실행하지 않습니다. 각 단계의
완료 결과를 확인한 뒤 다음 단계로 이동합니다.

| 순서 | 분류 | 언제·누가·어디서 | Exact 진입점 | 왜 실행하는가 | 완료·재개점 |
| --- | --- | --- | --- | --- | --- |
| `1` | 수동 필수 | Argo CD Core 완료 뒤, repository write 권한이 있는 운영자가 local repository root에서 | `deploy/overlays/stg` 편집, `kubectl kustomize` | 실제 RDS·URL·image digest를 Git 목표 상태로 확정 | Local render 성공 뒤 `2`절. 실패하면 `1`절부터 재개 |
| [`2-A`](#2-a-rds-role-적용) | Script 실행 | `1`절 입력을 가진 운영자가 local terminal에서 SSH로 EC2 script 실행 | `bootstrap/application/100-provision-database-access.sh` | PostgreSQL 16 client와 Runtime·migration DB role·최소 권한 준비 | Script exit `0` 뒤 `2-B`. 실패하면 원인 해결 뒤 같은 `100` 재실행 |
| [`2-B`](#2-b-kubernetes-application-prerequisite-적용) | Script 실행 | `2-A` 완료 뒤 같은 운영자가 local terminal에서 SSH로 EC2 script 실행 | `bootstrap/application/200-apply-application-prerequisites.sh` | Git에 저장하지 않는 Namespace·Secret 준비 | Script exit `0` 뒤 `2.1`. 실패하면 같은 `200` 재실행 |
| [`2.1`](#21-target-server-side-dry-run과-git-반영) | 수동 필수 | `1`·`2`절 완료 뒤 운영자가 local repository root와 target EC2를 사용 | `kubectl kustomize`, remote `kubectl apply --dry-run=server`, exact `main` push | Git 목표 상태가 target API에서도 유효한지 확인하고 Argo CD 연결 전 최초 목표를 remote에 게시 | Exact pushed SHA의 CI·Sonar 성공 뒤 `3`절. 두 application script는 재실행하지 않음 |
| [`3`](#3-read-only-github-app-준비) | 수동 필수 | Repository·App 관리 권한이 있는 운영자가 GitHub Console에서 | `Settings → Developer settings → GitHub Apps` | Argo CD에 repository read-only identity 제공 | App·Installation ID와 PEM 확보 뒤 `4`절 사전 확인 |
| [`5`](#5-local에서-ssh-stdin으로-최초-연결) | Script 실행 | `4`절 확인 완료 뒤 운영자가 local terminal에서 SSH로 EC2 script 실행 | `bootstrap/gitops/200-connect-private-gitops-repository.sh` | Repository Secret을 구성하고 Git에 추적된 AppProject·Application manifest를 최초 적용한 뒤 fresh sync 확인 | 완료 문구와 exit `0` 뒤 `6`절. 실패하면 원인 해결 뒤 같은 `5`절 명령 재실행 |
| [`6`](#6-최초-full-sync와-application-qualification) | 사람의 확인 | `5`절 성공 직후 운영자가 EC2 terminal에서 | 표준 `kubectl` read-only 명령 | 최초 sync revision·workload·exact image를 실제 상태와 결속 | 모든 확인 성공 시 GitOps 기술 bootstrap 완료 |
| 기능 qualification 중단점 | 현재 안전한 진입점 없음 | `6`절 뒤 application 기능 qualification을 해야 하는 운영자 | 별도 repository script가 아직 없음 | API→RDS write와 email provider 실제 사용은 Pod Ready만으로 증명할 수 없음 | Script 범위·입력·복구 계약을 별도 논의하기 전에는 전체 application qualification을 완료로 판정하지 않음 |

이 순서에는 local 값 조립과 SSH stdin 전달 명령이 필요하지만 application·GitOps mutation은
모두 위 repository script가 수행합니다. Runbook은 script 내부 SQL·owner guard를 복제하지
않으며, AppProject·Application spec은
`scripts/bootstrap/gitops/manifests/car-community-appproject.yaml`과
`scripts/bootstrap/gitops/manifests/car-community-stg-application.yaml`이 소유합니다.

## 공통 입력과 비밀 경계

Local repository root의 신뢰된 `.env`가 현재 사용자 소유 regular file이고 mode `0600`인지
[로컬 개발 환경](../development/100-local-development.md#환경-변수-key와-값-주입)에 따라
확인한 뒤, xtrace를 끈 현재 shell에 값을 읽습니다. 이 절은 **local
terminal**에서 실행합니다.

```bash
cd <LOCAL_REPOSITORY_ROOT>
set +x
test -f .env
test ! -L .env
test "$(stat -f '%u' .env)" = "$(id -u)"
test "$(stat -f '%Lp' .env)" = '600'
. ./.env
typeset +x \
  STG_DB_RUNTIME_PASSWORD STG_DB_MIGRATOR_PASSWORD \
  STG_AUTH_JWT_HMAC_SECRET STG_AUTH_LOGIN_IDENTIFIER_HMAC_SECRET \
  STG_RESEND_API_KEY
```

다음 값이 현재 shell에 존재해야 합니다.

- `STG_DB_RUNTIME_PASSWORD`, `STG_DB_MIGRATOR_PASSWORD`
- `STG_AUTH_JWT_HMAC_SECRET`, `STG_AUTH_LOGIN_IDENTIFIER_HMAC_SECRET`
- `STG_RESEND_API_KEY`
- `NOTIFICATION_EMAIL_FROM`
- `EMAIL_VERIFICATION_BASE_URL`, `ADMIN_INVITATION_BASE_URL`, `PASSWORD_RESET_BASE_URL`

RDS master password와 GitHub App PEM은 `.env`, Git, CLI argument와 log에 저장하지 않습니다.
실행 시 stdin으로만 전달합니다. 다음 placeholder는 실제 운영값으로 바꿉니다.

| Placeholder | Source |
| --- | --- |
| `<LOCAL_REPOSITORY_ROOT>` | `deploy`와 `scripts`가 바로 아래에 있는 local repository 절대 경로 |
| `<KEY_PATH>` | 현재 EC2 SSH private key file |
| `<EC2_SSH_HOST>` | 현재 EC2 public IPv4 또는 public DNS |
| `<GITHUB_APP_ID>` | 생성한 GitHub App의 App ID |
| `<GITHUB_APP_INSTALLATION_ID>` | `car-community`에 설치한 App의 Installation ID |
| `<LOCAL_GITHUB_APP_PRIVATE_KEY_PATH>` | Local mode `0600` PEM file |

## 1. 현재 private repository의 GitOps 경로 준비

### 실행

- **누가·어디서**: Repository 변경 권한이 있는 운영자가 local repository root에서
  실행합니다.
- **무엇을**: `deploy/overlays/stg`의 공개 설정과 exact image digest를 수정하고 표준
  `kubectl kustomize`로 render합니다.
- **왜**: Argo CD가 읽을 목표 상태를 실제 staging 입력으로 먼저 확정해야 Git 밖 Secret과
  repository 연결이 잘못된 manifest를 배포하지 않습니다.

다음 값을 source에서 확인하거나 반영합니다.

1. `deploy/overlays/stg/pre-sync/kustomization.yaml`의 `DB_URL`이 application `100`에
   고정된 staging endpoint와 같은지 확인합니다. `currentSchema=public`,
   `sslmode=verify-full`과 mounted CA path를 유지합니다.
2. `deploy/overlays/stg/kustomization.yaml`의 notification sender와 세 frontend URL을
   승인된 값으로 바꿉니다.
3. 같은 file의 `images[].digest`를
   [400 GitHub ECR image 게시](400-github-ecr-image-publishing-runbook.md)에서 ECR과 함께
   확인한 exact `sha256:<64자리>` digest로 바꿉니다.

```bash
cd <LOCAL_REPOSITORY_ROOT>
kubectl kustomize deploy/overlays/stg >/dev/null
! rg -n 'pending\.' deploy/overlays/stg
```

Render가 실패하거나 `pending.` sentinel이 남으면 다음 단계로 이동하지 않습니다. 아직
remote `main`에 반영하지 않고 `2`절에서 Git 밖 prerequisite를 준비합니다.

## 2. Application delivery 선행 조건 적용

### 2-A. RDS role 적용

#### 실행

- **누가·어디서**: RDS master credential과 EC2 SSH 권한을 가진 운영자가 local terminal에서
  실행합니다. Remote script의 현재 directory는 필요하지 않습니다.
- **무엇을**: 세 password를 NUL-delimited stdin으로 EC2의 application `100`에 전달합니다.
  Script는 PostgreSQL 16 client가 없으면 설치하고 두 application role 중 없는 role만
  고정된 이름으로 생성한 뒤 최소 권한을 적용합니다.
- **왜**: Application runtime과 Flyway migration 권한을 분리하고 master credential을
  workload에 주입하지 않기 위해서입니다.

```bash
set +x
printf 'Current RDS master password: ' >&2
IFS= read -r -s CURRENT_RDS_MASTER_PASSWORD
printf '\n' >&2

printf '%s\0' \
  "${CURRENT_RDS_MASTER_PASSWORD}" \
  "${STG_DB_RUNTIME_PASSWORD}" \
  "${STG_DB_MIGRATOR_PASSWORD}" \
| env -i HOME="${HOME}" PATH="${PATH}" ssh -i "<KEY_PATH>" "ubuntu@<EC2_SSH_HOST>" \
  'sudo -n /home/ubuntu/car-community-scripts/bootstrap/application/100-provision-database-access.sh'
unset CURRENT_RDS_MASTER_PASSWORD
```

Exit `0`과 script의 완료 메시지를 확인합니다. 기존 role과 password는 유지되며 없는
role만 생성됩니다. Script가 package 설치·입력·RDS 연결에서 중단하면 출력된 원인을 먼저
해결하고 같은 `100`을 재실행합니다. Script 내부 설치·DB 판정·권한 logic은 이 Runbook에
복제하지 않으며, 입력·결과·재실행의 exact 계약은
[Application bootstrap README](../../scripts/bootstrap/application/README.md)가 소유합니다.

### 2-B. Kubernetes application prerequisite 적용

#### 실행

- **누가·어디서**: `2`절의 `100`을 완료한 운영자가 같은 local terminal에서 실행합니다.
- **무엇을**: 다섯 Secret 값을 NUL-delimited stdin으로 EC2의 application `200`에
  전달합니다.
- **왜**: Namespace와 runtime·migration·auth·notification Secret은 Git에 저장할 수 없고,
  최초 Application보다 먼저 존재해야 하기 때문입니다.

Runtime과 migrator username은 script의 `carmunity_runtime`, `carmunity_migrator` 고정
값을 Secret에 기록하므로 local `.env`와 stdin에서 받지 않습니다.

```bash
printf '%s\0' \
  "${STG_DB_RUNTIME_PASSWORD}" \
  "${STG_DB_MIGRATOR_PASSWORD}" \
  "${STG_AUTH_JWT_HMAC_SECRET}" \
  "${STG_AUTH_LOGIN_IDENTIFIER_HMAC_SECRET}" \
  "${STG_RESEND_API_KEY}" \
| env -i HOME="${HOME}" PATH="${PATH}" ssh -i "<KEY_PATH>" "ubuntu@<EC2_SSH_HOST>" \
  'sudo -n /home/ubuntu/car-community-scripts/bootstrap/application/200-apply-application-prerequisites.sh'
```

Exit `0`과 resource postcondition 성공을 확인합니다. `100` 실패 상태에서는 `200`을 실행하지
않습니다.

### 2.1 Target server-side dry-run과 Git 반영

### 실행

- **누가·어디서**: `1`절과 `2`절을 완료한 운영자가 local repository root에서 실행합니다.
- **무엇을**: Local render를 SSH stdin으로 target EC2의 server-side dry-run에 전달합니다.
- **왜**: Local YAML syntax뿐 아니라 실제 cluster API schema·admission까지 통과한 Git
  목표만 최초 remote `main`에 게시하기 위해서입니다.

```bash
kubectl kustomize deploy/overlays/stg \
| env -i HOME="${HOME}" PATH="${PATH}" \
    ssh -i "<KEY_PATH>" "ubuntu@<EC2_SSH_HOST>" \
    'sudo -n KUBECONFIG=/etc/kubernetes/admin.conf kubectl apply --dry-run=server -f -'
```

현재 최초 bootstrap에서는 Argo CD가 아직 repository에 연결되지 않았고 운영자가 한
명이므로, 검증된 local `main`을 별도 branch·PR 우회 없이 exact refspec으로 직접 push합니다.

```bash
git status --short --branch
git push origin refs/heads/main:refs/heads/main
```

Push한 exact SHA의 `CI`와 Sonar revision·Quality Gate 성공을 확인한 뒤에만 `3`절 이후로
이동합니다. 두 application script가 이미 성공했다면 dry-run·push·CI 실패 때문에 다시
실행하지 않습니다. Argo CD 연결 이후의 `deploy/**` 변경과 rollback은 이 예외를 재사용하지
않고 [420 GitOps 배포와 rollback](420-gitops-deployment-and-rollback-runbook.md)의
branch·PR 절차를 사용합니다.

## 3. Read-only GitHub App 준비

### 실행

- **누가·어디서**: GitHub App과 repository installation을 관리할 운영자가 개인 계정의
  `Settings → Developer settings → GitHub Apps`에서 수행합니다.
- **무엇을**: OAuth App이 아닌 GitHub App을 만들고 `car-community` 한 repository에만
  설치합니다.
- **왜**: Argo CD가 사용자 PAT나 write credential 없이 source를 읽게 하기 위해서입니다.

| 항목 | 설정 | 이유 |
| --- | --- | --- |
| 이름 | `car-community-argocd` | Repository와 용도를 식별 |
| Homepage URL | `https://github.com/ckn1070/community` | 현재 source repository 표시 |
| Callback·Setup URL, Device Flow | 비움·비활성 | 사용자 OAuth flow를 사용하지 않음 |
| Webhook | 비활성 | 현재 polling을 사용하며 webhook endpoint가 없음 |
| Repository permission | `Contents: Read-only` | Git clone에 필요한 최소 권한 |
| 나머지 permission | `No access` | Repository 읽기 외 권한 제외 |
| 설치 범위 | `Only on this account` | 소유 계정 밖 설치 방지 |
| Installation target | `Only select repositories`의 `car-community` 하나 | 읽을 repository 제한 |

App ID와 Installation ID를 확인하고 private key를 한 번 발급합니다. PEM은 local의 mode
`0700` directory 안에 mode `0600` file로 보관하고 EC2에 복사하지 않습니다. `Contents`
permission은 repository 단위이므로 App은 `deploy/` 밖 source도 읽을 수 있고, Argo CD
Application의 exact path가 적용 범위를 제한합니다.

## 4. 실행 전 확인

다음 조건을 모두 확인한 뒤 최초 연결을 실행합니다.

- [340 Argo CD Core 설치](340-argocd-core-installation-runbook.md)가 실제 cluster에서
  완료됐습니다.
- Application `100`·`200`, target server-side dry-run과 최초 desired state의 exact `main` push가
  완료됐습니다.
- GitHub App이 `car-community` 하나에 설치됐고 App ID·Installation ID와 local mode `0600`
  PEM을 확인했습니다.
- 같은 이름의 Repository Secret·AppProject·Application이 없거나 현재 bootstrap owner
  resource입니다. 다른 owner resource를 자동 인수하지 않습니다.

## 5. Local에서 SSH stdin으로 최초 연결

### 실행

- **누가·어디서**: `1`~`4`절을 완료하고 EC2 SSH 권한이 있는 운영자가 local terminal에서
  실행합니다.
- **무엇을**: 공개 ID는 remote argument, PEM byte는 SSH stdin으로 GitOps `200`에
  전달합니다.
- **왜**: Repository Secret을 구성하고 Git에 추적된 제한된 AppProject와 최초 Application
  manifest를 server-side apply한 뒤 새 credential을 소비한 fresh reconcile까지 확인하기
  위해서입니다.

```bash
env -i HOME="${HOME}" PATH="${PATH}" ssh -i "<KEY_PATH>" "ubuntu@<EC2_SSH_HOST>" \
  "sudo -n /home/ubuntu/car-community-scripts/bootstrap/gitops/200-connect-private-gitops-repository.sh <GITHUB_APP_ID> <GITHUB_APP_INSTALLATION_ID>" \
  < "<LOCAL_GITHUB_APP_PRIVATE_KEY_PATH>"
```

`sudo -n`은 password prompt가 stdin의 PEM을 소비하지 못하게 합니다. Script가 다음 메시지와
exit `0`을 반환해야 합니다.

```text
[완료] 현재 private repository와 car-community-stg Application이 Synced·Healthy입니다.
```

Script는 `car-community` Namespace owner, Core owner와 동명 resource owner를 mutation 전에
확인합니다. PEM은 `/run` 임시 file로만 다루고 Repository Secret 적용 뒤 삭제합니다.
상세 mutation·fresh reconcile 계약은
[GitOps bootstrap README](../../scripts/bootstrap/gitops/README.md)가 소유합니다.

## 6. 최초 full sync와 application qualification

이 절의 qualification 범위는 Git revision, Argo CD sync, workload rollout과 exact image를
결속하는 GitOps 기술 상태까지입니다. API→RDS write와 email provider 사용은 바로 다음
중단점에서 별도 범위로 구분합니다.

### 실행

- **누가·어디서**: `5`절 성공 직후 운영자가 EC2 terminal에서 실행합니다.
- **무엇을**: Application revision·sync·health, workload rollout과 imageID를 읽습니다.
- **왜**: Script 종료만이 아니라 merge한 Git revision과 실제 runtime image가 같은 목표인지
  결속하기 위해서입니다.

```bash
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get application/car-community-stg -n argocd \
  -o jsonpath='{.status.sync.revision}{"|"}{.status.operationState.phase}{"|"}{.status.sync.status}{"|"}{.status.health.status}{"\n"}'

sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl rollout status deployment/car-community -n car-community --timeout=300s

sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get application/car-community-stg -n argocd \
  -o jsonpath='{range .status.operationState.syncResult.resources[*]}{.syncPhase}{"\t"}{.hookPhase}{"\t"}{.kind}{"\t"}{.name}{"\t"}{.status}{"\n"}{end}'

sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get pods -n car-community \
  -o custom-columns='NAME:.metadata.name,READY:.status.containerStatuses[*].ready,IMAGE_ID:.status.containerStatuses[*].imageID'
```

첫 출력은 merge한 40자리 `main` revision, `Succeeded|Synced|Healthy`와 일치해야 합니다.
Application condition이 있거나 Pod imageID가 `1`절의 exact digest와 다르면 완료로 판정하지
않습니다. Sync result에서 `car-community-config` PreSync ConfigMap이 성공한
`car-community-db-migration` PreSync Job보다 앞서고, Deployment rollout이 성공해야 합니다.

## Application 기능 qualification의 현재 중단점

Pod Ready와 exact image는 `6`절에서 확인할 수 있지만, 회원 생성 API를 통한 RDS
write와 email 인증 요청을 통한 Resend 사용은 별도 live qualification이 필요합니다.
이 과정은 staging recipient·임시 password 생성, port-forward, 두 HTTP 결과, inbox·provider
확인과 실패 후 test data 처리를 하나의 복구 계약으로 다루어야 합니다.

현재 repository에는 이 전체 경계를 안전하게 실행하는 script가 없습니다. 따라서 이
Runbook은 긴 ad-hoc shell을 대신 구현하지 않고 `6`절 성공에서 멈춥니다. Script의
입력, test member lifecycle, 민감값 삭제, timeout·재실행·정리 계약을 별도 개발
범위로 논의·승인하기 전에는 application 기능 qualification 완료를 선언하지 않습니다.

## 중단과 복구

| 실패 위치 | 남을 수 있는 상태 | 자동으로 하지 않는 일 | 재개점 |
| --- | --- | --- | --- |
| `1`절 render·sentinel 실패 | Git working tree 변경 | 빈 manifest 생성·remote 반영 | 값 수정 뒤 `1`절 |
| Application `100` package·입력·RDS 실패 | PostgreSQL client 또는 일부 application role·권한 | Role 삭제·password rotation·다른 major client 설치 | 원인을 해결한 뒤 같은 입력으로 [`2-A`](#2-a-rds-role-적용)의 `100` 재실행 |
| Application `100` 종료 결과를 확정할 수 없음 | Role이 생성됐을 수 있음 | Role 삭제·password rotation | 같은 입력으로 `100`을 재실행해 없는 role 생성·권한 적용·role 존재 확인을 완료 |
| Application `200` 실패 | 같은 owner의 Namespace·Secret 일부 | Secret 삭제·다른 값 추측 | 같은 다섯 값으로 [`2-B`](#2-b-kubernetes-application-prerequisite-적용)의 `200` 재실행 |
| Dry-run·push·CI 실패 | DB role과 Secret 유지, remote Git 미반영 또는 실패한 `main` revision | `100`·`200` 재실행·GitOps `200` 진행 | [`2.1`](#21-target-server-side-dry-run과-git-반영)에서 Git 또는 CI를 수정해 새 exact SHA로 재개 |
| GitHub App 설정·PEM 실패 | GitHub App 일부 설정 | PAT·write permission 우회 | [`3`](#3-read-only-github-app-준비) 설정 수정 |
| GitOps `200` owner·인증·sync 실패 | Repository Secret·AppProject·Application 일부 | Resource 삭제·label 보정·live spec 수정 | [180 Argo CD reconciliation 진단](diagnostics/180-argocd-reconciliation.md)으로 원인을 분류한 뒤 같은 [`5`](#5-local에서-ssh-stdin으로-최초-연결) 명령 재실행 |
| `Synced`·`Healthy` 뒤 `6`절 불일치 | Workload와 GitOps resource 유지 | GitOps `200` 재실행·live image 수정 | [170 Kubernetes workload 진단](diagnostics/170-kubernetes-workload-network-events-and-logs.md)과 application log로 원인을 forward fix한 뒤 [`6`](#6-최초-full-sync와-application-qualification) 재확인 |
| `6`절은 성공했지만 API·RDS·email qualification이 필요함 | GitOps 기술 bootstrap 완료, application 기능 qualification 미완료 | 긴 ad-hoc shell 실행·신규 script 임의 구현 | 별도 개발 범위와 실행 계약을 논의 |
| 세 GitOps resource와 Application 성공 상태 | 최초 연결 완료 | Bootstrap 재적용 | 반복 배포는 `420`, key 교체는 `520` |

Role이나 Secret을 삭제해 처음부터 다시 만드는 것을 일반 복구로 사용하지 않습니다.
Password·HMAC·provider key 교체는 최초 bootstrap과 섞지 않고 별도 변경 절차로 수행합니다.
Application `100`의 DB·RDS와 `200`의 Secret prerequisite 실패는 이 장애 진단 corpus로
일괄 이관하지 않고 위 표의 기존 application bootstrap owner에서 복구합니다.

## 관련 문서

- **현재 Argo CD 개념과 경계**: [Argo CD platform](../project/infrastructure/150-argocd-platform.md)
- **Application script 계약**: [Application bootstrap README](../../scripts/bootstrap/application/README.md)
- **GitOps script 계약**: [GitOps bootstrap README](../../scripts/bootstrap/gitops/README.md)
- **반복 배포 절차**: [420 GitOps 배포와 rollback](420-gitops-deployment-and-rollback-runbook.md)
- **Credential 변경 절차**: [520 Repository credential 교체](520-argocd-repository-credential-rotation-runbook.md)
- **Argo 진단**: [180 Argo CD reconciliation 진단](diagnostics/180-argocd-reconciliation.md)
- **Workload network 진단**: [170 Kubernetes workload 진단](diagnostics/170-kubernetes-workload-network-events-and-logs.md)
