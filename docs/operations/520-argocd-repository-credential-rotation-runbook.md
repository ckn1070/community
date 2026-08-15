# Argo CD repository credential 교체 runbook

현재 private repository의 GitOps 경로 최초 연결이 완료된 뒤 GitHub App private key를 새
key로 교체하는 반복 운영 절차입니다. Secret·AppProject·Application을 최초 생성하는 작업은
[Application delivery 최초 bootstrap](350-application-delivery-bootstrap-runbook.md)이
소유합니다. Credential lifecycle과 변경 원칙은
[Argo CD repository credential rotation](../project/infrastructure/330-argocd-repository-credential-rotation.md)에서
확인합니다.

## 실행 계약

| 항목 | 계약 |
| --- | --- |
| 언제 | 최초 repository 연결 완료 뒤 GitHub App private key를 계획 교체하거나 노출·무효 상태에서 복구할 때 실행합니다. |
| 누가 | GitHub App key 발급·revoke 권한과 target EC2 SSH·`sudo` 권한을 가진 운영자가 실행합니다. |
| 어디서 | 새 PEM 전달은 local terminal, script와 revoke 뒤 live 확인은 target EC2 terminal·GitHub App Console에서 수행합니다. |
| 무엇을 | `operations/gitops/rotate-private-gitops-repository-credential.sh`와 GitHub App의 이전 key revoke를 순서대로 실행합니다. |
| 왜 | 기존 Application spec을 바꾸지 않고 새 credential consumer의 fresh reconcile을 증명한 뒤 이전 key를 폐기하기 위해서입니다. |
| 완료·재개 | Script 성공→이전 key revoke→hard refresh의 `Synced`·`Healthy` 확인까지 완료합니다. 실패 지점의 동일 script 또는 확인 단계에서 재개합니다. |

## 사용 시점과 전제

- `car-community-gitops-repository` Secret, `car-community` AppProject,
  `car-community-stg` Application이 bootstrap owner label로 존재합니다.
- Application이 현재 `Synced`·`Healthy`입니다.
- GitHub App ID와 Installation ID는 유지하고 새 PEM private key를 local 안전 경로에
  발급했습니다. 기존 key는 새 key 검증이 끝날 때까지 revoke하지 않습니다.
- Local에서 기존 SSH key로 EC2에 접속할 수 있습니다.

Resource가 하나라도 없으면 rotation script는 새로 만들지 않고 중단합니다. 최초 연결의
부분 실패라면 `350`으로 돌아갑니다.

## 새 key 적용과 fresh reconciliation

Placeholder를 실제 값으로 바꾸고 local terminal에서 실행합니다.

```bash
ssh -i "<KEY_PATH>" "ubuntu@<EC2_SSH_HOST>" \
  "sudo -n /home/ubuntu/car-community-scripts/operations/gitops/rotate-private-gitops-repository-credential.sh <GITHUB_APP_ID> <GITHUB_APP_INSTALLATION_ID>" \
  < "<LOCAL_NEW_GITHUB_APP_PRIVATE_KEY_PATH>"
```

| 표현 | 이유 |
| --- | --- |
| 공개 ID 두 개 | Secret을 render할 식별자이며 private key는 아닙니다. |
| stdin redirection | PEM byte를 shell argument·environment·repository file에 넣지 않습니다. |
| `sudo -n` | Password prompt가 stdin key를 소비하지 못하게 즉시 실패시킵니다. |

Script는 세 managed resource와 Core owner를 확인한 뒤 새 key를 `/run` mode `0600` 임시
file로 읽습니다. Repository Secret만 갱신하고 그 `resourceVersion`을
application-controller StatefulSet Pod template annotation에 기록해 새 credential consumer
revision을 강제합니다. Controller rollout 동안 reconciliation은 잠시 중단되지만 기존
application workload는 유지됩니다. AppProject와 Application spec은 다시 apply하지
않습니다.

새 controller가 informer initial sync를 마친 뒤 hard refresh가 소비되고 `reconciledAt`이
바뀐 fresh reconcile, condition 없음, `Synced`와 `Healthy`까지 확인해야 exit `0`입니다.

## 이전 key revoke와 최종 확인

Script 성공 뒤 GitHub App 설정에서 **이전 private key만** revoke합니다. 새 key는 유지합니다.
그 다음 EC2에서 Application hard refresh를 요청하고 다시 `Synced`·`Healthy`인지 확인합니다.

```bash
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl annotate application/car-community-stg -n argocd \
  argocd.argoproj.io/refresh=hard --overwrite

sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl wait --for=jsonpath='{.status.sync.status}'=Synced \
  application/car-community-stg -n argocd --timeout=300s

sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl wait --for=jsonpath='{.status.health.status}'=Healthy \
  application/car-community-stg -n argocd --timeout=300s
```

이 최종 확인은 old key revoke 뒤에도 현재 Secret의 새 key로 인증한다는 live 증거입니다.
Private key 원문이나 Secret data를 출력·보관하지 않습니다.

## 중단과 복구

| 중단 상태 | 자동으로 하지 않는 일 | 복구 |
| --- | --- | --- |
| Managed resource 누락·owner 불일치 | Resource 생성·label 보정 | 최초 구축 상태와 owner를 확인하고 `340` 또는 별도 migration으로 돌아갑니다. |
| PEM·ID 검증 실패 | Secret 변경 | 새 key file과 ID를 확인하고 다시 실행합니다. |
| Controller rollout 실패 | Pod 강제 삭제 | StatefulSet·event·image 상태를 복구한 뒤 같은 새 key로 재실행합니다. |
| Fresh reconcile 실패 | Application 삭제·spec 재적용 | GitHub App installation·permission, repository와 controller log를 복구한 뒤 재실행합니다. |
| Old key revoke 뒤 실패 | Old key 재사용을 성공으로 추정 | 새 key가 App에 활성화됐는지 확인하고 필요하면 새 key를 다시 발급해 rotation을 반복합니다. |

## 완료 확인

- Rotation script가 새 controller의 fresh reconcile과 `Synced`·`Healthy`를 확인하고 exit
  code `0`으로 끝났습니다.
- 그 성공 뒤 old key만 revoke했습니다.
- Old key revoke 뒤 hard refresh와 `Synced`·`Healthy`를 다시 확인했습니다.
- Private key를 argument·environment·Git·EC2 영구 file에 남기지 않았습니다.

Script의 정적 공개 계약과 local test는
[Operations GitOps README](../../scripts/operations/gitops/README.md)가 소유합니다. 실제
GitHub key 발급·revoke와 cluster sync는 live 환경에서만 완료 판정합니다.

## 관련 문서

- **Credential lifecycle 개념**: [Argo CD repository credential rotation](../project/infrastructure/330-argocd-repository-credential-rotation.md)
- **최초 구축 절차**: [Application delivery 최초 bootstrap](350-application-delivery-bootstrap-runbook.md)
- **변경·복구 원칙**: [Infrastructure change and recovery](../project/infrastructure/300-change-and-recovery-principles.md)
- **Script 공개 계약**: [Operations GitOps README](../../scripts/operations/gitops/README.md)
