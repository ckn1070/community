# Kubernetes bootstrap runbook

Ubuntu 24.04 ARM64 host를 kubeadm 기반 단일 node Kubernetes cluster로 구축하는 최초
운영 진입점입니다. 이 문서는 실행 파일 전달과 필수 실행 순서를 소유하고, 각 단계의
명령·성공 결과·복구는 연결된 단계별 runbook이 소유합니다. Cluster가 작동하는 방식과
선택 근거는 [Kubernetes cluster 개념](../project/infrastructure/140-kubernetes-cluster.md)이
소유합니다.

AWS resource 생성과 EC2 접속은
[AWS 최초 구축 runbook](100-aws-initial-provisioning-runbook.md)이 선행합니다.
실제 EC2에서 필수 다섯 단계를 모두 성공해야 일반 workload를 배치할 수 있는 빈 cluster
구축이 끝납니다.

## 사용 시점과 actor

운영자가 local repository를 사용할 수 있고 staging Ubuntu EC2에 private key 기반 SSH로
접속할 수 있을 때 local terminal에서 시작합니다. Bootstrap script는 AWS API나 instance
metadata를 호출하지 않습니다.

## 공통 전제와 고정 계약

| 항목 | 현재 계약 |
| --- | --- |
| 운영체제·architecture | Ubuntu Server `24.04`, `arm64` |
| 권한 | 운영자는 `sudo`를 사용할 수 있음 |
| Outbound network | Ubuntu APT, `pkgs.k8s.io`, `raw.githubusercontent.com`, `registry.k8s.io`, `quay.io` 접근 가능 |
| Kubernetes | Semantic version `1.36.2`, Debian package `1.36.2-2.1` |
| Container runtime | Ubuntu containerd package `2.2.1-0ubuntu1~24.04.3` |
| CNI | Calico `3.32.1`, 일반 iptables dataplane과 kube-proxy 유지 |
| VPC·cluster network | VPC `10.0.0.0/16`, Pod `192.168.0.0/16`, Service `10.96.0.0/12`, DNS domain `cluster.local` |
| Cluster 형태 | Control plane과 workload를 함께 실행하는 단일 node IPv4 cluster |
| 전달 방식 | Bootstrap 열 개, 반복 검증 한 개와 운영 변경 한 개를 생애주기 경로 그대로 `tar.gz`로 묶어 SHA-256 file과 함께 SSH `scp`로 한 번 전달 |
| EC2 실행 위치 | 사용자 `ubuntu`, `/home/ubuntu/car-community-scripts` |
| 동시 변경 | 다른 운영자가 같은 host의 package, swap, sysctl, firewall이나 cluster를 동시에 변경하지 않음 |

Version과 CIDR은 실행 날짜의 최신값으로 자동 교체하지 않습니다. Pod·Service CIDR은 VPC와
겹치지 않아야 하며, peering·VPN·조직 network와 충돌할 가능성이 있으면 cluster 초기화
전에 다시 결정해야 합니다. IPv6·dual-stack은 현재 범위가 아닙니다.

이 표는 사람이 실행 전에 확인할 고정 입력입니다. Version 조합, network와 단일 node
선택의 근거·대안·재검토 조건은
[Kubernetes cluster 개념](../project/infrastructure/140-kubernetes-cluster.md)에서 확인합니다.

## EC2 실행 파일 전달

### 전달 대상과 실행 경계

다음 열세 executable과 세 readable source를 archive로 묶되 생애주기 경로를 유지합니다.

- `bootstrap/kubernetes/100-prepare-host.sh`
- `bootstrap/kubernetes/200-install-containerd.sh`
- `bootstrap/kubernetes/300-install-kubernetes-components.sh`
- `bootstrap/kubernetes/400-initialize-cluster.sh`
- `bootstrap/kubernetes/500-install-calico.sh`
- `bootstrap/ecr/100-configure-kubelet-credential-provider.sh`
- `verify/ecr/verify-private-image-pull.sh`
- `bootstrap/gitops/100-install-argocd-core.sh`
- `bootstrap/gitops/200-connect-private-gitops-repository.sh`
- `bootstrap/gitops/manifests/car-community-appproject.yaml`
- `bootstrap/gitops/manifests/car-community-stg-application.yaml`
- `bootstrap/application/100-provision-database-access.sh`
- `bootstrap/application/200-apply-application-prerequisites.sh`
- `lib/application-secret.sh`
- `operations/application/reconcile-notification-secret.sh`
- `operations/gitops/rotate-private-gitops-repository-credential.sh`

Application source, Dockerfile, 문서, local contract test와 credential은 archive에 넣지
않습니다. 전달 방식·제외 범위·checksum 보증 범위와 S3·Systems Manager 재검토 조건은
[Kubernetes cluster 개념](../project/infrastructure/140-kubernetes-cluster.md#실행-artifact-전달-경계)이
소유합니다.

열여섯 file 사이의 source 관계와 archive 밖 repository file을 읽지 않는 구현 의존성 경계는
[Repository script 목차의 EC2 전달 archive 구현 의존성](../../scripts/README.md#ec2-전달-archive-구현-의존성)이
소유합니다. 이 절차는 그 계약에 따라 repository 전체를 EC2에 clone하지 않고 열여섯 file만
전달합니다.

Archive를 한 번 전달해도 각 script의 실행 dependency는 바뀌지 않습니다.
`bootstrap/kubernetes/100`~`500`으로 빈 cluster를 완성하고 AWS IAM·ECR와 published digest가 준비된 뒤
[Kubelet ECR credential provider 설치](330-kubelet-ecr-credential-provider-installation-runbook.md)와
[Private ECR image pull 검증](410-private-ecr-image-pull-verification-runbook.md)에 따라
최초 구성과 반복 점검을 각각 실행합니다. Argo CD Core는 cluster 완료 뒤 설치할 수 있습니다.
DB role과 Git 밖 application resource는
[Application bootstrap README](../../scripts/bootstrap/application/README.md)에 따라 실행합니다.
Private repository 연결은 실제 digest의 `deploy/` overlay·GitHub App과 ECR pull이
준비된 뒤 실행하고 credential 교체 operations script는 최초 연결 완료 뒤에만 사용합니다.

명령의 placeholder는 실행 전에 다음 실제 값으로 바꿉니다.

| Placeholder | 실행 위치 | 의미 |
| --- | --- | --- |
| `<LOCAL_REPOSITORY_ROOT>` | Local | `scripts`가 바로 아래에 있는 repository 절대 경로 |
| `<KEY_PATH>` | Local | 현재 EC2 SSH 접속에 사용하는 private key file 경로 |
| `<EC2_SSH_HOST>` | Local | 현재 SSH 접속에 사용하는 EC2 public IPv4 또는 public DNS |

#### 1. Local에서 archive 생성

| 구분 | 값 |
| --- | --- |
| 실행 위치 | Local terminal |
| 시작 directory | 어디든 가능 |
| 작업 directory | `<LOCAL_REPOSITORY_ROOT>` |

```bash
cd "<LOCAL_REPOSITORY_ROOT>"
```

정확한 열세 executable과 세 readable source가 있고 local 수정이 섞이지 않았는지 확인합니다.

```bash
test -x scripts/bootstrap/kubernetes/100-prepare-host.sh &&
test -x scripts/bootstrap/kubernetes/200-install-containerd.sh &&
test -x scripts/bootstrap/kubernetes/300-install-kubernetes-components.sh &&
test -x scripts/bootstrap/kubernetes/400-initialize-cluster.sh &&
test -x scripts/bootstrap/kubernetes/500-install-calico.sh &&
test -x scripts/bootstrap/ecr/100-configure-kubelet-credential-provider.sh &&
test -x scripts/verify/ecr/verify-private-image-pull.sh &&
test -x scripts/bootstrap/gitops/100-install-argocd-core.sh &&
test -x scripts/bootstrap/gitops/200-connect-private-gitops-repository.sh &&
test -r scripts/bootstrap/gitops/manifests/car-community-appproject.yaml &&
test ! -x scripts/bootstrap/gitops/manifests/car-community-appproject.yaml &&
test -r scripts/bootstrap/gitops/manifests/car-community-stg-application.yaml &&
test ! -x scripts/bootstrap/gitops/manifests/car-community-stg-application.yaml &&
test -x scripts/bootstrap/application/100-provision-database-access.sh &&
test -x scripts/bootstrap/application/200-apply-application-prerequisites.sh &&
test -r scripts/lib/application-secret.sh &&
test ! -x scripts/lib/application-secret.sh &&
test -x scripts/operations/application/reconcile-notification-secret.sh &&
test -x scripts/operations/gitops/rotate-private-gitops-repository-credential.sh &&
printf '%s\n' 'Local script 실행 권한 확인 완료'

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
  scripts/bootstrap/gitops/manifests/car-community-appproject.yaml \
  scripts/bootstrap/gitops/manifests/car-community-stg-application.yaml \
  scripts/bootstrap/application/100-provision-database-access.sh \
  scripts/bootstrap/application/200-apply-application-prerequisites.sh \
  scripts/lib/application-secret.sh \
  scripts/operations/application/reconcile-notification-secret.sh \
  scripts/operations/gitops/rotate-private-gitops-repository-credential.sh
```

| 표현 | 의미와 선택 이유 |
| --- | --- |
| `test -x <path>` | 해당 path가 존재하고 현재 사용자가 실행할 수 있는 file인지 확인합니다. |
| `&&` | 앞의 검사가 성공했을 때만 다음 검사를 실행합니다. 하나라도 실패하면 마지막 완료 문구가 출력되지 않습니다. |
| `printf` | 열세 executable과 세 readable source 검사가 모두 성공했음을 화면에 명시적으로 출력합니다. |
| `git status --short` | 선택한 script의 Git 변경 상태를 짧은 형식으로 출력합니다. |
| Option separator `--` | 뒤의 값을 Git option이 아니라 확인할 file path로 고정합니다. |

`Local script 실행 권한 확인 완료`가 출력되고 `git status`는 아무것도 출력하지 않아야
합니다. 완료 문구가 없거나 Git 상태가 출력되면 그 내용을 전달 대상으로 임의 확정하지
않고 local file과 변경을 먼저 확인합니다. 다음 명령으로 archive를 생성합니다.

```bash
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
  bootstrap/gitops/manifests/car-community-appproject.yaml \
  bootstrap/gitops/manifests/car-community-stg-application.yaml \
  bootstrap/application/100-provision-database-access.sh \
  bootstrap/application/200-apply-application-prerequisites.sh \
  lib/application-secret.sh \
  operations/application/reconcile-notification-secret.sh \
  operations/gitops/rotate-private-gitops-repository-credential.sh

tar -tzf /tmp/car-community-scripts.tar.gz
```

| 표현 | 의미와 선택 이유 |
| --- | --- |
| `COPYFILE_DISABLE=1` | macOS가 file의 확장 속성을 `._*` AppleDouble 항목으로 archive에 추가하지 않게 합니다. 다른 OS에서 해석하지 않으면 `tar`에만 전달되는 환경변수로 남고 archive 내용에는 포함되지 않습니다. |
| `--no-xattrs` | macOS 확장 속성을 PAX header의 `LIBARCHIVE.xattr.*`·`SCHILY.xattr.*` 항목으로 기록하지 않습니다. Linux `tar`에서 알 수 없는 확장 header 경고가 발생하지 않게 합니다. |
| `-c` | 새 archive를 생성합니다. |
| `-z` | gzip으로 압축합니다. |
| `-f /tmp/car-community-scripts.tar.gz` | 바로 다음 값을 결과 archive 경로로 사용합니다. Local 임시 전달 artifact이므로 `/tmp`에 둡니다. |
| `-C scripts` | Script를 선택하기 전에 repository의 script root로 이동해 archive 안에 `bootstrap`·`verify`·`operations` 생애주기 경로를 보존합니다. |
| `tar -tzf` | 전송 전에 archive 경로를 나열합니다. 위에서 선택한 열여섯 file만 있고 `._*` 항목이 없어야 합니다. macOS `tar`는 자신이 만든 확장 속성 header를 경고 없이 읽을 수 있으므로 이 목록만으로 `--no-xattrs` 적용을 대신 검증하지 않습니다. |
| `\` | 하나의 긴 shell 명령을 다음 줄에서 계속 작성합니다. |

목록에 `._*` 같은 추가 항목이 있으면 checksum을 만들거나 전송하지 않습니다.
`COPYFILE_DISABLE=1`과 `--no-xattrs`가 모두 적용됐는지 확인하고 archive를 다시 생성한 뒤
목록부터 재검증합니다.

#### 2. Local에서 SHA-256 file 생성

| 구분 | 값 |
| --- | --- |
| 실행 위치 | Local terminal |
| 현재 directory | `<LOCAL_REPOSITORY_ROOT>` |

```bash
(
  cd /tmp
  shasum -a 256 car-community-scripts.tar.gz \
    > car-community-scripts.tar.gz.sha256
)
```

| 표현 | 의미와 선택 이유 |
| --- | --- |
| `( ... )` | 내부 명령을 subshell에서 실행해, 완료 후 local terminal의 현재 directory가 repository root로 유지되게 합니다. |
| `cd /tmp` | Checksum file 안에 archive의 절대 경로 대신 file 이름만 기록되게 합니다. |
| `shasum -a 256` | SHA-256 algorithm으로 archive digest를 계산합니다. |
| `>` | 출력된 digest와 file 이름을 checksum file로 기록합니다. 기존 같은 이름의 임시 file은 새 결과로 교체합니다. |

#### 3. Local에서 EC2로 전송

| 구분 | 값 |
| --- | --- |
| 실행 위치 | Local terminal |
| 현재 directory | `<LOCAL_REPOSITORY_ROOT>` |

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

#### 4. EC2에서 검증하고 압축 해제

먼저 local terminal에서 같은 SSH 정보로 EC2에 접속합니다.

```bash
ssh -i "<KEY_PATH>" "ubuntu@<EC2_SSH_HOST>"
```

| 표현 | 의미와 선택 이유 |
| --- | --- |
| `ssh` | EC2에서 후속 명령을 입력할 원격 shell session을 시작합니다. |
| `-i "<KEY_PATH>"` | `scp`와 같은 private key를 사용합니다. |
| `ubuntu@<EC2_SSH_HOST>` | Ubuntu AMI 사용자로 같은 EC2 SSH endpoint에 접속합니다. |

이후 명령은 모두 **EC2 server terminal**에서 실행합니다.

```bash
cd /tmp
sha256sum --check car-community-scripts.tar.gz.sha256
tar -tzf car-community-scripts.tar.gz
```

| 표현 | 의미와 선택 이유 |
| --- | --- |
| `sha256sum --check <file>` | Checksum file을 읽어 현재 archive의 digest와 비교합니다. `OK`가 아니면 압축을 풀지 않습니다. |
| `tar -t` | 압축을 풀기 전에 archive 안의 열여섯 file 이름을 나열합니다. 추가 항목이 없어야 하고 알 수 없는 extended header 경고가 없어야 합니다. |
| `-z` | gzip 압축을 읽습니다. |
| `-f` | 바로 다음 값을 읽을 archive로 지정합니다. |

Checksum이 `OK`이고 목록이 정확하며 extended header 경고가 없으면 EC2의 고정 실행
directory에 압축을 풉니다. `LIBARCHIVE.xattr.*`나 `SCHILY.xattr.*` 경고가 나오면 압축을
풀지 않고 Local에서 두 제외 설정을 모두 적용해 archive와 checksum을 다시 생성·전송합니다.

```bash
mkdir -p /home/ubuntu/car-community-scripts
tar -xzf /tmp/car-community-scripts.tar.gz \
  -C /home/ubuntu/car-community-scripts
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
  /home/ubuntu/car-community-scripts/bootstrap/application/100-provision-database-access.sh \
  /home/ubuntu/car-community-scripts/bootstrap/application/200-apply-application-prerequisites.sh \
  /home/ubuntu/car-community-scripts/operations/application/reconcile-notification-secret.sh \
  /home/ubuntu/car-community-scripts/operations/gitops/rotate-private-gitops-repository-credential.sh
chmod 0644 \
  /home/ubuntu/car-community-scripts/lib/application-secret.sh \
  /home/ubuntu/car-community-scripts/bootstrap/gitops/manifests/car-community-appproject.yaml \
  /home/ubuntu/car-community-scripts/bootstrap/gitops/manifests/car-community-stg-application.yaml
cd /home/ubuntu/car-community-scripts
```

| 표현 | 의미와 선택 이유 |
| --- | --- |
| `mkdir -p` | Directory가 없으면 만들고 이미 있으면 오류 없이 유지합니다. |
| `tar -x` | Archive의 file을 압축 해제합니다. |
| `-C /home/ubuntu/car-community-scripts` | 압축 해제 destination을 고정합니다. |
| `chmod 0755` | `ubuntu`는 읽기·쓰기·실행, group과 others는 읽기·실행만 가능하게 합니다. Script에는 secret이 없습니다. |

다음 확인이 모두 성공해야 전달 단계가 끝납니다.

```bash
test -x ./bootstrap/kubernetes/100-prepare-host.sh &&
test -x ./bootstrap/kubernetes/200-install-containerd.sh &&
test -x ./bootstrap/kubernetes/300-install-kubernetes-components.sh &&
test -x ./bootstrap/kubernetes/400-initialize-cluster.sh &&
test -x ./bootstrap/kubernetes/500-install-calico.sh &&
test -x ./bootstrap/ecr/100-configure-kubelet-credential-provider.sh &&
test -x ./verify/ecr/verify-private-image-pull.sh &&
test -x ./bootstrap/gitops/100-install-argocd-core.sh &&
test -x ./bootstrap/gitops/200-connect-private-gitops-repository.sh &&
test -r ./bootstrap/gitops/manifests/car-community-appproject.yaml &&
test -r ./bootstrap/gitops/manifests/car-community-stg-application.yaml &&
test -x ./bootstrap/application/100-provision-database-access.sh &&
test -x ./bootstrap/application/200-apply-application-prerequisites.sh &&
test -r ./lib/application-secret.sh &&
test ! -x ./lib/application-secret.sh &&
test -x ./operations/application/reconcile-notification-secret.sh &&
test -x ./operations/gitops/rotate-private-gitops-repository-credential.sh &&
printf '%s\n' 'EC2 script 실행 권한 확인 완료'
```

`test -x`는 현재 EC2 directory의 각 script가 존재하고 실행 가능한지를 확인합니다.
`&&`로 연결했으므로 열여섯 file 검사가 모두 exit code `0`일 때만
`EC2 script 실행 권한 확인 완료`가 출력됩니다.

Checksum 불일치, 예상하지 않은 archive 항목 또는 file 누락이 있으면 script를 실행하지
않습니다. Local의 archive·checksum file을 다시 생성해 둘을 함께 재전송하고 이 절차를
처음부터 검증합니다. 성공하면 현재 EC2 directory를 유지한 채
[Host 준비](210-kubernetes-host-preparation-runbook.md)로 진행합니다.

## 필수 실행 순서

다음 순서대로 한 단계씩 실행합니다. 각 단계가 nonzero로 종료되거나 해당 문서의 성공
결과를 충족하지 못하면 다음 단계로 진행하지 않습니다.

| 순서 | 실행 절차 | 입력 상태 | 다음 단계에 넘기는 결과 |
| --- | --- | --- | --- |
| 1 | [Host 준비](210-kubernetes-host-preparation-runbook.md) — `100-prepare-host.sh` | `/home/ubuntu/car-community-scripts`에 검증·배치된 script | Package 갱신, swap 비활성화, IPv4 forwarding, 재부팅 완료 상태 |
| 2 | [Containerd 설치](220-containerd-installation-runbook.md) — `200-install-containerd.sh` | 재부팅 필요가 없는 준비된 host | Enabled·active containerd와 CRI socket |
| 3 | [Kubernetes component 설치](230-kubernetes-components-installation-runbook.md) — `300-install-kubernetes-components.sh` | 정상 containerd runtime | Exact `kubelet`·`kubeadm`·`kubectl`과 official repository |
| 4 | [Control plane 초기화](240-kubernetes-control-plane-initialization-runbook.md) — `400-initialize-cluster.sh` | Runtime과 Kubernetes component 준비 완료 | API server·PKI·kubeconfig·etcd·control-plane·kube-proxy, CNI 전 중간 상태 |
| 5 | [Calico network 구성](250-calico-network-installation-runbook.md) — `500-install-calico.sh` | 정상 control plane과 CNI가 없는 cluster | Calico, Ready node·CoreDNS와 일반 workload network |

`100`이 재부팅 필요를 보고하면 EC2를 재부팅·재접속하고 `100`을 다시 실행합니다. 이
재검증이 성공하기 전에는 `200`을 실행하지 않습니다.

Control plane 초기화와 Calico 설치는 실패 후 재시작 지점이 달라 분리되어 있습니다.
`kubeadm init`의 부분 상태는 자동 반복하거나 reset하면 안 되지만, 정상 control plane을
유지한 채 Calico download·apply 실패는 `500`만 다시 실행할 수 있습니다.

## 선택 절차와 예외 경로

- 일반 사용자가 `kubectl`을 사용해야 할 때만
  [Admin kubeconfig 배포](510-kubernetes-admin-kubeconfig-runbook.md)를 실행합니다. 필수
  bootstrap 단계가 아니며 `400`과 `500`은 root의 `/etc/kubernetes/admin.conf`를 직접
  사용합니다.
- 정상 오류를 해결해 같은 단계로 재진입하는 대신 구축 자체를 의도적으로 되돌릴 때만
  [Kubernetes bootstrap 취소](500-kubernetes-bootstrap-cancellation-runbook.md)를
  사용합니다. 이 문서는 현재 허용된 backup 복원 경계만 제공하며 임의의
  `kubeadm reset`이나 data 삭제 절차를 만들지 않습니다.

## 전체 완료 결과

필수 다섯 단계가 실제 EC2에서 모두 성공하면 다음 상태입니다.

- Kubernetes `1.36.2` API server, local etcd와 control-plane component가 동작합니다.
- Calico와 kube-proxy가 동작하고 단일 node와 CoreDNS가 Ready입니다.
- Control-plane taint가 제거되어 일반 Pod를 배치할 수 있습니다.
- 임시 일반 workload로 namespace DNS, ClusterIP와 backend TCP 통신을 검증했습니다.
- 임시 smoke namespace가 삭제되어 애플리케이션이 없는 빈 cluster로 남습니다.

Repository의 script·contract test·Git 상태는 실제 EC2 완료 증거를 대신하지 않습니다.
실제 host 실행 전에는 문서와 local source가 정합하다는 것만 확인할 수 있습니다.

## 후속 범위

이 bootstrap 완료는 서비스 infrastructure 전체 완료가 아닙니다. 다음은 별도 절차입니다.

- Private ECR image 공급과 [exact digest pull 검증](410-private-ecr-image-pull-verification-runbook.md)
- Application bootstrap·manifest의 RDS role, ConfigMap·Secret과 workload 적용
- AWS Load Balancer Controller, instance·NodePort target, Ingress와 ALB
- `node.kubernetes.io/exclude-from-external-load-balancers` label 제거·검증
- HTTPS·domain·DNS
- [Repository·delivery platform bootstrap](300-repository-and-delivery-platform-bootstrap-runbook.md)의
  Core 설치·private repository 연결과 `deploy/` application sync

다중 AWS node로 확장하기 전에는 `VXLANCrossSubnet`과 EC2 source/destination check 또는
전체 `VXLAN` 전환을 다시 결정해야 합니다. Control plane HA·node 교체 전에는 stable
control-plane endpoint를 먼저 설계해야 합니다.

## 관련 문서

- **AWS 선행 절차**: [AWS 최초 구축 runbook](100-aws-initial-provisioning-runbook.md)
- **시스템 개념**: [현재 infrastructure system](../project/300-infrastructure-system.md)
- **Script 생애주기 목차**: [Repository script 목차](../../scripts/README.md)
- **Repository·delivery platform 후속 절차**: [Repository·delivery platform bootstrap](300-repository-and-delivery-platform-bootstrap-runbook.md)
- **전체 구축의 현재 위치**: [Staging 단일 node 전체 구축 시나리오](scenarios/100-staging-single-node-bootstrap.md)
- **운영 문서 탐색**: [운영 문서 목차](000-index.md)
