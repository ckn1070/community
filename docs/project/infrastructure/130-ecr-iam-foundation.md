# ECR·IAM image 공급 기반

이 상세 SOT는 private ECR repository와 GitHub OIDC push identity, EC2 pull identity의 관계,
현재 선택·권한·대안과 재검토 조건을 소유합니다. AWS resource를 실제로 생성·연결하는
절차는 [130 ECR·IAM 최초 구축 runbook](../../operations/130-ecr-iam-provisioning-runbook.md)이
소유합니다. 반복 workflow의 trigger·step·handoff는
[200 CI/CD pipeline](200-ci-cd-pipeline.md), image identity·build와 kubelet pull 원리는
[210 Container image delivery](210-container-image-delivery.md)가 소유합니다.

## 세 resource가 나뉘는 이유

```text
GitHub Actions
  └── GitHub OIDC federation
      └── car-community-github-ecr-push-role
          └── car-community repository write

EC2 kubelet
  └── EC2 instance profile
      └── car-community-k8s-node-ecr-pull-role
          └── car-community repository read
```

GitHub Actions는 image layer·manifest를 쓸 수 있지만 node는 읽기만 해야 합니다. Trust
principal도 GitHub OIDC federation과 EC2 service로 다릅니다. 두 actor가 한 role을 공유하면
runtime node에 write가 생기거나 GitHub trust가 node 권한과 섞이므로 분리합니다.

## ECR repository 선택

| 옵션 | 현재 선택 | 이유와 영향 | 재검토 조건 |
| --- | --- | --- | --- |
| Region | `ap-northeast-2` | 현재 EC2와 같은 region에서 pull하고 URI·ARN·workflow 계약을 하나로 고정합니다. | Node region 변경 |
| Visibility | Private | Application image·metadata를 public registry에 공개하지 않고 account IAM으로 제한합니다. | Public image 배포가 명시적으로 필요 |
| 이름 | `car-community` | 환경과 무관한 동일 application artifact를 한 번 저장하고 각 환경은 digest를 선택합니다. `stg-`로 image를 환경마다 다시 build하지 않습니다. | 독립 release lifecycle의 다른 image가 생김 |
| Tag immutability | 활성 | 같은 Git SHA가 다른 byte를 가리키지 못합니다. Workflow 재실행은 기존 tag digest를 조회합니다. | Mutable deployment가 필요하면 tag가 아닌 별도 계약 재검토 |
| Encryption | AES-256 AWS managed 기본 | 별도 KMS policy·cross-account 요구 없이 ECR at-rest 기본 암호화를 사용합니다. | Customer managed KMS 요구 |
| Scanning | Registry basic scan-on-push가 `car-community` 포함 | 새로 게시한 image의 지원 OS package에서 알려진 취약점을 무료로 검사합니다. 결과는 관찰 정보이며 현재 image 게시·배포를 자동 차단하지 않습니다. | 언어 package 검사·지속 재검사·severity 배포 gate가 필요할 때 Enhanced scanning과 비용을 함께 결정 |
| Lifecycle policy | 없음 | 보존 revision·기간이 미정이며 잘못된 rule이 rollback digest를 지울 수 있습니다. 공급 자체의 필수 조건도 아닙니다. | 배포 rollback window와 보존 기간·개수 확정 |
| Tag | `project=car-community`, `managed-by=manual` | Project와 현재 Console 관리 방식을 표시합니다. Repository는 환경 공용이라 `environment=stg`를 붙이지 않습니다. | 구성 소유권 자동화 시 `managed-by` 갱신 |

Repository URI 계약은
`<ACCOUNT_ID>.dkr.ecr.ap-northeast-2.amazonaws.com/car-community`입니다. Resource가 private이고
tag가 immutable이어도 publish actor·pull actor의 IAM이 자동 생기는 것은 아닙니다.

## GitHub OIDC push identity

Account 단위 OIDC provider는 다음 공개 issuer·audience를 신뢰합니다.

| 항목 | 값 | 의미 |
| --- | --- | --- |
| Provider | `https://token.actions.githubusercontent.com` | GitHub Actions OIDC issuer입니다. |
| Audience | `sts.amazonaws.com` | AWS STS role session용 token임을 제한합니다. |

Provider URL은 GitHub Actions가 발급하는 token의 `iss` claim과 일치합니다. Audience는
repository 식별자가 아니라 token의 수신자를 나타내는 `aud` claim이며, 공식
`aws-actions/configure-aws-credentials`가 AWS STS federation에 사용하는 값입니다.

Provider 자체는 `car-community` 권한을 주지 않습니다. Role
`car-community-github-ecr-push-role`의 trust가 audience와 subject를 모두 exact match로
제한합니다.

```text
aud = sts.amazonaws.com
sub = repo:example-org/car-community:ref:refs/heads/main
```

현재 repository는 2026-06-02에 생성됐고 GitHub OIDC 설정 조회 결과
`use_default=true`, `use_immutable_subject=false`입니다. 현재 image publish job도 GitHub
Environment를 사용하지 않으므로 이름·branch 기반 subject가 실제 계약입니다. Repository
소유자 ID는 `00000000`, repository ID는 `0000000000`이며 immutable subject를 활성화하면
다음처럼 ID를 포함한 형식으로 trust를 함께 바꿔야 합니다.

```text
repo:example-org@00000000/car-community@0000000000:ref:refs/heads/main
```

GitHub Environment를 publish job에 지정하면 branch 부분 대신
`environment:<ENVIRONMENT_NAME>` context를 사용합니다. Environment 도입이나 immutable
subject 전환은 GitHub 설정만 바꾸는 독립 변경이 아니라 AWS role trust와 workflow를
같이 갱신하고 실제 token assumption을 재검증하는 변경입니다.

Provider에는 `managed-by=manual`, `purpose=github-actions` tag를 사용합니다. 이 resource는
account 단위에서 여러 environment와 role이 재사용할 수 있으므로 `environment=stg`는
붙이지 않습니다. 사람의 AWS account SSO를 중앙 관리하는 IAM Identity Center와 GitHub
Actions workload가 임시 role credential을 얻는 이 OIDC provider는 서로 다른 기능입니다.

Push role은 `project=car-community`, `managed-by=manual`, `purpose=ecr-image-push` tag를
사용하고 `environment` tag는 사용하지 않습니다. 이 role의 consumer는 staging 배포
자체가 아니라 환경 공용 `car-community` image를 한 번 게시하는 GitHub Actions입니다.
각 environment는 나중에 별도 배포 권한과 digest 선택 계약으로 같은 artifact를 소비합니다.

Wildcard branch, pull request subject, fork와 다른 repository는 이 role을 assume할 수
없습니다. Role permission은 다음 경계를 가집니다.

- `ecr:GetAuthorizationToken`은 ECR API가 repository ARN 제한을 지원하지 않아 resource
  `*`를 사용합니다.
- Layer 확인·조회·upload·complete와 `ecr:PutImage`는 서울 region의 exact
  `car-community` repository ARN에만 허용합니다.
- Repository 생성·삭제, policy 변경, 다른 repository write와 IAM 관리 권한은 없습니다.
- GitHub에는 장기 AWS access key를 만들지 않고 repository variable에 role ARN만 둡니다.

Role trust와 permission은 서로 다른 축을 제한합니다. Trust의 `sub`는 image를
게시할 GitHub repository·branch를 고정하고, permission의 repository ARN은 image를 쓸
ECR repository를 고정합니다. 둘 모두 exact해야 올바른 주체가 올바른 artifact 저장소에만
게시할 수 있습니다.

## EC2 pull identity

Role `car-community-k8s-node-ecr-pull-role`과 같은 이름의 instance profile을 EC2에
연결합니다. 설명은 다음과 같습니다.

```text
Allows EC2 Kubernetes nodes to pull images from ECR car-community.
```

- Trust principal은 `ec2.amazonaws.com` 하나입니다.
- `ecr:GetAuthorizationToken`은 resource `*`입니다.
- `ecr:BatchGetImage`, `ecr:GetDownloadUrlForLayer`만 exact repository ARN에 허용합니다.
- Push·repository 관리 권한은 없습니다.

Role에는 `project=car-community`, `managed-by=manual`, `purpose=ecr-image-pull` tag를
사용하고 `environment` tag는 사용하지 않습니다. 현재 role은 환경 공용
`car-community` repository의 동일 image를 읽는 권한만 가지므로 환경별로 복제하지
않습니다. 환경별 AWS account·repository·node permission이 달라지면 role과 instance
profile을 환경별로 분리하고 `environment` tag를 함께 도입합니다.

EC2 최초 launch 때 IAM profile이 없었고 image 공급 요구 확정 뒤 이 pull profile을
연결했다는 두 사실은 서로 다른 시점의 계약입니다. 기존 profile이 있다면 내용을 모른 채
교체하지 않습니다. 이 role은 node 전체 identity이며 application Pod의 일반 AWS API 권한을
뜻하지 않습니다.

Staging external ingress를 구축할 때는 기존 ECR-only role/profile을 직접
변형하지 않고 `stg-car-community-k8s-node-role`과 같은 이름의 profile을 새로
만듭니다. 새 role에는 다음 customer-managed policy를 따로 연결합니다.

- `car-community` exact repository의 ECR pull policy
- AWS Load Balancer Controller `3.5.0` official policy를 staging VPC로 제한한 policy

이렇게 하면 ECR read와 controller AWS resource lifecycle의 purpose·diff·rollback을
분리하면서 EC2에 한 번에 하나만 연결할 수 있는 instance profile 제약을
지킵니다. 새 credential effective 확인, 기존 private ECR exact digest pull regression,
controller와 ALB create/delete lifecycle이 모두 통과하기 전에는 기존
`car-community-k8s-node-ecr-pull-role` role/profile을 삭제하지 않습니다.
이는 최초 image 공급 계약을 폐기한 것이 아니라 ingress platform capability를
추가하는 전환 경계입니다.

## 선택하지 않은 대안과 trade-off

| 대안 | 현재 제외 이유 | 재검토 조건 |
| --- | --- | --- |
| Public repository | Application artifact를 public으로 노출할 요구가 없습니다. | 공개 배포를 명시적으로 승인할 때 |
| Environment별 ECR repository·재build | 같은 application artifact와 environment 선택을 섞고 중복 build합니다. | 환경별 image가 독립 release lifecycle을 가질 때 |
| Mutable tag·`latest` | 동일 label이 다른 content를 가리켜 source·runtime 추적과 rollback을 약화합니다. | 현재 계획 없음. 변경하려면 배포 identity 계약 재설계 |
| Customer managed ECR KMS | 별도 key policy·rotation·cross-account 요구가 없습니다. | 해당 key control이 요구될 때 |
| Scanning 없음 | 게시 직후 known vulnerability 확인 경로를 유지합니다. | 별도 scanner가 완전히 대체할 때 |
| Enhanced scanning | 현재는 언어 package의 지속 재검사나 severity 배포 gate가 없으며 Amazon Inspector 비용·운영 범위를 먼저 늘리지 않습니다. | Java package까지 지속 검사하거나 scan 결과를 배포 gate로 사용할 때 |
| 지금 lifecycle policy 생성 | 보존 window 없이 digest를 삭제할 위험이 있습니다. | Rollback window·보존 비용 정책을 확정할 때 |
| GitHub static access key | 장기 secret 저장·rotation·유출 표면이 생깁니다. | OIDC를 사용할 수 없는 외부 제약을 별도 승인할 때 |
| Broad OIDC subject·wildcard branch | PR·fork·다른 branch가 privileged publish를 시도할 수 있습니다. | Publisher repository·branch가 실제로 늘어 trust를 명시 갱신할 때 |
| Environment별 GitHub ECR push role | 현재는 모든 environment가 같은 application image를 소비하며 role은 배포가 아닌 공용 artifact 게시만 담당합니다. | Environment별로 publisher·ECR account·repository·release lifecycle이 분리될 때 |
| Environment별 EC2 ECR pull role | 현재 node는 환경 공용 repository의 동일 read-only permission만 사용하므로 같은 role을 재사용합니다. | Environment별 AWS account·repository·node permission이 달라질 때 |
| AWS managed ECR full access | Target repository 밖 관리·write 권한이 과도합니다. | 사용하지 않음. 필요한 action만 추가 검토 |
| Push·pull role 통합 | Runtime node에 write를 주거나 서로 다른 trust를 혼합합니다. | 사용하지 않음. Actor 권한 분리는 유지 |
| Node static AWS key | 장기 credential을 host에 보관·rotation해야 합니다. | Instance profile을 쓸 수 없는 다른 runtime을 별도 설계할 때 |
| ECR token `imagePullSecret` rotation | 최대 12시간 token을 Secret·CronJob·모든 namespace에 배포하는 lifecycle이 생깁니다. | EC2 role/provider를 사용할 수 없는 cluster로 전환할 때 |

## 완료와 증거 경계

AWS 기반 완료에는 immutable repository·scanning filter, exact GitHub trust·push permission,
EC2-only trust·pull permission과 instance profile 연결을 live 확인해야 합니다. 이 완료는
다음을 아직 증명하지 않습니다.

- GitHub workflow가 role을 assume하고 image를 push했습니다.
- Kubelet credential provider가 instance role로 token을 얻었습니다.
- Node가 exact digest를 pull·실행했습니다.

Workflow actor·step·digest handoff는 [200 CI/CD pipeline](200-ci-cd-pipeline.md), kubelet
provider·exact digest pull 원리는 [210 Container image delivery](210-container-image-delivery.md),
실제 live 결과는 각 numbered operations runbook이 소유합니다.

## 변경 시 동반 확인

- Repository 이름·region 변경은 workflow 환경값, 두 IAM repository ARN, node provider
  match·smoke target과 현재 구성 SOT를 함께 바꿉니다.
- Source repository·publish branch, GitHub Environment와 immutable subject 설정 변경은
  OIDC `sub`, workflow trigger·guard와 branch protection을 함께 바꿉니다.
- Node architecture 변경은 repository IAM과 무관하지만 publish platform·digest consumer와
  node smoke를 함께 검증합니다.
- Lifecycle policy를 도입하기 전에 GitOps rollback이 필요로 하는 digest 보존 window를
  정합니다.
- EC2 profile을 ingress용 composite role로 바꾸면 기존 kubelet credential provider가 같은
  repository를 계속 pull하는지 exact digest smoke를 다시 실행합니다.

Lifecycle·구성 소유권·workload identity 후보의 상태와 도입 순서는
[004 Infrastructure evolution backlog](../../work-items/backlog/2026/004-infrastructure-evolution.md)가
소유합니다.

## 근거와 관련 문서

- **실제 AWS 최초 구축**: [130 ECR·IAM 최초 구축 runbook](../../operations/130-ecr-iam-provisioning-runbook.md)
- **반복 workflow 흐름**: [200 CI/CD pipeline](200-ci-cd-pipeline.md)
- **Image identity·build·pull 원리**: [210 Container image delivery](210-container-image-delivery.md)
- **EC2 node identity**: [120 EC2 Kubernetes node](120-ec2-kubernetes-node.md)
- **Ingress를 위한 node role 전환과 LBC policy**:
  [170 AWS Load Balancer ingress](170-aws-load-balancer-ingress.md)
- **Profile 교체·ECR regression live 절차**:
  [370 Staging 외부 HTTPS ingress 최초 구축 runbook](../../operations/370-staging-external-https-ingress-bootstrap-runbook.md)
- [ECR private repository 생성](https://docs.aws.amazon.com/AmazonECR/latest/userguide/repository-create.html)
- [ECR private repository](https://docs.aws.amazon.com/AmazonECR/latest/userguide/Repositories.html)
- [ECR tag immutability](https://docs.aws.amazon.com/AmazonECR/latest/userguide/image-tag-mutability.html)
- [ECR image scanning](https://docs.aws.amazon.com/AmazonECR/latest/userguide/image-scanning.html)
- [ECR IAM repository policy examples](https://docs.aws.amazon.com/AmazonECR/latest/userguide/security_iam_id-based-policy-examples.html)
- [GitHub Actions용 AWS OIDC provider](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_providers_create_oidc.html)
- [GitHub Actions의 AWS OIDC 구성](https://docs.github.com/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-aws)
- [GitHub Actions OIDC claim reference](https://docs.github.com/actions/reference/security/oidc)
- [GitHub Actions OIDC REST API](https://docs.github.com/rest/actions/oidc)
- [EC2 IAM role](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/iam-roles-for-amazon-ec2.html)
