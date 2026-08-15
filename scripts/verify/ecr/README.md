# Private ECR image pull 검증 script

이 디렉터리는 kubelet credential provider 구성이 끝난 cluster에서 지정한 private ECR
image의 **exact digest pull·실행**을 반복 확인합니다. Provider 최초 구성은
[bootstrap/ecr](../../bootstrap/ecr/README.md)가 소유합니다.

## `verify-private-image-pull.sh` 공개 계약

| 항목 | 계약 |
| --- | --- |
| caller·시점 | 운영자가 bootstrap ECR script의 postcondition과 publish workflow가 출력한 digest를 확인한 뒤 실행합니다. |
| 실행 | EC2 전달 root에서 `sudo ./verify/ecr/verify-private-image-pull.sh <IMAGE_REFERENCE>` |
| 입력 | `<12자리 account>.dkr.ecr.ap-northeast-2.amazonaws.com/car-community@sha256:<64자리 digest>` 한 개 |
| 거부 | Mutable tag, 다른 region·repository, digest 없는 reference |
| 일시 변경 | `car-community-ecr-smoke` namespace와 단기 Job을 만들고 완료·imageID·log 확인 뒤 namespace를 삭제합니다. |
| 성공 | Job complete, 실제 imageID가 요청 digest와 exact 일치, namespace 삭제 |
| 변경하지 않음 | `imagePullSecret`, ServiceAccount, Deployment와 application resource를 변경하지 않습니다. |

이 script는 점검이므로 번호를 bootstrap 순서에 억지로 연결하지 않습니다. 정상 반복 실행은
매번 새 smoke resource를 만들고 삭제합니다. 이전 비정상 종료로 같은 namespace가 남아
있으면 소유권을 추측해 삭제하지 않고 중단합니다. Lock으로 동시 실행을 막습니다.

일반 API 요청은 `30s`, Job wait는 `180s`/API `200s`, namespace 삭제는 `120s`/API `140s`로
제한해 API server 무응답이 무기한 실행으로 남지 않게 합니다.

## Local 검증

```bash
bash -n \
  scripts/verify/ecr/verify-private-image-pull.sh \
  scripts/verify/ecr/tests/test-verify-private-image-pull.sh

shellcheck -x -P SCRIPTDIR \
  scripts/verify/ecr/verify-private-image-pull.sh \
  scripts/verify/ecr/tests/test-verify-private-image-pull.sh

scripts/verify/ecr/tests/test-verify-private-image-pull.sh
```

Local 검증은 reference, resource lifecycle과 cleanup 계약만 증명합니다. 실제 IAM·ECR·pull은
target Kubernetes Job의 postcondition으로만 완료 판정합니다.
