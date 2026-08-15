# Kubernetes admin kubeconfig 배포 runbook

일반 사용자가 `kubectl`을 사용해야 할 때만 `/etc/kubernetes/admin.conf`를 명시적으로
선택한 사용자 home에 배포하는 선택 절차입니다. Kubernetes bootstrap의 필수 단계가
아니며 control plane·Calico bootstrap script는 root credential을 직접 사용합니다.
Credential의 권한과 배포 경계는
[Kubernetes admin access](../project/infrastructure/320-kubernetes-admin-access.md)에서
확인합니다.

## 실행 계약

| 항목 | 계약 |
| --- | --- |
| 언제 | Control plane 완료 뒤 명시적으로 선택한 일반 사용자가 cluster-admin `kubectl`을 사용해야 할 때만 실행합니다. |
| 누가 | 대상 사용자와 cluster-admin 권한 부여 필요성을 결정하고 EC2에서 `sudo`를 사용할 수 있는 운영자가 실행합니다. |
| 어디서 | SSH로 접속한 target EC2 terminal입니다. |
| 무엇을 | 아래 `getent`·`id`·`install` 표준 명령으로 `/etc/kubernetes/admin.conf`를 선택한 home에 배포합니다. Repository script는 없습니다. |
| 왜 | Bootstrap과 일반 사용자 credential 배포를 분리하고 대상 계정·권한을 추측하지 않기 위해서입니다. |
| 완료·재개 | 대상 config의 owner·group·mode와 원본 보존을 확인합니다. 기존 config가 있으면 중단하고 병합 방식을 별도 결정합니다. |

## 사용 조건과 보안 경계

- [Control plane 초기화](240-kubernetes-control-plane-initialization-runbook.md)가 성공해
  `/etc/kubernetes/admin.conf`가 존재합니다.
- 운영자가 배포 대상 사용자와 그 사용자에게 cluster-admin 권한을 부여할 필요를 직접
  확정했습니다.
- 기존 `${target_home}/.kube/config`가 없습니다. 기존 file이 있으면 생성 주체와 context를
  확인하기 전에는 덮어쓰지 않습니다.
- `/etc/kubernetes/super-admin.conf`는 일반 사용자에게 배포하지 않습니다.

`admin.conf`는 cluster-admin credential입니다. 단순 편의를 위해 `$SUDO_USER`나 현재
shell 사용자를 배포 대상으로 추측하지 않습니다.

## 실행

`<명시적으로 선택한 사용자>`를 실제 대상 계정으로 바꿉니다.

```bash
target_user='<명시적으로 선택한 사용자>'
target_home="$(getent passwd "${target_user}" | cut -d: -f6)"
target_group="$(id -gn "${target_user}")"
test -n "${target_home}"
sudo test ! -e "${target_home}/.kube/config"
sudo install -d -m 0700 -o "${target_user}" -g "${target_group}" "${target_home}/.kube"
sudo install -m 0600 -o "${target_user}" -g "${target_group}" \
  /etc/kubernetes/admin.conf "${target_home}/.kube/config"
```

## 단계별 기대 결과

| 동작 | 확인할 결과 |
| --- | --- |
| 대상 account에서 home과 primary group 조회 | 실제 user metadata를 기준으로 설치 경로와 소유권을 얻습니다. |
| 기존 kubeconfig 부재 확인 | 다른 cluster context와 credential을 덮어쓰지 않습니다. |
| `.kube` directory를 mode `0700`으로 생성 | 대상 사용자만 접근 가능한 config directory가 생깁니다. |
| `admin.conf`를 mode `0600`, 대상 user·group 소유로 복사 | 대상 사용자가 cluster-admin credential로 `kubectl`을 사용할 수 있습니다. |

## 성공 결과

- 대상 사용자의 `${target_home}/.kube/config`가 mode `0600`이고 대상 user·group
  소유입니다.
- 원본 `/etc/kubernetes/admin.conf`는 변경되지 않습니다.
- `super-admin.conf`와 다른 사용자의 kubeconfig는 변경되지 않습니다.

## 중단과 복구

| 상황 | 대응 |
| --- | --- |
| 대상 사용자가 없거나 home·group 조회 실패 | 사용자 값을 바꾸어 추측하지 말고 실제 account를 먼저 확인합니다. |
| 기존 `.kube/config` 존재 | 덮어쓰지 않습니다. 기존 context·credential과 필요한 병합 방식을 별도 결정합니다. |
| Directory·file 설치 실패 | 권한·filesystem 원인을 해결하고 각 postcondition을 확인한 뒤 같은 명령을 다시 실행합니다. |
| 잘못된 사용자에게 배포한 사실을 발견 | Broad 삭제 명령을 실행하지 않습니다. 대상 account, file 소유권과 사용 여부를 확인해 credential 회수·교체 범위를 별도로 결정합니다. |

이 절차는 control plane 초기화 성공 뒤 사용할 수 있지만, 필수 bootstrap 단계 사이에 끼워
넣지 않고 별도 선택 문서로 분리해 core bootstrap 순서와 권한 배포를 구분합니다.

## 관련 문서

- **Admin credential 경계**: [Kubernetes admin access](../project/infrastructure/320-kubernetes-admin-access.md)
- **전체 실행 순서**: [Kubernetes bootstrap](200-kubernetes-bootstrap-runbook.md)
- **Admin kubeconfig 생성 단계**: [Control plane 초기화](240-kubernetes-control-plane-initialization-runbook.md)
- **Script의 credential 비배포 계약**: [Kubernetes bootstrap README](../../scripts/bootstrap/kubernetes/README.md#400-initialize-clustersh-공개-계약)
