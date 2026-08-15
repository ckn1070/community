# Infrastructure system

이 문서는 현재 프로젝트의 AWS resource, 단일 node Kubernetes, container image 공급과
Argo CD GitOps delivery가 하나의 시스템으로 연결되는 방식을 설명합니다. 개별 선택값과
대안은 [Infrastructure 개념 목차](infrastructure/000-index.md), 사람이 실행하는 명령과
복구 절차는 [운영 문서 목차](../operations/000-index.md)가 소유합니다.

## 전체 mental model

```text
AWS VPC
├── public subnet 2a: EC2 단일 Kubernetes node
└── private subnet 2a·2b: RDS placement 후보

car-community main
├── application source 변경 → CI → Publish image → private ECR ARM64 digest
└── deploy/overlays/stg digest 변경 PR ───────────────┐
                                                      │
EC2 instance profile pull role                        │
└── kubelet ECR credential provider ← private ECR     │
                                                      │
Argo CD Core reads deploy/overlays/stg ───────────────┴─> PreSync migration + permission callback
                                                         └─> runtime Deployment·Service ─> RDS
```

VPC는 address·route·DNS·security 경계이고, EC2는 Kubernetes control plane과 worker를 함께
실행합니다. RDS는 같은 VPC의 private subnet 후보에 배치됩니다. ECR은 application artifact를
환경과 분리해 보관하고, 현재 repository의 `deploy/` 경로는 staging이 선택한 exact digest와
Kubernetes 목표 상태를 보관합니다.

## Actor·artifact·permission

| Actor | 읽는 입력 | 만드는·변경하는 결과 | 권한 경계 |
| --- | --- | --- | --- |
| AWS Console 운영자 | 확정한 resource 선택 | VPC·EC2·RDS·ECR·IAM | Console 운영 권한은 bootstrap·delivery runtime 권한과 분리합니다. |
| Local 운영자 | Repository script, SSH key, 공개 ID와 필요 시 local PEM | EC2에서 bootstrap·검증·credential 교체 실행 | 비밀키를 Git이나 EC2 영구 file에 복사하지 않습니다. |
| GitHub `CI` | Source revision과 main push `before..after` | 품질 검증 결과, SHA·run attempt에 결속한 image 게시 분류 artifact | `contents: read`만 사용하고 AWS 권한을 갖지 않습니다. |
| `Publish image` workflow | 성공한 exact `main` SHA와 같은 CI run·attempt의 분류 artifact | Immutable SHA tag와 ECR digest | Non-privileged classify가 artifact를 검증한 뒤에만 GitHub OIDC의 단기 session과 target repository push 권한을 사용합니다. |
| EC2 kubelet | Pod image reference | Container runtime의 image pull | EC2 instance profile의 target ECR read 권한만 사용합니다. |
| GitOps PR reviewer | Published digest와 `deploy/**` manifest diff | 승인된 `car-community/main`의 staging 목표 상태 | 같은 repository에서도 image 게시와 배포 승인을 별도 PR로 분리합니다. |
| Argo CD Core | Read-only GitHub App credential과 `deploy/overlays/stg` 목표 상태 | Kubernetes live state reconciliation | Repository write와 GitHub Actions의 cluster credential을 요구하지 않습니다. |
| Migration Job | Migrator Secret, DB URL·RDS CA와 migration·callback source | Schema migration 뒤 runtime 최소 권한 동기화 | Runtime credential을 사용하지 않고 단일 Job이 성공해야 Deployment sync가 진행됩니다. |
| Application Pod | Runtime·auth·notification Secret, Git-managed DB URL·RDS CA와 공개 application 설정 | Application request 처리·RDS connection·notification 발송 | Migrator·master credential과 Node ECR pull role을 application 권한으로 확대하지 않습니다. |

주요 artifact는 서로 대체되지 않습니다.

- Source SHA는 application source identity입니다.
- ECR tag는 사람이 SHA를 찾는 immutable label이고, digest가 runtime content identity입니다.
- `deploy/**` Git commit은 승인된 deployment intent와 변경 이력입니다.
- Kubernetes live resource는 Argo CD가 Git 목표 상태로 수렴시키는 실행 상태입니다.
- RDS Secrets Manager의 master credential은 database 관리 secret이며 application manifest나
  ECR credential이 아닙니다.

## 자동 경계와 수동 경계

| 경계 | 자동 | 사람이 결정·확인 |
| --- | --- | --- |
| AWS 기반 resource | Console workflow가 부속 resource를 만들 수 있음 | 옵션·비용·network·권한 선택, 성공 화면과 live 상태 확인 |
| Kubernetes bootstrap | Script가 한 단계의 목표 상태와 postcondition을 적용·검증 | 단계 순서, 재부팅, 실패 진단, destructive recovery 승인 |
| Image publish | 성공한 `main` CI 뒤 변경 경로를 분류하고 `deploy/**` 전용이면 publish job을 건너뜁니다. 그 외에는 build·push 또는 같은 SHA digest를 재사용합니다. | PR review·merge, workflow 분류와 결과 digest 확인 |
| Node pull | Kubelet이 필요할 때 provider를 실행하고 ECR token을 cache | Provider 최초 구성, exact digest smoke와 실패 진단 |
| GitOps delivery | Argo CD가 merge된 `main`을 pull하고 sync·prune·self-heal | Digest 변경 PR review·merge, credential 발급·rotation, 장애 복구 |

자동화 source가 있다는 사실은 외부 시스템의 실행 완료가 아닙니다. Repository 검증은
script·workflow의 정적 계약을 증명하고, 실제 AWS·GitHub·EC2·Kubernetes 상태는 live
postcondition으로만 증명합니다.

## 생애주기 경계

### 최초 구축

1. VPC·subnet·route·gateway와 DNS 기반을 만듭니다.
2. Public EC2 node와 private RDS 기반, private ECR와 두 IAM identity를 만듭니다.
3. EC2 host·containerd·Kubernetes component·control plane·Calico를 순서대로 구성합니다.
4. Kubelet ECR credential provider와 Argo CD Core를 최초 구성합니다.
5. Runtime·migrator DB role과 Git 밖 Namespace·네 Secret을 구성합니다.
6. Target dry-run을 통과한 최초 `deploy/` 목표를 `main`에 직접 게시한 뒤 GitHub
   App·Application을 연결합니다. 이때까지는 Argo CD가 repository를 읽지 않으므로 자동
   sync가 시작되지 않습니다.
7. Argo CD가 Git의 DB URL·RDS CA·notification ConfigMap과 workload를 순서대로 적용합니다.

각 단계는 다음 단계의 입력 상태를 만들지만, 서로의 완료 증거를 대신하지 않습니다.
예를 들어 EC2 SSH 성공은 Kubernetes 완료가 아니고, ECR repository 생성은 image publish나
node pull 성공이 아닙니다.

### 평소 반복 delivery

1. Application source 변경이 PR review를 거쳐 `main`에 merge됩니다.
2. `CI`가 exact revision을 검증합니다.
3. 성공한 revision만 ARM64 ECR image와 digest로 게시됩니다.
4. 같은 repository에서 `deploy/overlays/stg`의 digest만 바꾸는 별도 PR을 검토·merge합니다.
5. Argo CD가 승인된 목표를 pull해 단일 Migration PreSync Job을 실행합니다.
6. Migration과 runtime 권한 callback이 성공하면 runtime Deployment·Service를 sync하고 live drift를 복구합니다.

`deploy/**` 전용 merge도 `CI`는 실행하지만 image publish workflow의 분류 결과 AWS OIDC·
build·push job은 실행하지 않습니다. 현재 repository에는 image publish와 node pull 검증
source와 GitOps manifest가 있지만 digest 변경 PR 자동화는 아직 구현됐다고 주장하지 않습니다.

### 구성 변경·복구

- Package·Kubernetes·Calico·Argo CD·provider version은 exact 계약으로 바꾸고 각 소비자와
  live postcondition을 함께 검증합니다.
- Bootstrap의 정상 재실행, 구축 취소와 cluster data 손실 가능 복구를 구분합니다.
- GitHub App private key는 새 key 적용·fresh reconciliation 성공 뒤 이전 key를 revoke합니다.
- Resend key는 existing notification Secret에 적용하고 새 runtime Pod Ready·email smoke 뒤 이전
  provider key를 revoke합니다. DB password와 auth HMAC은 이 단순 교체 경로를 재사용하지 않습니다.
- Rollback은 Kubernetes live image를 직접 고치는 대신 이전 GitOps digest commit을 되돌리는
  PR로 수행합니다.
- 다중 node·HA·IPv6·NAT·ALB·Pod AWS identity·Terraform 전환은 현재 경계를 바꾸므로 해당
  구성요소의 재검토 조건부터 확인합니다.

## 증거 계층

| 증거 | 증명하는 것 | 증명하지 않는 것 |
| --- | --- | --- |
| Source·README·문서 대조 | 현재 의도와 공개 계약의 정합성 | 외부 resource 존재·동작 |
| Shell syntax·contract test·workflow 검사 | 분기·guard·render·정적 계약 | 실제 package·AWS IAM·network·cluster 실행 |
| AWS Console 성공·resource 상태 | 해당 AWS 작업과 관찰한 resource 상태 | EC2 내부 bootstrap·application E2E |
| EC2 script postcondition | 해당 host·cluster 단계의 실제 상태 | 다음 단계나 외부 GitHub 실행 |
| GitHub Actions job summary와 ECR digest | Exact revision image 게시 | Node pull·application rollout |
| Kubernetes smoke·Argo `Synced`·`Healthy` | 관찰한 consumer 경로와 reconciliation | RDS application 인증·외부 ingress E2E |

Staging 전체 구축의 현재 live 판정과 재개 위치는
[Staging 단일 node 전체 구축 시나리오](../operations/scenarios/100-staging-single-node-bootstrap.md)가
소유합니다.

## 관련 문서

- **최초 구축·반복 delivery·변경과 복구별 상세 개념**:
  [Infrastructure 개념 목차](infrastructure/000-index.md)
- **사람이 실행하는 구축·점검·복구 절차**: [운영 문서 목차](../operations/000-index.md)
- **Script 공개 계약**: [Repository script 목차](../../scripts/README.md)
- **Staging 현재 live 판정·재개 위치**:
  [Staging 단일 node 전체 구축 시나리오](../operations/scenarios/100-staging-single-node-bootstrap.md)
