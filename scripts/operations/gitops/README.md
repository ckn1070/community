# Argo CD private repository credential 교체

이 디렉터리는 Argo CD에 연결한 현재 private repository의 GitHub App private key를 **최초 연결
완료 뒤 교체**하는 반복 운영 작업을 소유합니다. 최초 Secret·AppProject·Application 생성은
[bootstrap/gitops](../../bootstrap/gitops/README.md)가 소유합니다.

## `rotate-private-gitops-repository-credential.sh` 공개 계약

| 항목 | 계약 |
| --- | --- |
| caller·시점 | Bootstrap `200` 완료 뒤 GitHub App의 새 private key를 발급했을 때 실행합니다. |
| 실행 | Local에서 `ssh ... "sudo -n /home/ubuntu/car-community-scripts/operations/gitops/rotate-private-gitops-repository-credential.sh <APP_ID> <INSTALLATION_ID>" < <NEW_KEY_PATH>` |
| 선행 상태 | Managed repository Secret·AppProject·Application 세 resource가 모두 존재해야 합니다. |
| 직접 변경 | Repository Secret과 application-controller StatefulSet Pod template generation annotation |
| 변경하지 않음 | AppProject·Application spec, Core resource, GitHub App와 repository를 만들거나 삭제하지 않습니다. |
| 성공 | Controller rollout, 새 key를 사용한 fresh reconcile, condition 없음, `Synced`·`Healthy` |

Script는 bootstrap `200`의 검증·Secret render·fresh reconciliation 함수를 재사용하지만 최초
resource apply 경로는 호출하지 않습니다. Resource가 없거나 owner label이 다르면 자동
복구·생성하지 않고 중단해 bootstrap과 operation 경계를 보존합니다.

새 key는 stdin으로만 전달하고 `/run`의 mode `0600` 임시 file을 Secret apply 직후
삭제합니다. 교체가 성공한 뒤 GitHub에서 이전 key를 revoke하고 이 script를 다시 실행하지
않은 상태로 Application hard refresh가 계속 성공하는지 운영자가 확인해야 key lifecycle이
완료됩니다. 실제 절차는
[Credential rotation runbook](../../../docs/operations/520-argocd-repository-credential-rotation-runbook.md)이
소유합니다.

## Local 검증

```bash
bash -n \
  scripts/operations/gitops/rotate-private-gitops-repository-credential.sh \
  scripts/operations/gitops/tests/test-rotate-private-gitops-repository-credential.sh

shellcheck -x -P SCRIPTDIR \
  scripts/operations/gitops/rotate-private-gitops-repository-credential.sh \
  scripts/operations/gitops/tests/test-rotate-private-gitops-repository-credential.sh

scripts/operations/gitops/tests/test-rotate-private-gitops-repository-credential.sh
```

Local test는 완료 상태 요구, 최초 resource 미생성, Secret 변경과 consumer rollout 순서를
증명합니다. 실제 새 key 인증·old key revoke는 live 운영 증거가 필요합니다.
