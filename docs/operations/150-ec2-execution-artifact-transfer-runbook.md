# EC2 실행 artifact 전달 runbook

Local repository의 bootstrap·검증·운영 artifact를 고정 inventory archive와 SHA-256 file로
묶어 staging EC2의 `/home/ubuntu/car-community-scripts`에 전달·검증·배치하는 공통 최초
절차입니다. Kubernetes뿐 아니라 ECR, Argo CD, Application과 Load Balancer 운영이 같은
artifact를 소비하므로 특정 기술 bootstrap에 종속되지 않습니다.

전달 구조와 checksum이 보증하는 범위, SSH를 선택한 이유와 재검토 조건은
[100 Infrastructure overview의 실행 artifact 전달 경계](../project/infrastructure/100-infrastructure-overview.md#실행-artifact-전달-경계)가
소유하고, file 사이의 구현 의존성은
[Repository script 목차](../../scripts/README.md#ec2-전달-archive-구현-의존성)가 소유합니다.

## 실행 계약

| 항목 | 계약 |
| --- | --- |
| 언제 | AWS EC2 SSH 접속이 가능해진 뒤, 이 artifact를 소비하는 첫 bootstrap·검증·운영 script 실행 전에 완료합니다. Inventory source가 바뀌면 같은 절차로 archive 전체를 다시 전달합니다. |
| 누가 | Local repository와 EC2 SSH private key를 가진 운영자가 실행합니다. |
| 어디서 | Local terminal과 SSH로 접속한 target EC2 terminal입니다. |
| 시작 directory | Local은 `car-community` repository root, EC2는 제한 없음 |
| 무엇을 | 고정 inventory 검증→archive·checksum 생성→`scp`→EC2 checksum·entry 검증→압축 해제·mode 검증 순서로 수행합니다. |
| 왜 | Repository 전체와 credential을 복사하지 않고, 여러 lifecycle이 공유하는 exact 실행 artifact만 같은 byte·경로·mode로 준비하기 위해서입니다. |
| 완료·재개 | EC2의 열아홉 file 유형·mode 검사가 모두 성공하면 완료입니다. 실패하면 변경하지 않은 앞 단계는 유지하고 archive 생성 또는 EC2 검증 경계에서 다시 시작합니다. |

## 전달 대상과 실행 경계

다음 열여섯 executable과 세 readable source를 archive로 묶되 생애주기 경로를 유지합니다.

- `bootstrap/kubernetes/100-prepare-host.sh`
- `bootstrap/kubernetes/200-install-containerd.sh`
- `bootstrap/kubernetes/300-install-kubernetes-components.sh`
- `bootstrap/kubernetes/400-initialize-cluster.sh`
- `bootstrap/kubernetes/500-install-calico.sh`
- `bootstrap/ecr/100-configure-kubelet-credential-provider.sh`
- `verify/ecr/verify-private-image-pull.sh`
- `bootstrap/gitops/100-install-argocd-core.sh`
- `bootstrap/gitops/200-connect-private-gitops-repository.sh`
- `bootstrap/load-balancer/100-install-aws-load-balancer-controller.sh`
- `bootstrap/gitops/manifests/car-community-appproject.yaml`
- `bootstrap/gitops/manifests/car-community-stg-application.yaml`
- `bootstrap/application/100-provision-database-access.sh`
- `bootstrap/application/200-apply-application-prerequisites.sh`
- `lib/application-secret.sh`
- `operations/application/reconcile-community-secret.sh`
- `operations/application/reconcile-notification-secret.sh`
- `operations/application/reconcile-catalog-redis-secrets.sh`
- `operations/gitops/rotate-private-gitops-repository-credential.sh`

Application source, Dockerfile, 문서, local contract test와 credential은 archive에 넣지
않습니다. 전달 방식·제외 범위·checksum 보증 범위와 S3·Systems Manager 재검토 조건은
[100 Infrastructure overview의 실행 artifact 전달 경계](../project/infrastructure/100-infrastructure-overview.md#실행-artifact-전달-경계)가
소유합니다.

열아홉 file 사이의 source 관계와 archive 밖 repository file을 읽지 않는 구현 의존성 경계는
[Repository script 목차의 EC2 전달 archive 구현 의존성](../../scripts/README.md#ec2-전달-archive-구현-의존성)이
소유합니다. 이 절차는 그 계약에 따라 repository 전체를 EC2에 clone하지 않고 열아홉 file만
전달합니다.

Archive를 한 번 전달해도 각 script의 실행 dependency는 바뀌지 않습니다.
`bootstrap/kubernetes/100`~`500`으로 빈 cluster를 완성하고 AWS IAM·ECR와 published digest가 준비된 뒤
[330 Kubelet ECR credential provider 설치](330-kubelet-ecr-credential-provider-installation-runbook.md)와
[410 Private ECR image pull 검증](410-private-ecr-image-pull-verification-runbook.md)에 따라
최초 구성과 반복 점검을 각각 실행합니다. Argo CD Core는 cluster 완료 뒤 설치할 수 있습니다.
DB role과 Git 밖 application resource는
[Application bootstrap README](../../scripts/bootstrap/application/README.md)에 따라 실행합니다.
Private repository 연결은 실제 digest의 `deploy/` overlay·GitHub App과 ECR pull이
준비된 뒤 실행하고 credential 교체 operations script는 최초 연결 완료 뒤에만 사용합니다.

Local 명령은 `scripts` directory가 바로 아래에 보이는 `car-community` repository root에서
실행합니다. 다음 placeholder만 실행 전에 실제 값으로 바꿉니다.

| Placeholder | 실행 위치 | 의미 |
| --- | --- | --- |
| `<KEY_PATH>` | Local | 현재 EC2 SSH 접속에 사용하는 private key file 절대 경로 |
| `<EC2_SSH_HOST>` | Local | 현재 SSH 접속에 사용하는 EC2 public IPv4 또는 public DNS |

### 1. Local에서 archive 생성

| 구분 | 값 |
| --- | --- |
| 실행 위치 | Local terminal |
| 시작 directory | `car-community` repository root |

정확한 열여섯 executable과 세 readable source가 있고 local 수정이 섞이지 않았는지 확인합니다.

```bash
# 1. 전달 대상 file 유형·권한 검증
# 확인: 모든 검사가 성공하고 Local script 실행 권한 확인 완료 출력
printf '\n\n========== [1/2] 전달 대상 file 검증 ==========\n\n'
test -x scripts/bootstrap/kubernetes/100-prepare-host.sh &&
test -x scripts/bootstrap/kubernetes/200-install-containerd.sh &&
test -x scripts/bootstrap/kubernetes/300-install-kubernetes-components.sh &&
test -x scripts/bootstrap/kubernetes/400-initialize-cluster.sh &&
test -x scripts/bootstrap/kubernetes/500-install-calico.sh &&
test -x scripts/bootstrap/ecr/100-configure-kubelet-credential-provider.sh &&
test -x scripts/verify/ecr/verify-private-image-pull.sh &&
test -x scripts/bootstrap/gitops/100-install-argocd-core.sh &&
test -x scripts/bootstrap/gitops/200-connect-private-gitops-repository.sh &&
test -x scripts/bootstrap/load-balancer/100-install-aws-load-balancer-controller.sh &&
test -r scripts/bootstrap/gitops/manifests/car-community-appproject.yaml &&
test ! -x scripts/bootstrap/gitops/manifests/car-community-appproject.yaml &&
test -r scripts/bootstrap/gitops/manifests/car-community-stg-application.yaml &&
test ! -x scripts/bootstrap/gitops/manifests/car-community-stg-application.yaml &&
test -x scripts/bootstrap/application/100-provision-database-access.sh &&
test -x scripts/bootstrap/application/200-apply-application-prerequisites.sh &&
test -r scripts/lib/application-secret.sh &&
test ! -x scripts/lib/application-secret.sh &&
test -x scripts/operations/application/reconcile-community-secret.sh &&
test -x scripts/operations/application/reconcile-notification-secret.sh &&
test -x scripts/operations/application/reconcile-catalog-redis-secrets.sh &&
test -x scripts/operations/gitops/rotate-private-gitops-repository-credential.sh &&
printf '%s\n' 'Local script 실행 권한 확인 완료'

# 2. 전달 대상의 local Git 변경 조회
# 확인: 출력 없음
printf '\n\n========== [2/2] 전달 대상 Git 상태 ==========\n\n'
git status --short -- \
  scripts/bootstrap/kubernetes/100-prepare-host.sh \
  scripts/bootstrap/kubernetes/200-install-containerd.sh \
  scripts/bootstrap/kubernetes/300-install-kubernetes-components.sh \
  scripts/bootstrap/kubernetes/400-initialize-cluster.sh \
  scripts/bootstrap/kubernetes/500-install-calico.sh \
  scripts/bootstrap/ecr/100-configure-kubelet-credential-provider.sh \
  scripts/verify/ecr/verify-private-image-pull.sh \
  scripts/bootstrap/gitops/100-install-argocd-core.sh \
  scripts/bootstrap/gitops/200-connect-private-gitops-repository.sh \
  scripts/bootstrap/load-balancer/100-install-aws-load-balancer-controller.sh \
  scripts/bootstrap/gitops/manifests/car-community-appproject.yaml \
  scripts/bootstrap/gitops/manifests/car-community-stg-application.yaml \
  scripts/bootstrap/application/100-provision-database-access.sh \
  scripts/bootstrap/application/200-apply-application-prerequisites.sh \
  scripts/lib/application-secret.sh \
  scripts/operations/application/reconcile-community-secret.sh \
  scripts/operations/application/reconcile-notification-secret.sh \
  scripts/operations/application/reconcile-catalog-redis-secrets.sh \
  scripts/operations/gitops/rotate-private-gitops-repository-credential.sh
```

1. File 검증은 실행 script와 readable manifest·library의 유형과 권한을 확인합니다.
2. Git 상태 조회는 local 수정이 archive에 섞이지 않았는지 확인합니다.

| 표현 | 의미와 선택 이유 |
| --- | --- |
| `test -x <path>` | 해당 path가 존재하고 현재 사용자가 실행할 수 있는 file인지 확인합니다. |
| `&&` | 앞의 검사가 성공했을 때만 다음 검사를 실행합니다. 하나라도 실패하면 마지막 완료 문구가 출력되지 않습니다. |
| `printf` | 열여섯 executable과 세 readable source 검사가 모두 성공했음을 화면에 명시적으로 출력합니다. |
| `git status --short` | 선택한 script의 Git 변경 상태를 짧은 형식으로 출력합니다. |
| Option separator `--` | 뒤의 값을 Git option이 아니라 확인할 file path로 고정합니다. |

`Local script 실행 권한 확인 완료`가 출력되고 `git status`는 아무것도 출력하지 않아야
합니다. 완료 문구가 없거나 Git 상태가 출력되면 그 내용을 전달 대상으로 임의 확정하지
않고 local file과 변경을 먼저 확인합니다. 다음 명령으로 archive를 생성합니다.

```bash
# 1. 고정 inventory archive 생성
# 확인: exit 0이며 /tmp/car-community-scripts.tar.gz 생성
printf '\n\n========== [1/2] Script archive 생성 ==========\n\n'
COPYFILE_DISABLE=1 tar --no-xattrs -czf /tmp/car-community-scripts.tar.gz \
  -C scripts \
  bootstrap/kubernetes/100-prepare-host.sh \
  bootstrap/kubernetes/200-install-containerd.sh \
  bootstrap/kubernetes/300-install-kubernetes-components.sh \
  bootstrap/kubernetes/400-initialize-cluster.sh \
  bootstrap/kubernetes/500-install-calico.sh \
  bootstrap/ecr/100-configure-kubelet-credential-provider.sh \
  verify/ecr/verify-private-image-pull.sh \
  bootstrap/gitops/100-install-argocd-core.sh \
  bootstrap/gitops/200-connect-private-gitops-repository.sh \
  bootstrap/load-balancer/100-install-aws-load-balancer-controller.sh \
  bootstrap/gitops/manifests/car-community-appproject.yaml \
  bootstrap/gitops/manifests/car-community-stg-application.yaml \
  bootstrap/application/100-provision-database-access.sh \
  bootstrap/application/200-apply-application-prerequisites.sh \
  lib/application-secret.sh \
  operations/application/reconcile-community-secret.sh \
  operations/application/reconcile-notification-secret.sh \
  operations/application/reconcile-catalog-redis-secrets.sh \
  operations/gitops/rotate-private-gitops-repository-credential.sh

# 2. 생성한 archive entry 조회
# 확인: 선택한 file만 있고 ._* 항목 없음
printf '\n\n========== [2/2] Script archive 내용 ==========\n\n'
tar -tzf /tmp/car-community-scripts.tar.gz
```

1. Archive 생성은 검증한 고정 inventory만 전달 artifact로 묶습니다.
2. Entry 조회는 누락·추가 file과 macOS 확장 속성 항목을 전송 전에 차단합니다.

| 표현 | 의미와 선택 이유 |
| --- | --- |
| `COPYFILE_DISABLE=1` | macOS가 file의 확장 속성을 `._*` AppleDouble 항목으로 archive에 추가하지 않게 합니다. 다른 OS에서 해석하지 않으면 `tar`에만 전달되는 환경변수로 남고 archive 내용에는 포함되지 않습니다. |
| `--no-xattrs` | macOS 확장 속성을 PAX header의 `LIBARCHIVE.xattr.*`·`SCHILY.xattr.*` 항목으로 기록하지 않습니다. Linux `tar`에서 알 수 없는 확장 header 경고가 발생하지 않게 합니다. |
| `-c` | 새 archive를 생성합니다. |
| `-z` | gzip으로 압축합니다. |
| `-f /tmp/car-community-scripts.tar.gz` | 바로 다음 값을 결과 archive 경로로 사용합니다. Local 임시 전달 artifact이므로 `/tmp`에 둡니다. |
| `-C scripts` | Script를 선택하기 전에 repository의 script root로 이동해 archive 안에 `bootstrap`·`verify`·`operations` 생애주기 경로를 보존합니다. |
| `tar -tzf` | 전송 전에 archive 경로를 나열합니다. 위에서 선택한 열아홉 file만 있고 `._*` 항목이 없어야 합니다. macOS `tar`는 자신이 만든 확장 속성 header를 경고 없이 읽을 수 있으므로 이 목록만으로 `--no-xattrs` 적용을 대신 검증하지 않습니다. |
| `\` | 하나의 긴 shell 명령을 다음 줄에서 계속 작성합니다. |

목록에 `._*` 같은 추가 항목이 있으면 checksum을 만들거나 전송하지 않습니다.
`COPYFILE_DISABLE=1`과 `--no-xattrs`가 모두 적용됐는지 확인하고 archive를 다시 생성한 뒤
목록부터 재검증합니다.

### 2. Local에서 SHA-256 file 생성

| 구분 | 값 |
| --- | --- |
| 실행 위치 | Local terminal |
| 시작 directory | `car-community` repository root |

```bash
shasum -a 256 /tmp/car-community-scripts.tar.gz \
  > /tmp/car-community-scripts.tar.gz.sha256
```

| 표현 | 의미와 선택 이유 |
| --- | --- |
| `shasum -a 256` | SHA-256 algorithm으로 archive digest를 계산합니다. |
| `>` | 출력된 digest와 `/tmp` 절대 archive 경로를 checksum file로 기록합니다. Local과 EC2가 같은 경로를 사용하므로 현재 directory와 관계없이 검증할 수 있습니다. 기존 같은 이름의 임시 file은 새 결과로 교체합니다. |

### 3. Local에서 EC2로 전송

| 구분 | 값 |
| --- | --- |
| 실행 위치 | Local terminal |
| 시작 directory | `car-community` repository root |

```bash
scp -i "<KEY_PATH>" \
  /tmp/car-community-scripts.tar.gz \
  /tmp/car-community-scripts.tar.gz.sha256 \
  "ubuntu@<EC2_SSH_HOST>:/tmp/"
```

| 표현 | 의미와 선택 이유 |
| --- | --- |
| `scp` | SSH 연결을 통해 local file을 EC2로 복사합니다. |
| `-i "<KEY_PATH>"` | 현재 SSH 접속에 사용하는 private key를 명시합니다. |
| 두 `/tmp/...` 인자 | Archive와 그 archive를 검증할 checksum file을 함께 전송합니다. |
| `ubuntu@<EC2_SSH_HOST>` | Ubuntu AMI의 SSH 사용자와 현재 접속 주소입니다. |
| `:/tmp/` | EC2에서 checksum 확인과 압축 해제 전까지만 사용할 전달 위치입니다. |

### 4. EC2에서 검증하고 압축 해제

#### 4.1 Local — EC2 SSH session 시작

**실행 위치:** Local terminal

**시작 directory:** 제한 없음

먼저 local terminal에서 같은 SSH 정보로 EC2에 접속합니다.

```bash
ssh -i "<KEY_PATH>" "ubuntu@<EC2_SSH_HOST>"
```

| 표현 | 의미와 선택 이유 |
| --- | --- |
| `ssh` | EC2에서 후속 명령을 입력할 원격 shell session을 시작합니다. |
| `-i "<KEY_PATH>"` | `scp`와 같은 private key를 사용합니다. |
| `ubuntu@<EC2_SSH_HOST>` | Ubuntu AMI 사용자로 같은 EC2 SSH endpoint에 접속합니다. |

#### 4.2 EC2 — Archive 검증

**실행 위치:** 앞 절에서 접속한 target EC2 terminal

**시작 directory:** 제한 없음

이후 명령은 모두 **EC2 server terminal**에서 실행합니다.

```bash
# 1. 전달 archive checksum 검증
# 확인: /tmp/car-community-scripts.tar.gz: OK
printf '\n\n========== [1/2] 전달 archive checksum ==========\n\n'
sha256sum --check /tmp/car-community-scripts.tar.gz.sha256

# 2. 전달 archive entry 조회
# 확인: 고정 inventory만 있고 extended header 경고 없음
printf '\n\n========== [2/2] 전달 archive 내용 ==========\n\n'
tar -tzf /tmp/car-community-scripts.tar.gz
```

1. Checksum은 전송 중 archive byte가 바뀌지 않았는지 확인합니다.
2. Entry 조회는 압축 해제 전에 내용과 확장 header를 다시 확인합니다.

| 표현 | 의미와 선택 이유 |
| --- | --- |
| `sha256sum --check <file>` | Checksum file을 읽어 현재 archive의 digest와 비교합니다. `OK`가 아니면 압축을 풀지 않습니다. |
| `tar -t` | 압축을 풀기 전에 archive 안의 열아홉 file 이름을 나열합니다. 추가 항목이 없어야 하고 알 수 없는 extended header 경고가 없어야 합니다. |
| `-z` | gzip 압축을 읽습니다. |
| `-f` | 바로 다음 값을 읽을 archive로 지정합니다. |

Checksum이 `OK`이고 목록이 정확하며 extended header 경고가 없으면 EC2의 고정 실행
directory에 압축을 풉니다. `LIBARCHIVE.xattr.*`나 `SCHILY.xattr.*` 경고가 나오면 압축을
풀지 않고 Local에서 두 제외 설정을 모두 적용해 archive와 checksum을 다시 생성·전송합니다.

#### 4.3 EC2 — Archive 압축 해제와 mode 적용

**실행 위치:** 앞 절에서 접속한 target EC2 terminal

**시작 directory:** 제한 없음

```bash
# 1. 고정 실행 directory 준비
# 확인: directory가 존재하며 오류 없이 exit 0
printf '\n\n========== [1/4] 실행 directory 준비 ==========\n\n'
mkdir -p /home/ubuntu/car-community-scripts

# 2. 검증한 archive 압축 해제
# 확인: 고정 inventory file이 destination에 생성됨
printf '\n\n========== [2/4] Archive 압축 해제 ==========\n\n'
tar -xzf /tmp/car-community-scripts.tar.gz \
  -C /home/ubuntu/car-community-scripts

# 3. 실행 script mode 적용
# 확인: 나열한 executable이 mode 0755
printf '\n\n========== [3/4] Script mode 적용 ==========\n\n'
chmod 0755 \
  /home/ubuntu/car-community-scripts/bootstrap/kubernetes/100-prepare-host.sh \
  /home/ubuntu/car-community-scripts/bootstrap/kubernetes/200-install-containerd.sh \
  /home/ubuntu/car-community-scripts/bootstrap/kubernetes/300-install-kubernetes-components.sh \
  /home/ubuntu/car-community-scripts/bootstrap/kubernetes/400-initialize-cluster.sh \
  /home/ubuntu/car-community-scripts/bootstrap/kubernetes/500-install-calico.sh \
  /home/ubuntu/car-community-scripts/bootstrap/ecr/100-configure-kubelet-credential-provider.sh \
  /home/ubuntu/car-community-scripts/verify/ecr/verify-private-image-pull.sh \
  /home/ubuntu/car-community-scripts/bootstrap/gitops/100-install-argocd-core.sh \
  /home/ubuntu/car-community-scripts/bootstrap/gitops/200-connect-private-gitops-repository.sh \
  /home/ubuntu/car-community-scripts/bootstrap/load-balancer/100-install-aws-load-balancer-controller.sh \
  /home/ubuntu/car-community-scripts/bootstrap/application/100-provision-database-access.sh \
  /home/ubuntu/car-community-scripts/bootstrap/application/200-apply-application-prerequisites.sh \
  /home/ubuntu/car-community-scripts/operations/application/reconcile-community-secret.sh \
  /home/ubuntu/car-community-scripts/operations/application/reconcile-notification-secret.sh \
  /home/ubuntu/car-community-scripts/operations/application/reconcile-catalog-redis-secrets.sh \
  /home/ubuntu/car-community-scripts/operations/gitops/rotate-private-gitops-repository-credential.sh

# 4. Read-only source mode 적용
# 확인: library와 manifest가 mode 0644
printf '\n\n========== [4/4] Read-only source mode 적용 ==========\n\n'
chmod 0644 \
  /home/ubuntu/car-community-scripts/lib/application-secret.sh \
  /home/ubuntu/car-community-scripts/bootstrap/gitops/manifests/car-community-appproject.yaml \
  /home/ubuntu/car-community-scripts/bootstrap/gitops/manifests/car-community-stg-application.yaml
```

1. 실행 directory가 없으면 만들고 기존 directory는 유지합니다.
2. Checksum과 entry를 확인한 archive만 고정 destination에 풉니다.
3. 사람이 실행할 script에 executable mode를 적용합니다.
4. Library와 manifest는 executable이 아닌 readable source로 유지합니다.

| 표현 | 의미와 선택 이유 |
| --- | --- |
| `mkdir -p` | Directory가 없으면 만들고 이미 있으면 오류 없이 유지합니다. |
| `tar -x` | Archive의 file을 압축 해제합니다. |
| `-C /home/ubuntu/car-community-scripts` | 압축 해제 destination을 고정합니다. |
| `chmod 0755` | `ubuntu`는 읽기·쓰기·실행, group과 others는 읽기·실행만 가능하게 합니다. Script에는 secret이 없습니다. |

다음 확인이 모두 성공해야 전달 단계가 끝납니다.

```bash
test -x /home/ubuntu/car-community-scripts/bootstrap/kubernetes/100-prepare-host.sh &&
test -x /home/ubuntu/car-community-scripts/bootstrap/kubernetes/200-install-containerd.sh &&
test -x /home/ubuntu/car-community-scripts/bootstrap/kubernetes/300-install-kubernetes-components.sh &&
test -x /home/ubuntu/car-community-scripts/bootstrap/kubernetes/400-initialize-cluster.sh &&
test -x /home/ubuntu/car-community-scripts/bootstrap/kubernetes/500-install-calico.sh &&
test -x /home/ubuntu/car-community-scripts/bootstrap/ecr/100-configure-kubelet-credential-provider.sh &&
test -x /home/ubuntu/car-community-scripts/verify/ecr/verify-private-image-pull.sh &&
test -x /home/ubuntu/car-community-scripts/bootstrap/gitops/100-install-argocd-core.sh &&
test -x /home/ubuntu/car-community-scripts/bootstrap/gitops/200-connect-private-gitops-repository.sh &&
test -x /home/ubuntu/car-community-scripts/bootstrap/load-balancer/100-install-aws-load-balancer-controller.sh &&
test -r /home/ubuntu/car-community-scripts/bootstrap/gitops/manifests/car-community-appproject.yaml &&
test -r /home/ubuntu/car-community-scripts/bootstrap/gitops/manifests/car-community-stg-application.yaml &&
test -x /home/ubuntu/car-community-scripts/bootstrap/application/100-provision-database-access.sh &&
test -x /home/ubuntu/car-community-scripts/bootstrap/application/200-apply-application-prerequisites.sh &&
test -r /home/ubuntu/car-community-scripts/lib/application-secret.sh &&
test ! -x /home/ubuntu/car-community-scripts/lib/application-secret.sh &&
test -x /home/ubuntu/car-community-scripts/operations/application/reconcile-community-secret.sh &&
test -x /home/ubuntu/car-community-scripts/operations/application/reconcile-notification-secret.sh &&
test -x /home/ubuntu/car-community-scripts/operations/application/reconcile-catalog-redis-secrets.sh &&
test -x /home/ubuntu/car-community-scripts/operations/gitops/rotate-private-gitops-repository-credential.sh &&
printf '%s\n' 'EC2 script 실행 권한 확인 완료'
```

`test -x`는 고정 EC2 절대 경로의 각 script가 존재하고 실행 가능한지를 확인합니다.
`&&`로 연결했으므로 열아홉 file 검사가 모두 exit code `0`일 때만
`EC2 script 실행 권한 확인 완료`가 출력됩니다.

Checksum 불일치, 예상하지 않은 archive 항목 또는 file 누락이 있으면 script를 실행하지
않습니다. Local의 archive·checksum file을 다시 생성해 둘을 함께 재전송하고 이 절차를
처음부터 검증합니다. 최초 AWS 구축 흐름에서 진입했다면 성공 뒤
[200 Kubernetes bootstrap](200-kubernetes-bootstrap-runbook.md)으로 인계합니다. `330`·`340`·
`350`·`370`·`410`·`520`·`540`에서 inventory 갱신 때문에 재전달했다면 새 bootstrap을
시작하지 않고 이 runbook을 호출한 절차의 중단 지점으로 돌아갑니다.
