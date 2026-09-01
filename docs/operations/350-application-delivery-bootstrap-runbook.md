# Application delivery 최초 bootstrap runbook

Argo CD Core 설치가 끝난 staging cluster에 application DB role과 Git 밖 Secret을 준비하고,
현재 private `car-community` repository를 read-only GitHub App으로 연결해 최초
`car-community-stg` Application을 `Synced`·`Healthy`로 만드는 절차입니다.

이 Runbook은 최초 연결만 소유합니다. 이후 image digest 배포·rollback은
[420 GitOps 배포와 rollback](420-gitops-deployment-and-rollback-runbook.md), GitHub App
private key 교체는
[520 Repository credential 교체](520-argocd-repository-credential-rotation-runbook.md)가
소유합니다. 같은 repository 사용·read-only 인증·AppProject·자동 sync의 작동 원리와
선택 이유는 [160 Argo CD platform](../project/infrastructure/160-argocd-platform.md)에서
확인합니다.

## 실행 계약과 순서

다음 단계는 서로 다른 상태와 재개점을 가지므로 한꺼번에 실행하지 않습니다. 각 단계의
완료 결과를 확인한 뒤 다음 단계로 이동합니다.

| 순서 | 분류 | 언제·누가·어디서 | Exact 진입점 | 왜 실행하는가 | 완료·재개점 |
| --- | --- | --- | --- | --- | --- |
| `1` | 수동 필수 | Argo CD Core 완료 뒤, repository write 권한이 있는 운영자가 local terminal에서 | `deploy/overlays/stg` 편집, `kubectl kustomize` | 실제 RDS·URL·image digest를 Git 목표 상태로 확정 | Local render 성공 뒤 `2`절. 실패하면 `1`절부터 재개 |
| [`2-A`](#2-a-rds-role-적용) | Script 실행 | `1`절 입력을 가진 운영자가 local terminal에서 | `bootstrap/application/100-provision-database-access.sh` | PostgreSQL 16 client와 Runtime·migration DB role·최소 권한 준비 | Script exit `0` 뒤 `2-B`. 실패하면 원인 해결 뒤 같은 `100` 재실행 |
| [`2-B`](#2-b-kubernetes-application-prerequisite-적용) | Script 실행 | `2-A` 완료 뒤 같은 운영자가 local terminal에서 | `bootstrap/application/200-apply-application-prerequisites.sh` | Git에 저장하지 않는 Namespace·Secret 준비 | Script exit `0` 뒤 `2.1`. 실패하면 같은 `200` 재실행 |
| [`2.1`](#21-target-server-side-dry-run과-git-반영) | 수동 필수 | `1`·`2`절 완료 뒤 운영자가 local terminal과 target EC2 terminal에서 | `kubectl kustomize`, remote `kubectl apply --dry-run=server`, exact `main` push | Git 목표 상태가 target API에서도 유효한지 확인하고 Argo CD 연결 전 최초 목표를 remote에 게시 | Exact pushed SHA의 CI·Sonar 성공 뒤 `3`절. 두 application script는 재실행하지 않음 |
| [`3`](#3-read-only-github-app-준비) | 수동 필수 | Repository·App 관리 권한이 있는 운영자가 GitHub Console에서 | `Settings → Developer settings → GitHub Apps` | Argo CD에 repository read-only identity 제공 | App·Installation ID와 PEM 확보 뒤 `4`절 사전 확인 |
| [`5`](#5-local에서-ssh-stdin으로-최초-연결) | Script 실행 | `4`절 확인 완료 뒤 운영자가 local terminal에서 | `bootstrap/gitops/200-connect-private-gitops-repository.sh` | Repository Secret을 구성하고 Git에 추적된 AppProject·Application manifest를 최초 적용한 뒤 fresh sync 확인 | 완료 문구와 exit `0` 뒤 `6`절. 실패하면 원인 해결 뒤 같은 `5`절 명령 재실행 |
| [`6`](#6-최초-full-sync와-workload-기술-검증) | 사람의 확인 | `5`절 성공 직후 운영자가 EC2 terminal에서 | 표준 `kubectl` read-only 명령 | 최초 sync revision·workload·exact image를 실제 상태와 결속 | 모든 확인 성공 시 Application delivery 기술 bootstrap 완료 |
| 선택 handoff | 선택 점검 | `6`절 완료 뒤 기능 경로까지 확인하려는 운영자가 target EC2 terminal에서 | [430 Application 기능 qualification](430-application-functional-qualification-runbook.md) | API→RDS write와 실제 email provider 사용은 Pod Ready만으로 증명할 수 없음 | 실행하면 `430`의 완료·cleanup까지 확인합니다. 생략해도 이 Runbook의 기술 bootstrap 완료 상태는 유지 |

이 순서에는 local 값 조립과 SSH stdin 전달 명령이 필요하지만 application·GitOps mutation은
모두 위 repository script가 수행합니다. Runbook은 script 내부 SQL·owner guard를 복제하지
않으며, AppProject·Application spec은
`scripts/bootstrap/gitops/manifests/car-community-appproject.yaml`과
`scripts/bootstrap/gitops/manifests/car-community-stg-application.yaml`이 소유합니다.

## 공통 입력과 비밀 경계

**실행 위치:** local repository terminal

**시작 directory:** repository root

먼저 [150 EC2 실행 artifact 전달](150-ec2-execution-artifact-transfer-runbook.md)을 완료해
이 runbook이 호출하는 Application·GitOps script를 고정 절대 경로에 준비합니다.
Local repository root의 신뢰된 `.env`가 현재 사용자 소유 regular file이고 mode `0600`인지
[로컬 개발 환경](../development/100-local-development.md#환경-변수-key와-값-주입)에 따라
확인한 뒤, xtrace를 끈 현재 shell에 값을 읽습니다. 이 절은 **local repository root**의
terminal에서 실행합니다.

```bash
set +x
test -f .env
test ! -L .env
test "$(stat -f '%u' .env)" = "$(id -u)"
test "$(stat -f '%Lp' .env)" = '600'
. ./.env
typeset +x \
  STG_DB_RUNTIME_PASSWORD STG_DB_MIGRATOR_PASSWORD \
  STG_AUTH_JWT_HMAC_SECRET STG_AUTH_LOGIN_IDENTIFIER_HMAC_SECRET \
  STG_COMMUNITY_CURSOR_HMAC_SECRET \
  STG_RESEND_API_KEY
```

다음 값이 현재 shell에 존재해야 합니다.

- `STG_DB_RUNTIME_PASSWORD`, `STG_DB_MIGRATOR_PASSWORD`
- `STG_AUTH_JWT_HMAC_SECRET`, `STG_AUTH_LOGIN_IDENTIFIER_HMAC_SECRET`
- `STG_COMMUNITY_CURSOR_HMAC_SECRET`
- `STG_RESEND_API_KEY`
- `NOTIFICATION_EMAIL_FROM`
- `EMAIL_VERIFICATION_BASE_URL`, `ADMIN_INVITATION_BASE_URL`, `PASSWORD_RESET_BASE_URL`

RDS master password와 GitHub App PEM은 `.env`, Git, CLI argument와 log에 저장하지 않습니다.
실행 시 stdin으로만 전달합니다. 다음 placeholder는 실제 운영값으로 바꿉니다.

| Placeholder | Source |
| --- | --- |
| `<KEY_PATH>` | 현재 EC2 SSH private key file의 절대 경로 |
| `<EC2_SSH_HOST>` | 현재 EC2 public IPv4 또는 public DNS |
| `<GITHUB_APP_ID>` | 생성한 GitHub App의 App ID |
| `<GITHUB_APP_INSTALLATION_ID>` | `car-community`에 설치한 App의 Installation ID |
| `<LOCAL_GITHUB_APP_PRIVATE_KEY_PATH>` | Local mode `0600` PEM file의 절대 경로 |

## 1. 현재 private repository의 GitOps 경로 준비

### 실행

- **실행 주체**: Repository 변경 권한이 있는 운영자
- **실행 위치**: Local terminal
- **시작 directory**: Local repository root
- **무엇을**: `deploy/overlays/stg`의 공개 설정과 exact image digest를 수정하고 표준
  `kubectl kustomize`로 render합니다.

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
# 1. Staging overlay render 검증
# 확인: exit 0이며 오류 출력 없음
printf '\n\n========== [1/2] Staging overlay render ==========\n\n'
kubectl kustomize deploy/overlays/stg >/dev/null

# 2. 미확정 sentinel 조회
# 확인: 출력 없음, 즉 pending. 값이 남아 있지 않음
printf '\n\n========== [2/2] 미확정 sentinel 부재 ==========\n\n'
! rg -n 'pending\.' deploy/overlays/stg
```

1. Render는 Argo CD가 읽을 목표 상태의 YAML이 유효한지 확인합니다.
2. Sentinel 조회는 실제 staging 입력으로 바꾸지 않은 placeholder의 배포를 막습니다.
   두 검사는 Git 밖 Secret과 repository 연결이 잘못된 manifest를 배포하지 않게 합니다.

Render가 실패하거나 `pending.` sentinel이 남으면 다음 단계로 이동하지 않습니다. 아직
remote `main`에 반영하지 않고 `2`절에서 Git 밖 prerequisite를 준비합니다.

## 2. Application delivery 선행 조건 적용

### 2-A. RDS role 적용

#### 실행

- **실행 주체**: RDS master credential과 EC2 SSH 권한을 가진 운영자
- **실행 위치**: Local terminal
- **시작 directory**: 제한 없음
- **무엇을**: 세 password를 NUL-delimited stdin으로 EC2의 application `100`에 전달합니다.
  Script는 PostgreSQL 16 client가 없으면 설치하고 두 application role 중 없는 role만
  고정된 이름으로 생성한 뒤 최소 권한을 적용합니다.

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
role만 생성됩니다. 이 단계는 Application runtime과 Flyway migration 권한을 분리하고 master
credential을 workload에 주입하지 않게 합니다. Script가 package 설치·입력·RDS 연결에서
중단하면 출력된 원인을 먼저 해결하고 같은 `100`을 재실행합니다. Script 내부 설치·DB
판정·권한 logic은 이 Runbook에
복제하지 않으며, 입력·결과·재실행의 exact 계약은
[Application bootstrap README](../../scripts/bootstrap/application/README.md)가 소유합니다.

### 2-B. Kubernetes application prerequisite 적용

#### 실행

- **실행 주체**: `2`절의 `100`을 완료한 운영자
- **실행 위치**: Local terminal
- **시작 directory**: 제한 없음
- **무엇을**: 여섯 Secret 값을 NUL-delimited stdin으로 EC2의 application `200`에
  전달합니다.

Runtime과 migrator username은 script의 `carmunity_runtime`, `carmunity_migrator` 고정
값을 Secret에 기록하므로 local `.env`와 stdin에서 받지 않습니다.

```bash
printf '%s\0' \
  "${STG_DB_RUNTIME_PASSWORD}" \
  "${STG_DB_MIGRATOR_PASSWORD}" \
  "${STG_AUTH_JWT_HMAC_SECRET}" \
  "${STG_AUTH_LOGIN_IDENTIFIER_HMAC_SECRET}" \
  "${STG_COMMUNITY_CURSOR_HMAC_SECRET}" \
  "${STG_RESEND_API_KEY}" \
| env -i HOME="${HOME}" PATH="${PATH}" ssh -i "<KEY_PATH>" "ubuntu@<EC2_SSH_HOST>" \
  'sudo -n /home/ubuntu/car-community-scripts/bootstrap/application/200-apply-application-prerequisites.sh'
```

Exit `0`과 resource postcondition 성공을 확인합니다. `100` 실패 상태에서는 `200`을 실행하지
않습니다. 이 단계에서 준비하는 Namespace와 Secret은 Git에 저장할 수 없고 최초
Application보다 먼저 존재해야 합니다.

### 2.1 Target server-side dry-run과 Git 반영

### 실행

- **실행 주체**: `1`절과 `2`절을 완료한 운영자
- **실행 위치**: Local terminal
- **시작 directory**: Local repository root
- **무엇을**: Local render를 SSH stdin으로 target EC2의 server-side dry-run에 전달합니다.

```bash
kubectl kustomize deploy/overlays/stg \
| env -i HOME="${HOME}" PATH="${PATH}" \
    ssh -i "<KEY_PATH>" "ubuntu@<EC2_SSH_HOST>" \
    'sudo -n KUBECONFIG=/etc/kubernetes/admin.conf kubectl apply --dry-run=server -f -'
```

이 dry-run은 local YAML syntax뿐 아니라 실제 cluster API schema·admission까지 통과한 Git
목표만 최초 remote `main`에 게시하게 합니다.

현재 최초 bootstrap에서는 Argo CD가 아직 repository에 연결되지 않았고 운영자가 한
명이므로, 검증된 local `main`을 별도 branch·PR 우회 없이 exact refspec으로 직접 push합니다.

```bash
# 1. Push 전 branch와 변경 상태 확인
# 확인: 현재 branch가 main이고 의도한 변경만 표시됨
printf '\n\n========== [1/2] Push 대상 상태 ==========\n\n'
git status --short --branch

# 2. 검증된 local main을 exact remote ref로 push
# 확인: main -> main 갱신 또는 Everything up-to-date
printf '\n\n========== [2/2] Exact main push ==========\n\n'
git push origin refs/heads/main:refs/heads/main
```

1. 상태 조회는 다른 branch나 의도하지 않은 변경을 게시하지 않도록 합니다.
2. Exact refspec push는 검증한 local `main`만 remote `main`에 게시합니다.

Push한 exact SHA의 `CI`와 Sonar revision·Quality Gate 성공을 확인한 뒤에만 `3`절 이후로
이동합니다. 두 application script가 이미 성공했다면 dry-run·push·CI 실패 때문에 다시
실행하지 않습니다. Argo CD 연결 이후의 `deploy/**` 변경과 rollback은 이 예외를 재사용하지
않고 [420 GitOps 배포와 rollback](420-gitops-deployment-and-rollback-runbook.md)의
branch·PR 절차를 사용합니다.

## 3. Read-only GitHub App 준비

### 실행

- **실행 주체**: GitHub App과 repository installation을 관리할 운영자
- **실행 위치**: 개인 GitHub 계정의
  `Settings → Developer settings → GitHub Apps`
- **무엇을**: OAuth App이 아닌 GitHub App을 만들고 `car-community` 한 repository에만
  설치합니다.

| 항목 | 설정 | 이유 |
| --- | --- | --- |
| 이름 | `car-community-argocd` | Repository와 용도를 식별 |
| Homepage URL | `https://github.com/example-org/car-community` | 현재 source repository 표시 |
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

이 설정은 Argo CD가 사용자 PAT나 write credential 없이 source를 읽게 합니다.

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

- **실행 주체**: `1`~`4`절을 완료하고 EC2 SSH 권한이 있는 운영자
- **실행 위치**: Local terminal
- **시작 directory**: 제한 없음
- **무엇을**: 공개 ID는 remote argument, PEM byte는 SSH stdin으로 GitOps `200`에
  전달합니다.

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

이 실행은 Repository Secret을 구성하고 Git에 추적된 제한된 AppProject와 최초 Application
manifest를 server-side apply한 뒤 새 credential을 소비한 fresh reconcile까지 확인합니다.

Script는 `car-community` Namespace owner, Core owner와 동명 resource owner를 mutation 전에
확인합니다. PEM은 `/run` 임시 file로만 다루고 Repository Secret 적용 뒤 삭제합니다.
상세 mutation·fresh reconcile 계약은
[GitOps bootstrap README](../../scripts/bootstrap/gitops/README.md)가 소유합니다.

## 6. 최초 full sync와 workload 기술 검증

### 실행

- **실행 주체**: `5`절 성공 직후 운영자
- **실행 위치**: EC2 terminal
- **시작 directory**: 제한 없음
- **무엇을**: Application revision·sync·health, workload rollout과 imageID를 읽습니다.

```bash
# 1. Application revision·operation·sync·health 조회
# 확인: merge한 40자리 SHA|Succeeded|Synced|Healthy
printf '\n\n========== [1/4] Application 목표 상태 ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get application/car-community-stg -n argocd \
  -o jsonpath='{.status.sync.revision}{"|"}{.status.operationState.phase}{"|"}{.status.sync.status}{"|"}{.status.health.status}{"\n"}'

# 2. Application Deployment rollout 대기
# 확인: deployment "car-community" successfully rolled out
printf '\n\n========== [2/4] Workload rollout ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl rollout status deployment/car-community -n car-community --timeout=300s

# 3. Sync resource와 hook 실행 순서 조회
# 확인: PreSync ConfigMap·Migration Job 성공 뒤 Deployment Synced
printf '\n\n========== [3/4] Sync resource 결과 ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get application/car-community-stg -n argocd \
  -o jsonpath='{range .status.operationState.syncResult.resources[*]}{.syncPhase}{"\t"}{.hookPhase}{"\t"}{.kind}{"\t"}{.name}{"\t"}{.status}{"\n"}{end}'

# 4. Pod readiness와 실제 image digest 조회
# 확인: READY=true이고 IMAGE_ID가 1절의 exact digest와 일치
printf '\n\n========== [4/4] Pod와 image digest ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get pods -n car-community \
  -o custom-columns='NAME:.metadata.name,READY:.status.containerStatuses[*].ready,IMAGE_ID:.status.containerStatuses[*].imageID'
```

1. Application 조회는 merge한 Git revision과 Argo CD의 최종 상태를 결속합니다.
2. Rollout 대기는 runtime Deployment가 새 목표로 교체됐는지 확인합니다.
3. Sync 결과는 ConfigMap과 Migration hook, Deployment의 실행 순서를 확인합니다.
4. Pod 조회는 Ready 상태와 실제 실행 image digest를 확인합니다.

이 절의 qualification 범위는 Git revision, Argo CD sync, workload rollout과 exact image를
결속하는 GitOps 기술 상태까지입니다. API→RDS write와 email provider 사용은 바로 다음
선택 handoff에서 별도 범위로 구분합니다.

첫 출력은 merge한 40자리 `main` revision, `Succeeded|Synced|Healthy`와 일치해야 합니다.
Application condition이 있거나 Pod imageID가 `1`절의 exact digest와 다르면 완료로 판정하지
않습니다. Sync result에서 `car-community-config` PreSync ConfigMap이 성공한
`car-community-db-migration` PreSync Job보다 앞서고, Deployment rollout이 성공해야 합니다.

## 선택 기능 qualification handoff

Pod Ready와 exact image는 `6`절에서 확인하지만 회원가입 API를 통한 RDS write와 실제
Resend 메일 전달은 별도 기능 증거입니다. 이 증거까지 필요한 staging 점검에서는
[430 Application 기능 qualification](430-application-functional-qualification-runbook.md)을
선택 실행합니다. `430`은 test member 입력, port-forward, `201`·`202`·실제 수신·`204`,
DB `ACTIVE`와 transaction cleanup을 한 실행 계약으로 소유합니다.

기능 qualification은 최초 delivery 기술 bootstrap의 필수 조건이 아닙니다. 실행하지
않으면 기능 경로가 `미검증`으로 남을 뿐 `6`절의 `Synced`·`Healthy`·Pod Ready 결과를
미완료로 되돌리지 않습니다. 수동 HTTP·cleanup 절차를 이 Runbook에 복제하거나 별도
script를 선행 조건으로 만들지 않습니다.

운영상 최초 최고 관리자가 필요하면 `6`절 완료 뒤
[360 Super Admin 최초 bootstrap](360-super-admin-bootstrap-runbook.md)을 선택 실행합니다.
계정 생성 command, 초기 로그인과 password 변경·재로그인 절차는 `360`이 소유합니다.

## 중단과 복구

**실행 위치:** `1`·`2-A`·`2-B`·`2.1`·`5`의 재실행은 Local terminal, GitHub App 설정
복구는 GitHub App Console, `6`의 workload·Argo·DB 진단은 EC2 terminal에서 수행합니다.
`420`·`430`·`360`으로 인계하는 항목은 각 canonical runbook의 실행 위치를 따릅니다.

**시작 directory:** `1`·`2.1`은 repository root, 그 밖의 Local terminal과 EC2 terminal은
제한 없음

| 실패 위치 | 남을 수 있는 상태 | 자동으로 하지 않는 일 | 재개점 |
| --- | --- | --- | --- |
| `1`절 render·sentinel 실패 | Git working tree 변경 | 빈 manifest 생성·remote 반영 | 값 수정 뒤 `1`절 |
| Application `100` package·입력·RDS 실패 | PostgreSQL client 또는 일부 application role·권한 | Role 삭제·password rotation·다른 major client 설치 | 원인을 해결한 뒤 같은 입력으로 [`2-A`](#2-a-rds-role-적용)의 `100` 재실행 |
| Application `100` 종료 결과를 확정할 수 없음 | Role이 생성됐을 수 있음 | Role 삭제·password rotation | 같은 입력으로 `100`을 재실행해 없는 role 생성·권한 적용·role 존재 확인을 완료 |
| Application `200` 실패 | 같은 owner의 Namespace·Secret 일부 | Secret 삭제·다른 값 추측 | 같은 여섯 값으로 [`2-B`](#2-b-kubernetes-application-prerequisite-적용)의 `200` 재실행 |
| Dry-run·push·CI 실패 | DB role과 Secret 유지, remote Git 미반영 또는 실패한 `main` revision | `100`·`200` 재실행·GitOps `200` 진행 | [`2.1`](#21-target-server-side-dry-run과-git-반영)에서 Git 또는 CI를 수정해 새 exact SHA로 재개 |
| GitHub App 설정·PEM 실패 | GitHub App 일부 설정 | PAT·write permission 우회 | [`3`](#3-read-only-github-app-준비) 설정 수정 |
| GitOps `200` owner·인증·sync 실패 | Repository Secret·AppProject·Application 일부 | Resource 삭제·label 보정·live spec 수정 | [180 Argo CD reconciliation 진단](diagnostics/180-argocd-reconciliation.md)으로 원인을 분류한 뒤 같은 [`5`](#5-local에서-ssh-stdin으로-최초-연결) 명령 재실행 |
| `Synced`·`Healthy` 뒤 runtime table·sequence `permission denied` | Workload와 GitOps resource 유지, runtime 권한 원인 미확정 | DB master 직접 `GRANT`·GitOps `200` 재실행·live Job 적용·원인 확인 전 sync | [420 Runtime DB 권한 실패 진단](420-gitops-deployment-and-rollback-runbook.md#runtime-db-권한-실패-진단과-복구)으로 artifact·wiring·effective privilege·Migration 증거를 확인하고 해당 원인만 수정한 뒤 [`6`](#6-최초-full-sync와-workload-기술-검증) 재확인 |
| `Synced`·`Healthy` 뒤 그 밖의 `6`절 불일치 | Workload와 GitOps resource 유지 | GitOps `200` 재실행·live image 수정 | [170 Kubernetes workload 진단](diagnostics/170-kubernetes-workload-network-events-and-logs.md)과 application log로 원인을 forward fix한 뒤 [`6`](#6-최초-full-sync와-workload-기술-검증) 재확인 |
| `6`절은 성공했고 API·RDS·email 기능도 선택 점검하려 함 | Application delivery 기술 bootstrap 완료, 기능 경로 미검증 | 이 Runbook에 HTTP·SQL 복제·신규 script 임의 구현 | [430 Application 기능 qualification](430-application-functional-qualification-runbook.md) 실행 |
| `6`절은 성공했고 최초 최고 관리자가 필요함 | Application delivery 기술 bootstrap 완료, Super Admin 미생성 또는 password 변경 미완료 | DB 직접 수정·별도 shell 작성 | [360 Super Admin 최초 bootstrap](360-super-admin-bootstrap-runbook.md) 실행 |
| 세 GitOps resource와 Application 성공 상태 | 최초 연결 완료 | Bootstrap 재적용 | 반복 배포는 `420`, key 교체는 `520` |

Role이나 Secret을 삭제해 처음부터 다시 만드는 것을 일반 복구로 사용하지 않습니다.
Password·HMAC·provider key 교체는 최초 bootstrap과 섞지 않고 별도 변경 절차로 수행합니다.
Application `100`의 DB·RDS와 `200`의 Secret prerequisite 실패는 이 장애 진단 corpus로
일괄 이관하지 않고 위 표의 기존 application bootstrap owner에서 복구합니다.

## 관련 문서

- **현재 Argo CD 개념과 경계**: [160 Argo CD platform](../project/infrastructure/160-argocd-platform.md)
- **Application script 계약**: [Application bootstrap README](../../scripts/bootstrap/application/README.md)
- **선택 기능 점검**: [430 Application 기능 qualification](430-application-functional-qualification-runbook.md)
- **선택 최초 관리자**: [360 Super Admin 최초 bootstrap](360-super-admin-bootstrap-runbook.md)
- **GitOps script 계약**: [GitOps bootstrap README](../../scripts/bootstrap/gitops/README.md)
- **반복 배포 절차**: [420 GitOps 배포와 rollback](420-gitops-deployment-and-rollback-runbook.md)
- **Credential 변경 절차**: [520 Repository credential 교체](520-argocd-repository-credential-rotation-runbook.md)
- **Argo 진단**: [180 Argo CD reconciliation 진단](diagnostics/180-argocd-reconciliation.md)
- **Workload network 진단**: [170 Kubernetes workload 진단](diagnostics/170-kubernetes-workload-network-events-and-logs.md)
