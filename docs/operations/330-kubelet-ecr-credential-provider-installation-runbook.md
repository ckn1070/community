# Kubelet ECR credential provider 최초 설치 runbook

이 절차는 Kubernetes cluster와 첫 ECR image 게시가 모두 완료된 뒤 실행합니다.

이미 구축된 Kubernetes node에 official ECR exec credential provider를 최초 설치하고 실행
중 kubelet이 그 provider 설정을 읽는지 확인하는 절차입니다. 실제 private image pull은
[Exact digest pull 점검](410-private-ecr-image-pull-verification-runbook.md)이 소유합니다.

Provider가 EC2 instance profile의 임시 credential을 사용하는 구조와 선택 이유는
[130 ECR·IAM 기반](../project/infrastructure/130-ecr-iam-foundation.md)과
[210 Container image delivery](../project/infrastructure/210-container-image-delivery.md)에서
확인합니다. Artifact·version·checksum·file mutation의 정적 계약은
[ECR bootstrap README](../../scripts/bootstrap/ecr/README.md)가 소유합니다.

## 실행 계약

| 항목 | 계약 |
| --- | --- |
| 언제 | Kubernetes bootstrap, EC2 pull instance profile 연결과 첫 ECR image 게시가 완료된 뒤, 첫 private image pull 전에 최초 한 번 실행합니다. |
| 누가 | Target EC2에 SSH 접속하고 `sudo`를 사용할 수 있는 운영자가 실행합니다. |
| 기본 실행 위치 | EC2 terminal입니다. |
| 기본 시작 directory | 제한 없음 |
| 무엇을 | `sudo /home/ubuntu/car-community-scripts/bootstrap/ecr/100-configure-kubelet-credential-provider.sh` |
| 왜 | Kubelet이 EC2 instance profile의 임시 credential로 private ECR image를 pull하게 하기 위해서입니다. |
| 완료·재개 | Script exit `0`, 완료 메시지와 active kubelet 인자를 확인합니다. 실패하면 같은 script의 중단 표에 따라 재실행합니다. |

## 실행 전 확인

- [Kubernetes bootstrap](200-kubernetes-bootstrap-runbook.md)의 `100`~`500` live 단계가
  완료됐습니다.
- EC2 instance에 `car-community-k8s-node-ecr-pull-role` instance profile이 연결돼
  있습니다.
- [150 EC2 실행 artifact 전달](150-ec2-execution-artifact-transfer-runbook.md)을 완료해 EC2의
  `/home/ubuntu/car-community-scripts` 아래에 현재 전달 archive의
  `bootstrap/ecr/100-configure-kubelet-credential-provider.sh`가 있습니다.
- Kubelet package가 소유한 `/etc/default/kubelet`의 빈 기본값
  `KUBELET_EXTRA_ARGS=`는 provider를 아직 구성하지 않은 정상 시작 상태입니다.
- 다른 owner가 `/etc/default/kubelet`이나 target provider path를 관리한다면 현재 script로
  인수하지 않습니다.

## 실행

EC2 terminal에서 실행합니다.

```bash
sudo /home/ubuntu/car-community-scripts/bootstrap/ecr/100-configure-kubelet-credential-provider.sh
```

Script가 exit code `0`으로 끝나고 다음 메시지를 출력해야 합니다.

```text
[완료] Kubelet ECR credential provider 설치와 실행 인자 검증을 통과했습니다.
```

이 결과는 다음 세 file의 현재 계약과 active kubelet process의 두 provider argument를
script가 확인했다는 뜻입니다.

- `/usr/local/libexec/kubernetes/kubelet-credential-providers/ecr-credential-provider`
- `/etc/kubernetes/image-credential-provider-config.yaml`
- `/etc/default/kubelet`

정상 상태에서 같은 명령을 다시 실행하면 검증된 목표를 다시 게시하고 kubelet을 restart한
뒤 같은 postcondition을 확인합니다. Cluster resource나 workload를 삭제하지 않습니다.

## 중단과 복구

| 중단 상태 | 자동으로 하지 않는 일 | 확인·복구 | 재시작 |
| --- | --- | --- | --- |
| 지원 OS·architecture·kubelet version 불일치 | 다른 artifact·version 자동 선택 | EC2와 kubelet의 실제 version을 확인하고 지원 범위 변경을 별도 설계합니다. | 현재 script 사용 안 함 |
| Provider download·checksum 실패 | 다른 URL·임의 checksum 사용 | Network, official release byte와 repository 고정값을 대조합니다. | 원인 해결 뒤 같은 명령 |
| Kubelet service가 `/etc/default/kubelet`을 읽지 않음 | Unit file 자동 수정 | `systemctl cat kubelet.service`로 실제 unit owner와 EnvironmentFile 계약을 확인합니다. | 계약 복구 뒤 같은 명령 |
| Kubelet defaults나 provider path에 다른 내용 존재 | Package 기본 빈 값과 script의 exact 목표 외 내용을 자동 merge·삭제·덮어쓰지 않습니다. | 세 file과 관리 owner를 확인하고 통합·migration 여부를 먼저 결정합니다. | 결정 전 중단 |
| Kubelet restart·active process 인자 검증 실패 | Cluster reset·workload 삭제 | `systemctl status kubelet`, unit과 process argument를 확인해 service 원인을 복구합니다. | 같은 명령 |

## 완료 확인과 다음 단계

- Script exit code가 `0`입니다.
- 위 완료 메시지가 출력됐습니다.
- Script가 active kubelet process의 provider binary directory와 configuration argument를
  확인했습니다.

Local contract test는 실제 EC2 kubelet 상태를 대신하지 않습니다. 설치가 끝나면 publish
workflow에서 얻은 exact digest로
[Private ECR image pull 점검](410-private-ecr-image-pull-verification-runbook.md)을 실행합니다.

## 관련 문서

- **현재 image identity·node pull 구조**: [210 Container image delivery](../project/infrastructure/210-container-image-delivery.md)
- **Script 공개 계약**: [ECR bootstrap README](../../scripts/bootstrap/ecr/README.md)
- **반복 live 점검**: [Private ECR image pull 점검](410-private-ecr-image-pull-verification-runbook.md)
