# Argo CD repository credential rotation

이 문서는 현재 private repository의 GitOps 경로 최초 연결 뒤 GitHub App private key를
교체하는 identity·consumer·evidence lifecycle을 설명합니다. 실제 key 적용·revoke·확인 절차는
[Argo CD credential 교체 runbook](../../operations/520-argocd-repository-credential-rotation-runbook.md)이
소유합니다.

## 최초 연결과 rotation이 다른 이유

최초 연결은 repository Secret, AppProject와 Application을 생성합니다. Rotation은 이 세
resource가 이미 정상 존재하는 상태에서 **repository Secret credential만** 바꾸고 current
controller가 새 credential을 소비했다는 사실을 증명합니다. Resource가 하나라도 없거나
owner가 다르면 rotation으로 자동 생성·복구하지 않고 최초 구축·migration 경계로 돌아갑니다.

## 안전한 순서

```text
새 GitHub App private key 발급
  -> 기존 key 유지
  -> 새 key를 SSH stdin으로 전달
  -> Repository Secret 갱신
  -> application-controller 새 revision rollout
  -> 새 credential의 fresh reconciliation 조건 통과
  -> GitHub에서 이전 key만 revoke
  -> hard refresh 후 같은 조건 재확인
```

이전 key를 먼저 revoke하면 새 key·Secret·controller 소비 경로에 문제가 있을 때 repository
access를 모두 잃습니다. 따라서 새 key live 증거 뒤 revoke합니다.

## Secret 전달과 consumer generation

- App ID·Installation ID는 공개 식별자로 remote argument에 전달할 수 있습니다.
- PEM byte는 shell argument·environment·Git·EC2 영구 file에 넣지 않고 local file에서 SSH
  stdin으로 전달합니다.
- Remote에서는 `/run` mode `0600` 임시 file로만 materialize하고 Secret apply 직후·중간
  실패 때 삭제합니다.
- `sudo -n`으로 password prompt가 stdin key를 소비하지 못하게 합니다.
- Kubernetes Secret에는 credential이 지속됩니다. Current self-managed etcd at-rest
  encryption 부재와 RBAC 경계는 [Argo CD platform](150-argocd-platform.md)이 소유합니다.

Secret update 뒤 새 controller revision·informer initial sync·hard refresh로 credential
소비를 증명하는 canonical mechanism과 freshness 조건은
[Argo CD platform의 automated reconciliation 계약](150-argocd-platform.md#automated-reconciliation-계약)이
소유합니다. Rotation은 그 mechanism을 재사용하지만 AppProject·Application spec과 Core
resource는 다시 apply하지 않습니다.

## 성공 증거

1. 새 key 적용 뒤 Argo CD platform의 fresh credential 조건을 모두 충족합니다.
2. 이전 key를 revoke합니다.
3. 다시 hard refresh해 같은 fresh credential 조건을 충족합니다.

Rotation script의 exit `0`만으로 GitHub에서 old key를 revoke한 결과까지 증명하지 않습니다.
마지막 fresh reconciliation이 현재 Secret의 새 key로 인증한다는 live evidence입니다. Secret
data·private key 원문은 확인을 위해 출력하지 않습니다.

## 실패·복구 경계

| 실패 | 보호 행동 | 복구 방향 |
| --- | --- | --- |
| Managed resource 누락·owner 불일치 | 새 resource·label을 만들지 않음 | 최초 구축 또는 owner migration 확인 |
| ID·PEM 검증 실패 | Secret을 바꾸지 않음 | Local App ID·Installation ID·key pair 확인 |
| Controller rollout 실패 | Pod 강제 삭제·Application 성공 처리 안 함 | StatefulSet·event·image 문제 복구 후 같은 새 key 재실행 |
| Fresh reconcile 실패 | Application 삭제·spec 재apply 안 함 | App installation·permission·repository·controller log 복구 |
| Old key revoke 뒤 실패 | Old key 성공을 추정하지 않음 | 새 key 활성 여부 확인, 필요 시 새 key 재발급·rotation 반복 |

Controller rollout 동안 reconciliation은 잠시 중단되지만 기존 workload를 삭제·재시작하지
않습니다. 현재 단일 controller 경계에서 감수한 control-plane interruption입니다.

## 관련 문서

- **실제 rotation 절차**: [Credential 교체 runbook](../../operations/520-argocd-repository-credential-rotation-runbook.md)
- **최초 identity·Secret·AppProject 경계**: [Argo CD platform](150-argocd-platform.md)
- **반복 deployment state**: [GitOps application delivery](210-gitops-application-delivery.md)
