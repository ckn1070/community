# Resend API key 교체 runbook

이 절차는 최초 Application bootstrap 뒤 기존 notification Secret의 Resend API key를 교체하고
새 Pod의 실제 email 발송을 확인한 뒤 이전 provider key를 폐기하는 전용 수명주기를 소유합니다.

## 실행 계약

| 항목 | 계약 |
| --- | --- |
| 언제 | 기존 Resend credential을 정기 또는 사고 대응으로 교체할 때 사용합니다. |
| 누가 | Resend key 발급·폐기 권한, local Secret source와 target SSH 권한을 가진 운영자가 실행합니다. |
| 어디서 | 입력·script는 local repository terminal, 적용은 SSH를 통해 target EC2, provider 확인은 Resend Console에서 수행합니다. |
| 입력 | Local `.env`의 `STG_RESEND_API_KEY`, `<KEY_PATH>`, `<EC2_SSH_HOST>` |
| 완료·재개 | 새 Pod의 실제 email 성공 뒤 이전 key를 폐기하고 재검증하면 완료합니다. 실패 표의 동일 entrypoint에서 재개합니다. |

최초 notification Secret 생성은
[350 Application delivery 최초 bootstrap](350-application-delivery-bootstrap-runbook.md)이 소유합니다.

## 1. 새 key 준비

Resend Console에서 새 key를 발급하되 기존 key는 폐기하지 않습니다. Local repository root의
신뢰된 `.env`에 새 값을 넣고 다음을 실행합니다.

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

## 2. Secret 적용과 rollout

[150 EC2 실행 artifact 전달](150-ec2-execution-artifact-transfer-runbook.md)을 완료한 뒤 같은
local terminal에서 실행합니다.

```bash
printf '%s\0' "${STG_RESEND_API_KEY}" \
| env -i HOME="${HOME}" PATH="${PATH}" ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
  'sudo -n /home/ubuntu/car-community-scripts/operations/application/reconcile-notification-secret.sh'
```

Exit code `0`과 다음 완료 메시지 중 하나를 확인합니다.

```text
[완료] Notification Secret을 적용하고 새 runtime Pod Ready를 확인했습니다.
[완료] Notification Secret과 runtime Pod가 이미 목표 상태입니다.
```

Script는 actual value를 출력하지 않고 기존 bootstrap owner, exact schema와 encoded value를
확인합니다. 최초 resource를 만들거나 `--force-conflicts`, Secret 삭제를 사용하지 않습니다.

## 3. Provider 전환 완료

1. Staging의 이메일 인증, 관리자 초대 또는 비밀번호 재설정 중 대표 email을 한 건 발송합니다.
2. Resend Console에서 새 key의 요청 성공을 확인하되 recipient·token·API key를 기록하지 않습니다.
3. 성공한 뒤에만 기존 Resend key를 폐기합니다.
4. 폐기 뒤 대표 email을 다시 확인합니다.

## 4. 중단과 재개

| 실패 위치 | 남을 수 있는 상태 | 재개 |
| --- | --- | --- |
| 입력·owner·resource preflight | 변경 없음 | 원인을 고친 뒤 같은 명령 |
| Secret apply 실패 | 이전 값 또는 API가 적용한 새 값 | 값을 바꾸지 말고 같은 명령 |
| Secret apply 뒤 rollout 실패 | Secret은 새 값, Deployment annotation은 이전 값일 수 있음 | 같은 명령으로 current `resourceVersion` rollout 재개 |
| 새 Pod Ready 뒤 email smoke 실패 | 새 key를 가진 Pod | 이전 key를 폐기하지 말고 Pod log·provider 설정 진단 뒤 재확인 |
| 이전 key 폐기 뒤 실패 | Provider rollback 필요 가능 | Provider가 허용하면 이전 key를 복구하고 이 절차를 다시 수행 |

현재 operation은 기존 Secret의 approved key 하나만 변경합니다. Key 추가·삭제나 최초 resource
생성은 Application binding, bootstrap reconstruction, operation schema와 consumer를 함께 바꾸는
별도 개발·검증 없이 실행하지 않습니다.
