# Application notification credential 변경 runbook

이 절차는 Application delivery 최초 bootstrap과 Argo CD 최초 sync가 완료된 staging에서
Resend API key 값을 교체할 때 사용합니다. 최초 `car-community-notification` Secret 생성은
[Application delivery 최초 bootstrap](350-application-delivery-bootstrap-runbook.md)이 소유합니다.

## 실행 계약

| 항목 | 계약 |
| --- | --- |
| 언제 | 최초 Application sync 완료 뒤 현재 approved schema의 `RESEND_API_KEY`를 교체할 때 사용합니다. Notification key schema 변경에는 사용하지 않습니다. |
| 누가 | Provider key와 local `.env`, target EC2 SSH·`sudo` 권한을 가진 운영자가 실행합니다. |
| 어디서 | 입력 준비와 SSH stdin 전달은 local repository root, script 실행과 rollout 확인은 target EC2입니다. |
| 무엇을 | `operations/application/reconcile-notification-secret.sh`에 `STG_RESEND_API_KEY`를 stdin으로 전달하고 provider 기능을 사람이 확인합니다. |
| 왜 | Secret 값과 새 Pod를 같은 generation으로 수렴시키고 새 key 사용을 증명한 뒤 이전 provider key를 폐기하기 위해서입니다. |
| 완료·재개 | Script exit `0`, 새 Pod Ready, 대표 email 성공과 이전 key 폐기 뒤 재확인을 완료합니다. 실패하면 아래 표의 같은 단계에서 재개합니다. |

## 실행 전 경계

- Provider에서 새 key를 발급하고 기존 key는 아직 폐기하지 않습니다.
- `car-community` Namespace, bootstrap owner의 `car-community-notification` Secret과 Argo CD
  owner의 `car-community` Deployment가 이미 존재해야 합니다.
- Script는 `RESEND_API_KEY` 외의 임의 key, 다른 Secret 이름과 최초 resource 생성을 받지 않습니다.
- Secret 값은 environment variable로 주입되므로 실행 중 Pod에는 자동 반영되지 않습니다.
  Script가 Deployment annotation을 current Secret `resourceVersion`으로 바꾸고 새 Pod Ready까지
  기다립니다.

## Local 입력 준비

Local repository root에서 신뢰된 `.env`의 owner·type·mode를 확인하고 xtrace를 끈 뒤 값만
읽습니다. Actual key를 출력하지 않습니다.

```bash
set +x
test -f .env
test ! -L .env
test "$(stat -f '%u' .env)" = "$(id -u)"
test "$(stat -f '%Lp' .env)" = '600'
. ./.env
typeset +x STG_RESEND_API_KEY
test -n "${STG_RESEND_API_KEY}"
```

## 적용과 rollout

Placeholder를 실제 SSH 값으로 바꿔 실행합니다.

```bash
printf '%s\0' "${STG_RESEND_API_KEY}" \
| env -i HOME="${HOME}" PATH="${PATH}" ssh -i "<KEY_PATH>" "ubuntu@<EC2_SSH_HOST>" \
  'sudo -n /home/ubuntu/car-community-scripts/operations/application/reconcile-notification-secret.sh'
```

Exit code `0`과 다음 완료 메시지 중 하나를 확인합니다.

```text
[완료] Notification Secret을 적용하고 새 runtime Pod Ready를 확인했습니다.
[완료] Notification Secret과 runtime Pod가 이미 목표 상태입니다.
```

Script는 actual value를 출력하지 않고 Secret owner·type·exact key·encoded value와 Deployment
rollout annotation을 API에서 검증합니다. `--force-conflicts`와 Secret 삭제는 사용하지 않습니다.

## Provider 전환 완료

새 Pod Ready만으로 Resend provider가 새 key를 실제 사용했다고 단정하지 않습니다.

1. Staging의 이메일 인증, 관리자 초대 또는 비밀번호 재설정 중 대표 email을 한 건 발송합니다.
2. Resend에서 새 key의 요청 성공을 확인하되 recipient·token·API key를 기록하지 않습니다.
3. 성공한 뒤에만 기존 Resend key를 폐기합니다.
4. 폐기 뒤 대표 email을 다시 확인합니다.

## 중단과 재개

| 실패 위치 | 남을 수 있는 상태 | 재개 |
| --- | --- | --- |
| 입력·owner·resource preflight | 변경 없음 | 원인을 고친 뒤 같은 명령 |
| Secret apply 실패 | 이전 값 또는 API가 적용한 새 값 | 값을 바꾸지 말고 같은 명령 |
| Secret apply 뒤 rollout 실패 | Secret은 새 값, Deployment annotation은 이전 값일 수 있음 | 같은 명령이 current `resourceVersion` rollout을 재개 |
| 새 Pod Ready 뒤 email smoke 실패 | 새 key를 가진 Pod | 기존 key를 폐기하지 말고 Pod log·provider 설정을 진단한 뒤 재확인 |
| 기존 key 폐기 뒤 실패 | Provider rollback 필요 가능 | Provider가 허용하면 기존 key를 복구하고 이 절차를 새 key로 다시 수행 |

## Notification key schema 변경의 현재 중단점

현재 executable의 approved schema는 `RESEND_API_KEY` 하나입니다. 새 notification key를
추가하거나 기존 key를 schema에서 제거하는 lifecycle에는 현재 안전한 repository
entrypoint와 검증·재개 계약이 없습니다. 이 Runbook의 `reconcile-notification-secret.sh`에
임의 field를 전달하거나 빈 문자열·Secret object 삭제로 우회하지 않습니다.

Application code·properties, `.env.example`, bootstrap reconstruction, operation schema,
Deployment consumer와 Secret field-manager ownership을 함께 변경하는 개발 범위를 먼저
논의·승인해야 합니다. 그 구현과 검증된 Procedure가 마련되기 전에는 schema 변경을
실행하지 않습니다.
