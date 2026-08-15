# Staging 단일 node 전체 구축 시나리오

이 문서는 서울 리전의 staging AWS 기반부터 단일 node Kubernetes, image 공급과 Argo CD
application delivery까지 전체 구축 흐름을 한 번에 추적하는 canonical scenario입니다.
전체 순서·handoff·재개 지점과 이 환경의 현재 live 상태만 소유합니다. 각 단계에는
운영자가 **어디에서 무엇을 실행하거나 조작해야 하는지**와 완료 결과를 명시하고, 실제
입력값·전체 명령·단계별 실패 복구는 연결된 runbook을 따릅니다.

현재 live 상태는 이 파일 한 곳에서만 갱신합니다. 상태가 바뀌면 이전 설명을 누적하지
않고 checklist와 현재 실행 상태를 교체하며, 과거 snapshot은 Git 이력으로 복원합니다.

## 적용 범위와 완료 결과

- **환경**: `stg`, AWS 서울 `ap-northeast-2`
- **기반**: 수동 AWS resource, Ubuntu `arm64` EC2 한 대의 Kubernetes control plane·worker
- **배포**: Private ECR exact digest와 Argo CD Core의 `deploy/overlays/stg` full sync
- **완료 결과**: AWS 기반, 빈 Kubernetes cluster, CI·image supply, Argo CD와 application이
  실제 환경에서 연결되고 대표 application·notification 경로가 동작합니다.

구성요소·actor·artifact와 선택 이유는
[300 Infrastructure system](../../project/300-infrastructure-system.md)과
[000 Infrastructure 개념 목차](../../project/infrastructure/000-index.md)가 소유합니다.

## 현재 실행 상태

| 항목 | 현재 값 |
| --- | --- |
| 현재 milestone | 최초 GitOps 기술 배포 완료, application 기능 qualification 준비 |
| 마지막 확인 결과 | 2 GiB node에서 Argo reconcile·migration이 겹칠 때 available memory 약 50 MiB, load `34.84`, 높은 I/O wait와 API TLS timeout이 관찰됐습니다. Memory를 4 GiB로 늘리고 instance를 시작한 뒤 available memory 약 1.6 GiB, load `1.49`, API readyz·Node Ready와 전체 Pod Running을 확인했습니다. `car-community-stg`는 revision `<PRIVATE_REVISION>`에서 `Succeeded`·`Synced`·`Healthy`이고 condition이 없습니다. Migration PreSync Job과 Deployment rollout이 성공했으며 runtime Pod의 실제 imageID가 목표 ECR digest `sha256:0000000000000000000000000000000000000000000000000000000000000000`와 일치합니다. |
| 다음 행동 | 일회성 staging 기능 검증은 새 script를 선행 조건으로 만들지 않습니다. 대표 API→RDS write와 Resend email smoke의 actor·입력·port-forward·기대 결과·test data 정리 경계를 수동 runbook으로 확정한 뒤 실행합니다. 같은 검증을 반복하거나 secret·cleanup 오류 위험이 커질 때만 script로 승격합니다. |
| 현재 blocker | 외부 장애는 없습니다. Application 기능 qualification에 사용할 staging recipient·임시 test member lifecycle과 exact API 절차를 아직 확정하지 않았습니다. |

`미검증`은 실패나 미생성을 뜻하지 않습니다. 실제 환경을 다시 관찰하기 전에는 완료로
올리지도, 없다고 단정해 새 resource를 만들지도 않습니다.

## 실행 순서와 현재 checklist

각 항목은 위에서 아래로 실행합니다. `행동`은 이 scenario에서 수행할 작업, `실행`은 실제
actor·위치·진입점, `완료`는 다음 항목으로 넘어갈 조건입니다. 연결된 runbook 제목만 보고
실행 대상을 추측하지 않습니다.

### 1. AWS 기반

- [x] **VPC** — [110 VPC 최초 구축](../110-vpc-provisioning-runbook.md)
  - **행동**: Seoul region에서 VPC workflow로 staging VPC, public subnet, route table과
    internet gateway를 생성합니다.
  - **실행**: 운영자가 AWS Console의 `VPC → VPC 생성`을 사용합니다. 이 단계의 repository
    script는 없습니다.
  - **완료**: VPC workflow 성공 화면을 확인했습니다. 세부 resource를 별도 CLI로 전수
    재검증했다는 뜻은 아닙니다.
- [x] **EC2 Kubernetes node** — [120 EC2 node 최초 구축](../120-ec2-node-provisioning-runbook.md)
  - **행동**: VPC의 public subnet에 Ubuntu ARM64 EC2 node를 생성하고 SSH 접속을 확인합니다.
  - **실행**: 운영자가 AWS Console의 `EC2 → 인스턴스 → 인스턴스 시작`을 사용합니다. 이
    단계의 repository script는 없습니다.
  - **완료**: `example-car-community-node` 생성과 `ubuntu` SSH 접속을 확인했습니다.
    최초 2 GiB 구성은 Argo reconcile·migration이 겹친 실제 부하에서 host·API 응답성을
    유지하지 못했습니다. Memory를 4 GiB로 늘리고 instance를 다시 시작한 뒤 API·Node와
    전체 workload가 자동 복구됐습니다. 이 thread에서는 변경한 exact EC2 instance type을
    별도로 확인하지 않았으므로 type 이름은 추측해 기록하지 않습니다.
- [x] **Private ECR·IAM** — [130 ECR·IAM 최초 구축](../130-ecr-iam-provisioning-runbook.md)
  - **행동**: Private ECR repository, GitHub Actions OIDC push role, EC2 pull role과 instance
    profile을 생성·연결합니다.
  - **실행**: 운영자가 AWS Console의 ECR·IAM·EC2 화면을 runbook 순서대로 조작합니다. 이
    단계의 repository script는 없습니다.
  - **완료**: Private `car-community` repository의 immutable tag, AES-256 암호화,
    project·managed-by tag와 registry scan 설정을 확인했습니다. OIDC provider, `main`
    push-only role, EC2 pull-only role과 instance profile 연결도 확인했습니다. 실제 role
    assumption·image push·pull은 뒤 단계가 검증합니다.
- [x] **RDS PostgreSQL** — [140 RDS 최초 구축](../140-rds-provisioning-runbook.md)
  - **행동**: DB subnet group과 staging RDS PostgreSQL instance를 생성하고 EC2에서 master
    연결을 확인합니다.
  - **실행**: 운영자가 AWS Console의 `Aurora and RDS` 화면을 runbook 순서대로 조작합니다.
    이 단계의 repository script는 없습니다. EC2에서 현재 Secrets Manager credential로
    인증하는 안전한 조회·주입 진입점은 아직 canonical procedure에 없으므로, 새 환경에서는
    임의 명령으로 대신하지 않고 [140 RDS 최초 구축](../140-rds-provisioning-runbook.md#9-생성-직후-단계-기대-결과)의
    해당 checkpoint를 미완료로 남깁니다.
  - **완료**: RDS 구축과 EC2에서의 연결 검증을 완료했습니다. Application
    runtime·migrator role 적용은 5절에서 별도로 추적합니다. 현재 체크는 이 환경에서 당시
    확인한 live 결과이며, 안전한 credential 조회·주입 절차가 확정됐다는 뜻은 아닙니다.

AWS umbrella의 공통 전제·전체 종료 조건은
[100 AWS 최초 구축](../100-aws-initial-provisioning-runbook.md)에서 확인합니다.

### 2. Repository 품질과 image 게시

이 절은 RDS·Kubernetes와 독립적으로 준비할 수 있지만, ECR 게시 설정은 앞 절의 ECR·push
role이 있어야 합니다.

- [x] **`main` CI·Sonar 품질 확인** — [310 CI 품질 게이트 최초 설정](../310-github-ci-quality-configuration-runbook.md)
  - **행동**: Sonar project·token과 GitHub secret·variable을 구성하고 `main` CI를
    성공시킵니다.
  - **실행**: 운영자가 SonarQube Cloud와 GitHub repository 설정 화면을 조작합니다. PR과
    `main` push 뒤 GitHub Actions의 [`.github/workflows/ci.yml`](../../../.github/workflows/ci.yml)
    `CI` workflow가 자동 실행되며, 별도 shell script는 없습니다.
  - **완료**: 게시된 첫 image revision의 `main` CI·Sonar exact revision Quality Gate 성공을
    확인했습니다. 현재 `main` branch protection과 required check 강제는 비활성 상태이며,
    단일 운영자의 최초 bootstrap 완료 조건으로 요구하지 않습니다. Argo CD 연결 뒤 반복
    배포 변경은 420 runbook의 PR 승인 경계를 사용합니다.
- [x] **GitHub ECR 게시 설정** — [320 ECR 게시 최초 설정](../320-github-ecr-publishing-configuration-runbook.md)
  - **행동**: GitHub repository variable `AWS_ECR_PUSH_ROLE_ARN`에 앞서 생성한 ECR push
    role ARN을 저장합니다.
  - **실행**: 운영자가 GitHub repository의 `Settings → Secrets and variables → Actions`를
    사용합니다. 이 단계의 repository script는 없습니다.
  - **완료**: GitHub Actions에서 private ECR 첫 image 게시까지 완료해 설정의 실제 사용을
    확인했습니다.
- [x] **첫 ARM64 image 게시** — [400 GitHub ECR image 게시](../400-github-ecr-image-publishing-runbook.md)
  - **행동**: 게시할 revision을 `main`에 반영하고 `Publish image` workflow의 성공과 출력
    digest를 확인합니다.
  - **실행**: 같은 revision의 `CI` workflow가 성공하면 GitHub Actions의
    [`.github/workflows/publish-image.yml`](../../../.github/workflows/publish-image.yml)
    `Publish image` workflow가 자동 실행됩니다. 운영자는 Actions job summary와 ECR
    repository를 확인합니다.
  - **완료**: 첫 ARM64 image를 게시하고 immutable digest reference를 확보했습니다.

### 3. Kubernetes cluster

- [x] **EC2 실행 파일 전달** — [200 Kubernetes bootstrap의 EC2 실행 파일 전달](../200-kubernetes-bootstrap-runbook.md#ec2-실행-파일-전달)
  - **행동**: runbook의 exact inventory를 local archive로 묶어 checksum을 확인하고 EC2의
    `/home/ubuntu/car-community-scripts`에 전달·압축 해제·권한 검증합니다.
  - **실행**: local 운영자가 runbook의 표준 `tar`·`shasum`·`scp`·`ssh` 명령을 실행합니다.
    이 전달 전체를 대신하는 repository script는 없습니다.
  - **완료**: EC2에서 runbook의 exact executable·source-only library inventory를 사용할 수
    있는 상태를 확인했습니다.
- [x] **Kubernetes host 준비** — [210 Host 준비](../210-kubernetes-host-preparation-runbook.md)
  - **행동**: EC2 host의 swap·kernel module·sysctl과 Kubernetes 전제 상태를 설정합니다.
  - **실행**: EC2에서 `/home/ubuntu/car-community-scripts`로 이동해
    `sudo ./bootstrap/kubernetes/100-prepare-host.sh`를 실행합니다. Reboot가 요구되면
    runbook에 따라 재접속해 같은 script의 완료를 다시 확인한 뒤 다음 단계로 갑니다.
  - **완료**: Host 준비 script의 완료 검증을 통과했습니다.
- [x] **containerd 설치** — [220 containerd 설치](../220-containerd-installation-runbook.md)
  - **행동**: Kubernetes CRI로 사용할 containerd와 고정 설정을 설치합니다.
  - **실행**: EC2의 같은 directory에서
    `sudo ./bootstrap/kubernetes/200-install-containerd.sh`를 실행합니다.
  - **완료**: containerd service와 CRI 설정 검증을 통과했습니다.
- [x] **Kubernetes component 설치** — [230 Kubernetes component 설치](../230-kubernetes-components-installation-runbook.md)
  - **행동**: 고정 version의 kubeadm·kubelet·kubectl을 설치합니다.
  - **실행**: EC2의 같은 directory에서
    `sudo ./bootstrap/kubernetes/300-install-kubernetes-components.sh`를 실행합니다.
  - **완료**: 세 component의 설치·version 검증을 통과했습니다.
- [x] **Control plane 초기화** — [240 Control plane 초기화](../240-kubernetes-control-plane-initialization-runbook.md)
  - **행동**: kubeadm으로 단일 node control plane을 만들고 admin kubeconfig와 worker
    scheduling 상태를 준비합니다.
  - **실행**: EC2의 같은 directory에서
    `sudo ./bootstrap/kubernetes/400-initialize-cluster.sh`를 실행합니다.
  - **완료**: Control plane과 node 상태 검증을 통과했습니다.
- [x] **Calico 설치와 cluster smoke** — [250 Calico 설치](../250-calico-network-installation-runbook.md)
  - **행동**: Calico CNI를 설치하고 Node·CoreDNS Ready, namespace DNS·ClusterIP·backend TCP
    smoke와 임시 resource 정리를 확인합니다.
  - **실행**: EC2의 같은 directory에서
    `sudo ./bootstrap/kubernetes/500-install-calico.sh`를 실행합니다.
  - **완료**: 빈 Kubernetes cluster의 network와 smoke postcondition을 모두 확인했습니다.

### 4. Node image 공급

이 절은 Kubernetes cluster와 첫 ECR image가 모두 완료된 뒤 실행합니다.

- [x] **Kubelet ECR credential provider** — [330 Provider 최초 설치](../330-kubelet-ecr-credential-provider-installation-runbook.md)
  - **행동**: Kubelet이 EC2 pull role로 private ECR credential을 얻도록 credential provider를
    설치합니다.
  - **실행**: EC2의 `/home/ubuntu/car-community-scripts`에서
    `sudo ./bootstrap/ecr/100-configure-kubelet-credential-provider.sh`를 실행합니다.
  - **완료**: Provider 설치와 kubelet 실행 인자 검증을 완료했습니다.
- [x] **Node exact digest pull** — [410 Private ECR pull 점검](../410-private-ecr-image-pull-verification-runbook.md)
  - **행동**: 첫 image의 exact digest reference로 temporary Pod를 실행해 pull·ARM64 실행과
    실제 `imageID` 일치를 확인하고 임시 namespace를 정리합니다.
  - **실행**: EC2의 같은 directory에서 runbook대로 `IMAGE_REFERENCE`를 준비한 뒤
    `sudo ./verify/ecr/verify-private-image-pull.sh "${IMAGE_REFERENCE}"`를 실행합니다.
  - **완료**: `sha256:0000000000000000000000000000000000000000000000000000000000000000`의
    pull·실행·`imageID` 일치와 임시 namespace 정리를 확인했습니다. 현재 overlay도 이
    digest를 staging 배포 목표로 사용합니다.

### 5. Argo CD와 application delivery

Application prerequisite 확인·적용, GitHub App 설치와 최초 desired state Git 반영 track은 서로
독립적으로 진행할 수 있지만, 모두 완료돼야 private repository 연결을 실행할 수 있습니다.

- [x] **Argo CD Core 설치** — [340 Argo CD Core 최초 설치](../340-argocd-core-installation-runbook.md)
  - **행동**: Argo CD Core를 cluster에 설치하고 component rollout과 CRD를 확인합니다.
  - **실행**: EC2의 `/home/ubuntu/car-community-scripts`에서
    `sudo ./bootstrap/gitops/100-install-argocd-core.sh`를 실행합니다.
  - **완료**: Argo CD Core `v3.5.0` component rollout과 `Application`·`AppProject` CRD를
    확인했습니다.
- [x] **Application DB role 적용** — [350 Application delivery 선행 조건](../350-application-delivery-bootstrap-runbook.md#2-application-delivery-선행-조건-적용)
  - **행동**: Local `.env`의 staging DB password와 현재 RDS master password를 사용해
    PostgreSQL 16 client를 자동 준비하고 `carmunity_runtime`·`carmunity_migrator` role과
    권한을 적용합니다.
  - **실행**: local 운영자가 runbook의 NUL stdin·SSH 명령으로 EC2의
    `/home/ubuntu/car-community-scripts/bootstrap/application/100-provision-database-access.sh`
    를 `sudo -n` 실행합니다.
  - **완료**: PostgreSQL 16 client를 설치하고 `carmunity_runtime`·`carmunity_migrator` role을
    생성한 뒤 최소 schema 권한 적용과 transaction commit을 확인했습니다.
- [x] **Application Namespace·Secret 적용** — [350 Application delivery 선행 조건](../350-application-delivery-bootstrap-runbook.md#2-application-delivery-선행-조건-적용)
  - **행동**: Local `.env`의 허용된 staging 값만 전달해 `car-community` Namespace와
    application Secret을 적용합니다.
  - **실행**: local 운영자가 runbook의 NUL stdin·SSH 명령으로 EC2의
    `/home/ubuntu/car-community-scripts/bootstrap/application/200-apply-application-prerequisites.sh`
    를 `sudo -n` 실행합니다.
  - **완료**: `100` 성공 뒤 `200`을 실행해 Namespace와 네 Secret을 적용하고 총 5개
    resource의 postcondition을 확인했습니다. GitOps `200`은 이 단계와 별개의 최초 연결
    작업입니다.
- [x] **Staging local desired state 확정·render** — [350 Application delivery 최초 bootstrap](../350-application-delivery-bootstrap-runbook.md#1-현재-private-repository의-gitops-경로-준비)
  - **행동**: 실제 RDS endpoint, 승인된 notification 공개값과 live pull로 검증한 image
    digest를 `deploy/overlays/stg`와 `deploy/overlays/stg/pre-sync`에 반영하고 render합니다.
  - **실행**: local 운영자가 두 `kustomization.yaml`을 수정한 뒤 repository root에서
    `kubectl kustomize deploy/overlays/stg`를 실행합니다. 이 작업을 대신하는 repository
    script는 없습니다.
  - **완료**: 실제 staging 값과 `c290...` digest가 들어 있고 `.pending`·`pending.invalid`가
    없는 Kustomize render를 확인했습니다.
- [x] **Read-only GitHub App 생성·private key 발급** — [350 Application delivery 최초 bootstrap](../350-application-delivery-bootstrap-runbook.md#3-read-only-github-app-준비)
  - **행동**: GitHub App을 repository contents read-only로 생성하고 private key PEM을 한 번
    발급합니다.
  - **실행**: 운영자가 GitHub Web UI의 `Developer settings → GitHub Apps`를 사용합니다. 이
    단계의 repository script는 없습니다.
  - **완료**: 개인 계정에 `car-community-argocd` App을 생성했고 App ID는 `<GITHUB_APP_ID>`입니다.
    Webhook·OAuth·Device Flow·Setup URL은 사용하지 않고 `Contents: Read-only` 외 permission을
    추가하지 않았으며 설치 가능 범위는 `Only on this account`입니다. Private key PEM도
    local에 다운로드했습니다. PEM 원문·파일명·local 경로는 기록하지 않습니다.
- [x] **GitHub App 설치·credential 입력 준비** — [350 Application delivery 최초 bootstrap](../350-application-delivery-bootstrap-runbook.md#3-read-only-github-app-준비)
  - **행동**: PEM을 local 안전 directory로 옮겨 권한을 확인하고, App을
    `car-community` repository 하나에만 설치해 Installation ID를 확보합니다.
  - **실행**: local 운영자가 runbook의 directory·file permission 확인 명령을 실행하고,
    GitHub Web UI의 App 설치 화면에서 `Only select repositories`를 선택합니다. 이 단계의
    repository script는 없습니다.
  - **완료**: App을 `car-community` 하나에 설치했고 Installation ID `<GITHUB_APP_INSTALLATION_ID>`을
    확인했습니다. Local PEM file mode `0600`을 확인한 뒤 SSH stdin으로 GitOps `200`에
    전달해 credential 입력 경계까지 실제 사용했습니다.
- [x] **Target dry-run·최초 `main` 반영** — [350 Application delivery 최초 bootstrap](../350-application-delivery-bootstrap-runbook.md#21-target-server-side-dry-run과-git-반영)
  - **행동**: 현재 overlay 변경의 target cluster server-side dry-run을 통과시킨 뒤 현재
    local `main`을 `origin/main`에 직접 push합니다.
  - **실행**: local 운영자가 runbook의 `kubectl kustomize`와 SSH를 통한
    `kubectl apply --dry-run=server` 명령을 실행하고, exact refspec으로 `main`을 push한 뒤
    GitHub Actions와 SonarQube Cloud에서 해당 exact SHA를 확인합니다. 별도 repository
    script는 없습니다.
  - **완료**: Target API가 ConfigMap 3개, Service, Deployment와 migration Job으로 구성된
    전체 render를 server-side dry-run에서 승인했습니다. 검증된 desired state를 GitHub
    `main`에 직접 push하고 그 exact SHA의 CI·Sonar 성공을 확인했습니다. 이 최초 반영 뒤
    GitOps `200`이 같은 repository의 `deploy/overlays/stg`를 source로 사용했습니다. 연결
    이후 digest 변경은 [420 GitOps 배포·rollback](../420-gitops-deployment-and-rollback-runbook.md)의
    PR 절차를 사용합니다.
- [x] **Private repository 연결** — [350 Application delivery 최초 bootstrap](../350-application-delivery-bootstrap-runbook.md)
  - **행동**: App ID·Installation ID·private key를 사용해 Argo CD에 private repository를
    연결하고 실제 `car-community` AppProject와 staging Application을 생성합니다.
  - **실행**: local 운영자가 PEM을 runbook의 SSH stdin으로 전달해 EC2의
    `/home/ubuntu/car-community-scripts/bootstrap/gitops/200-connect-private-gitops-repository.sh`
    를 App ID·Installation ID 인자와 함께 `sudo -n` 실행합니다.
  - **완료**: Repository Secret 적용, application-controller rollout, AppProject와
    Application 적용까지 성공했습니다. 최초 script의 마지막 상태 조회는 host resource
    pressure로 API TLS timeout이 발생해 nonzero였지만 resource를 삭제·재적용하지 않았습니다.
    Node memory 증설·restart 뒤 read-only 조회에서 같은 Application의 fresh revision,
    condition 없음과 `Succeeded`·`Synced`·`Healthy`를 확인해 repository credential과 연결의
    effective 결과를 완료로 판정했습니다. 이 단계의 repository 구현·live qualification은 기존
    [013 Staging application GitOps delivery](../../work-items/features/2026/013-autoscaling-safe-application-delivery.md)와
    연결됩니다.
- [x] **최초 full sync와 workload 기술 검증** — [350 Application delivery 최초 bootstrap](../350-application-delivery-bootstrap-runbook.md#6-최초-full-sync와-application-qualification)
  - **행동**: GitOps `200`의 fresh reconciliation 완료를 기다린 뒤 revision, sync result,
    workload image와 readiness를 읽기 전용으로 검증합니다.
  - **실행**: 앞 단계의 GitOps `200` script가 fresh `Synced`·`Healthy`까지 기다립니다.
    성공 후 운영자가 EC2에 접속해 350 runbook 6절의 세 `kubectl` 조회를 실행합니다.
    별도의 두 번째 bootstrap script를 실행하지 않습니다.
  - **완료**: Revision `<PRIVATE_REVISION>`에서 PreSync
    ConfigMap과 단일 Migration Job이 성공했고, Service·Deployment가 Synced 상태입니다.
    Application condition 없음·`Succeeded`·`Synced`·`Healthy`, Deployment rollout 성공,
    runtime Pod Ready와 목표 ECR exact digest와 같은 imageID를 확인했습니다.
- [ ] **Application 기능 qualification 수동 절차 확정·실행** — [350 Application delivery 최초 bootstrap](../350-application-delivery-bootstrap-runbook.md#application-기능-qualification의-현재-중단점)
  - **행동**: 회원 생성 API를 통한 RDS write와 email 인증 요청을 통한 Resend 사용을
    검증하는 일회성 staging 절차를 확정하고 실행합니다.
  - **실행**: 운영자가 staging recipient·임시 password·port-forward·HTTP 요청과 기대 결과,
    test data 정리와 민감값 lifecycle을 먼저 runbook에 명시한 뒤 표준 명령을 순서대로
    실행합니다. 현재 단계에서는 별도 repository script를 필수 선행 조건으로 만들지
    않습니다.
  - **완료**: 대표 API·RDS write와 Approved Resend key의 email smoke를 실제로 통과하고
    test data를 정리해야 합니다. 반복 실행·복구 자동화 필요가 확인될 때만 동일 계약을
    script로 승격합니다. 기존과 다른 old provider key가 있을 때만 별도 credential 변경
    절차에서 검증 뒤 폐기합니다.

## 중단과 재개

1. Checklist에서 마지막으로 실제 확인한 항목과 첫 미완료 항목을 찾습니다.
2. 실패한 항목의 runbook에 있는 중단·복구 절을 따릅니다. Scenario에 상세 복구 명령을
   복제하지 않습니다.
3. 완료된 선행 단계를 무조건 다시 실행하지 않습니다. 다음 단계가 요구하는 effective
   state만 확인하고 첫 미완료 단계에서 재개합니다.
4. Partial resource, credential 불일치나 destructive recovery가 필요한 경우 정상 재실행으로
   추측하지 않고 해당 runbook의 승인 경계에서 중단합니다.
5. 실제 확인 결과가 바뀌면 이 문서의 현재 실행 상태와 checklist만 현재형으로 갱신합니다.

## 전체 완료 조건

다음 결과가 모두 실제 환경에서 확인돼야 이 scenario가 완료됩니다.

- VPC·EC2·ECR/IAM·RDS가 각 runbook의 live postcondition을 충족합니다.
- Kubernetes 단일 node와 network smoke가 완료됩니다.
- PR과 `main` CI·Sonar Gate, image publish와 node exact digest pull이 성공합니다.
- Argo CD Core, runtime·migrator DB role, Namespace·네 Secret과 Git-managed ConfigMap이
  연결됩니다.
- 최초 full sync가 `Synced`·`Healthy`, runtime Pod Ready로 끝납니다.
- Runtime Pod의 RDS 연결, 대표 API와 notification email smoke가 성공합니다.

Repository source·contract test·Kustomize render만으로 위 live 결과를 완료 처리하지 않습니다.

## 연결된 작업과 scenario backlog

### 기존 작업

- [013 Staging application GitOps delivery](../../work-items/features/2026/013-autoscaling-safe-application-delivery.md):
  Application delivery의 repository 결과와 live qualification 요구를 소유합니다. 현재 실제
  실행 위치와 완료 여부는 이 scenario checklist가 소유합니다.

### Scenario-local backlog

아래 항목은 현재 scenario 완료 조건이 아니며 아직 새 work item을 만들지 않습니다.
구현 작업으로 승격하기 전에는 사용자와 범위·우선순위를 먼저 논의합니다.

- HPA·Cluster Autoscaler와 실제 resource·connection pool tuning
- Ingress·ALB·HTTPS·domain·DNS
- 수동 AWS resource의 Terraform 전환
- RDS master credential을 EC2 인증에 안전하게 조회·주입하는 반복 가능한 진입점
- DB password·auth HMAC rotation과 image digest PR 자동화

## 반복과 확장 규칙

- 같은 staging 환경의 재개·재구축은 이 문서의 현재 상태와 checklist를 갱신합니다.
- 단순한 값 변경은 기존 scenario와 runbook 입력으로 처리합니다.
- Actor·운영 목표·단계 순서·hard dependency·복구 경계·전체 완료 조건이 달라질 때만 새
  scenario가 필요한지 검토합니다.
- 새 scenario나 work item을 자동 생성하지 않습니다. 필요한 범위는 사용자와 먼저
  논의합니다.

## 관련 문서

- **세부 절차 탐색**: [000 운영 문서 목차](../000-index.md)
- **현재 영구 구조**: [300 Infrastructure system](../../project/300-infrastructure-system.md)
- **Infrastructure 선택과 이유**: [000 Infrastructure 개념 목차](../../project/infrastructure/000-index.md)
