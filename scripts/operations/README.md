# Operations script 목차

이 디렉터리는 target 구축 완료 뒤 운영자가 필요할 때 반복하는 변경·복구 작업을
소유합니다. 최초 구축 script와 분리해 운영 입력, 실패 복구 지점과 영향 범위를 명확히
합니다.

| 기술 영역 | 작업 |
| --- | --- |
| [`gitops/`](gitops/README.md) | Argo CD private GitOps repository credential 교체 |
| [`application/`](application/README.md) | Community·Notification Secret과 Catalog Redis 역할별 credential 생성·변경·복구 |

운영 script는 같은 기술의 bootstrap 구현을 재사용할 수 있지만 bootstrap 소유 경계를
먼저 확인합니다. Missing Secret 생성 지원 여부와 허용 schema는 각 entrypoint가 명시하며,
다른 owner의 resource를 인수하거나 전체 bootstrap을 대신하지 않습니다.
