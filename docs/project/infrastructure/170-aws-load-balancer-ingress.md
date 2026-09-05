# AWS Load Balancer ingress

이 상세 SOT는 staging public API의 AWS Load Balancer Controller, ALB, certificate, DNS,
Kubernetes Service·Ingress와 node IAM/security boundary를 소유합니다. 실제 AWS·Cloudflare·
EC2 실행 순서는 [370 Staging 외부 HTTPS ingress 최초 구축 runbook](../../operations/370-staging-external-https-ingress-bootstrap-runbook.md)이
소유합니다.

## 전체 mental model

```text
stg-api.example.com
  Cloudflare DNS-only CNAME
    -> internet-facing ALB
       ├── :80  -> :443 redirect
       └── :443 + ACM certificate + TLS policy
             -> instance target
             -> node auto-assigned NodePort
             -> car-community Service
             -> car-community Pod

Git deploy/overlays/stg Ingress
  -> Argo CD apply
  -> AWS Load Balancer Controller reconcile
  -> ALB·Target Group·listener·frontend/backend SG
```

Cloudflare는 이 baseline에서 authoritative DNS만 제공하며 HTTP reverse proxy가 아닙니다.
TLS는 client와 ALB 사이에서 ACM certificate로 종료되고, ALB에서 node NodePort까지는 현재
HTTP입니다.

## Actor·source·resource ownership

| Actor | 읽는 source | 소유 결과 | 소유하지 않는 것 |
| --- | --- | --- | --- |
| AWS 운영자 | 이 문서와 `370` runbook | IAM policy·role·profile, subnet tag, ACM certificate | ALB Console direct lifecycle |
| Cloudflare 운영자 | ACM validation record, ALB DNS name | Validation CNAME, `stg-api` DNS-only CNAME | ALB certificate·listener |
| Helm bootstrap | Exact artifact와 runtime VPC ID | LBC release·CRD·webhook·RBAC | Application Ingress |
| Argo CD | Git `deploy/overlays/stg` | Service·Ingress desired state | LBC release와 AWS IAM |
| AWS Load Balancer Controller | Ingress·Service, AWS API, node role | ALB·Target Group·listener·managed SG와 target registration | DNS record와 ACM certificate 발급 |
| Kubelet·kube-proxy·Calico | Service·Pod network | NodePort→Pod 전달 | ALB lifecycle |

AWS Console에서 controller-managed ALB·Target Group·listener·security group을 직접 수정하지
않습니다. Git이나 controller input을 고치지 않은 Console 변경은 drift이며 reconciliation에서
되돌아가거나 controller state와 충돌할 수 있습니다.

## Staging 고정 계약

| 항목 | 현재 계약 | 이유·영향 |
| --- | --- | --- |
| Domain | `stg-api.example.com` | Staging API identity를 production과 분리합니다. |
| ALB scheme | `internet-facing` | Public client가 직접 접근합니다. |
| Listener | HTTP `80`, HTTPS `443` | `80`은 application으로 forward하지 않고 `443`으로 redirect합니다. |
| TLS policy | `ELBSecurityPolicy-TLS13-1-2-Res-2021-06` | Security Hub ELB.17 권장 정책이며 TLS 1.0·1.1을 제외합니다. |
| Certificate | `ap-northeast-2` ACM public certificate | ALB와 같은 region에 있고 Cloudflare CNAME으로 DNS validation합니다. |
| Certificate selection | Ingress TLS/rule host discovery | Certificate ARN을 Git에 고정하지 않습니다. |
| DNS | Cloudflare DNS-only CNAME | 최초 구축에서 edge proxy 계층을 제외합니다. |
| Target type | `instance` | Calico overlay Pod IP 대신 VPC-routable EC2 node를 target으로 사용합니다. |
| Service type | `NodePort`, port auto-assignment | Target Group이 node에 도달하며 고정 NodePort를 예약하지 않습니다. |
| Node provider identity | `aws:///<AZ>/<INSTANCE_ID>` | Self-managed kubeadm에 AWS CCM이 없으므로 LBC bootstrap이 IMDSv2 exact EC2 identity와 Node `spec.providerID`를 검증·수렴시킵니다. |
| Subnet | Existing public `2a`·`2b`, `kubernetes.io/role/elb=1` | LBC가 두 AZ의 public subnet을 명시적으로 발견합니다. |
| SG | LBC auto frontend + shared backend | NodePort를 public CIDR에 직접 열지 않고 controller가 target rule을 제한합니다. |
| Controller | Chart `3.5.0`, replica `1`, autoscaling disabled, HPA absent | 현재 단일 node 규모와 exact artifact 계약이며 replica owner를 하나로 제한합니다. |

## Network와 security boundary

Public subnet은 다음 live 조건을 만족해야 합니다.

- `ap-northeast-2a` public subnet `10.0.0.0/20`
- `ap-northeast-2b` public subnet `10.0.16.0/20`
- 각 subnet route table에 Internet Gateway IPv4 default route
- 각 subnet available IPv4 `8`개 이상
- `kubernetes.io/role/elb=1`

LBC가 frontend SG를 자동 생성하면 listener port의 inbound client traffic과 shared backend
SG→node target traffic을 함께 관리합니다. 다음은 현재 계약이 아닙니다.

- Node SG에 NodePort range를 `0.0.0.0/0`으로 여는 수동 rule
- Ingress custom frontend SG annotation
- Global `enable-manage-backend-security-group-rules` override
- ALB IP address를 node SG source로 고정

### ENI와 node SG 선택

ENI와 Security Group은 별도 AWS resource입니다. ENI가 연결된 Security Group ID 목록을
속성으로 가지므로 EC2 instance의 network 경계는 다음 관계로 구성됩니다.

```text
EC2 instance
└── primary ENI (`eni-*`: IP·subnet과 연결 SG 목록)
    ├── node SG (`sg-*`: node ingress·egress rule)
    └── RDS 자동 연결 SG (`ec2-rds-N`)
```

현재 `instance` target에서 LBC는 target node의 primary ENI를 찾고, 그 ENI에 연결된 SG를
열거한 뒤, 여러 개이면 cluster tag가 붙은 정확히 하나의 SG를 backend rule 변경 대상으로
선택합니다. 따라서 primary ENI는 SG를 찾는 경로이고 cluster tag를 기록하는 대상은 ENI가
아니라 선택할 node SG resource입니다.

Node primary ENI에는 node 기본 SG와 RDS 자동 연결용 `ec2-rds-N` SG가 함께 연결될 수
있습니다. `stg-car-community-k8s-node-sg`에만
`kubernetes.io/cluster/stg-car-community-k8s=shared` tag를 두고, `ec2-rds-N`을 포함한
다른 연결 SG에는 같은 cluster tag를 두지 않습니다. `shared`는 이 SG를 cluster가 독점
소유하지 않는다는 lifecycle marker입니다. AWS 계정 간 공유·public access·rule 허용을
뜻하지 않으며, LBC의 shared backend SG라는 별도 개념과도 다릅니다. 이 node SG는
Kubernetes보다 먼저 수동 생성됐고 SSH·node network 경계도 함께 소유하므로 `owned` 대신
`shared`를 사용합니다. 이 선택 과정에서 RDS 연결 rule과 SG attachment는 변경하지
않습니다.

Frontend SG는 public `80`·`443`을 받으므로 origin 자체는 public입니다. Cloudflare proxy를
나중에 도입해도 ALB ingress가 계속 public이면 Cloudflare를 우회할 수 있습니다. Proxy의
WAF·DDoS·rate limit 효과를 origin까지 강제할 때는 Cloudflare published IP range만 허용하는
custom frontend policy를 별도로 설계합니다.

## Node IAM과 credential 경계

Ingress platform의 확정된 target은 self-managed node가 다음 하나의 instance
profile을 사용하는 구성입니다.

```text
stg-car-community-k8s-node-role
├── exact car-community ECR pull policy
└── AWS Load Balancer Controller 3.5 policy
```

ECR pull과 LBC policy는 별도 customer-managed policy로 유지해 purpose·diff·rollback을
분리합니다. LBC official policy는 exact release와 checksum을 기준으로 가져오고 official
installation guide가 지정한 첫 security-group ingress statement에 exact staging VPC
`vpc-0123456789abcdef0` condition만 추가하고 그 statement의 `Resource: "*"`는
보존합니다. 뒤의 ingress/revoke/delete + resource-tag `Null` statement를 포함한
다른 official statement·resource·condition은 그대로 유지합니다.

Self-managed cluster에는 workload별 AWS identity가 없으므로 controller Pod가 node IMDS
credential을 사용합니다. IMDSv2 token required, hop limit `2`는 container network에서
credential을 소비하기 위한 현재 조건입니다. 같은 node의 다른 Pod가 IMDS에 도달할 수 있는
권한 노출은 staging single-tenant trade-off이며 production Best Practice로 승격하지 않습니다.
EKS·multi-tenant·다중 node로 전환할 때 Pod Identity나 IRSA를 재검토합니다.

기존 ECR-only profile은 다음 세 증거가 모두 생길 때까지 삭제하지 않습니다.

1. 새 instance profile credential effective 확인
2. Private ECR exact digest pull regression 성공
3. Controller reconciliation과 ALB create/delete lifecycle 성공

이 target profile의 live 연결·검증을 아직 실행했다고 이 문서에서 주장하지
않습니다. 기존 ECR-only profile과 새 profile 중 현재 effective 값은
[100 Staging 단일 node 전체 구축 시나리오](../../operations/scenarios/100-staging-single-node-bootstrap.md)의
live checklist가 소유합니다.

## Self-managed Node providerID 경계

LBC의 `instance` target은 Kubernetes Node를 EC2 instance identity와 연결해야 합니다.
현재 cluster는 AWS CCM이 없는 self-managed kubeadm이므로 bootstrap이 다음 exact
계약을 소유합니다.

1. 같은 IMDSv2 token으로 instance ID와 `placement/availability-zone`을 조회합니다.
2. Expected providerID를 `aws:///<AZ>/<INSTANCE_ID>`로 구성합니다.
3. Singleton Node `.spec.providerID`가 비어 있으면 controller 설치 전에만 patch합니다.
4. 이미 exact면 no-op으로 검증하고, 다른 non-empty 값·다중 Node·IMDS/API 실패는
   기존 identity를 덮어쓰지 않고 fail-close합니다.

이 값은 ALB나 controller가 삭제돼도 해당 node의 EC2 identity를 표현하므로
임의로 비우지 않습니다. Instance 교체·node join lifecycle이 생기면 새 EC2 identity와
providerID 수렴 절차를 다중 node 설계와 함께 재검토합니다.

## TLS와 DNS state model

1. ACM public certificate를 `ap-northeast-2`에서 요청합니다.
2. ACM이 제공한 validation CNAME을 Cloudflare에 DNS-only로 만듭니다.
3. Certificate `Issued`를 확인하고 validation CNAME을 유지합니다.
4. Ingress `spec.tls.hosts`와 rule host가 `stg-api.example.com`을 선언합니다.
5. LBC가 host에 맞는 certificate를 탐색해 explicit HTTPS listener에 연결합니다.
6. ALB DNS name이 생긴 뒤 Cloudflare `stg-api` CNAME을 DNS-only로 만듭니다.

Validation CNAME과 service CNAME은 역할이 다릅니다. 앞의 record는 domain ownership과 ACM
renewal, 뒤의 record는 client request routing을 담당합니다. Certificate ARN을 Ingress에
기록하지 않으며 `Issued` 전에는 Ingress를 merge하지 않습니다.

Cloudflare proxy를 켜면 DDoS proxy·WAF·rate limiting·cache·edge analytics를 사용할 수
있지만 Cloudflare TLS mode, header, timeout, caching과 origin restriction이 새 운영
계층이 됩니다. 현재 DNS-only는 단순한 staging baseline이고 web traffic에 대한 Cloudflare의
일반 권고보다 보안·edge 기능이 약합니다.

## Controller와 GitOps ownership

Controller는 verified ephemeral Helm client로 bootstrap합니다.

- Helm `3.21.4` Linux ARM64 SHA-256
  `b54c04b4e0b2540bbdc08c17a121dab70e9a2ed0de5705528fec68a5fd3b85a7`
- LBC chart `3.5.0` SHA-256
  `45051f634b33e10baccb3354d0681b7de787c60445e599fa276e0c9aedd4ccd5`
- LBC image
  `public.ecr.aws/eks/aws-load-balancer-controller:v3.5.0@sha256:1ee3cc54fb62248bd56f2157f7be79baba2be8f0bbf2d926e2650d7ba710ee5f`

Bootstrap은 `clusterName=stg-car-community-k8s`, `region=ap-northeast-2`, exact runtime VPC ID,
`replicaCount=1`, autoscaling disabled를 명시합니다. Existing controller HPA는 fixed replica
계약의 foreign autoscaling owner이므로 자동 인수하지 않고 fail-close합니다.

ProviderID가 exact로 수렴한 다음 chart의 일반 CRD와 Gateway CRD를 fixed field manager
`car-community-load-balancer-bootstrap`으로 server-side apply하고, 다음 required CRD 7개가
각각 `Established`인 뒤에만 Helm release를 적용합니다.

- `albtargetcontrolconfigs.elbv2.k8s.aws`
- `globalaccelerators.aga.k8s.aws`
- `ingressclassparams.elbv2.k8s.aws`
- `targetgroupbindings.elbv2.k8s.aws`
- `listenerruleconfigurations.gateway.k8s.aws`
- `loadbalancerconfigurations.gateway.k8s.aws`
- `targetgroupconfigurations.gateway.k8s.aws`

이 barrier는 CRD discovery가 비동기이고 Helm upgrade가 기존 CRD를 갱신하지 않는 경계를
명시합니다. Argo CD는 controller release를 소유하지 않고 application Ingress만 소유합니다.
AppProject는 existing namespace allowlist에 `networking.k8s.io/Ingress`만 추가합니다.

## 변경·실패·삭제 경계

- LBC 설치 실패는 Ingress를 merge해 우회하지 않습니다. 같은 exact owner/version의
  partial release만 bootstrap script로 재실행합니다.
- Required CRD 7개 중 하나라도 `Established`가 아니면 Helm release로 넘어가지 않습니다.
- Existing controller HPA가 있으면 fixed replica owner를 추측해 삭제하거나 인수하지 않고
  별도 platform migration 결정을 기다립니다.
- ACM discovery 실패는 certificate region·status·host를 고치며 certificate ARN을 임시로
  Git에 넣지 않습니다.
- Target unhealthy는 node SG·NodePort·Service endpoint·Pod readiness 순서로 진단합니다.
- ProviderID가 다른 non-empty 값이면 bootstrap으로 overwrite하지 않고 node·EC2
  identity 어느 쪽이 foreign인지 조사합니다.
- Primary ENI에서 node SG 외 다른 SG에
  `kubernetes.io/cluster/stg-car-community-k8s` tag가 있으면 임의 삭제·덮어쓰지 않고
  foreign owner를 확인합니다.
- DNS는 ALB와 target health가 확인된 뒤 생성합니다.
- 정상 rollback은 Ingress Git revert입니다. ALB를 Console에서 먼저 삭제하지 않습니다.
- 전체 철거는 service DNS CNAME 제거, Ingress Git 제거와 ALB/finalizer 완료, LBC release,
  subnet tag·new profile/policy, certificate·validation CNAME 순서로 각 owner가 수행합니다.

## 현재 선택의 재검토 trigger

- Cloudflare proxy·WAF·rate limit과 origin lock-down 도입
- ALB access log·deletion protection·AWS WAF 운영 요구
- Multi-node·Auto Scaling과 controller replica 수 변경
- AWS VPC CNI·`ip` target 전환
- EKS Pod Identity·IRSA 또는 workload별 IAM
- Multiple host/certificate와 independent IngressClass policy
- Production domain·certificate·DNS 또는 구성 소유권 자동화 요구

이 trigger에서 파생되는 활성 후보의 상태와 도입 순서는
[004 Infrastructure evolution backlog](../../work-items/backlog/2026/004-infrastructure-evolution.md)가
소유합니다.

## 근거와 관련 문서

- **실행 절차**: [370 Staging 외부 HTTPS ingress 최초 구축](../../operations/370-staging-external-https-ingress-bootstrap-runbook.md)
- **현재 live 상태**: [100 Staging 단일 node 전체 구축 시나리오](../../operations/scenarios/100-staging-single-node-bootstrap.md)
- **VPC**: [110 VPC network](110-vpc-network.md)
- **Node와 IAM**: [120 EC2 Kubernetes node](120-ec2-kubernetes-node.md),
  [130 ECR·IAM image 공급 기반](130-ecr-iam-foundation.md)
- **GitOps**: [160 Argo CD platform](160-argocd-platform.md),
  [220 GitOps application delivery](220-gitops-application-delivery.md)
- [AWS Load Balancer Controller certificate discovery](https://kubernetes-sigs.github.io/aws-load-balancer-controller/latest/guide/ingress/cert_discovery/)
- [AWS Load Balancer Controller installation·node IAM trade-off](https://kubernetes-sigs.github.io/aws-load-balancer-controller/latest/deploy/installation/)
- [AWS Load Balancer Controller target type defaults](https://github.com/kubernetes-sigs/aws-load-balancer-controller/blob/v3.5.0/helm/aws-load-balancer-controller/values.yaml)
- [AWS Load Balancer Controller security groups](https://kubernetes-sigs.github.io/aws-load-balancer-controller/latest/deploy/security_groups/)
- [AWS Load Balancer Controller worker node SG selection](https://github.com/kubernetes-sigs/aws-load-balancer-controller/blob/v3.5.0/docs/guide/gateway/gateway.md#worker-node-security-groups-selection)
- [AWS Load Balancer Controller subnet discovery](https://kubernetes-sigs.github.io/aws-load-balancer-controller/latest/deploy/subnet_discovery/)
- [Amazon EC2 instance와 network interface의 Security Group 관계](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/changing-security-group.html)
- [Kubernetes Cloud Controller Manager](https://kubernetes.io/docs/concepts/architecture/cloud-controller/)
- [ACM DNS validation](https://docs.aws.amazon.com/acm/latest/userguide/dns-validation.html)
- [AWS Security Hub ELB controls](https://docs.aws.amazon.com/securityhub/latest/userguide/elb-controls.html)
- [ALB security policies](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/describe-ssl-policies.html)
- [Cloudflare proxy status](https://developers.cloudflare.com/dns/proxy-status/)
