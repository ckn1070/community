# Private ECR exact digest pull 점검 runbook

Kubelet ECR credential provider 설치가 끝난 cluster에서 지정한 private ECR exact digest를
단기 Kubernetes Job으로 실제 pull·실행하고, Pod `imageID`와 임시 resource 정리까지
확인하는 반복 점검 절차입니다. Provider 최초 설치는
[Kubelet ECR credential provider 설치](330-kubelet-ecr-credential-provider-installation-runbook.md)가
소유합니다.

점검의 actor·credential·artifact 경계는
[Container image delivery](../project/infrastructure/200-container-image-delivery.md), resource
lifecycle과 timeout의 정적 계약은
[ECR verify README](../../scripts/verify/ecr/README.md)에서 확인합니다.

## 실행 계약

| 항목 | 계약 |
| --- | --- |
| 언제 | Kubelet ECR provider 설치 뒤 새 exact digest 또는 기존 digest의 node pull 경로를 점검할 때 반복합니다. |
| 누가 | Target EC2에 SSH 접속하고 cluster-admin kubeconfig와 `sudo`를 사용할 수 있는 운영자가 실행합니다. |
| 어디서 | EC2 terminal의 `/home/ubuntu/car-community-scripts`입니다. |
| 무엇을 | `sudo ./verify/ecr/verify-private-image-pull.sh "${IMAGE_REFERENCE}"` |
| 왜 | EC2 instance profile→credential provider→ECR→container runtime 경로가 실제 digest를 pull·실행하는지 확인하기 위해서입니다. |
| 완료·재개 | Script exit `0`, exact `imageID`와 smoke namespace 삭제를 확인합니다. 실패하면 같은 digest로 verify script만 재실행합니다. |

## 입력과 전제

- Provider 설치 script의 live postcondition이 성공했습니다.
- EC2 instance profile은 target `car-community` private ECR pull 최소 권한을 가집니다.
- [GitHub ECR image 게시](400-github-ecr-image-publishing-runbook.md)의 job summary와 ECR
  Console에서 같은 exact digest reference를 확인했습니다.
- EC2의 `/home/ubuntu/car-community-scripts`에 현재 전달 archive의
  `verify/ecr/verify-private-image-pull.sh`가 있습니다.
- `car-community-ecr-smoke` namespace가 없습니다. 이미 있으면 다른 actor 또는 이전 실패의
  resource인지 확인하기 전에는 삭제하지 않습니다.

## 실행

Publishing job summary의 full reference를 넣고 EC2 terminal에서 실행합니다.

```bash
cd /home/ubuntu/car-community-scripts
IMAGE_REFERENCE='<ACCOUNT_ID>.dkr.ecr.ap-northeast-2.amazonaws.com/car-community@sha256:<DIGEST>'
sudo ./verify/ecr/verify-private-image-pull.sh "${IMAGE_REFERENCE}"
```

Mutable tag, 다른 region·repository 또는 digest 없는 reference를 입력하지 않습니다.
Script가 exit code `0`으로 끝나고 다음 메시지를 출력해야 합니다.

```text
[완료] Private ECR image의 pull·실행·exact digest 검증을 통과했습니다.
```

Script는 단기 namespace와 Job을 만들고 다음 live 결과를 확인한 뒤 namespace를 삭제합니다.

1. Job이 제한 시간 안에 complete입니다.
2. 실제 Pod `imageID`가 요청한 exact digest와 일치합니다.
3. Application image가 실행되어 `java -version` log를 읽을 수 있습니다.
4. `car-community-ecr-smoke` namespace가 제한 시간 안에 삭제됩니다.

이 점검은 provider, EC2 IAM, ECR, DNS·network, containerd, kubelet과 scheduler를 통과하지만
application readiness·RDS 연결은 검증하지 않습니다.

## 실패 판정과 복구

| 상태 | 자동으로 하지 않는 일 | 확인·복구 | 재시작 |
| --- | --- | --- | --- |
| Reference 형식·account·region·repository 오류 | 다른 image 자동 선택 | Publish summary와 ECR Console의 full exact digest를 다시 복사합니다. | 같은 digest로 재실행 |
| `ImagePullBackOff`·`401`·`403` | Provider 재설치·광범위 IAM 부여 | EC2 instance profile, `ecr:GetAuthorizationToken`, target repository의 `BatchGetImage`·`GetDownloadUrlForLayer`와 ECR URI를 확인합니다. | 원인 해결 뒤 verify만 재실행 |
| Job timeout·실패 | Deployment 생성·Application 성공 처리 | Script가 출력한 Job, Pod, event에서 scheduler·runtime·image 실행 원인을 확인합니다. | 같은 digest로 재실행 |
| 실제 `imageID` 불일치 | Tag나 다른 digest를 성공으로 수용 | 요청 reference와 Pod 상태, ECR digest를 대조합니다. | 원인 해결 뒤 재실행 |
| Namespace·Job 생성 요청 timeout | Server 적용 여부 추측·중복 생성 | 아래 read-only 명령으로 namespace 실제 상태를 확인합니다. | 상태에 따라 재실행 또는 확인 후 수동 정리 |
| 시작 전에 smoke namespace 존재 | 자동 삭제 | Resource와 event를 확인해 이 runbook의 smoke임을 확정한 뒤에만 수동 삭제합니다. | 정리 뒤 재실행 |
| Namespace cleanup 실패 | 점검 성공 처리 | 남은 resource와 API server 상태를 확인하고 이 smoke namespace임을 확정해 정리합니다. | 정리 뒤 재실행 |

생성 요청 timeout 뒤 실제 상태는 다음으로 확인합니다.

```bash
sudo kubectl --kubeconfig /etc/kubernetes/admin.conf \
  get namespace car-community-ecr-smoke
```

Namespace가 없으면 verify script를 다시 실행합니다. 있으면 resource와 event를 확인하고 해당
smoke resource임을 확정한 뒤 수동 삭제하고 재실행합니다.

## 완료 확인

- Script exit code가 `0`이고 exact digest·application 실행을 확인했습니다.
- 임시 namespace가 삭제됐습니다.
- ECR password, AWS static key, `imagePullSecret`이나 ServiceAccount 변경을 만들지 않았습니다.

Local test는 live IAM·ECR·pull 성공을 대신하지 않습니다. 같은 경로를 다시 확인해야 할 때는
새 exact digest 또는 기존 exact digest로 이 verify script만 반복 실행합니다.

## 관련 문서

- **현재 image 전달 개념**: [Container image delivery](../project/infrastructure/200-container-image-delivery.md)
- **Provider 최초 설치**: [Kubelet ECR credential provider 설치](330-kubelet-ecr-credential-provider-installation-runbook.md)
- **Verify script 공개 계약**: [ECR verify README](../../scripts/verify/ecr/README.md)
- **Digest 생산 절차**: [GitHub ECR image 게시](400-github-ecr-image-publishing-runbook.md)
