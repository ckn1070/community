# Staging 단일 node 전체 구축 시나리오

이 문서는 서울 리전의 staging AWS 기반부터 단일 node Kubernetes, image 공급,
Argo CD application delivery와 외부 HTTPS ingress까지 전체 구축 흐름을 한 번에
추적하는 canonical scenario입니다.
전체 순서·handoff·재개 지점과 이 환경의 현재 live 상태만 소유합니다. 각 단계에는
운영자가 **어디에서 무엇을 실행하거나 조작해야 하는지**와 완료 결과를 명시하고, 실제
입력값·전체 명령·단계별 실패 복구는 연결된 runbook을 따릅니다.

현재 live 상태는 이 파일 한 곳에서만 갱신합니다. 상태가 바뀌면 이전 설명을 누적하지
않고 checklist와 현재 실행 상태를 교체하며, 과거 snapshot은 Git 이력으로 복원합니다.

## 적용 범위와 완료 결과

- **환경**: `stg`, AWS 서울 `ap-northeast-2`
- **기반**: 수동 AWS resource, Ubuntu `arm64` EC2 한 대의 Kubernetes control plane·worker
- **배포**: Private ECR exact digest와 Argo CD Core의 `deploy/overlays/stg` full sync
- **외부 ingress**: Cloudflare DNS-only `stg-api.ronams.com`→internet-facing ALB→NodePort
- **필수 완료 결과**: AWS 기반, Kubernetes cluster, CI·image supply, Argo CD와 application이
  실제 환경에서 연결되고 최초 full sync·migration·workload 기술 검증과 public
  HTTPS→ALB→Pod E2E를 통과합니다.
- **선택 결과**: 대표 application·notification 기능 qualification과 최초 Super Admin
  provisioning은 필요할 때 별도로 실행하며, 미실행 상태는 필수 구축 실패가 아닙니다.

구성요소·actor·artifact와 선택 이유는
[100 Infrastructure overview](../../project/infrastructure/100-infrastructure-overview.md)과
[000 Infrastructure 개념 목차](../../project/infrastructure/000-index.md)가 소유합니다.

## 현재 실행 상태

| 항목 | 현재 값 |
| --- | --- |
| 현재 milestone | Staging 단일 node 전체 구축 필수 결과 완료 |
| Node·ingress platform 확인 | Primary ENI의 `stg-car-community-k8s-node-sg`에 cluster tag를 적용하고, EC2를 ECR pull·LBC policy가 연결된 `stg-car-community-k8s-node-role`로 전환했습니다. IMDSv2 required·hop limit `2`, public subnet ELB role tag와 `stg-api.ronams.com` ACM certificate `Issued`를 확인했습니다. LBC chart `3.5.0`, replica `1`, exact digest image, required CRD 7개 `Established`, HPA 부재, providerID `aws:///ap-northeast-2a/i-07fe84c38f1f31f1b`와 external load balancer 제외 label 해제를 확인했습니다. 새 role의 private ECR exact digest pull regression과 temporary namespace cleanup도 성공했습니다. |
| GitOps·workload 확인 | AppProject는 `networking.k8s.io/Ingress`를 허용하고 Secret·wildcard를 허용하지 않으며, staging overlay render와 target API server-side dry-run이 성공했습니다. Ingress source를 포함한 revision `4a75d77cf58c2139ee81915bff8dd2a2db23ea8f`는 remote `main`에 반영됐고 CI·Sonar·image publish가 성공했습니다. Argo CD Application은 `Synced`·`Healthy`이고 condition은 없으며, Service는 NodePort `30503`, Ingress는 ALB hostname `k8s-carcommu-carcommu-d11f20047e-481537360.ap-northeast-2.elb.amazonaws.com`을 보고합니다. |
| Public ingress E2E 확인 | 같은 ALB가 exact VPC와 public `2a`·`2b` subnet을 사용하고, HTTP `80`→HTTPS `443` redirect, exact TLS policy·ACM certificate, instance Target Group의 HTTP target `Healthy`를 확인했습니다. Frontend SG는 public `80`·`443`만 받고, node SG의 동적 NodePort `30503` source는 shared backend SG이며 public NodePort rule은 없습니다. DNS 전 ALB 직접 요청은 HTTPS readiness `200`과 HTTP `301`을 통과했습니다. Cloudflare DNS-only CNAME은 exact ALB hostname으로 수렴했고, public `stg-api.ronams.com`은 HTTPS readiness `200`, HTTP→HTTPS `301`, `CN=stg-api.ronams.com` Amazon certificate를 반환했습니다. |
| 다음 행동 | 필수 구축 결과에 남은 행동은 없습니다. 선택 후속 기능과 scenario-local backlog는 별도 승인 전 실행하지 않습니다. |
| 현재 blocker | 없습니다. 필수 checklist와 전체 완료 조건을 모두 충족했습니다. |

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
  - **실행**: 인스턴스 생성은 AWS Console의 `EC2 → 인스턴스 → 인스턴스 시작`, SSH
    접속 확인은 local terminal에서 수행합니다. Local terminal의 시작 directory는 제한
    없습니다. 이 단계의 repository script는 없습니다.
  - **완료**: `stg-car-community-k8s-node-01` 생성과 `ubuntu` SSH 접속을 확인했습니다.
    최초 2 GiB 구성은 Argo reconcile·migration이 겹친 실제 부하에서 host·API 응답성을
    유지하지 못했습니다. Memory를 4 GiB로 늘리고 instance를 다시 시작한 뒤 API·Node와
    전체 workload가 자동 복구됐습니다. 이 thread에서는 변경한 exact EC2 instance type을
    별도로 확인하지 않았으므로 type 이름은 추측해 기록하지 않습니다.
- [x] **Private ECR·IAM** — [130 ECR·IAM 최초 구축](../130-ecr-iam-provisioning-runbook.md)
  - **행동**: Private ECR repository, GitHub Actions OIDC push role, EC2 pull role과 instance
    profile을 생성·연결합니다.
  - **실행**: ECR·IAM·EC2 resource 생성과 연결은 AWS Console, OIDC subject 확인은 `gh`
    인증이 완료된 local terminal에서 수행합니다. Local terminal의 시작 directory는 제한
    없습니다. 이 단계의 repository script는 없습니다.
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
    해당 checkpoint를 미완료로 남깁니다. 이 procedure 후보는
    [004 Infrastructure 진화 Backlog](../../work-items/backlog/2026/004-infrastructure-evolution.md)가
    소유합니다.
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

- [x] **EC2 실행 파일 전달** — [150 EC2 실행 artifact 전달](../150-ec2-execution-artifact-transfer-runbook.md)
  - **행동**: 현재 runbook의 exact archive inventory를 묶어 checksum을 확인하고
    EC2의 `/home/ubuntu/car-community-scripts`에 전달·압축 해제·mode 검증합니다.
  - **실행**: Archive 생성·checksum·`scp`·SSH 검증 호출은 repository root의 local
    terminal, 전달된 artifact의 원격 검증은 시작 directory 제한이 없는 EC2 terminal에서
    수행합니다. 이 전달 전체를 대신하는 repository script는 없습니다.
  - **완료**: EC2에서 현재 runbook이 지정한 executable·readable source 목록과
    mode 검사가 모두 성공했습니다.
- [x] **Kubernetes host 준비** — [210 Host 준비](../210-kubernetes-host-preparation-runbook.md)
  - **행동**: EC2 host의 swap·kernel module·sysctl과 Kubernetes 전제 상태를 설정합니다.
  - **실행**: EC2 terminal의 어느 directory에서나
    `sudo /home/ubuntu/car-community-scripts/bootstrap/kubernetes/100-prepare-host.sh`를 실행합니다. Reboot가 요구되면
    runbook에 따라 재접속해 같은 script의 완료를 다시 확인한 뒤 다음 단계로 갑니다.
  - **완료**: Host 준비 script의 완료 검증을 통과했습니다.
- [x] **containerd 설치** — [220 containerd 설치](../220-containerd-installation-runbook.md)
  - **행동**: Kubernetes CRI로 사용할 containerd와 고정 설정을 설치합니다.
  - **실행**: EC2 terminal의 어느 directory에서나
    `sudo /home/ubuntu/car-community-scripts/bootstrap/kubernetes/200-install-containerd.sh`를 실행합니다.
  - **완료**: containerd service와 CRI 설정 검증을 통과했습니다.
- [x] **Kubernetes component 설치** — [230 Kubernetes component 설치](../230-kubernetes-components-installation-runbook.md)
  - **행동**: 고정 version의 kubeadm·kubelet·kubectl을 설치합니다.
  - **실행**: EC2 terminal의 어느 directory에서나
    `sudo /home/ubuntu/car-community-scripts/bootstrap/kubernetes/300-install-kubernetes-components.sh`를 실행합니다.
  - **완료**: 세 component의 설치·version 검증을 통과했습니다.
- [x] **Control plane 초기화** — [240 Control plane 초기화](../240-kubernetes-control-plane-initialization-runbook.md)
  - **행동**: kubeadm으로 단일 node control plane을 만들고 admin kubeconfig와 worker
    scheduling 상태를 준비합니다.
  - **실행**: EC2 terminal의 어느 directory에서나
    `sudo /home/ubuntu/car-community-scripts/bootstrap/kubernetes/400-initialize-cluster.sh`를 실행합니다.
  - **완료**: Control plane과 node 상태 검증을 통과했습니다.
- [x] **Calico 설치와 cluster smoke** — [250 Calico 설치](../250-calico-network-installation-runbook.md)
  - **행동**: Calico CNI를 설치하고 Node·CoreDNS Ready, namespace DNS·ClusterIP·backend TCP
    smoke와 임시 resource 정리를 확인합니다.
  - **실행**: EC2 terminal의 어느 directory에서나
    `sudo /home/ubuntu/car-community-scripts/bootstrap/kubernetes/500-install-calico.sh`를 실행합니다.
  - **완료**: 빈 Kubernetes cluster의 network와 smoke postcondition을 모두 확인했습니다.

### 4. Node image 공급

이 절은 Kubernetes cluster와 첫 ECR image가 모두 완료된 뒤 실행합니다.

- [x] **Kubelet ECR credential provider** — [330 Provider 최초 설치](../330-kubelet-ecr-credential-provider-installation-runbook.md)
  - **행동**: Kubelet이 EC2 pull role로 private ECR credential을 얻도록 credential provider를
    설치합니다.
  - **실행**: EC2 terminal의 어느 directory에서나
    `sudo /home/ubuntu/car-community-scripts/bootstrap/ecr/100-configure-kubelet-credential-provider.sh`를 실행합니다.
  - **완료**: Provider 설치와 kubelet 실행 인자 검증을 완료했습니다.
- [x] **Node exact digest pull** — [410 Private ECR pull 점검](../410-private-ecr-image-pull-verification-runbook.md)
  - **행동**: 첫 image의 exact digest reference로 temporary Pod를 실행해 pull·ARM64 실행과
    실제 `imageID` 일치를 확인하고 임시 namespace를 정리합니다.
  - **실행**: EC2 terminal의 어느 directory에서나 runbook대로 `IMAGE_REFERENCE`를 준비한 뒤
    `sudo /home/ubuntu/car-community-scripts/verify/ecr/verify-private-image-pull.sh "${IMAGE_REFERENCE}"`를 실행합니다.
  - **완료**: `sha256:c290d43dd44f30f273efb8be370e396317af483fadba977a22b82a86dab959c8`의
    pull·실행·`imageID` 일치와 임시 namespace 정리를 확인했습니다. 현재 overlay도 이
    digest를 staging 배포 목표로 사용합니다.

### 5. Argo CD와 application delivery

Application prerequisite 확인·적용, GitHub App 설치와 최초 desired state Git 반영 track은 서로
독립적으로 진행할 수 있지만, 모두 완료돼야 private repository 연결을 실행할 수 있습니다.

- [x] **Argo CD Core 설치** — [340 Argo CD Core 최초 설치](../340-argocd-core-installation-runbook.md)
  - **행동**: Argo CD Core를 cluster에 설치하고 component rollout과 CRD를 확인합니다.
  - **실행**: EC2 terminal의 어느 directory에서나
    `sudo /home/ubuntu/car-community-scripts/bootstrap/gitops/100-install-argocd-core.sh`를 실행합니다.
  - **완료**: Argo CD Core `v3.5.0` component rollout과 `Application`·`AppProject` CRD를
    확인했습니다.
- [x] **Application DB role 적용** — [350 Application delivery 선행 조건](../350-application-delivery-bootstrap-runbook.md#2-application-delivery-선행-조건-적용)
  - **행동**: Local `.env`의 staging DB password와 현재 RDS master password를 사용해
    PostgreSQL 16 client를 자동 준비하고 `carmunity_runtime`·`carmunity_migrator` role과
    권한을 적용합니다.
  - **실행**: 시작 directory 제한이 없는 local terminal에서 운영자가 runbook의 NUL
    stdin·SSH 명령으로 EC2의
    `/home/ubuntu/car-community-scripts/bootstrap/application/100-provision-database-access.sh`
    를 `sudo -n` 실행합니다.
  - **완료**: PostgreSQL 16 client를 설치하고 `carmunity_runtime`·`carmunity_migrator` role을
    생성한 뒤 최소 schema 권한 적용과 transaction commit을 확인했습니다.
- [x] **Application Namespace·Secret 적용** — [350 Application delivery 선행 조건](../350-application-delivery-bootstrap-runbook.md#2-application-delivery-선행-조건-적용)
  - **행동**: Local `.env`의 허용된 staging 값만 전달해 `car-community` Namespace와
    application Secret을 적용합니다.
  - **실행**: 시작 directory 제한이 없는 local terminal에서 운영자가 runbook의 NUL
    stdin·SSH 명령으로 EC2의
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
  - **완료**: 개인 계정에 `car-community-argocd` App을 생성했고 App ID는 `4582121`입니다.
    Webhook·OAuth·Device Flow·Setup URL은 사용하지 않고 `Contents: Read-only` 외 permission을
    추가하지 않았으며 설치 가능 범위는 `Only on this account`입니다. Private key PEM도
    local에 다운로드했습니다. PEM 원문·파일명·local 경로는 기록하지 않습니다.
- [x] **GitHub App 설치·credential 입력 준비** — [350 Application delivery 최초 bootstrap](../350-application-delivery-bootstrap-runbook.md#3-read-only-github-app-준비)
  - **행동**: PEM을 local 안전 directory로 옮겨 권한을 확인하고, App을
    `car-community` repository 하나에만 설치해 Installation ID를 확보합니다.
  - **실행**: 시작 directory 제한이 없는 local terminal에서 운영자가 runbook의
    directory·file permission 확인 명령을 실행하고, GitHub Web UI의 App 설치 화면에서
    `Only select repositories`를 선택합니다. 이 단계의 repository script는 없습니다.
  - **완료**: App을 `car-community` 하나에 설치했고 Installation ID `153662061`을
    확인했습니다. Local PEM file mode `0600`을 확인한 뒤 SSH stdin으로 GitOps `200`에
    전달해 credential 입력 경계까지 실제 사용했습니다.
- [x] **Target dry-run·최초 `main` 반영** — [350 Application delivery 최초 bootstrap](../350-application-delivery-bootstrap-runbook.md#21-target-server-side-dry-run과-git-반영)
  - **행동**: 현재 overlay 변경의 target cluster server-side dry-run을 통과시킨 뒤 현재
    local `main`을 `origin/main`에 직접 push합니다.
  - **실행**: Repository root의 local terminal에서 운영자가 runbook의 `kubectl
    kustomize`와 SSH를 통한 `kubectl apply --dry-run=server` 명령을 실행하고, exact
    refspec으로 `main`을 push한 뒤 GitHub Actions와 SonarQube Cloud에서 해당 exact SHA를
    확인합니다. 별도 repository script는 없습니다.
  - **완료**: Target API가 ConfigMap 3개, Service, Deployment와 migration Job으로 구성된
    전체 render를 server-side dry-run에서 승인했습니다. 검증된 desired state를 GitHub
    `main`에 직접 push하고 그 exact SHA의 CI·Sonar 성공을 확인했습니다. 이 최초 반영 뒤
    GitOps `200`이 같은 repository의 `deploy/overlays/stg`를 source로 사용했습니다. 연결
    이후 digest 변경은 [420 GitOps 배포·rollback](../420-gitops-deployment-and-rollback-runbook.md)의
    PR 절차를 사용합니다.
- [x] **Private repository 연결** — [350 Application delivery 최초 bootstrap](../350-application-delivery-bootstrap-runbook.md)
  - **행동**: App ID·Installation ID·private key를 사용해 Argo CD에 private repository를
    연결하고 실제 `car-community` AppProject와 staging Application을 생성합니다.
  - **실행**: 시작 directory 제한이 없는 local terminal에서 운영자가 PEM을 runbook의
    SSH stdin으로 전달해 EC2의
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
- [x] **최초 full sync와 workload 기술 검증** — [350 Application delivery 최초 bootstrap](../350-application-delivery-bootstrap-runbook.md#6-최초-full-sync와-workload-기술-검증)
  - **행동**: GitOps `200`의 fresh reconciliation 완료를 기다린 뒤 revision, sync result,
    workload image와 readiness를 읽기 전용으로 검증합니다.
  - **실행**: 앞 단계의 GitOps `200` script가 fresh `Synced`·`Healthy`까지 기다립니다.
    성공 후 운영자가 시작 directory 제한이 없는 EC2 terminal에 접속해 350 runbook 6절의
    세 `kubectl` 조회를 실행합니다. 별도의 두 번째 bootstrap script를 실행하지 않습니다.
  - **완료**: Revision `197890b0e810bf39e727b6ed9e86b34fadc467d9`에서 PreSync
    ConfigMap과 단일 Migration Job이 성공했고, Service·Deployment가 Synced 상태입니다.
    Application condition 없음·`Succeeded`·`Synced`·`Healthy`, Deployment rollout 성공,
    runtime Pod Ready와 목표 ECR digest
    `sha256:32351d30bdc40a547dc6acf42b18c99759298c3074168d0bbf3863d755aa96b3`와 같은
    imageID를 확인했습니다. Runtime role의 `public.auth_role` `SELECT` 권한도 `t`로
    확인했습니다. 여기까지가
    Application delivery 기술 구축의 완료 경계입니다.

### 6. 선택 Super Admin 최초 bootstrap

이 절은 운영상 최고 관리자가 필요할 때 한 번 실행하는 선택 절차입니다. 미실행 상태는
선행 delivery 결과나 external HTTPS ingress를 실패로 바꾸지 않습니다.

- [x] **최초 Super Admin provisioning** — [360 Super Admin 최초 bootstrap](../360-super-admin-bootstrap-runbook.md)
  - **행동**: 최초 `ROLE_SUPER_ADMIN` 한 명을 선택적으로 생성합니다. 이 계정은
    qualification test data가 아니며 cleanup하지 않습니다.
  - **실행**: 운영자가 시작 directory 제한이 없는 두 EC2 SSH terminal session에서
    `360`의 Application command·port-forward와 초기 로그인·password 변경·재로그인
    절차를 순서대로 수행합니다. 별도 repository script는 없습니다.
  - **선택 완료**: `CREATED` 또는 credential을 아는 기존 Super Admin의
    `ALREADY_BOOTSTRAPPED`, password 변경 `204`, 새 password 로그인 `201`과
    `FULL`·`ROLE_SUPER_ADMIN`을 확인했습니다.

### 7. Staging external HTTPS ingress

이 절의 항목은 repository source 존재가 아니라 AWS·Cloudflare·EC2·Kubernetes의
live postcondition을 근거로만 완료합니다. 모든 세부 명령과 rollback 경계는
[370 Staging 외부 HTTPS ingress 최초 구축](../370-staging-external-https-ingress-bootstrap-runbook.md)을
따릅니다.

- [x] **Primary ENI SG admission**
  - **행동**: Primary ENI의 여러 SG 중 `stg-car-community-k8s-node-sg` 하나만 cluster
    tag로 선택하고 RDS 자동 연결 SG는 유지합니다.
  - **실행**: AWS Console에서 `370` 1.1절을 수행합니다. `370` 1.2절의 Kubernetes·existing
    resource baseline은 선택 진단이며 생략해도 이 checklist를 막지 않습니다.
  - **완료**: Primary ENI에서 `stg-car-community-k8s-node-sg`만
    `kubernetes.io/cluster/stg-car-community-k8s=shared`를 가지며 다른 연결 SG에는 같은
    key가 없습니다.
- [x] **Node IAM/profile 안전 전환**
  - **행동**: Exact ECR pull policy와 LBC `3.5.0` policy를 분리한 새
    `stg-car-community-k8s-node-role` profile로 전환하고 IMDSv2 required·hop `2`를
    확인합니다. 교체 직전 current profile ARN은 rollback 값으로 기록합니다.
  - **실행**: ECR policy·role/profile·association·IMDS 설정은 AWS Console, official LBC
    policy 확보와 SSH 접속은 시작 directory 제한이 없는 macOS local terminal, 실제 role
    credential 확인은 시작 directory 제한이 없는 EC2 terminal에서 `370` 2절을 수행합니다.
    기존 ECR-only profile은 아직 삭제하지 않습니다.
  - **완료**: IMDS credential role name이 새 role과 exact입니다.
- [x] **Public subnet discovery·ACM certificate**
  - **행동**: Public `2a`·`2b`에만 `kubernetes.io/role/elb=1`을 두고,
    `ap-northeast-2`의 `stg-api.ronams.com` public certificate를 Cloudflare DNS-only
    validation CNAME으로 발급합니다.
  - **실행**: Public subnet tag와 route 확인은 AWS VPC Console, certificate 요청과
    `Issued` 확인은 AWS ACM Console, validation CNAME 생성은 Cloudflare Console에서
    `370` 3·4절을 수행합니다.
  - **완료**: 두 subnet의 role tag·IGW route·free IP와 certificate `Issued`를
    확인하고 validation CNAME을 유지합니다.
- [x] **LBC bootstrap·providerID·ECR regression**
  - **행동**: 앞선 archive로 전달된 exact script를 실행해 Node providerID를
    `aws:///<AZ>/<INSTANCE_ID>`로 수렴시키고 LBC rollout 후 기존 private ECR
    image pull을 재검증합니다.
  - **실행**: 시작 directory 제한이 없는 EC2 terminal에서 `370` 5·6절을 수행합니다.
  - **완료**: Exact providerID, chart/image·replica, controller Ready와 exact digest pull
    regression, required CRD 7개 `Established`와 controller HPA absent를 확인합니다.
- [x] **AppProject permission·GitOps ingress merge**
  - **행동**: Tracked AppProject YAML의 Ingress 허용만 one-time server-side apply하고
    Service NodePort patch·Ingress를 정상 GitOps PR로 merge합니다.
  - **실행**: AppProject source·Ingress render는 repository root의 local terminal,
    전달 manifest·AppProject 적용과 target dry-run은 시작 directory 제한이 없는 EC2
    terminal, PR review·merge는 GitHub Web UI에서 `370` 7·8절을 수행합니다. GitOps `200`
    전체를 다시 실행하지 않습니다.
  - **현재 상태**: AppProject permission, local render, target server dry-run과 remote
    `main` 반영을 완료했고, Argo CD `Synced`·`Healthy`와 Ingress ALB hostname을
    확인했습니다.
  - **완료**: AppProject가 Ingress만 추가 허용하고 Argo가 merge revision을
    reconcile해 ALB hostname을 보고합니다.
- [x] **ALB·Cloudflare DNS·HTTPS E2E**
  - **행동**: Healthy target·listener·managed SG를 먼저 확인한 뒤
    Cloudflare DNS-only `stg-api` CNAME을 만들고 HTTPS·redirect·DNS·Argo를 검증합니다.
  - **실행**: `370` 9절의 Kubernetes reconciliation은 시작 directory 제한이 없는 EC2
    terminal, AWS resource 완료 확인은 AWS Console 또는 시작 directory 제한이 없는 AWS
    API·CLI terminal, DNS 전 ALB
    직접 요청은 시작 directory 제한이 없는 local 또는 EC2 terminal에서 수행합니다.
    `10`절의 CNAME 생성은 Cloudflare Console, public resolver 확인은 시작 directory 제한이
    없는 public network terminal에서 수행합니다. `11`절의 public DNS·TLS 요청은 public
    network terminal, Kubernetes·Argo final state 확인은 EC2 terminal에서 수행합니다.
  - **현재 상태**: Target Group `Healthy`, DNS 전 ALB 직접 HTTPS readiness `200`과
    HTTP `301`, Cloudflare DNS-only CNAME의 exact ALB hostname 수렴, public HTTPS
    readiness `200`·HTTP→HTTPS `301`·ACM certificate, Service NodePort `30503`, Argo CD
    revision `4a75d77cf58c2139ee81915bff8dd2a2db23ea8f`의 `Synced`·`Healthy`와 condition 부재를
    확인했습니다.
  - **완료**: `stg-api.ronams.com` HTTPS `200`, HTTP→HTTPS `301`, target healthy,
    DNS-only CNAME과 Argo `Synced`·`Healthy`를 모두 확인합니다.

### 선택 후속 기능 qualification

아래 항목은 필수 checklist가 아닙니다. 미실행 상태는 기능을 실패로 판정하지 않고
`미검증`으로 유지하며, 실제 실행했을 때만 결과를 이 scenario에 갱신합니다.

- [x] **Application 기능 qualification** — [430 Application 기능 qualification](../430-application-functional-qualification-runbook.md)
  - **행동**: 새 test member의 회원가입, 실제 인증 메일 수신과 token confirmation으로
    API→runtime DB role→RDS→Resend 경로를 최소 검증합니다.
  - **실행**: 운영자가 시작 directory 제한이 없는 `430`의 두 EC2 SSH session 절차를
    사용합니다. 별도 script를 만들지 않고, Runbook의 exact email·`memberId` guard와
    transaction SQL로 test data를 정리합니다.
  - **선택 완료**: `201`·`202`·실제 수신·`204`·DB `ACTIVE`, 필수 event 3종
    `SUCCESS`, cleanup `COMMIT`·잔여 account 0건을 모두 확인했습니다.
    Email·`memberId`·password·token은 이 scenario에 기록하지 않습니다.

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

다음 필수 결과가 모두 실제 환경에서 확인돼야 이 scenario가 완료됩니다.

- VPC·EC2·ECR/IAM·RDS가 각 runbook의 live postcondition을 충족합니다.
- Kubernetes 단일 node와 network smoke가 완료됩니다.
- 승인된 Git 반영과 `main` CI·Sonar Gate, image publish와 node exact digest pull이 성공합니다.
- Argo CD Core, runtime·migrator DB role, Namespace·네 Secret과 Git-managed ConfigMap이
  연결됩니다.
- 최초 full sync가 `Synced`·`Healthy`, runtime Pod Ready로 끝납니다.
- New node profile·providerID·public subnet role tag·ACM·LBC prerequisite가 live 검증됩니다.
- `stg-api.ronams.com` DNS-only→ALB→NodePort→Pod target이 healthy이고 HTTPS `200`,
  HTTP→HTTPS `301`과 Argo `Synced`·`Healthy`가 같이 확인됩니다.

대표 API·notification 기능 qualification과 최초 Super Admin은 위 선택 section에서 별도로
추적하며 필수 완료 조건에 포함하지 않습니다.

Repository source·contract test·Kustomize render만으로 위 live 결과를 완료 처리하지 않습니다.

## 연결된 작업과 scenario backlog

### 기존 작업

- [013 Staging application GitOps delivery](../../work-items/features/2026/013-autoscaling-safe-application-delivery.md):
  Application delivery의 repository 결과와 live qualification 요구를 소유합니다. 현재 실제
  실행 위치와 완료 여부는 이 scenario checklist가 소유합니다.
- [014 Staging 외부 HTTPS ingress](../../work-items/features/2026/014-staging-external-https-ingress.md):
  LBC·ALB·ACM·Cloudflare와 Git-owned Ingress의 repository 결과·선택 근거를 소유합니다.
  현재 live 완료여부와 재개점은 이 scenario가 소유합니다.

### 선택 후속 infrastructure 후보

현재 scenario 완료 조건 밖의 확장·관측·보안·구성 자동화 후보는
[004 Infrastructure 진화 Backlog](../../work-items/backlog/2026/004-infrastructure-evolution.md)가
단일 owner입니다. 이 scenario는 후보 상세나 우선순위를 복제하지 않고, 실제 작업으로
승격된 뒤 live checklist에 합류한 결과만 소유합니다.

## 반복과 확장 규칙

- 같은 staging 환경의 재개·재구축은 이 문서의 현재 상태와 checklist를 갱신합니다.
- 단순한 값 변경은 기존 scenario와 runbook 입력으로 처리합니다.
- Actor·운영 목표·단계 순서·hard dependency·복구 경계·전체 완료 조건이 달라질 때만 새
  scenario가 필요한지 검토합니다.
- 새 scenario나 work item을 자동 생성하지 않습니다. 필요한 범위는 사용자와 먼저
  논의합니다.

## 관련 문서

- **세부 절차 탐색**: [000 운영 문서 목차](../000-index.md)
- **현재 영구 구조**: [100 Infrastructure overview](../../project/infrastructure/100-infrastructure-overview.md)
- **Infrastructure 선택과 이유**: [000 Infrastructure 개념 목차](../../project/infrastructure/000-index.md)
