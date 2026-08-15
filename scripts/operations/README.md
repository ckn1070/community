# Operations script 목차

이 디렉터리는 target 구축 완료 뒤 운영자가 필요할 때 반복하는 변경·복구 작업을
소유합니다. 최초 구축 script와 분리해 운영 입력, 실패 복구 지점과 영향 범위를 명확히
합니다.

| 기술 영역 | 작업 |
| --- | --- |
| [`gitops/`](gitops/README.md) | Argo CD private GitOps repository credential 교체 |
| [`application/`](application/README.md) | 기존 application notification Secret 변경과 runtime Pod 반영 |

운영 script는 같은 기술의 bootstrap 구현을 재사용할 수 있지만 bootstrap 완료 상태를
명시적으로 요구하고 최초 resource를 새로 만들지 않습니다.
