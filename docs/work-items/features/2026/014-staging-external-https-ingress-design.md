# Staging 외부 HTTPS ingress 상세 설계

## 배경

Staging application은 private ECR image, 단일 kubeadm node, Calico overlay, Argo CD와
ClusterIP Service까지 동작합니다. 외부 client가 stable domain과 HTTPS로 API를 호출하려면
AWS load balancer, certificate, DNS와 Kubernetes Service·Ingress의 owner를 연결해야 합니다.

이번 설계는 관리형 EKS나 AWS VPC CNI를 전제하지 않습니다. 현재 public EC2 한 대가
control plane과 worker를 함께 수행하고, public subnet은 `ap-northeast-2a`와
`ap-northeast-2b`에 하나씩 있습니다.

## 확정 결정

### D1. ALB lifecycle owner

- 배경: ALB를 Console에서 먼저 만들면 Target Group·listener·security group과 Kubernetes
  workload 변경의 owner가 분리됩니다.
- 검토한 선택지: Console에서 ALB 수동 생성, Kubernetes Service `LoadBalancer`, AWS Load
  Balancer Controller가 Ingress reconcile
- 선택: AWS Load Balancer Controller가 Git의 Ingress를 기준으로 internet-facing ALB,
  Target Group, listener와 security group을 생성·변경·삭제합니다.
- 선택 근거: Application route와 AWS L7 resource를 같은 declarative source에 결속하고,
  workload·replica 변경 때 target membership을 controller가 수렴시킵니다.
- 영향: ALB Console direct edit는 canonical 변경 경로가 아니며, Git revert와 controller
  reconciliation이 rollback 경로입니다.
- 배제한 대안: 수동 ALB는 동일 resource의 이중 owner와 drift를 만들고, Service
  `LoadBalancer`는 이번 HTTP host·redirect·certificate 기반 L7 경계를 충분히 표현하지
  못합니다.
- 재검토 조건: 외부 ALB lifecycle을 중앙 platform team이나 Terraform이 독립적으로
  소유하고 TargetGroupBinding으로 연결해야 합니다.

### D2. External DNS 경계

- 배경: Domain은 Cloudflare에서 관리하지만 첫 staging ingress는 ALB와 origin의 동작을
  직접 분리해 검증해야 합니다.
- 검토한 선택지: Cloudflare proxy, Cloudflare DNS-only, Route 53 migration
- 선택: `stg-api.ronams.com` CNAME을 ALB DNS name으로 만들고 Cloudflare proxy는 끈
  DNS-only를 baseline으로 사용합니다.
- 선택 근거: 최초 구축에서 TLS·header·timeout 실패 계층을 ALB와 application으로 제한하고,
  ACM certificate가 client TLS endpoint가 되게 합니다.
- 영향: Cloudflare WAF·DDoS proxy·rate limiting·cache·edge HTTP analytics의 이점을 사용하지
  않고 ALB origin이 DNS에 직접 드러납니다. Cloudflare가 web traffic에는 proxy를 권장한다는
  점에서 이 선택은 staging 초기 진단을 위한 조건부 trade-off이지 일반 Best Practice가
  아닙니다.
- 배제한 대안: Proxy를 지금 켜면 Cloudflare TLS mode·header·timeout·cache까지 함께
  진단해야 합니다. Route 53 이전은 현재 authoritative DNS owner를 바꿀 근거가 없습니다.
- 재검토 조건: Edge WAF·rate limit·DDoS 보호·analytics가 필요하거나 public API 운영
  maturity가 높아집니다. Proxy를 켤 때 origin bypass도 막으려면 ALB frontend ingress를
  Cloudflare published IP range로 제한하는 별도 설계가 필요합니다.

### D3. Target type과 Service

- 배경: 현재 node network는 self-managed kubeadm과 Calico overlay이며 Pod IP가 VPC-native
  target이 아닙니다.
- 검토한 선택지: `ip` target과 ClusterIP, `instance` target과 NodePort
- 선택: ALB Target Group은 `instance`, staging Service는 Kubernetes가 port를 자동 할당하는
  `NodePort`를 사용합니다.
- 선택 근거: ALB가 VPC에서 routable한 EC2 instance로 전달하고 kube-proxy가 Pod로
  전달하는 현재 network model에 맞습니다.
- 영향: Target Group에는 node가 등록되고 health check와 request가 NodePort를 통과합니다.
  고정 NodePort를 Git에 예약하지 않습니다.
- Prerequisite: Self-managed kubeadm에는 AWS Cloud Controller Manager가 없으므로
  Node `spec.providerID`가 자동으로 EC2 identity에 수렴한다고 가정하지 않습니다.
  LBC bootstrap은 IMDSv2의 instance ID와 placement AZ로
  `aws:///<AZ>/<INSTANCE_ID>`를 만들고 singleton Node의 빈 providerID만 patch합니다.
  Exact 값은 no-op, 다른 non-empty 값이나 IMDS/API 조회 실패는 fail-close입니다.
- 배제한 대안: `ip` target은 현재 Calico Pod CIDR을 ALB가 직접 routable target으로
  사용할 수 있다는 보장이 없습니다.
- 재검토 조건: AWS VPC CNI·routable Pod address 또는 검증된 pod-direct target network를
  도입합니다.

### D4. Security group owner

- 배경: Internet-facing listener와 node NodePort 사이에는 frontend·backend 두 보안 경계가
  필요합니다.
- 검토한 선택지: 수동 custom frontend SG, global backend-rule 관리 flag, controller 기본
  SG lifecycle
- 선택: Ingress에 custom security group annotation을 두지 않고 LBC가 ALB별 frontend SG와
  shared backend SG, node SG의 필요한 restricted port rule을 관리하게 합니다.
- 선택 근거: 기본 자동 frontend SG에서는 controller가 backend instance rule도 자동으로
  관리하며, 대상 port 범위를 실제 Service에 맞게 제한합니다.
- 영향: NodePort나 node SG를 `0.0.0.0/0`에 직접 열지 않습니다. Frontend SG만 listener
  `80`·`443`의 internet client traffic을 받습니다.
- SG selection invariant: Node primary ENI에 RDS 자동 연결용 `ec2-rds-N` 등 여러 SG가
  연결될 수 있습니다. LBC가 backend rule을 관리할 SG를 유일하게 찾도록
  `stg-car-community-k8s-node-sg` 하나에만
  `kubernetes.io/cluster/stg-car-community-k8s=shared`를 둡니다. Node SG는 cluster보다
  먼저 수동 생성됐고 SSH·node network 경계를 함께 소유하므로 `owned`가 아니라
  `shared`입니다. RDS SG의 attachment와 rule은 변경하지 않습니다.
- 배제한 대안: Custom SG와 `manage-backend-security-group-rules`를 섞으면 현재 한 Ingress에
  불필요한 이중 설정과 수동 lifecycle을 추가합니다.
- 재검토 조건: Cloudflare proxy origin lock-down, 고정 corporate CIDR, 중앙 SG owner가
  필요합니다.

### D5. Subnet discovery

- 배경: Internet-facing ALB는 서로 다른 AZ의 public subnet 둘 이상이 필요하고 LBC가
  의도한 subnet만 찾아야 합니다.
- 검토한 선택지: Ingress에 subnet ID 고정, route reachability 추론, subnet role tag
- 선택: 기존 public `2a`(`10.0.0.0/20`)와 public `2b`(`10.0.16.0/20`)에
  `kubernetes.io/role/elb=1`을 적용하고 controller auto-discovery를 사용합니다.
- 선택 근거: AWS resource ID를 Git manifest에 고정하지 않으면서 public 역할을 명시하고
  두 AZ를 제공합니다.
- 영향: 적용 전에 각 subnet의 Internet Gateway 기본 route와 available IPv4 `8`개 이상을
  live 확인합니다. Private subnet에는 이 tag를 넣지 않습니다.
- 배제한 대안: Reachability 추론만 사용하면 의도한 subnet role이 tag로 남지 않고, subnet
  ID annotation은 environment AWS ID를 application Git에 결속합니다.
- 재검토 조건: 여러 public subnet 중 명시 선택, 독립 IngressClassParams나 전용 ALB subnet
  policy가 필요합니다.

### D6. HTTPS certificate와 listener

- 배경: `stg-api.ronams.com`의 client TLS를 ALB에서 종료하고 HTTP를 암호화 경로로
  수렴시켜야 합니다.
- 검토한 선택지: Certificate ARN을 Git에 기록, Ingress host certificate discovery,
  Cloudflare edge certificate
- 선택: `ap-northeast-2` ACM public certificate를 Cloudflare DNS CNAME으로 검증하고,
  validation CNAME을 유지합니다. Ingress는 `spec.tls.hosts`와 rule host에
  `stg-api.ronams.com`을 두되 certificate ARN annotation은 두지 않습니다. Listener는
  `[{"HTTP":80},{"HTTPS":443}]`을 명시하고 `80`을 `443`으로 redirect합니다.
- 선택 근거: LBC의 host discovery로 certificate identity를 domain 계약에 결속하고 AWS
  account resource ARN을 Git에서 제거합니다. Validation CNAME 유지는 ACM 자동 갱신에
  필요합니다.
- 영향: Certificate status가 `Issued`가 되기 전에 Ingress를 merge하지 않습니다.
- 배제한 대안: ARN 고정은 certificate 교체·재생성 때 Git 변경을 강제하고, Cloudflare
  certificate는 DNS-only client path의 TLS endpoint가 아닙니다.
- 재검토 조건: 하나의 listener에 여러 certificate 우선순위, cross-account certificate
  selection 또는 명시 ARN 감사 요구가 생깁니다.

### D7. TLS policy

- 배경: HTTPS listener가 허용할 protocol과 cipher를 명시해야 합니다.
- 검토한 선택지: ALB default, TLS 1.3 only, Security Hub 권장 TLS 1.3/1.2 policy
- 선택: `ELBSecurityPolicy-TLS13-1-2-Res-2021-06`을 사용합니다.
- 선택 근거: AWS Security Hub ELB.17 권장 목록에 포함되고 TLS 1.3·1.2와 forward-secrecy
  GCM cipher를 제공하면서 TLS 1.0·1.1을 제외합니다.
- 영향: TLS 1.2를 지원하지 않는 legacy client는 연결할 수 없습니다.
- 배제한 대안: Default 의존은 policy drift를 숨기고 TLS 1.3 only는 현재 public API client
  compatibility를 과도하게 제한합니다.
- 재검토 조건: 실제 legacy client 지원 요구가 확인되거나 AWS 권장 policy가 바뀝니다.

### D8. Node AWS identity

- 배경: Self-managed cluster에는 EKS Pod Identity나 IRSA가 없고 controller Pod가 AWS API
  credential을 얻을 경로가 필요합니다.
- 검토한 선택지: 기존 ECR-only role에 policy 추가, access key Secret, 새 staging node
  platform role/profile
- 선택: `stg-car-community-k8s-node-role`과 같은 이름의 instance profile을 새로 만들고,
  exact ECR pull policy와 official LBC `3.5.0` IAM policy를 별도 customer-managed policy로
  연결합니다. Official installation guide가 지정한 첫 SG ingress statement에만 exact
  staging VPC condition을 추가하되 `Resource: "*"`는 보존합니다. 뒤의
  ingress/revoke/delete + resource-tag `Null` statement를 포함한 나머지 official
  statement·condition도 바꾸지 않습니다.
- 선택 근거: Node가 실제 소비하는 ECR·LBC capability를 한 profile에 제공하되 policy
  purpose와 검토 단위를 분리합니다. Static access key를 Kubernetes Secret에 저장하지
  않습니다.
- 영향: Node의 Pod가 IMDS에 도달하면 node role credential을 사용할 가능성이 있으므로
  current single-tenant staging에 한정한 trade-off입니다. IMDSv2 token required, hop limit
  `2`를 사용해 container network에서 controller가 credential을 얻을 수 있게 합니다.
- 안전한 전환: 기존 ECR-only role/profile은 새 credential 확인, private ECR pull regression,
  controller와 ALB lifecycle 검증이 모두 끝날 때까지 보존합니다.
- 배제한 대안: 기존 role mutation은 rollback boundary가 약하고, static key는 rotation과
  노출 surface를 키웁니다.
- 재검토 조건: Multi-tenant workload, 다중 node, production 또는 EKS 전환 시 Pod identity,
  IRSA와 workload별 AWS identity를 다시 설계합니다.

### D9. Controller 설치 owner와 tool lifecycle

- 배경: Controller는 application Ingress가 존재하기 전에 CRD·webhook·RBAC와 함께
  bootstrap되어야 하고, Argo CD Application 자신이 의존하는 platform을 제거하면 안 됩니다.
- 검토한 선택지: Argo CD가 controller chart 관리, host에 Helm 영구 설치, verified
  ephemeral Helm bootstrap
- 선택: Bootstrap script가 chart `3.5.0`과 image exact digest를 검증해 controller를
  설치합니다. Helm `3.21.4` Linux ARM64 archive는 checksum을 검증한 temporary directory의
  client만 실행하고 host PATH에 영구 설치하지 않습니다. Argo CD는 application Ingress만
  관리합니다.
- 선택 근거: Platform bootstrap과 application reconciliation의 실패·삭제 경계를 분리하고,
  tool version을 재현하면서 host package lifecycle을 늘리지 않습니다.
- 영향: Controller upgrade·uninstall과 CRD lifecycle은 별도 platform change입니다. Helm
  install은 `clusterName`, `region`, `vpcId`, `replicaCount=1`과 autoscaling disabled를
  명시합니다. Chart의 일반·Gateway CRD manifest는 fixed field manager
  `car-community-load-balancer-bootstrap`으로 먼저
  server-side apply하고 required CRD 7개가 각각 `Established`인 뒤에만 Helm release를
  적용합니다. CRD discovery의 비동기 경계를 넘기지 않고 Helm upgrade가 기존 CRD를
  갱신하지 않는 동작을 보완하기 위한 barrier입니다. 기존 controller HPA는 fixed replica
  계약과 충돌하므로 owner와 무관하게 자동 인수하지 않고 fail-close합니다.
- 배제한 대안: Argo self-management는 initial dependency와 deletion blast radius를
  증가시키고, 영구 Helm 설치는 현재 단일 bootstrap 이후 불필요한 mutable tool을 남깁니다.
- 재검토 조건: 중앙 platform GitOps repository와 platform bootstrap cluster가 생기거나
  controller lifecycle을 별도 release automation이 소유합니다.

### D10. AppProject permission

- 배경: Argo CD가 staging Ingress를 적용하려면 AppProject allowlist가 새 kind를 허용해야
  합니다.
- 선택: Existing namespace resource allowlist에 `networking.k8s.io/Ingress`만 추가합니다.
- 선택 근거: ALB·Target Group·listener·security group은 Kubernetes API resource가 아니라
  LBC가 AWS에서 만들므로 Argo에 cluster-scoped AWS controller resource permission을 줄
  필요가 없습니다.
- 영향: 변경된 tracked AppProject YAML은 Ingress merge 전에 한 번 server-side apply하며
  field manager는 `car-community-bootstrap`을 사용합니다. Repository credential을 다시
  만들거나 GitOps `200` 전체를 재실행하지 않습니다.
- 재검토 조건: Application이 다른 Kubernetes kind를 실제로 소유하게 됩니다.

## Ownership과 삭제 순서

| 대상 | Canonical owner | 정상 변경 | 삭제·rollback |
| --- | --- | --- | --- |
| ACM certificate·validation CNAME | AWS/Cloudflare 운영자 | Console·DNS change | Ingress 제거와 ALB 삭제 확인 뒤 certificate/CNAME 제거 |
| Node IAM role·profile·policy | AWS 운영자 | `370` runbook | Old profile로 rollback할 때 새 controller AWS 사용을 먼저 중단 |
| Public subnet role tag | AWS 운영자 | `370` runbook | 모든 LBC-managed public LB 삭제 뒤 제거 |
| LBC release·CRD | Helm bootstrap | Versioned bootstrap/change | Application Ingress·ALB 삭제 뒤 명시 uninstall/change |
| AppProject·Application | Git-tracked bootstrap manifest | One-time server-side apply + Git source | Permission rollback은 Ingress 제거 뒤 수행 |
| Service·Ingress | `deploy/overlays/stg`와 Argo CD | PR merge | Git revert/제거 PR로 controller reconciliation |
| ALB·Target Group·listener·SG | LBC | Ingress reconciliation | Ingress 제거 뒤 controller finalizer 완료 확인, Console 수동 삭제 금지 |
| Cloudflare API CNAME | DNS 운영자 | ALB DNS name 확인 뒤 생성 | Client drain 뒤 CNAME 제거 |

## Artifact 고정 계약

| Artifact | Exact source·target | Integrity |
| --- | --- | --- |
| Helm Linux ARM64 | `https://get.helm.sh/helm-v3.21.4-linux-arm64.tar.gz` | SHA-256 `b54c04b4e0b2540bbdc08c17a121dab70e9a2ed0de5705528fec68a5fd3b85a7` |
| AWS LBC chart | `https://aws.github.io/eks-charts/aws-load-balancer-controller-3.5.0.tgz` | SHA-256 `45051f634b33e10baccb3354d0681b7de787c60445e599fa276e0c9aedd4ccd5` |
| AWS LBC image | `public.ecr.aws/eks/aws-load-balancer-controller:v3.5.0` | `sha256:1ee3cc54fb62248bd56f2157f7be79baba2be8f0bbf2d926e2650d7ba710ee5f` |
| Official LBC IAM policy | `https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/v3.5.0/docs/install/iam_policy.json` | SHA-256 `16f232c9d9f79366fe949c4550ad517a202380058a9e48d45a4e215044a20a6a` |

## 관련 문서와 공식 근거

- **작업 상태**: [014 Staging 외부 HTTPS ingress](014-staging-external-https-ingress.md)
- **영구 구조**: [170 AWS Load Balancer ingress](../../../project/infrastructure/170-aws-load-balancer-ingress.md)
- **실행 절차**: [370 Staging 외부 HTTPS ingress 최초 구축](../../../operations/370-staging-external-https-ingress-bootstrap-runbook.md)
- [AWS Load Balancer Controller certificate discovery](https://kubernetes-sigs.github.io/aws-load-balancer-controller/latest/guide/ingress/cert_discovery/)
- [AWS Load Balancer Controller installation·node IAM trade-off](https://kubernetes-sigs.github.io/aws-load-balancer-controller/latest/deploy/installation/)
- [AWS Load Balancer Controller target type defaults](https://github.com/kubernetes-sigs/aws-load-balancer-controller/blob/v3.5.0/helm/aws-load-balancer-controller/values.yaml)
- [AWS Load Balancer Controller security groups](https://kubernetes-sigs.github.io/aws-load-balancer-controller/latest/deploy/security_groups/)
- [AWS Load Balancer Controller subnet discovery](https://kubernetes-sigs.github.io/aws-load-balancer-controller/latest/deploy/subnet_discovery/)
- [Kubernetes Cloud Controller Manager](https://kubernetes.io/docs/concepts/architecture/cloud-controller/)
- [AWS Security Hub ELB controls](https://docs.aws.amazon.com/securityhub/latest/userguide/elb-controls.html)
- [ALB security policies](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/describe-ssl-policies.html)
- [ACM DNS validation](https://docs.aws.amazon.com/acm/latest/userguide/dns-validation.html)
- [Cloudflare proxy status](https://developers.cloudflare.com/dns/proxy-status/)
