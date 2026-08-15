# Infrastructure 변경과 복구 원칙

이 문서는 최초 구축 뒤 같은 자동화를 다시 실행하는 경우, 목표를 변경하는 경우, 구축을
취소하는 경우와 data 손실 가능 복구를 구분하는 공통 원리를 소유합니다. 실제 명령은
[운영 문서 목차](../../operations/000-index.md)의 `변경과 복구` 절차가 소유합니다.

## 네 lifecycle을 구분합니다

| Lifecycle | 목적 | 허용되는 기본 행동 |
| --- | --- | --- |
| 정상 재실행 | 같은 목표의 부분 실패를 복구·재검증 | 이미 충족한 상태를 유지하고 harmless command를 반복하며 postcondition으로 판정 |
| 구성 변경·upgrade | Version·network·permission·owner 같은 목표 자체 변경 | 영향·호환·migration·rollback을 먼저 확정한 별도 변경 |
| 구축 취소 | 아직 service가 없는 초기 bootstrap의 소유 변경을 제한적으로 복원 | 실제 backup·owner를 확인한 exact 범위만 복원 |
| Destructive recovery | PKI·etcd·runtime data·live workload 손실 가능 상태 처리 | 자동화하지 않고 data 영향 조사와 명시 승인 뒤 별도 procedure |

실패했다는 이유만으로 reset·delete·force conflict·broad permission·fallback version을 사용하지
않습니다. 반대로 안전한 APT index 갱신, exact package 재요청, 동일 config 게시와 service
restart처럼 반복 자체가 상태를 훼손하지 않는 command를 모두 marker로 생략하는 형식적
멱등성도 목표가 아닙니다.

## 재실행 안전성 기준

- 정상 완료 상태는 실제 postcondition을 다시 확인합니다.
- 부분 실패는 이미 적용된 무해한 상태를 유지하고 같은 단계에서 재개합니다.
- 다른 owner의 file·resource·field를 발견하면 자동 merge·rename·overwrite·delete하지
  않습니다.
- Lock 실패 때 lock file을 직접 지우지 않고 기존 process 종료를 확인합니다.
- API create timeout처럼 결과가 불확실하면 성공·실패를 추측하지 않고 actual state를
  조회합니다.
- Same-name 임시 namespace가 남아 있어도 label 하나만으로 전체 ownership을 추정하지
  않습니다.
- Static test와 live postcondition을 서로 대체하지 않습니다.

## 진단은 관찰 깊이를 단계적으로 늘립니다

장애 증상이 없는 baseline 확인과 이미 발생한 증상의 fault domain 분류는
[000 Platform 진단 목차](../../operations/diagnostics/000-index.md)가 함께 소유합니다. 먼저
outline의 영역별 핵심 probe로 Kubernetes 운영 stack을 훑고, 의심 영역 상세 문서 상단의 확장 진단
묶음으로 더 넓게 수집한 뒤, 같은 문서 하단의 option·출력 필드·증거 조합으로 원인을
좁힙니다.

진단은 mutation 전에 read-only 증거를 보존하고 다음 recovery owner를 고르는 단계이며,
command가 실행됐다는 사실을 정상 복구 완료로 해석하지 않습니다. 현재 진단 명령은 결과를
사람이 하나씩 해석하는 표준 read-only 조회이므로 별도 script 파일로 배포하지 않습니다.
반복 가능한 수집·오류 처리·값 조립을 하나의 결과로 묶어야 할 때는 문서의 긴 shell block으로
대신하지 않고 별도 executable의 필요성을 먼저 결정합니다.

## 변경이 별도 설계를 요구하는 신호

- Kubernetes·containerd·Calico·Argo CD·credential provider version 변경
- Node architecture·VPC CIDR·Pod·Service CIDR·stable API endpoint 변경
- Single-node에서 다중 node·HA·Auto Scaling 전환
- Different CNI·raw manifest·Argo full/HA·다른 field manager로 owner 전환
- IAM trust principal·repository·branch·permission 확대
- Terraform import·replacement와 state ownership 전환
- Credential 전달·보존·rotation 방식 변경

이 변경은 현재 bootstrap에 분기를 덧붙여 자동 흡수하지 않습니다. Consumer, data·permission
영향, acceptance evidence와 rollback을 함께 설계합니다.

## 관련 개념과 운영 절차

- [Kubernetes bootstrap 취소 원리](310-kubernetes-bootstrap-cancellation.md)
- [Kubernetes admin access](320-kubernetes-admin-access.md)
- [Argo repository credential rotation](330-argocd-repository-credential-rotation.md)
- [Kubernetes bootstrap 취소 runbook](../../operations/500-kubernetes-bootstrap-cancellation-runbook.md)
- [Argo credential 교체 runbook](../../operations/520-argocd-repository-credential-rotation-runbook.md)
- [Sonar token 교체 runbook](../../operations/530-sonar-token-rotation-runbook.md)
- [Repository script 생애주기](../../../scripts/README.md)
- [000 Platform 진단 목차](../../operations/diagnostics/000-index.md)
