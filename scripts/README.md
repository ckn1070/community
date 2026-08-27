# Repository script 목차

이 디렉터리는 script를 기술 이름보다 **target 생애주기**로 먼저 분류합니다. 같은 ECR이나
GitOps 기능이라도 최초 구축, 구축 뒤 운영 변경, 반복 점검은 실패 복구 지점과 실행 권한이
다르기 때문입니다.

| 경로 | 사용 시점 | 소유하는 효과 |
| --- | --- | --- |
| [`bootstrap/`](bootstrap/README.md) | target 최초 구축·재구축 | 원하는 초기 상태를 만드는 변경 |
| [`operations/`](operations/README.md) | 구축 완료 뒤 필요할 때 | 반복 운영 변경·복구 |
| [`verify/`](verify/README.md) | 구축 결과·운영 상태 확인 | 영구 목표 상태를 만들지 않는 점검 |
| [`quality/`](quality/README.md) | repository 결과 검증 | source·workflow 품질 검증 |

번호는 각 기술 영역 안의 실행 순서만 나타냅니다. 전체 서비스 구축 순서를 뜻하지 않습니다.
실제 EC2 전달·실행 명령과 성공·복구 절차는 [운영 문서 목차](../docs/operations/000-index.md)가
소유합니다. 배치 기준과 재실행 계약은
[스크립트 엔지니어링 가이드](../docs/workflow/240-script-engineering-guideline.md)를 따릅니다.

## EC2 전달 archive 구현 의존성

[150 EC2 실행 artifact 전달 runbook](../docs/operations/150-ec2-execution-artifact-transfer-runbook.md)이
전달하는 열네 executable과 세 readable source 중
`operations/gitops/rotate-private-gitops-repository-credential.sh`만 같은 archive의
`bootstrap/gitops/200-connect-private-gitops-repository.sh`를 source해 구현을 재사용합니다.
GitOps `200`은 같은 archive의
`bootstrap/gitops/manifests/car-community-appproject.yaml`과
`bootstrap/gitops/manifests/car-community-stg-application.yaml`을 읽어 최초 resource spec을
적용합니다.
`bootstrap/application/200-apply-application-prerequisites.sh`와
`operations/application/reconcile-notification-secret.sh`는 같은 archive의
`lib/application-secret.sh`를 source해 Notification Secret render·apply·postcondition을
공유합니다. `bootstrap/load-balancer/100-install-aws-load-balancer-controller.sh`는 archive
밖 repository file을 읽지 않고 검증된 Helm·chart artifact를 실행 중 임시 directory로
다운로드합니다. 나머지 executable도 archive 밖 repository file을 source하거나 읽지 않습니다.
따라서 runbook이 지정한 생애주기 경로를 유지해 열일곱 file을 모두 전달하면 repository
전체를 EC2에 clone하지 않아도 됩니다.

이 경계는 repository 내부 구현 file 의존성만 뜻합니다. 각 script가 요구하는 OS command,
network에서 내려받는 고정 artifact, AWS·Kubernetes live resource와 secret 입력은 각 script
README와 해당 운영 runbook의 선행 조건을 따라야 합니다.
