# Staging 외부 HTTPS ingress 최초 구축 runbook

기존 staging kubeadm cluster와 Argo CD application에 AWS Load Balancer Controller를
bootstrap하고 `stg-api.ronams.com`의 internet-facing ALB·HTTPS·DNS 경로를 최초로 만드는
절차입니다.

이 Runbook은 AWS IAM·subnet·ACM, controller 설치, AppProject permission, application
Ingress merge와 Cloudflare DNS를 하나의 의존 순서로 연결합니다. ALB·Target Group·listener·
security group은 AWS Load Balancer Controller가 소유하므로 Console에서 직접 만들지
않습니다. 구조와 선택 이유는
[170 AWS Load Balancer ingress](../project/infrastructure/170-aws-load-balancer-ingress.md)가
소유합니다.

## 실행 계약과 완료 경계

전체 구축의 현재 상태와 재개점은
[100 Staging 단일 node 전체 구축 시나리오](scenarios/100-staging-single-node-bootstrap.md)에서
확인합니다.

| 순서 | Actor·위치 | 행동 | 완료·재개점 |
| --- | --- | --- | --- |
| `1` | AWS 운영자 | Primary ENI의 여러 SG 중 LBC backend rule owner를 cluster tag exact-one으로 선택 | Node SG만 선택됐으면 `2`; 선택 baseline 진단은 생략 가능 |
| `2` | AWS Console 운영자 | 새 node IAM policy·role·profile 생성, EC2 profile과 IMDS 설정 전환 | IMDS에 새 role 이름이 보이면 `3`; 실패하면 old profile rollback |
| `3` | AWS Console 운영자 | 두 existing public subnet route·free IP 검증과 role tag 적용 | 두 AZ subnet만 eligible이면 `4` |
| `4` | AWS ACM·Cloudflare 운영자 | ACM public certificate 요청·validation CNAME 적용 | `Issued` 뒤 `5`; CNAME을 유지 |
| `5` | EC2 운영자 | 전달된 LBC script 확인·실행, Node providerID 수렴 | Exact providerID·controller rollout·label postcondition 통과 뒤 `6` |
| `6` | EC2 운영자 | 새 node role로 existing private ECR exact digest pull regression | Pull·실행·cleanup 성공 뒤 `7` |
| `7` | Local→EC2 운영자 | Tracked AppProject YAML만 one-time server-side apply | Ingress kind 허용 확인 뒤 `8`; GitOps `200` 재실행 금지 |
| `8` | Git 운영자 | Staging Service·Ingress를 정상 GitOps PR로 merge | Argo revision 반영과 ALB hostname 생성 뒤 `9` |
| `9` | EC2·AWS·local terminal 운영자 | Argo CD·Ingress 결과, ALB·target·listener·managed SG와 DNS 전 ALB 직접 응답 확인 | `Synced`·`Healthy`, ALB hostname, AWS 완료 조건과 직접 HTTPS `200`·HTTP `301` 확인 뒤 `10`; 하나라도 실패하면 `10` 전 중단 |
| `10` | Cloudflare 운영자 | `stg-api` DNS-only CNAME 생성 | Public DNS가 ALB로 수렴하면 `11` |
| `11` | 운영자 | HTTPS·redirect·DNS·target health·Argo final verification | 모든 postcondition 성공 시 최초 ingress 완료 |

Repository source나 server-side dry-run만으로 외부 완료를 주장하지 않습니다. 각 live 단계가
실패하면 뒤 단계로 이동하지 않고 해당 절의 recovery boundary에서 재개합니다.

## 고정 입력과 placeholder

| 항목 | Exact 값·source |
| --- | --- |
| AWS account | `355653384583` |
| Region | `ap-northeast-2` |
| VPC | `vpc-0d3d5522a3c04861b` |
| Cluster | `stg-car-community-k8s` |
| EC2 instance | AWS Console의 Name `stg-car-community-k8s-node-01`과 live instance ID를 대조 |
| Node security group | Name `stg-car-community-k8s-node-sg`과 live SG ID를 대조 |
| Node primary ENI SG selection | `stg-car-community-k8s-node-sg`만 `kubernetes.io/cluster/stg-car-community-k8s=shared`; 다른 연결 SG에는 같은 key 없음 |
| Node providerID | IMDSv2 instance ID·AZ로 만든 `aws:///<AZ>/<INSTANCE_ID>` |
| New role/profile | `stg-car-community-k8s-node-role` |
| Existing profile | `car-community-k8s-node-ecr-pull-role`; 교체 직전 current association ARN을 `2.4`에서 확보 |
| ECR repository | `arn:aws:ecr:ap-northeast-2:355653384583:repository/car-community` |
| Host | `stg-api.ronams.com` |
| TLS policy | `ELBSecurityPolicy-TLS13-1-2-Res-2021-06` |

명령의 placeholder는 실행 전에 다음 authoritative source에서 채웁니다.

| Placeholder | Source |
| --- | --- |
| `<KEY_PATH>` | 현재 EC2 SSH private key의 절대 경로 |
| `<EC2_SSH_HOST>` | Current EC2 public DNS 또는 public IPv4 |
| `<PUBLIC_SUBNET_2A_ID>`, `<PUBLIC_SUBNET_2B_ID>` | VPC Console에서 Name·AZ·CIDR·VPC를 모두 대조한 live ID |
| `<OLD_INSTANCE_PROFILE_ARN>` | EC2 instance의 current IAM association 상세 |
| `<ALB_DNS_NAME>` | Ingress status에서 얻고 같은 ALB resource의 유효한 AWS Console·API·CLI live evidence 중 하나로 대조한 hostname |

Subnet ID, ALB name·ARN·DNS와 managed SG ID는 생성 전 추측해 문서나 manifest에 넣지
않습니다.

## 1. Primary ENI SG 필수 확인과 Kubernetes 선택 조회

### 1.1 필수 — Primary ENI의 node SG 하나만 cluster SG로 선택

**실행 위치:** AWS Console의 account `355653384583`, region `ap-northeast-2`

1. `EC2 → Instances → stg-car-community-k8s-node-01`을 엽니다.
2. Instance의 VPC가 `vpc-0d3d5522a3c04861b`인지 확인합니다.
3. `네트워킹 → 네트워크 인터페이스`에서 `디바이스 인덱스`가 `0`인 primary ENI를
   확인합니다.
4. Primary ENI의 연결 SG에 `stg-car-community-k8s-node-sg`가 있는지 확인합니다.
   RDS 자동 연결용 `ec2-rds-N`을 포함한 다른 SG는 분리하거나 rule을 변경하지 않습니다.
5. Primary ENI의 연결 SG 목록에서 각 SG 이름 또는 ID를 열고, 해당 SG resource의 `태그`
   탭에서 `kubernetes.io/cluster/stg-car-community-k8s` key가 있는지 확인합니다.
6. 같은 key가 어느 SG에도 없으면 `stg-car-community-k8s-node-sg → 태그 → 태그 관리`에서
   다음 tag를 추가하고 저장합니다.

   | 키 | 값 |
   | --- | --- |
   | `kubernetes.io/cluster/stg-car-community-k8s` | `shared` |

7. 다시 primary ENI의 모든 연결 SG tag를 확인해 위 key와 `shared` 값을 가진 SG가
   `stg-car-community-k8s-node-sg` 정확히 하나인지 확인합니다.

Primary ENI는 연결 SG를 찾는 경로일 뿐입니다. Tag를 추가하는 대상은 ENI가 아니라
`stg-car-community-k8s-node-sg` resource이며, ENI 자체나 `ec2-rds-N` SG에는 이 tag를
추가하지 않습니다.

**완료 기준:** VPC가 일치하고 primary ENI에 `stg-car-community-k8s-node-sg`가 연결돼
있으며, 연결된 SG 중 이 SG만
`kubernetes.io/cluster/stg-car-community-k8s=shared`를 가집니다. RDS 자동 연결용 SG와
그 rule은 기존 상태를 유지합니다.

**왜 필수인가:** `5`절에서 설치하는 AWS Load Balancer Controller가 ALB에서 node로
들어오는 backend rule을 추가할 SG owner를 하나로 확정해야 하기 때문입니다. LBC는
primary ENI에 여러 SG가 있으면 cluster tag가 붙은 SG 하나를 선택하므로 SG 이름이나
RDS 용도만으로 owner를 추측하지 않습니다. `shared`는 cluster보다 먼저 생성되어 SSH와
node network 경계도 소유하는 node SG의 lifecycle을 보존하는 값이며 public access나
AWS 계정 간 공유를 뜻하지 않습니다. ENI·SG 관계와 선택 원리는
[170 AWS Load Balancer ingress의 ENI와 node SG 선택](../project/infrastructure/170-aws-load-balancer-ingress.md#eni와-node-sg-선택)이
소유합니다.

**중단 조건:** VPC가 다르거나 `stg-car-community-k8s-node-sg`가 primary ENI에 없거나,
같은 cluster tag가 다른 연결 SG에도 있거나, node SG의 기존 tag 값이 `owned` 또는 다른
값이면 `2`절로 진행하지 않습니다. 기존 tag를 임의 삭제·덮어쓰지 않고 해당 SG의 owner를
확인한 뒤 이 절부터 재개합니다.

기존 IAM role 내용과 IMDS 설정은 여기서 다시 확인하지 않습니다. 실제로 값이 필요한
current profile rollback ARN은 `2.4`, subnet ID·route·free IP는 `3`, certificate 상태는
`4`에서 각각 확인합니다.

### 1.2 선택 — Kubernetes baseline을 필요할 때만 조회

**실행 위치:** EC2 terminal

**시작 directory:** 제한 없음

```bash
# 1. Node 수와 Ready 상태 조회
# 확인: Node 1개, STATUS=Ready
printf '\n\n========== [1/6] Node 상태 ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get node -o wide

# 2. Node providerID 조회
# 확인: 빈 값 또는 aws:///<AZ>/<INSTANCE_ID>; 다른 non-empty 값은 충돌
printf '\n\n========== [2/6] Node providerID ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get node \
  -o 'jsonpath={range .items[*]}{.metadata.name}{"\t"}{.spec.providerID}{"\n"}{end}'

# 3. Argo CD Application 상태 조회
# 확인: Synced Healthy
printf '\n\n========== [3/6] Argo CD Application ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get application/car-community-stg -n argocd \
  -o 'jsonpath={.status.sync.status}{"\t"}{.status.health.status}{"\n"}'

# 4. Application Deployment와 Service 조회
# 확인: Deployment READY·AVAILABLE, Service TYPE·PORT·selector
printf '\n\n========== [4/6] Deployment와 Service ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get deployment/car-community service/car-community -n car-community -o wide

# 5. 기존 AWS Load Balancer Controller Deployment 조회
# 확인: 빈 출력이면 미설치; 출력이 있으면 replica 상태
printf '\n\n========== [5/6] 기존 controller Deployment ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get deployment/aws-load-balancer-controller -n kube-system --ignore-not-found

# 6. 기존 controller HPA 조회
# 확인: 빈 출력, 즉 HPA 없음
printf '\n\n========== [6/6] 기존 controller HPA ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get horizontalpodautoscaler/aws-load-balancer-controller \
  -n kube-system --ignore-not-found
```

위 여섯 조회는 현재 상태를 미리 보고 싶을 때만 실행합니다. **모두 읽기 전용이며 전부
생략해도 `2`~`4`절 진행과 runbook 완료를 막지 않습니다.**

실제 변경 직전의 필수 안전 검사는 `5`절 script가 다시 수행하고,
Application·Deployment·Service의 필수 검증은 실제 소비 단계인 `8`~`11`절에서 수행합니다.

#### 공통 명령 형식

- `sudo`는 `/etc/kubernetes/admin.conf`를 읽을 수 있는 관리자 권한으로 뒤의 명령을
  실행합니다.
- `KUBECONFIG=/etc/kubernetes/admin.conf`는 바로 뒤의 `kubectl` 한 번에 사용할 cluster
  접속 정보와 관리자 credential을 지정합니다.
- 줄 끝의 `\`는 명령이 다음 줄에 이어진다는 shell 표기입니다.
- `kubectl get`은 resource를 조회하며 변경하지 않습니다.
- `-n`은 namespace, `-o wide`는 추가 열, `-o jsonpath=...`는 지정한 field만 출력합니다.
- `--ignore-not-found`는 resource가 없을 때 오류 대신 빈 출력을 반환합니다.

조회 이유는 다음과 같습니다.

1. `5`절 script가 지원하는 singleton Ready Node인지 미리 봅니다. 생략해도 script가 설치
   전에 같은 조건을 검사합니다.
2. `5`절의 providerID 처리 경로를 미리 봅니다. Script는 IMDSv2의 실제 값으로 빈
   providerID만 채우고 다른 non-empty 값은 덮어쓰지 않고 중단합니다.
3. Ingress 변경 전 Argo CD baseline을 봅니다. 실제 merge와 최종 상태는 `8`~`11`절에서
   다시 검증합니다.
4. Ingress가 연결될 기존 workload와 Service baseline을 봅니다. ECR pull은 `6`절,
   Service·Ingress·ALB postcondition은 `8`~`11`절에서 확인합니다.
5. 새 설치인지 기존 설치 재사용 후보인지 봅니다. `5`절 script가 기존 항목의 Helm owner,
   chart version, image, replica와 release 일치를 검사해 exact 계약만 재사용합니다.
6. `replicaCount=1` 고정 계약과 충돌할 HPA를 미리 봅니다. 생략해도 `5`절 script가 HPA를
   자동 인수·삭제하지 않고 중단합니다.

Partial retry나 장애 진단에서 existing ACM certificate·ALB·Target Group을 미리 보고
싶다면 AWS Console의 같은 host·cluster/Ingress owner tag를 추가로 확인할 수 있습니다.
이 조회도 선택이며 `2`절 진입 조건이 아닙니다.

## 2. Node IAM role과 instance profile 전환

이 단계는 기존 role을 수정하지 않고 새 role/profile을 병렬로 준비한 뒤 EC2 association만
교체합니다. Old role/profile은 `11`절 완료 전까지 삭제하지 않습니다.

### 2.1 Exact ECR pull policy 생성

**실행 위치:** AWS Console의 account `355653384583`, region `ap-northeast-2`

`IAM → Policies → Create policy → JSON`에서 다음 policy를 만들고 이름을
`stg-car-community-ecr-pull-policy`로 지정합니다.

Console 기본 골격의 `Statement1`은 사용하지 않고, 각 statement의 역할을
구분하도록 `Sid`를 각각 `GetEcrAuthorizationToken`, `PullCarCommunityImage`로
지정합니다.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "GetEcrAuthorizationToken",
      "Effect": "Allow",
      "Action": "ecr:GetAuthorizationToken",
      "Resource": "*"
    },
    {
      "Sid": "PullCarCommunityImage",
      "Effect": "Allow",
      "Action": [
        "ecr:BatchGetImage",
        "ecr:GetDownloadUrlForLayer"
      ],
      "Resource": "arn:aws:ecr:ap-northeast-2:355653384583:repository/car-community"
    }
  ]
}
```

`GetAuthorizationToken`만 AWS API 특성상 `*`이고 image content read는 exact repository에
한정돼야 합니다. `ecr:*`, push·delete action을 추가하지 않습니다.

`Review and create` 화면의 설명과 태그는 **선택사항**이며, 둘 다 비워도 policy
생성과 이 단계 완료를 막지 않습니다. 관리 metadata를 남길 때만 다음 값을
사용합니다.

- Description: `Allows the staging Kubernetes node role to pull images from ECR car-community.`

| Tag key | Tag value |
| --- | --- |
| `project` | `car-community` |
| `environment` | `stg` |
| `managed-by` | `manual` |
| `purpose` | `ecr-image-pull` |

### 2.2 LBC official policy 확보와 VPC scope

#### Local terminal — official policy 확보와 checksum 검증

**실행 위치:** 인터넷에 연결된 macOS local terminal

**시작 directory:** 제한 없음

AWS Console을 열 browser와 같은 macOS의 trusted local terminal에서 다음 block을
한 번에 실행합니다. Official tag의 policy를 임시 file로 받아 checksum을 확인하고,
검증에 성공한 JSON만 macOS clipboard에 복사합니다. Subshell이 종료되면 임시
file은 성공·실패와 관계없이 삭제됩니다.

```bash
(
  set -euo pipefail

  policy_file="$(mktemp)"
  trap '[[ -z "${policy_file:-}" ]] || rm -f -- "${policy_file}"' EXIT

  printf '\n\n========== [1/1] LBC official IAM policy 검증·clipboard 복사 ==========\n\n'

  curl --fail --silent --show-error --location \
    --connect-timeout 15 --max-time 300 \
    --output "${policy_file}" \
    https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/v3.5.0/docs/install/iam_policy.json

  printf '%s  %s\n' \
    '16f232c9d9f79366fe949c4550ad517a202380058a9e48d45a4e215044a20a6a' \
    "${policy_file}" \
  | shasum -a 256 -c -

  pbcopy <"${policy_file}"
  printf '%s\n' 'Verified LBC IAM policy JSON copied to the macOS clipboard.'
)
```

Checksum이 `OK`가 아니거나 clipboard 복사 완료 문구가 없으면 AWS Console에
붙여 넣지 않습니다. 다른 release나 latest policy로 대체하지 않고 이 block부터
재실행합니다.

#### AWS Console — VPC scope condition 적용

**실행 위치:** AWS Console의 account `355653384583`, region `ap-northeast-2`

AWS Console을 열어 둔 같은 macOS browser에서 `IAM → Policies → Create policy →
JSON`으로 이동한 뒤 다음 순서대로 편집합니다.

1. 기본 JSON 전체를 지우고 `⌘V`로 clipboard의 검증된 official policy를 붙여 넣습니다.
2. JSON editor에서 `ec2:AuthorizeSecurityGroupIngress`를 검색합니다. 검증된
   `v3.5.0` 원문에는 이 action을 포함한 statement가 두 개 있습니다.
3. 두 statement 중 action이 아래 두 개뿐이고 `Condition` key가 없는 다음 **전체
   statement**를 찾습니다. `ec2:DeleteSecurityGroup` action까지 있거나 resource-tag
   `Null` condition이 있는 뒤쪽 statement는 대상이 아닙니다.

```json
{
  "Effect": "Allow",
  "Action": [
    "ec2:AuthorizeSecurityGroupIngress",
    "ec2:RevokeSecurityGroupIngress"
  ],
  "Resource": "*"
}
```

4. 위 statement의 여는 `{`부터 닫는 `}`까지 **전체를** 다음 statement로 교체합니다.
   기존 `Resource` 뒤에 일부 JSON을 덧붙이지 않습니다.

```json
{
  "Effect": "Allow",
  "Action": [
    "ec2:AuthorizeSecurityGroupIngress",
    "ec2:RevokeSecurityGroupIngress"
  ],
  "Resource": "*",
  "Condition": {
    "ArnEquals": {
      "ec2:Vpc": "arn:aws:ec2:ap-northeast-2:355653384583:vpc/vpc-0d3d5522a3c04861b"
    }
  }
}
```

5. JSON editor 하단의 `오류`가 `0`인지 확인합니다. 오류가 하나라도 있으면 `다음`으로
   이동하지 말고 `4`번의 전체 statement 교체 결과부터 다시 확인합니다.
6. 검색창의 `1/2` 오른쪽에 있는 아래쪽 화살표를 눌러 검색 결과 `2/2`로 이동합니다.
   `Action` 배열이 아래 세 값과 정확히 일치하는 다음 **전체 statement**가 그대로
   남아 있는지 확인합니다. 이 statement는 official policy 원문 보존 확인 대상이며,
   내용을 추가·삭제·수정하지 않습니다.

```json
{
  "Effect": "Allow",
  "Action": [
    "ec2:AuthorizeSecurityGroupIngress",
    "ec2:RevokeSecurityGroupIngress",
    "ec2:DeleteSecurityGroup"
  ],
  "Resource": "*",
  "Condition": {
    "Null": {
      "aws:ResourceTag/elbv2.k8s.aws/cluster": "false"
    }
  }
}
```

   세 action 중 하나가 없거나 다른 action·condition으로 바뀌어 있으면 `다음`으로
   이동하지 않습니다. `1`번에서 official policy를 다시 붙여 넣고 `4`번의 전체
   statement 교체만 다시 수행합니다.
7. `다음`(`Next`) 또는 `Review and create`로 이동하고 Policy 이름을
   `stg-car-community-aws-load-balancer-controller-policy`로 지정합니다.

추가한 `ec2:Vpc` condition은 ingress rule의 추가·제거 권한을 이 staging VPC로
제한합니다. Official policy의 다른 statement는 변경하지 않습니다.

`Review and create` 화면의 설명과 태그는 **선택사항**이며, 둘 다 비워도 policy
생성과 이 단계 완료를 막지 않습니다. 관리 metadata를 남길 때만 다음 값을
사용합니다.

- Description: `Allows AWS Load Balancer Controller in staging to manage AWS load balancer resources.`

| Tag key | Tag value |
| --- | --- |
| `project` | `car-community` |
| `environment` | `stg` |
| `managed-by` | `manual` |
| `purpose` | `aws-load-balancer-controller` |

생성 뒤 JSON summary에서 다음을 다시 확인합니다.

- Official policy의 모든 statement가 남아 있습니다.
- `Action` 배열이 `ec2:AuthorizeSecurityGroupIngress`와
  `ec2:RevokeSecurityGroupIngress` 두 값만 가진 statement에만 `Resource: "*"`와 exact
  VPC ARN condition이 함께 있습니다.
- `Action` 배열이 `ec2:AuthorizeSecurityGroupIngress`,
  `ec2:RevokeSecurityGroupIngress`, `ec2:DeleteSecurityGroup` 세 값인 statement의
  resource-tag `Null` condition과 다른 official statement는 바뀌지 않았습니다.
- Wildcard `CreateSecurityGroup` 같은 AWS가 resource 생성 전에 요구하는 official permission을
  임의로 제거하지 않았습니다.

### 2.3 New EC2 role/profile 생성

**실행 위치:** AWS Console의 account `355653384583`, region `ap-northeast-2`

AWS Console의 `IAM → Roles → Create role`에서 다음 순서로 생성합니다.

1. `Trusted entity type`은 `AWS service`, `Service or use case`는 `EC2`를 선택하고
   `Next`를 누릅니다. 이 선택은 EC2 instance가 이 role의 임시 AWS credential을 받을 수
   있도록 trust policy의 service principal을 `ec2.amazonaws.com`으로 만듭니다.
2. `Permissions policies`에서 다음 두 customer-managed policy를 모두 선택하고 `Next`를
   누릅니다.
   - `stg-car-community-ecr-pull-policy`: 새 profile로 교체한 뒤에도 kubelet이 private
     `car-community` image를 pull할 수 있게 기존 ECR read 권한을 보존합니다.
   - `stg-car-community-aws-load-balancer-controller-policy`: Controller가 ALB, Target
     Group과 관련 security group 같은 AWS resource를 생성·조회·변경·삭제할 수 있게
     합니다.
3. `Name, review, and create` 화면에서 다음 **필수 입력·확인값**을 사용합니다.

| 항목 | 필수값·확인값 | 틀리거나 빠졌을 때의 결과 |
| --- | --- | --- |
| Role name | `stg-car-community-k8s-node-role` | 다음 절이 이 exact 이름의 profile을 선택합니다. 생성 뒤 role 이름은 바꿀 수 없으므로 한 글자라도 다르면 생성하지 않습니다. |
| Trusted entity | `AWS service` / `EC2` | Trust policy가 EC2를 허용하지 않으면 instance가 role credential을 받을 수 없습니다. |
| Attached policies | 위의 ECR pull policy와 LBC policy 두 개 | ECR policy가 없으면 기존 private image pull이 깨지고, LBC policy가 없으면 Controller가 ALB lifecycle을 수행할 수 없습니다. |

현재 이 role의 trust policy와 두 permission policy는 `Description`이나 role tag를 조건으로
참조하지 않습니다. 따라서 `Description`과 `Tags`는 **선택사항**이며, 둘 다 비워도 이
단계와 다음 절의 profile 연결을 진행할 수 있습니다. 운영자가 Console에서 역할 목적을
쉽게 구분할 metadata를 남길 때만 다음 값을 사용합니다.

- Description: `Allows the staging Kubernetes EC2 node to pull car-community images and manage AWS load balancer resources.`

| Tag key | Tag value |
| --- | --- |
| `project` | `car-community` |
| `environment` | `stg` |
| `managed-by` | `manual` |

현재 Console이 자동으로 채운 일반 설명 `Allows EC2 instances to call AWS services on your
behalf.`를 그대로 두어도 권한 동작은 같습니다. 위 project-specific description은 두
policy의 목적을 화면에서 바로 식별하기 위한 선택 metadata입니다.

생성 직전에 trust policy의 `Principal.Service`가 `ec2.amazonaws.com` 하나이고, 선택한
policy가 위 두 개뿐인지 다시 확인합니다. 이 role은 기존 ECR-only role을 수정하거나
삭제하는 대신 새로 만듭니다. EC2에는 한 번에 하나의 instance profile만 연결하므로 새
role 하나에 ECR pull과 LBC policy를 함께 연결하고, 기존 profile은 다음 절의 전환·검증이
끝날 때까지 rollback 대상으로 보존합니다.

`Create role`을 누르면 IAM Console이 role과 같은 이름의 instance profile
`stg-car-community-k8s-node-role`을 자동으로 만듭니다. 다음 `2.4`절에서 EC2에 실제로
연결하는 대상은 이 instance profile입니다. 새 profile ARN은 이후 입력이나 rollback에
사용하지 않으므로 이 단계에서 별도로 기록하지 않습니다. 생성 뒤 role의 이름·trust·두
policy 중 하나라도 다르면 `2.4`절로 넘어가지 않습니다.

### 2.4 EC2 association과 IMDS 적용

#### AWS Console — profile association과 IMDS 설정

**실행 위치:** AWS Console의 account `355653384583`, region `ap-northeast-2`

Association을 바꾸기 직전에
`EC2 → Instances → stg-car-community-k8s-node-01 → Security → IAM role`에서 current profile이
`car-community-k8s-node-ecr-pull-role`인지 확인하고 association 상세의 exact ARN을
`<OLD_INSTANCE_PROFILE_ARN>`으로 기록합니다. 이 값은 실패 시 rollback 대상이므로
필수입니다. 다른 profile이 연결돼 있으면 이름만 보고 교체하지 않고 owner를 확인할 때까지
중단합니다.

`EC2 → Instances → stg-car-community-k8s-node-01 → Actions → Security → Modify IAM role`에서
새 `stg-car-community-k8s-node-role`을 선택합니다. 이어
`Actions → Instance settings → Modify instance metadata options`에서 다음을 사용합니다.

- IMDS endpoint: enabled
- IMDSv2: required
- PUT response hop limit: `2`

#### EC2에 SSH 접속 — 실제 role credential 경로 확인

**실행 위치:** macOS local terminal과 SSH로 접속한 EC2 terminal

**시작 directory:** Local과 EC2 모두 제한 없음

AWS Console을 닫지 않은 상태에서 macOS local terminal을 열고, 기존에 이 node에 접속할 때
사용하던 SSH private key와 현재 public IP 또는 public DNS로 EC2 Linux shell에 접속합니다.

```bash
ssh -i "<KEY_PATH>" "ubuntu@<EC2_SSH_HOST>"
```

접속 뒤 prompt가 `ubuntu@ip-10-0-8-159:~$`처럼 바뀐 명령창이 이 runbook에서 말하는
**EC2 terminal**입니다.

**실행 위치:** 위 SSH session의 EC2 terminal

`169.254.169.254`는 이 instance의 public·private IP나 internet의 AWS API 주소가 아닙니다.
AWS가 각 EC2 instance 내부에 제공하는 **Instance Metadata Service(IMDS)의 IPv4 link-local
주소**입니다. Link-local은 요청이 현재 EC2가 연결된 local network 경계 안에서만 유효하다는
뜻이며, Mac에서 이 주소를 호출해도 이 EC2의 metadata를 조회할 수 없습니다.

이 단계의 요청·응답 구조는 다음과 같습니다.

```text
운영자의 Mac terminal
└─ SSH → stg-car-community-k8s-node-01의 Linux shell
   ├─ PUT 169.254.169.254/latest/api/token
   │  └─ 현재 EC2의 IMDSv2가 60초 동안 유효한 session token 반환
   └─ GET 169.254.169.254/latest/meta-data/iam/security-credentials/
      + 위 token header
      └─ 현재 EC2가 실제 credential 제공 대상으로 노출하는 IAM role 이름 반환
```

AWS Console의 IAM role 표시는 EC2 association의 **설정 상태**입니다. 반면 아래 IMDS 조회는
EC2 안에서 실행될 Controller와 AWS SDK가 실제로 접근하는 **현재 credential 제공 경로**가
새 role로 바뀌었는지 확인합니다. 목록 URL 끝에 role 이름을 붙이면 temporary access key,
secret key와 session token 원문이 반환되므로, 이 runbook은 목록 URL까지만 조회해 role
이름만 확인하고 credential 원문은 출력하지 않습니다.

```bash
# 실행 위치: SSH로 접속한 stg-car-community-k8s-node-01의 EC2 terminal
# 확인: 현재 IMDS credential role 이름이 stg-car-community-k8s-node-role
imds_token="$(curl --fail --silent --show-error \
  --request PUT \
  --header 'X-aws-ec2-metadata-token-ttl-seconds: 60' \
  --noproxy '*' \
  --connect-timeout 2 \
  --max-time 5 \
  http://169.254.169.254/latest/api/token)"

printf 'X-aws-ec2-metadata-token: %s\n' "${imds_token}" \
| curl --fail --silent --show-error \
    --noproxy '*' \
    --header @- \
    --connect-timeout 2 \
    --max-time 5 \
    http://169.254.169.254/latest/meta-data/iam/security-credentials/

unset imds_token
```

첫 `PUT`은 IMDSv2 요청에 필요한 수명이 60초인 session token을 shell 변수
`imds_token`에만 저장합니다. 두 번째 `GET`은 그 token을 header로 보내 현재 instance의
effective role 이름 목록을 받습니다. `--noproxy '*'`는 link-local IMDS 요청이 proxy로
나가지 않게 하고, token header는 stdin으로 전달해 process argument에 token을 노출하지
않습니다. 마지막 `unset`은 확인이 끝난 token 변수를 현재 shell에서 제거합니다.

출력이 exact `stg-car-community-k8s-node-role`이어야 합니다. Role이 보이지 않거나 old role이
계속 보이면 뒤 단계로 가지 않습니다. Association propagation을 기다린 뒤 다시 확인하고,
계속 실패하면 AWS Console에서 `<OLD_INSTANCE_PROFILE_ARN>`으로 되돌립니다.

이 검증이 필요한 구조적 이유는
[170 AWS Load Balancer ingress의 Node IAM과 credential 경계](../project/infrastructure/170-aws-load-balancer-ingress.md#node-iam과-credential-경계)가
소유합니다. `169.254.169.254`의 범위와 IMDSv2 token 요청은
[AWS의 instance metadata 접근 문서](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/instancedata-data-retrieval.html),
role 이름과 temporary credential 경로는
[AWS의 instance metadata credential 문서](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/instance-metadata-security-credentials.html)를
기준으로 합니다.

## 3. Public subnet discovery 준비

**실행 위치:** AWS Console의 account `355653384583`, region `ap-northeast-2`

VPC Console에서 VPC·AZ·CIDR을 대조해 public subnet 두 개의 exact ID를 선택합니다.

| 대상 | AZ | CIDR | 추가 tag |
| --- | --- | --- | --- |
| `<PUBLIC_SUBNET_2A_ID>` | `ap-northeast-2a` | `10.0.0.0/20` | `kubernetes.io/role/elb=1` |
| `<PUBLIC_SUBNET_2B_ID>` | `ap-northeast-2b` | `10.0.16.0/20` | `kubernetes.io/role/elb=1` |

각 subnet에서 VPC ID, associated route table의 Internet Gateway default route와 available
IPv4 `8`개 이상을 다시 확인한 뒤 tag를 추가합니다. Private subnet
`10.0.128.0/20`·`10.0.144.0/20`에는 public ELB role tag를 넣지 않습니다. Existing cluster
tag가 다른 cluster 이름을 가리키면 LBC filtering과 충돌할 수 있으므로 임의로 덮어쓰지
않고 중단합니다.

## 4. ACM certificate와 Cloudflare validation

### 4.1 ACM public certificate 요청

**실행 위치:** AWS Console의 account `355653384583`, region `ap-northeast-2`

#### 기존 요청 확인과 재개 위치 선택

`Certificate Manager → 인증서(Certificates)`에서 domain이 `stg-api.ronams.com`인 certificate를
검색합니다. 이 조회는 일반 inventory가 아니라 같은 domain의 certificate를 중복 요청하지
않고 현재 상태에서 재개하기 위한 필수 분기입니다.

| Console에서 확인한 상태 | 수행할 작업 | 다음 절 |
| --- | --- | --- |
| Exact domain certificate가 없음 | 아래 신규 요청을 수행 | `4.2` |
| Exact domain certificate가 `Pending validation` | 새로 요청하지 않고 해당 certificate 상세의 CNAME name·value를 복사 | `4.2` |
| Exact domain certificate가 `Issued` | 새로 요청하지 않고 기존 validation CNAME을 유지 | `5` |
| Exact domain certificate가 `Failed` 또는 `Validation timed out` | 실패한 요청을 그대로 재사용하지 않고 DNS validation 실패 원인을 확인한 뒤 해당 요청을 삭제하고 신규 요청 | `4.2` |

검색 결과에 wildcard `*.ronams.com`, root `ronams.com` 또는 다른 domain certificate만 있으면
현재 exact domain certificate가 있는 것으로 계산하지 않습니다. Exact domain certificate가
있지만 tags가 `project=car-community`, `environment=stg`, `managed-by=manual`과 다르면 새로
만들거나 기존 것을 인수하지 않고 owner를 확인할 때까지 중단합니다.

#### Exact domain certificate가 없을 때 신규 요청

`Certificate Manager → 인증서 요청(Request a certificate) → 퍼블릭 인증서 요청
(Request a public certificate)`에서 다음을 선택합니다.

| Console 항목 | 선택값 |
| --- | --- |
| 완전히 정규화된 도메인 이름(Fully qualified domain name) | `stg-api.ronams.com` |
| 내보내기 허용(Allow export) | **내보내기 비활성화(Disable export)** |
| 검증 방법(Validation method) | **DNS 검증(DNS validation)** |
| 키 알고리즘(Key algorithm) | **RSA 2048** |

**내보내기 비활성화(Disable export)**는 certificate의 private key를 AWS 밖으로 내보내지
못하게 하고 Elastic Load Balancing 같은 통합 AWS service에서만 사용하게 합니다. 현재
certificate의 유일한 소비자는 같은 region의 ALB HTTPS listener이므로 내보내기가 필요하지
않습니다. **내보내기 활성화(Enable export)**는 외부 server·application에 certificate와
private key를 직접 배포해야 할 때만 사용하며, exportable certificate 발급·갱신 비용과
private key 보관·배포 책임이 추가되므로 이 runbook에서는 선택하지 않습니다.

`Add another name to this certificate`는 누르지 않으며 wildcard나 root domain을 추가하지
않습니다. Tags에는 다음 세 항목을 추가합니다.

| Key | Value |
| --- | --- |
| `project` | `car-community` |
| `environment` | `stg` |
| `managed-by` | `manual` |

`Request`를 누른 뒤 certificate status가 `Pending validation`인지 확인하고, certificate
상세의 domain validation에서 ACM이 제공한 exact CNAME name과 value를 복사합니다. 이 두
값은 다음 `4.2`에서 Cloudflare DNS record를 만드는 입력입니다.

### 4.2 Cloudflare validation record

**실행 위치:** Cloudflare Console의 `ronams.com` zone

Cloudflare `ronams.com → DNS → Records`에서 ACM이 제공한 CNAME을 그대로 생성합니다.

- Type: `CNAME`
- Name: ACM validation record name
- Target: ACM validation record value
- Proxy status: DNS only
- TTL: Auto

Cloudflare UI가 zone suffix를 자동으로 붙이는지 preview를 확인해
`ronams.com.ronams.com`처럼 중복되지 않게 합니다.

#### ACM Console — certificate 발급 상태 확인

**실행 위치:** AWS Console의 account `355653384583`, region `ap-northeast-2`

ACM Console에서 certificate status가 `Issued`가 될 때까지 기다립니다. Validation CNAME은 발급 뒤에도 자동 renewal을 위해
삭제하지 않습니다. `Issued` 전에 Ingress를 merge하지 않습니다.

## 5. AWS Load Balancer Controller bootstrap

### 5.1 전달된 Script 확인

**실행 위치:** EC2 terminal

**시작 directory:** 제한 없음

EC2에서 canonical script의 실행 가능 여부를 확인합니다.

```bash
test -x \
  /home/ubuntu/car-community-scripts/bootstrap/load-balancer/100-install-aws-load-balancer-controller.sh
```

이 file은 앞선
[150 EC2 실행 artifact 전달](150-ec2-execution-artifact-transfer-runbook.md)이
current archive inventory에 포함해 canonical 경로에 전달합니다. 여기서 같은 script를 다시
개별 전송하지 않습니다.

없거나 실행 mode가 다르면 이 절에서 개별 `scp`로 보정하지 않고 `150`의 archive 전달을
현재 inventory로 다시 완료한 뒤 돌아옵니다.

### 5.2 Script 실행

**실행 위치:** EC2 terminal

**시작 directory:** 제한 없음

EC2에서 exact VPC ID를 인자로 실행합니다.

```bash
sudo /home/ubuntu/car-community-scripts/bootstrap/load-balancer/100-install-aws-load-balancer-controller.sh \
  vpc-0d3d5522a3c04861b
```

Script는 먼저 같은 IMDSv2 token으로 instance ID와 placement AZ를 조회해
expected `aws:///<AZ>/<INSTANCE_ID>`를 만듭니다. Singleton Node
`.spec.providerID`가 비어 있으면 controller 설치 전 patch하고, exact면 no-op으로
검증합니다. 다른 non-empty providerID, 다중 Node, IMDS·API 조회 실패는 덮어쓰지
않고 중단합니다. Self-managed kubeadm에 AWS CCM이 없기 때문에 `instance` target이
EC2 identity를 안전하게 소비하기 위한 prerequisite입니다.

이후 verified ephemeral Helm `3.21.4` client, chart `3.5.0`, exact image digest를
사용합니다. Chart의 일반·Gateway CRD를 fixed field manager
`car-community-load-balancer-bootstrap`으로 server-side apply한 뒤
required CRD 7개 각각의 `Established`를 기다리고, 모두 성공한 경우에만 Helm release를
적용합니다. Region·VPC·cluster name은 explicit value로 설치하며 `replicaCount=1`,
autoscaling disabled와 HPA absent를 검증합니다. Controller rollout과 전체 postcondition이
성공한 다음에만 single node의
`node.kubernetes.io/exclude-from-external-load-balancers` label을 마지막에 제거합니다.

Exit `0`과 `[완료] AWS Load Balancer Controller 3.5.0 bootstrap을 확인했습니다.` 메시지가
이 절의 필수 완료 조건입니다. Script가 rollout, 고정 설치값, required CRD, HPA 부재,
providerID와 external load balancer 제외 label을 직접 확인하고 하나라도 다르면 실패합니다.

#### 선택 진단: 설치값 다시 조회

아래 다섯 조회는 script 성공 뒤 사람이 현재 값을 다시 펼쳐 보고 싶을 때만 실행합니다.
실행하지 않아도 `6`절로 진행할 수 있으며 이 선택 진단의 미실행은 구축 실패가 아닙니다.

```bash
# 1. Controller rollout 대기
# 확인: deployment successfully rolled out
printf '\n\n========== [1/5] Controller rollout ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl rollout status deployment/aws-load-balancer-controller \
  -n kube-system --timeout=5m

# 2. Chart·replica·image 조회
# 확인: chart 3.5.0, replica 1, exact digest image
printf '\n\n========== [2/5] Controller 설치값 ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get deployment/aws-load-balancer-controller -n kube-system \
  -o 'jsonpath={.metadata.labels.helm\.sh/chart}{"\t"}{.spec.replicas}{"\t"}{.spec.template.spec.containers[0].image}{"\n"}'

# 3. Required CRD 상태 조회
# 확인: 7개 CRD 모두 ESTABLISHED=True
printf '\n\n========== [3/5] Required CRD ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get \
  customresourcedefinition/albtargetcontrolconfigs.elbv2.k8s.aws \
  customresourcedefinition/globalaccelerators.aga.k8s.aws \
  customresourcedefinition/ingressclassparams.elbv2.k8s.aws \
  customresourcedefinition/targetgroupbindings.elbv2.k8s.aws \
  customresourcedefinition/listenerruleconfigurations.gateway.k8s.aws \
  customresourcedefinition/loadbalancerconfigurations.gateway.k8s.aws \
  customresourcedefinition/targetgroupconfigurations.gateway.k8s.aws \
  -o 'custom-columns=NAME:.metadata.name,ESTABLISHED:.status.conditions[?(@.type=="Established")].status'

# 4. Controller HPA 부재 조회
# 확인: 빈 출력
printf '\n\n========== [4/5] Controller HPA 부재 ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get horizontalpodautoscaler/aws-load-balancer-controller \
  -n kube-system --ignore-not-found

# 5. Node providerID와 exclude label 조회
# 확인: exact providerID이고 label 값은 비어 있음
printf '\n\n========== [5/5] Node providerID와 label ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get node \
  -o 'jsonpath={range .items[*]}{.metadata.name}{"\t"}{.spec.providerID}{"\t"}{.metadata.labels.node\.kubernetes\.io/exclude-from-external-load-balancers}{"\n"}{end}'
```

1. Rollout은 controller Pod가 Ready인지 확인합니다.
2. 설치값 조회는 고정 chart·replica·image 계약을 확인합니다.
3. CRD 조회는 controller가 사용할 7개 API가 모두 Established인지 확인합니다.
4. HPA 조회는 singleton 계약을 바꾸는 autoscaler가 없는지 확인합니다.
5. Node 조회는 AWS target 식별자와 load balancer 제외 label 해제를 확인합니다.

선택 진단을 실행했다면 chart `aws-load-balancer-controller-3.5.0`, replica `1`, exact
digest image가 보여야 합니다. Required CRD 7개는 모두 `ESTABLISHED=True`이고 HPA 조회는
아무 resource도 출력하지 않아야 합니다. Node providerID는 현재 AZ·instance ID의
`aws:///<AZ>/<INSTANCE_ID>`와 exact이고 마지막 label value는 비어 있어야 합니다.

## 6. Private ECR pull regression

**실행 위치:** EC2 terminal

**시작 directory:** 제한 없음

새 node profile로 기존 runtime image를 계속 pull할 수 있어야 합니다. EC2에서 current
Deployment의 exact image reference를 읽고 기존 smoke script에 전달합니다.

```bash
image_reference="$(sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get deployment/car-community -n car-community \
  -o 'jsonpath={.spec.template.spec.containers[0].image}')"

sudo /home/ubuntu/car-community-scripts/verify/ecr/verify-private-image-pull.sh "${image_reference}"
unset image_reference
```

Script exit `0`, private image pull·ARM64 실행·exact digest 확인과 temporary namespace cleanup을
모두 확인합니다. 실패하면 LBC permission을 더 추가하지 않고 ECR policy, new profile
association과 kubelet credential provider를 진단합니다. 필요하면 old profile로 rollback한
뒤 ECR 경로부터 복구합니다.

## 7. AppProject Ingress permission one-time apply

### 7.1 Local source permission contract 확인

**실행 위치:** local terminal

**시작 directory:** `scripts`가 바로 아래에 보이는 local repository root

Tracked manifest가 `networking.k8s.io/Ingress`를 허용하고 Secret이나 namespaced
group·kind wildcard를 허용하지 않는지 contract test로 확인합니다.

```bash
printf '\n\n========== [1/1] AppProject permission contract test ==========\n\n'
scripts/bootstrap/gitops/tests/test-200-connect-private-gitops-repository.sh
```

Exit `0`과
`PASS: 200-connect-private-gitops-repository.sh contract tests`를 확인합니다. 실패하면
AppProject manifest를 EC2에 전달하거나 apply하지 않고 test가 출력한 contract 위반부터
수정합니다.

현재 checkout에 아직 commit하지 않은 AppProject 관련 변경이 있을 때만 다음 diff를
**선택 확인**으로 사용합니다. Clean checkout에서 출력이 비어 있는 것은 정상이며 이 diff는
위 contract test를 대신하지 않습니다.

```bash
git diff HEAD -- \
  scripts/bootstrap/gitops/manifests/car-community-appproject.yaml \
  scripts/bootstrap/gitops/tests/test-200-connect-private-gitops-repository.sh
```

### 7.2 EC2 전달 manifest 확인

**실행 위치:** EC2 terminal

**시작 directory:** 제한 없음

Manifest는 `150` archive가 이미 canonical 경로에 전달하므로 여기서 다시 개별 전송하지
않고 전달된 file을 확인합니다.

```bash
test -r \
  /home/ubuntu/car-community-scripts/bootstrap/gitops/manifests/car-community-appproject.yaml
```

없거나 current source와 다르면 이 절에서 `/tmp`로 보정하지 않고 `150`의 archive 전달을
현재 inventory로 다시 완료한 뒤 돌아옵니다.

### 7.3 AppProject 적용과 허용 범위 확인

**실행 위치:** EC2 terminal

**시작 directory:** 제한 없음

Tracked YAML을 existing bootstrap field manager로 한 번 apply합니다.

```bash
# 1. Tracked AppProject manifest 적용
# 확인: appproject.argoproj.io/car-community serverside-applied
printf '\n\n========== [1/2] AppProject 적용 ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl apply --server-side \
  --field-manager=car-community-bootstrap \
  -f /home/ubuntu/car-community-scripts/bootstrap/gitops/manifests/car-community-appproject.yaml

# 2. Namespace resource 허용 목록 조회
# 확인: networking.k8s.io/Ingress 포함, Secret·wildcard 없음
printf '\n\n========== [2/2] AppProject 허용 목록 ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get appproject/car-community -n argocd \
  -o 'jsonpath={range .spec.namespaceResourceWhitelist[*]}{.group}{"/"}{.kind}{"\n"}{end}'
```

1. Apply는 tracked manifest의 제한된 Ingress permission을 기존 field manager로 반영합니다.
2. 조회는 허용 범위가 Ingress 외 resource로 넓어지지 않았는지 확인합니다.

Output에 `networking.k8s.io/Ingress`가 있고 Secret이나 cluster-scoped wildcard가 없어야
합니다. Repository Secret과 Application을 이미 연결했으므로 GitOps
`200-connect-private-gitops-repository.sh`를 다시 실행하지 않습니다.

## 8. Staging Service·Ingress GitOps merge

### 8.1 Merge 전 검증

**실행 위치:** local terminal

**시작 directory:** `deploy`가 바로 아래에 보이는 local repository root

Certificate `Issued`, controller Ready, ECR regression과 AppProject permission이 모두
성공한 뒤 `deploy`가 바로 아래에 보이는 local repository root에서 실행합니다.

```bash
# 1. Staging overlay local render
# 확인: exit 0이며 오류 출력 없음
printf '\n\n========== [1/2] Staging overlay render ==========\n\n'
kubectl kustomize deploy/overlays/stg >/dev/null

# 2. Target API server-side dry-run
# 확인: 모든 resource가 configured (server dry run)이며 오류 없음
printf '\n\n========== [2/2] Target server dry-run ==========\n\n'
kubectl kustomize deploy/overlays/stg \
| env -i HOME="${HOME}" PATH="${PATH}" \
    ssh -i "<KEY_PATH>" "ubuntu@<EC2_SSH_HOST>" \
    'sudo -n KUBECONFIG=/etc/kubernetes/admin.conf \
      kubectl apply --dry-run=server -f -'
```

1. Local render는 merge할 overlay의 YAML 구성을 확인합니다.
2. Server-side dry-run은 같은 render가 target API schema·admission을 통과하는지 확인합니다.

Render에는 다음 계약이 있어야 합니다.

- Service `type: NodePort`, 고정 `nodePort` 없음
- Ingress class `alb`, scheme `internet-facing`, target type `instance`
- Listener `80`·`443`, redirect `443`
- Host와 TLS host `stg-api.ronams.com`
- Certificate ARN annotation 없음
- TLS policy `ELBSecurityPolicy-TLS13-1-2-Res-2021-06`
- Custom security group과 subnet ID annotation 없음

### 8.2 Normal GitOps approval

**실행 위치:** GitHub Web UI의 `ckn1070/car-community` repository

Manifest 변경을 별도 deployment PR로 review·merge합니다. Source image build 변경과 섞지
않고, repository의 현재 PR 운영 정책과
[420 GitOps 배포·rollback](420-gitops-deployment-and-rollback-runbook.md)을 사용합니다.
Live Service나 Ingress를 `kubectl apply`로 직접 만들지 않습니다.

## 9. ALB reconciliation과 DNS 전 검증

### 9.1 필수: 현재 reconciliation 결과와 다음 단계 입력

**실행 위치:** EC2 terminal

**시작 directory:** 제한 없음

EC2에서 다음 두 결과를 한 번 확인합니다. 같은 Ingress의 현재 결과가 이미 이 실행
scenario에 기록돼 있다면 다시 조회하지 않고 그 증거를 재사용합니다.

```bash
# 1. Argo CD Application 현재 상태 조회
# 확인: Synced Healthy
printf '\n\n========== [1/2] Application 상태 ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get application/car-community-stg -n argocd \
  -o 'jsonpath={.status.sync.status}{"\t"}{.status.health.status}{"\n"}'

# 2. Ingress ALB hostname 조회
# 확인: AWS가 발급한 ALB DNS 이름 한 줄
printf '\n\n========== [2/2] Ingress ALB hostname ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get ingress/car-community -n car-community \
  -o 'jsonpath={.status.loadBalancer.ingress[0].hostname}{"\n"}'
```

Application 상태는 Git desired state가 현재 cluster에 수렴했는지 판정하는 필수
postcondition입니다. Hostname은 뒤의 AWS resource 선택, ALB 직접 요청과 Cloudflare CNAME
target이 실제로 소비하는 필수 입력이므로 `<ALB_DNS_NAME>`으로 기록합니다. `Synced`·
`Healthy`가 아니거나 hostname이 비어 있으면 `9.2` 이후로 진행하지 않습니다.

#### 선택 진단: reconciliation 대기·event 조회

아직 `Synced`·`Healthy`가 아니어서 상태 변화를 계속 보고 싶을 때만 watch를 사용합니다.

```bash
# Argo CD Application reconciliation watch
# 확인: Synced·Healthy가 출력되면 Ctrl+C로 watch를 직접 종료
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get application/car-community-stg -n argocd -w
```

`-w`는 Application 변경을 계속 관찰하므로 `Synced`·`Healthy`가 출력돼도 자동 종료하지
않습니다. 두 상태를 확인한 운영자가 `Ctrl+C`로 직접 종료합니다. 이미 `9.1`의 두 필수
결과를 확인했다면 이 watch를 실행하지 않습니다.

Application이 수렴하지 않거나 hostname이 비어 있을 때만 다음 진단 조회를 사용합니다.

```bash
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl describe ingress/car-community -n car-community
```

이 조회는 Ingress event에서 중단 원인을 찾기 위한 선택 진단입니다. 정상 결과에서
생략해도 다음 단계를 막지 않습니다.

### 9.2 필수: AWS resource 완료 조건

**실행 위치:** AWS Console 또는 AWS API·CLI를 실행할 terminal

**시작 directory:** AWS Console은 해당 없음, API·CLI terminal은 제한 없음

**기본 확인 경로:** AWS Console의 account `355653384583`, region `ap-northeast-2`

**필수 입력:** `9.1`의 `[2/2] Ingress ALB hostname`이 출력한 `<ALB_DNS_NAME>` 한 줄

필수인 것은 아래에 열거한 live 완료 결과이며 AWS Console 사용 자체가 아닙니다. 같은 결과를
read-only API·CLI로 확인해도 됩니다. Console·API·CLI 중 한 수단으로 확인한 유효한 결과가
현재 scenario에 이미 기록돼 있다면 다시 확인하지 않습니다. 기록이 없을 때에는 다음 Console
경로를 사용하거나 같은 resource와 field를 반환하는 read-only API·CLI를 사용합니다.

1. `EC2 → Load Balancers`에서 **DNS name**이 `<ALB_DNS_NAME>`과 정확히 같은 ALB를
   선택합니다. 생성된 Load Balancer 이름이나 ARN을 입력으로 추측하지 않습니다.
2. 선택한 ALB의 **Details**와 **Network mapping**에서 다음을 확인합니다.
   - Scheme: `Internet-facing`
   - VPC: `vpc-0d3d5522a3c04861b`
   - Availability Zones: `ap-northeast-2a`와 `ap-northeast-2b`의 기존 public subnet 두 개
3. **Listeners and rules**에서 다음 두 listener와 rule을 확인합니다.
   - `HTTP:80`: HTTPS `443`으로 redirect, status code `HTTP_301`
   - `HTTPS:443`: host `stg-api.ronams.com`, path `/*` rule이 Target Group으로 forward
   - `HTTPS:443 → Security`: policy
     `ELBSecurityPolicy-TLS13-1-2-Res-2021-06`, certificate `stg-api.ronams.com`
4. `HTTPS:443`의 forward rule에 표시된 Target Group link를 엽니다. 생성된 Target Group
   이름과 port는 이 link에서 발견하는 동적 값이며 문서의 고정 입력이 아닙니다. 다음을
   확인하고 현재 target port를 `<TARGET_NODE_PORT>`로 기록합니다.
   - Target type: `Instance`
   - Protocol: `HTTP`
   - Registered target: 현재 EC2 instance 한 개, health status `Healthy`
   - Target Group의 port와 registered target의 port가 같은 `<TARGET_NODE_PORT>`
5. ALB의 **Security**에서 연결된 두 Security Group을 확인합니다.
   - 설명이 `[k8s] Managed SecurityGroup for LoadBalancer`인 frontend SG
   - 설명이 `[k8s] Shared Backend SecurityGroup`인 shared backend SG
   frontend SG의 inbound는 IPv4 `0.0.0.0/0`에서 TCP `80`·`443`을 받는 listener rule 두
   개이며, NodePort public rule을 두지 않습니다. Shared backend SG ID를
   `<SHARED_BACKEND_SG_ID>`로 기록합니다.
6. `EC2 → Instances → stg-car-community-k8s-node-01 → Security`에서
   `stg-car-community-k8s-node-sg`를 엽니다. Inbound rule에 TCP
   `<TARGET_NODE_PORT>`가 하나 있고 source가 `<SHARED_BACKEND_SG_ID>`인지 확인합니다.
   같은 NodePort에 `0.0.0.0/0` source가 있으면 실패입니다. SSH `22`의 승인된 운영자
   `/32` rule은 이 NodePort 판정 대상이 아닙니다.

이 추적에서 shared backend SG는 ALB에 연결된 source identity이고, 실제 target port
inbound rule은 node SG에 있습니다. ENI와 SG ID, 생성된 ALB·Target Group 이름과
`<TARGET_NODE_PORT>`는 controller가 만든 현재 resource에서 발견하며 runbook에 하드코딩하지
않습니다.

이 항목은 runbook 전체 완료를 주장하기 위한 필수 검증입니다. 같은 resource의 유효한 결과를
scenario에 기록한 뒤에는 단계마다 반복하지 않습니다. 하나라도 다르거나 target이 `healthy`가 아니면
Cloudflare service CNAME을 만들지 않습니다. ALB·listener·Target Group·security group을
Console에서 직접 보정하지 않고 Git·controller input을 수정해 다시 reconcile합니다. 새
ALB나 Target Group으로 교체되면 `9.1`에서 새 hostname을 얻은 뒤 이 절을 새 resource에
다시 적용합니다.

### 9.3 필수: DNS 전 ALB 직접 검증

**실행 위치:** macOS local terminal 또는 EC2 terminal

**실행 조건:** `<ALB_DNS_NAME>`의 TCP `80`·`443`에 연결 가능

**권장 위치:** VPC 외부의 macOS local terminal

**시작 directory:** 제한 없음

`<ALB_DNS_NAME>`에는 `9.1`의 `[2/2] Ingress ALB hostname` 조회가 출력한 한 줄 전체를
넣습니다. 이 값은 `k8s-...ap-northeast-2.elb.amazonaws.com` 형태의 AWS ALB DNS
hostname이며, AWS Console의 Load Balancer 이름·ARN이나 `stg-api.ronams.com`을 넣지
않습니다. 명령에 붙여 넣을 때 `<`와 `>`는 제거합니다.

아래 block의 첫 줄에서 `9.1` 출력값을 한 번 입력하면 두 요청이 같은 ALB를 사용합니다.
DNS propagation과 분리해 SNI·Host를 유지한 채 그 ALB에 직접 연결합니다.

```bash
alb_dns_name='<9.1 [2/2] 출력값>'

# 1. ALB HTTPS readiness 직접 요청
# 확인: certificate 검증을 통과하고 HTTP 200
printf '\n\n========== [1/2] ALB HTTPS readiness ==========\n\n'
curl --fail --silent --show-error \
  --connect-to "stg-api.ronams.com:443:${alb_dns_name}:443" \
  https://stg-api.ronams.com/actuator/health/readiness

# 2. ALB HTTP redirect 직접 요청
# 확인: HTTP 301이며 Location이 HTTPS service endpoint
printf '\n\n========== [2/2] ALB HTTP redirect ==========\n\n'
curl --silent --show-error --head \
  --connect-to "stg-api.ronams.com:80:${alb_dns_name}:80" \
  http://stg-api.ronams.com/

unset alb_dns_name
```

첫 요청은 certificate 검증을 우회하지 않고 HTTP `200`을 반환해야 합니다. 두 번째는 HTTP
`301`이고 `Location` 값의 scheme·host·port가 HTTPS `stg-api.ronams.com:443`로 수렴해야
합니다. 표준 HTTPS port 표기를 생략한 response는 동일 endpoint로 해석합니다. 두 결과는
Cloudflare CNAME 생성 직전의 필수 gate이며 실패하면 `10`절로 진행하지 않습니다.

두 실행 위치 모두 certificate·SNI·Host 검증, HTTPS readiness `200`과 HTTP→HTTPS `301`을
확인할 수 있습니다. 현재 EC2 node도 public subnet·public IPv4와 Internet Gateway route를
사용하므로 EC2 terminal에서 실행해도 이 필수 결과를 확인할 수 있습니다. VPC 외부의 macOS
local terminal은 같은 결과에 더해 외부 client가 internet-facing ALB의 public entrypoint로
진입하는 경로까지 확인하므로 권장합니다. 이는 EC2 terminal의 실행 금지가 아니며, local
terminal을 사용할 수 없으면 EC2 terminal에서 실행해도 됩니다.

이 구분은 internet-facing Load Balancer DNS가 node의 public IP로 resolve되어 internet
client 요청을 받는다는 [AWS ELB 동작 설명](https://docs.aws.amazon.com/elasticloadbalancing/latest/userguide/how-elastic-load-balancing-works.html#load-balancer-scheme),
public subnet·public IPv4의 EC2가 Internet Gateway를 통해 외부로 연결된다는
[AWS VPC Internet Gateway 설명](https://docs.aws.amazon.com/vpc/latest/userguide/VPC_Internet_Gateway.html),
`--connect-to`가 연결 대상만 바꾸고 TLS SNI·certificate 검증과 application protocol의
hostname은 바꾸지 않는다는 [curl 공식 설명](https://curl.se/docs/manpage.html#--connect-to)에
근거합니다.

#### 선택 진단: DNS 전 검증 실패 원인 조회

`9.2` 또는 `9.3`이 실패했을 때만 Ingress event, Service endpoint·NodePort, Pod readiness,
controller log와 managed SG source/port 순서로 진단합니다. 필수 결과가 모두 성공했다면
이 진단과 AWS Console의 반복 재조회는 생략합니다.

## 10. Cloudflare service CNAME

### 10.1 Service CNAME 생성

**실행 위치:** Cloudflare Console의 `ronams.com` zone

Cloudflare `ronams.com → DNS → Records`에서 다음 record를 만듭니다.

- Type: `CNAME`
- Name: `stg-api`
- Target: `<ALB_DNS_NAME>`
- Proxy status: DNS only
- TTL: Auto

ACM validation CNAME은 그대로 유지합니다. Service CNAME과 validation CNAME을 합치거나
하나를 삭제하지 않습니다.

### 10.2 Public resolver 확인

**실행 위치:** Public DNS를 조회할 수 있는 terminal

**시작 directory:** 제한 없음

Public resolver에서 확인합니다.

```bash
# 1. Service CNAME target 조회
# 확인: <ALB_DNS_NAME>으로 수렴
printf '\n\n========== [1/2] Service CNAME ==========\n\n'
dig +short CNAME stg-api.ronams.com

# 2. Service hostname address 조회
# 확인: ALB address이며 Cloudflare anycast IP가 아님
printf '\n\n========== [2/2] Service DNS address ==========\n\n'
dig +short stg-api.ronams.com
```

1. CNAME 조회는 public record가 exact ALB를 가리키는지 확인합니다.
2. Address 조회는 DNS-only baseline이며 Cloudflare proxy가 개입하지 않는지 확인합니다.

Cloudflare anycast IP가 아니라 ALB CNAME·address로 수렴해야 DNS-only baseline입니다.

## 11. End-to-end 완료 검증

### 11.1 필수: Public DNS·TLS 검증

**실행 위치:** Public DNS와 TCP `80`·`443`에 연결할 수 있는 terminal

**시작 directory:** 제한 없음

```bash
# 1. Public HTTPS readiness 요청
# 확인: HTTP 200
printf '\n\n========== [1/3] Public HTTPS readiness ==========\n\n'
curl --fail --silent --show-error \
  https://stg-api.ronams.com/actuator/health/readiness

# 2. Public HTTP redirect 요청
# 확인: HTTP 301이며 Location이 HTTPS service endpoint
printf '\n\n========== [2/3] Public HTTP redirect ==========\n\n'
curl --silent --show-error --head \
  http://stg-api.ronams.com/

# 3. Public certificate 조회
# 확인: stg-api.ronams.com subject·issuer·유효기간
printf '\n\n========== [3/3] Public TLS certificate ==========\n\n'
openssl s_client \
  -connect stg-api.ronams.com:443 \
  -servername stg-api.ronams.com \
  </dev/null 2>/dev/null \
| openssl x509 -noout -subject -issuer -dates
```

1. HTTPS 요청은 public DNS를 통한 readiness `200`을 확인합니다.
2. HTTP 요청은 public 경로의 HTTPS `301` redirect를 확인합니다.
3. Certificate 조회는 host·issuer·validity를 확인합니다.

### 11.2 필수: Kubernetes·Argo final state

**실행 위치:** EC2 terminal

**시작 directory:** 제한 없음

```bash
# 1. Service와 Ingress 최종 상태 조회
# 확인: Service NodePort와 Ingress ALB hostname
printf '\n\n========== [1/3] Service와 Ingress ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get service/car-community ingress/car-community -n car-community -o wide

# 2. Application revision·sync·health 조회
# 확인: target revision과 Synced·Healthy
printf '\n\n========== [2/3] Application 상태 ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get application/car-community-stg -n argocd \
  -o 'jsonpath={.status.sync.revision}{"\t"}{.status.sync.status}{"\t"}{.status.health.status}{"\n"}'

# 3. Application condition 조회
# 확인: 빈 출력
printf '\n\n========== [3/3] Application condition 부재 ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get application/car-community-stg -n argocd \
  -o 'jsonpath={range .status.conditions[*]}{.type}{"\t"}{.message}{"\n"}{end}'
```

1. Service·Ingress 조회는 NodePort와 ALB hostname을 확인합니다.
2. Application 조회는 Git target과 최종 sync·health를 결속합니다.
3. Condition 조회는 남은 reconciliation 오류가 없는지 확인합니다.

Controller·CRD·HPA·providerID는 `5.2` script가 이미 fail-close했고 `5.2`의 성공 결과가
현재 scenario에 기록돼 있으므로 여기서 다시 조회하지 않습니다. 현재 값을 다시 펼쳐 보고
싶을 때만 `5.2`의 **선택 진단: 설치값 다시 조회**를 사용하며, 그 미실행은 이 절의 완료를
막지 않습니다.

다음은 이 runbook 전체의 필수 완료 결과입니다. 각 결과는 해당 절에서 한 번 실제로
확인하고 scenario에 기록하면 되며, 이후 절에서 같은 값을 반복 조회하지 않습니다.

- Application의 target revision, `Synced`, `Healthy`, condition 없음
- Service NodePort auto-assignment와 Ingress ALB hostname
- Controller Ready, required CRD 7개 `Established`, controller HPA absent
- AWS Target Group node target `healthy`
- ACM certificate `Issued`와 exact HTTPS listener policy
- Public DNS-only CNAME, HTTPS `200`, HTTP→HTTPS `301`
- Node SG에 public NodePort manual rule 없음
- New role credential과 private ECR pull regression 성공
- Node providerID가 IMDSv2의 현재 AZ·instance ID와 exact
- Node primary ENI에서 `stg-car-community-k8s-node-sg`만
  `kubernetes.io/cluster/stg-car-community-k8s=shared`로 선택

이 결과를
[100 Staging 단일 node 전체 구축 시나리오](scenarios/100-staging-single-node-bootstrap.md)의
해당 checklist에 기록한 뒤에만 work item을 완료합니다.

## 실패·재개·rollback

**실행 위치:** IAM role·instance profile·subnet tag·ACM·ALB·Target Group·Security Group
복구는 AWS Console, LBC·CRD·providerID·ECR pull·AppProject·Ingress event 복구는 EC2
terminal, official policy checksum 재확보는 macOS local terminal, validation·service CNAME
복구는 Cloudflare Console, public DNS·HTTPS 재검증은 public network terminal, 잘못된
Ingress 복구 PR은 GitHub Web UI에서 수행합니다.

**시작 directory:** macOS local terminal, EC2 terminal과 public network terminal은 제한
없음

| 실패 | 하지 않는 일 | 복구·재개 |
| --- | --- | --- |
| New role credential 없음 | Old role 삭제, access key 발급 | Association propagation 확인, 필요 시 old profile ARN으로 복귀 |
| ECR regression 실패 | ECR wildcard·push 권한 추가 | Exact ECR pull policy와 provider 진단, old profile rollback 가능 |
| LBC artifact checksum 실패 | Latest URL·다른 version 사용 | Network와 official artifact를 확인한 뒤 같은 script 재실행 |
| Required CRD `Established` 실패 | Helm release 강행·CRD wait 생략 | 같은 exact chart CRD apply 원인을 고친 뒤 script 재실행 |
| Existing controller HPA 발견 | HPA 자동 삭제·fixed replica와 동시 인수 | Replica owner와 migration을 별도 결정한 뒤 재개 |
| Foreign controller owner/version | 자동 Helm 인수·upgrade | Existing release owner와 migration을 별도 결정 |
| ProviderID가 다른 non-empty 값이거나 IMDS/API 조회 실패 | Existing providerID overwrite·임의 instance ID 입력 | Node·EC2 identity를 대조하고 exact owner가 확정될 때까지 LBC 설치 중단 |
| 다른 primary ENI SG에도 cluster tag가 있거나 node SG tag 값이 다름 | 기존 tag 삭제·덮어쓰기, RDS SG 분리 | SG owner를 확인하고 node SG만 `shared`인 상태로 수렴한 뒤 `1.1`부터 재개 |
| ACM `Pending validation` | Ingress merge·certificate ARN 우회 | Cloudflare validation CNAME name/value·DNS-only 확인 |
| Ingress `FailedBuildModel` | ALB Console 수동 생성 | Event·certificate discovery·subnet tag·IAM policy를 수정 |
| Target unhealthy | NodePort public 개방 | Service endpoint·Pod readiness·managed SG source/port 진단 |
| DNS/HTTPS 실패 | Cloudflare proxy 켜서 우회 | ALB direct, DNS, certificate/SNI 순서로 fault domain 분리 |
| 잘못된 Ingress merge | Live patch·ALB 삭제 | Git revert PR, Argo reconciliation과 LBC cleanup 확인 |

### New profile rollback

**실행 위치:** Controller 중단·진단과 ECR pull 재확인은 EC2 terminal, IAM role association
복귀는 AWS Console

**시작 directory:** EC2 terminal은 제한 없음

Ingress가 아직 merge되지 않았고 controller AWS call이 실패한다면 controller release를
중단·진단한 뒤 EC2의 IAM role association을 `<OLD_INSTANCE_PROFILE_ARN>`으로 되돌릴 수
있습니다. Old profile로 private ECR pull을 다시 확인합니다. New role·policy/profile은
원인을 보존한 채 삭제하지 않고 별도 승인 후 정리합니다.
Exact Node providerID는 현재 EC2 identity를 표현하므로 profile rollback을 이유로 비우지
않습니다. 그 값이 잘못되었다면 임의 제거나 덮어쓰기 전에 node·instance
identity 원인을 별도로 확정합니다.

### 전체 철거 순서

| 순서 | 실행 위치 | 행동 |
| --- | --- | --- |
| 1 | Cloudflare Console | Client drain 뒤 `stg-api` service CNAME을 제거합니다. |
| 2 | GitHub Web UI | Ingress·NodePort patch를 제거하는 Git PR을 merge합니다. |
| 3 | EC2 terminal | Argo sync 뒤 Ingress finalizer 완료를 확인합니다. |
| 4 | AWS Console | ALB·Target Group·managed SG 삭제를 확인합니다. |
| 5 | EC2 terminal | AppProject Ingress permission rollback 필요 여부를 결정합니다. |
| 6 | EC2 terminal | LBC Helm release·CRD uninstall은 별도 platform change로 수행합니다. |
| 7 | AWS Console | Public subnet role tag를 제거합니다. |
| 8 | AWS Console | EC2를 old profile로 되돌립니다. |
| 9 | EC2 terminal | Old profile 복귀 뒤 ECR pull을 확인합니다. |
| 10 | AWS Console | ECR pull 확인 뒤 new role/profile/policy 정리를 별도 승인합니다. |
| 11 | AWS Console | 다른 consumer가 없을 때만 ACM certificate를 제거합니다. |
| 12 | Cloudflare Console | 다른 consumer가 없을 때만 validation CNAME을 제거합니다. |

**시작 directory:** EC2 terminal은 제한 없음

ALB를 먼저 Console에서 삭제하거나 controller가 남긴 finalizer를 강제로 제거하지 않습니다.

## 공식 기준과 관련 문서

- **구조와 선택 이유**: [170 AWS Load Balancer ingress](../project/infrastructure/170-aws-load-balancer-ingress.md)
- **작업 상태와 상세 결정**:
  [014 Staging 외부 HTTPS ingress](../work-items/features/2026/014-staging-external-https-ingress.md),
  [014 상세 설계](../work-items/features/2026/014-staging-external-https-ingress-design.md)
- **전체 staging 상태**: [100 Staging 단일 node 전체 구축 시나리오](scenarios/100-staging-single-node-bootstrap.md)
- **GitOps 반복 변경**: [420 GitOps 배포·rollback](420-gitops-deployment-and-rollback-runbook.md)
- [AWS Load Balancer Controller installation](https://kubernetes-sigs.github.io/aws-load-balancer-controller/latest/deploy/installation/)
- [AWS Load Balancer Controller target type defaults](https://github.com/kubernetes-sigs/aws-load-balancer-controller/blob/v3.5.0/helm/aws-load-balancer-controller/values.yaml)
- [AWS Load Balancer Controller certificate discovery](https://kubernetes-sigs.github.io/aws-load-balancer-controller/latest/guide/ingress/cert_discovery/)
- [AWS Load Balancer Controller security groups](https://kubernetes-sigs.github.io/aws-load-balancer-controller/latest/deploy/security_groups/)
- [AWS Load Balancer Controller subnet discovery](https://kubernetes-sigs.github.io/aws-load-balancer-controller/latest/deploy/subnet_discovery/)
- [Kubernetes Cloud Controller Manager](https://kubernetes.io/docs/concepts/architecture/cloud-controller/)
- [ACM DNS validation](https://docs.aws.amazon.com/acm/latest/userguide/dns-validation.html)
- [ACM public certificate 요청과 export 선택](https://docs.aws.amazon.com/acm/latest/userguide/acm-public-certificates.html)
- [ACM exportable public certificate](https://docs.aws.amazon.com/acm/latest/userguide/acm-exportable-certificates.html)
- [AWS Security Hub ELB controls](https://docs.aws.amazon.com/securityhub/latest/userguide/elb-controls.html)
- [ALB security policies](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/describe-ssl-policies.html)
- [Cloudflare proxy status](https://developers.cloudflare.com/dns/proxy-status/)
