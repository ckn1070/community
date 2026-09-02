# Application configuration operations

이 디렉터리는 최초 bootstrap이 끝난 뒤 application Secret을 생성·변경하고 새 runtime Pod에
반영하는 반복 운영 작업을 소유합니다. 각 script가 최초 Secret 생성 지원 여부를 별도로 고정합니다.

| Script | 고정 대상 | 현재 schema |
| --- | --- | --- |
| `reconcile-community-secret.sh` | `car-community-community` | `COMMUNITY_CURSOR_HMAC_SECRET` |
| `reconcile-notification-secret.sh` | `car-community-notification` | `RESEND_API_KEY` |

Community operation은 local `.env`의 `STG_COMMUNITY_CURSOR_HMAC_SECRET` 한 값을 받아 Secret이
없으면 bootstrap owner로 생성하고, 있으면 exact owner·schema를 확인해 수렴시킵니다. 32 bytes
이상이고 현재 Auth HMAC Secret과 다른 값만 허용합니다.

Notification operation은 local `.env`의 `STG_RESEND_API_KEY` 한 값을 NUL-delimited stdin으로 받고 기존
bootstrap owner의 Secret만 같은 field manager로 server-side apply합니다. 값이 바뀌면 Secret
`resourceVersion`을 Argo CD가 무시하도록 선언한 Deployment annotation에 기록해 새 Pod
template을 만들고 rollout·Ready를 기다립니다. 같은 Secret 값과 annotation이면 no-op입니다.

새 notification key는 임의 runtime argument로 추가하지 않습니다. Application binding,
`.env.example`, bootstrap reconstruction, 이 operation의 approved schema와 Deployment consumer를
같은 source 변경으로 확장합니다. Key 폐기는 consumer를 먼저 제거한 image를 배포·검증한 뒤
approved schema에서 key를 제거해 같은 field manager가 소유한 key만 omit하는 순서로 합니다.

실제 Community 활성화는
[560 Community 기능 활성화](../../../docs/operations/560-community-feature-activation-runbook.md),
Resend key 교체와 기존 key 폐기는
[550 Resend API key 교체](../../../docs/operations/550-resend-api-key-rotation-runbook.md)가 소유합니다.

## Local 검증

```bash
bash -n \
  scripts/lib/application-secret.sh \
  scripts/operations/application/reconcile-community-secret.sh \
  scripts/operations/application/tests/test-reconcile-community-secret.sh \
  scripts/operations/application/reconcile-notification-secret.sh \
  scripts/operations/application/tests/test-reconcile-notification-secret.sh

shellcheck -x -P SCRIPTDIR \
  scripts/lib/application-secret.sh \
  scripts/operations/application/reconcile-community-secret.sh \
  scripts/operations/application/tests/test-reconcile-community-secret.sh \
  scripts/operations/application/reconcile-notification-secret.sh \
  scripts/operations/application/tests/test-reconcile-notification-secret.sh

scripts/operations/application/tests/test-reconcile-community-secret.sh
scripts/operations/application/tests/test-reconcile-notification-secret.sh
```

Contract test는 입력 경계, Community missing resource 생성, foreign owner 거부, Auth HMAC
재사용 거부, Secret 적용 전후 rollout 순서, 부분 실패 재개와 no-op을 검증합니다. 실제
Kubernetes SSA, Pod rollout과 기능 성공은 target 환경 runbook에서 별도로 확인합니다.
