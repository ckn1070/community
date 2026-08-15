# Private ECR·image 공급 IAM 최초 구축 runbook

GitHub Actions가 application image를 push하고 EC2 Kubernetes node가 pull할 private ECR
repository와 두 개의 최소 권한 IAM role을 AWS Console에서 처음 만드는 절차입니다.
Actor·trust·permission 분리와 image lifecycle 선택은
[ECR·IAM 기반](../project/infrastructure/120-ecr-iam-foundation.md)이 소유합니다.

## 사용 시점과 actor

- **사용 시점**: `stg-car-community-k8s-node-01`을 생성한 뒤 image 공급 경로의 AWS
  resource를 처음 준비할 때 사용합니다.
- **actor**: ECR repository, IAM OIDC provider·role·inline policy와 EC2 instance
  profile을 생성·연결할 권한이 있는 AWS Console 운영자이며, GitHub repository의 Actions
  OIDC 설정을 읽을 수 있어야 합니다.
- **실행 위치**: `ap-northeast-2`의 ECR·IAM·EC2 Console과 현재 repository를 읽을 수 있게
  `gh`가 인증된 신뢰할 수 있는 local shell입니다.

## 전제와 입력

- 현재 12자리 AWS account ID를 확인합니다. 아래 JSON의 `<AWS_ACCOUNT_ID>`는 실제 값으로
  바꾸되 문서 자체를 account별 값으로 수정하지 않습니다.
- `gh auth status`로 현재 GitHub account가 `ckn1070/community`를 읽을 수 있는지
  확인하되 token 값을 출력하거나 복사하지 않습니다.
- GitHub repository와 배포 branch는 `ckn1070/community`의 `main`입니다.
- ECR repository는 environment 공용 application artifact를 저장하므로 이름에 `stg-`를
  붙이지 않습니다.
- GitHub push role과 EC2 pull role을 재사용하지 않습니다. access key도 생성하지
  않습니다.
- 기존 OIDC provider·role·instance profile이 있으면 이름만 보고 덮어쓰거나 교체하지
  않고 trust, permission과 연결 대상을 먼저 대조합니다.

## 정상 실행 순서와 단계별 기대 결과

### 1. Private ECR repository 생성

`Elastic Container Registry → Private registry → Repositories → Create repository`에서
다음 값을 입력합니다.

| 옵션 | 입력값 |
| --- | --- |
| Region | `ap-northeast-2` |
| Visibility | Private |
| Repository name | `car-community` |
| Tag immutability | 활성화 |
| Encryption | AES-256, AWS managed 기본 암호화 |
| Lifecycle policy | 생성하지 않음 |

Repository를 생성한 뒤 상세 화면의 `Repository tags → Edit`에서 다음 resource tag를
추가합니다.

| key | value |
| --- | --- |
| `project` | `car-community` |
| `managed-by` | `manual` |

Repository는 environment 공용 image를 저장하므로 `environment` tag는 추가하지 않습니다.

왼쪽 메뉴의 `Private registry → Features and settings → Scanning`으로 이동합니다. 한국어
Console에서는 `프라이빗 레지스트리 → 기능 및 설정 → 스캔`으로 표시됩니다.

1. Scan type은 `Basic scanning`을 선택합니다.
2. `Scan all repositories on push`는 선택하지 않습니다.
3. Filter 입력란에 `car-community`를 입력하고 `Add filter`를 선택합니다.
4. 추가된 filter가 `car-community`인지 확인하고 저장합니다.

Repository 생성 화면의 deprecated image scanning 항목이나 repository 상세 화면만 보고
scan-on-push가 구성됐다고 판정하지 않습니다. Lifecycle policy는 image 보존 개수·기간과
rollback window가 확정되지 않았으므로 만들지 않습니다.

단계 기대 결과는 URI
`<ACCOUNT_ID>.dkr.ecr.ap-northeast-2.amazonaws.com/car-community`, immutable tag,
AES-256 암호화, 두 resource tag와 scan-on-push 대상 포함입니다.

### 2. GitHub OIDC provider 확인·생성

`IAM → Identity providers`에서 다음 provider를 찾습니다.

| 항목 | 값 |
| --- | --- |
| Provider URL | `https://token.actions.githubusercontent.com` |
| Audience | `sts.amazonaws.com` |

같은 URL과 audience의 provider가 있으면 account 단위 resource를 중복 생성하지 않습니다.
없으면 `OpenID Connect provider`로 생성하고 다음 resource tag를 추가합니다.

| key | value |
| --- | --- |
| `managed-by` | `manual` |
| `purpose` | `github-actions` |

Provider는 account에서 여러 GitHub Actions role이 재사용할 수 있으므로 특정 배포 환경을
뜻하는 `environment=stg` tag를 추가하지 않습니다. Provider URL은 GitHub Actions가
발급하는 OIDC token의 issuer(`iss`)이고, audience는 공식 AWS credential action이 AWS
STS용 token에 사용하는 `aud`입니다. Repository나 branch는 audience에 입력하지 않고 다음
role trust의 exact `sub`에서 제한합니다.

생성 뒤 성공 안내가 `token.actions.githubusercontent.com` provider 추가와 IAM role 할당
필요를 표시하고, provider 목록의 유형이 `OpenID Connect`인지 확인합니다. Role 할당 안내는
provider 생성만으로 AWS 권한이 생기지 않았으며 다음 단계의 role이 필요하다는 뜻입니다.
함께 표시될 수 있는 IAM Identity Center 안내는 사람의 AWS account SSO를 위한 별도
기능이므로 이 GitHub Actions federation 절차에서 설정하지 않습니다.

### 3. GitHub ECR push role 생성

Role을 만들기 직전에 GitHub repository의 현재 subject 형식을 읽습니다.

```sh
gh api repos/ckn1070/community/actions/oidc/customization/sub \
  --jq '{use_default, use_immutable_subject, include_claim_keys}'
```

현재 repository는 `use_default=true`, `use_immutable_subject=false`이고 image publish job에
GitHub Environment를 지정하지 않으므로 branch 기반 subject
`repo:ckn1070/community:ref:refs/heads/main`을 사용합니다. 조회값이 다르거나
`.github/workflows/publish-image.yml`의 publish job에 `environment`가 추가됐다면 아래 trust를
그대로 만들지 않고 [ECR·IAM 기반](../project/infrastructure/120-ecr-iam-foundation.md)의
subject 변경 조건을 먼저 적용합니다.

Provider 생성 성공 안내의 `역할 할당 → 새 역할 생성`을 선택하거나
`IAM → Roles → Create role`로 이동합니다. 신뢰할 수 있는 엔터티는
`Web identity`, ID provider는 `token.actions.githubusercontent.com`, audience는
`sts.amazonaws.com`을 선택하고 다음 repository 범위를 입력합니다.

| 항목 | 입력값 |
| --- | --- |
| GitHub organization | `ckn1070` |
| GitHub repository | `car-community` |
| GitHub branch | `main` |

Role 이름은 `car-community-github-ecr-push-role`로 지정합니다. 이 role은 환경에
application을 배포하는 role이 아니라 환경 공용 ECR에 동일한 application artifact를
게시하므로 `stg-`를 붙이지 않습니다. 설명은 다음과 같이 지정합니다.

```text
Allows GitHub Actions on ckn1070/community main to push images to ECR car-community.
```

생성 전 검토 화면의 trust policy가 다음 exact 계약과 같은지 확인합니다.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::<AWS_ACCOUNT_ID>:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
          "token.actions.githubusercontent.com:sub": "repo:ckn1070/community:ref:refs/heads/main"
        }
      }
    }
  ]
}
```

Audience와 `sub`가 모두 `StringEquals`인지 확인합니다. Wildcard branch, PR subject와 다른
repository를 추가하지 않습니다. Console wizard가 동일한 `sub` 값을 여러 번 표시하더라도
그 중복을 다른 branch나 repository 허용으로 해석하지 않습니다. Role 생성 후
`Trust relationships → Edit trust policy`에서 동일한 `sub`를 하나만 남기고 `aud`와 함께
위 JSON의 exact `StringEquals`로 정규화합니다. 이 현상은 현재 Console 작업에서 관찰한
복구 경계이며 Console이 항상 중복을 만든다거나 그 원인이 확정됐다는 뜻은 아닙니다.

Role 생성 wizard의 `Permissions → Create inline policy → JSON` 또는 생성 후
`Permissions → Add permissions → Create inline policy → JSON`에서 policy 이름을
`car-community-ecr-push`로 지정하고 다음을 입력합니다.

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
      "Sid": "PushCarCommunityImage",
      "Effect": "Allow",
      "Action": [
        "ecr:BatchCheckLayerAvailability",
        "ecr:BatchGetImage",
        "ecr:CompleteLayerUpload",
        "ecr:InitiateLayerUpload",
        "ecr:PutImage",
        "ecr:UploadLayerPart"
      ],
      "Resource": "arn:aws:ecr:ap-northeast-2:<AWS_ACCOUNT_ID>:repository/car-community"
    }
  ]
}
```

`ecr:GetAuthorizationToken`만 API 계약상 `Resource: "*"`이고 image action은 exact
repository ARN이어야 합니다. Repository 생성·삭제, policy 변경과 IAM 관리 permission을
추가하지 않습니다. Role에는 다음 resource tag를 추가합니다.

| key | value |
| --- | --- |
| `project` | `car-community` |
| `managed-by` | `manual` |
| `purpose` | `ecr-image-push` |

Environment 공용 artifact publisher이므로 `environment=stg` tag는 추가하지 않습니다. 생성 후
role ARN을 복사하되 access key를 만들지 않습니다.

### 4. EC2 ECR pull role과 instance profile 연결

`IAM → Roles → Create role → AWS service → EC2`에서 role 이름을
`car-community-k8s-node-ecr-pull-role`로 지정합니다. Console이 만드는 instance
profile도 같은 이름을 사용합니다. Trust는 EC2 service만 허용합니다.

Role 설명은 다음과 같이 지정합니다.

```text
Allows EC2 Kubernetes nodes to pull images from ECR car-community.
```

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": { "Service": "ec2.amazonaws.com" },
      "Action": "sts:AssumeRole"
    }
  ]
}
```

다음 inline policy를 `car-community-ecr-pull` 이름으로 추가합니다.

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
      "Resource": "arn:aws:ecr:ap-northeast-2:<AWS_ACCOUNT_ID>:repository/car-community"
    }
  ]
}
```

EC2 pull role에 다음 resource tag를 추가합니다.

| key | value |
| --- | --- |
| `project` | `car-community` |
| `managed-by` | `manual` |
| `purpose` | `ecr-image-pull` |

환경 공용 repository의 동일 read-only permission을 재사용하므로 `environment=stg` tag는
추가하지 않습니다. 환경별 AWS account·repository·node permission이 달라지면 role 분리를
먼저 결정합니다.

EC2 pull role에 ECR write action을 추가하지 않습니다. `EC2 → Instances →
stg-car-community-k8s-node-01 → Actions → Security → Modify IAM role`에서 같은 이름의
instance profile을 연결합니다. EC2 최초 launch 때 profile이 없었던 것과 이 후속 연결은
서로 다른 시점입니다.

### 5. 단계 기대 결과 확인

- Repository URI의 `<ACCOUNT_ID>` 부분이 현재 account ID이고 immutability, encryption과
  scanning 설정이 입력과 같습니다.
- `car-community-github-ecr-push-role` trust의 audience와 subject가 exact 값이고
  permission은 대상 repository push에 필요한 action만 허용하며 role tag가 입력과
  같습니다.
- `car-community-k8s-node-ecr-pull-role`의 trust principal은 EC2뿐이고 permission과
  resource tag가 입력과 같으며 ECR write action이 없습니다.
- `stg-car-community-k8s-node-01`에 같은 이름의 pull instance profile이 연결됐습니다.

여기까지는 AWS 기반 resource 준비입니다. 이어서
[GitHub ECR 게시 설정](320-github-ecr-publishing-configuration-runbook.md)과
[kubelet ECR credential provider 설치](330-kubelet-ecr-credential-provider-installation-runbook.md)를
완료합니다. GitHub role assumption·image push와 kubelet private ECR pull은 각각
[GitHub ECR image 게시](400-github-ecr-image-publishing-runbook.md)와
[private ECR image pull 검증](410-private-ecr-image-pull-verification-runbook.md)에서 실제
검증합니다.

## 중단·실패 판정

- 같은 OIDC URL의 기존 provider가 다른 audience 계약을 가지고 있으면 중복 생성이나
  임의 변경을 하지 않고 중단합니다.
- `<AWS_ACCOUNT_ID>`가 치환되지 않았거나 role trust의 `aud`·`sub`가 exact 값이 아니면
  role을 사용하지 않습니다.
- Console이 생성한 trust에 동일한 `sub`가 중복되어 있거나 `StringLike`로 남아 있으면
  최종 trust 정규화가 끝나기 전에 role을 사용하지 않습니다.
- push policy가 다른 repository나 IAM·ECR 관리 permission을 허용하거나 pull policy에
  write action이 있으면 완료로 처리하지 않습니다.
- EC2에 기존 instance profile이 연결돼 있으면 내용을 확인하지 않고 교체하지 않습니다.
- ECR repository·IAM role의 존재만으로 workflow push와 node pull을 성공으로 판정하지
  않습니다.

## 복구·재개

1. IAM policy simulator나 Console JSON summary에서 실패한 trust·permission 문장을 exact
   입력과 대조합니다.
2. 생성 중 오류가 난 role에 임시 broad permission을 붙이지 말고 trust와 inline policy를
   각각 수정합니다.
3. 기존 EC2 profile을 발견했으면 그 role의 현재 책임과 permission을 확인해 병합·교체
   결정이 날 때까지 연결을 바꾸지 않습니다.
4. AWS resource checklist가 정상인 checkpoint에서 310·320의 최초 설정·설치를 수행하고,
   400·410 runbook의 live publish·pull 검증으로 재개합니다.

## 완료 확인

- [ ] private `car-community` repository가 immutable·scan-on-push 계약을 충족합니다.
- [ ] GitHub OIDC provider가 exact issuer·audience와 `managed-by`·`purpose` tag를 사용합니다.
- [ ] `car-community-github-ecr-push-role`이 exact `main` branch trust, push-only permission과
      `project`·`managed-by`·`purpose` tag를 사용합니다.
- [ ] `car-community-k8s-node-ecr-pull-role`과 같은 이름의 instance profile이 준비되고
      node에 연결됐습니다.
- [ ] access key와 broad IAM permission을 만들지 않았습니다.
- [ ] 실제 완료 상태를
      [Staging 단일 node 전체 구축 시나리오](scenarios/100-staging-single-node-bootstrap.md)에
      반영했습니다.

## 공식 기준과 관련 문서

- **선택·actor·permission 경계**: [ECR·IAM 기반](../project/infrastructure/120-ecr-iam-foundation.md)
- **상위 절차**: [AWS 최초 구축](100-aws-initial-provisioning-runbook.md)
- **최초 GitHub 설정**: [GitHub ECR 게시 설정](320-github-ecr-publishing-configuration-runbook.md)
- **최초 node 설치**: [kubelet ECR credential provider 설치](330-kubelet-ecr-credential-provider-installation-runbook.md)
- **반복 게시 절차**: [GitHub ECR image 게시](400-github-ecr-image-publishing-runbook.md)
- **pull 검증 절차**: [private ECR image pull 검증](410-private-ecr-image-pull-verification-runbook.md)
