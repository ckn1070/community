# 작업 문서 목차

이 문서 그룹은 기능, 버그, 개선, 조사 작업의 계획·진행·결과와 의사결정 과정을 소유합니다.
현재 개념·구조·정책·제품 범위와 이를 이해하는 데 필요한 이유는 project, product, domain,
architecture, tech-stack 등 영구 SOT에서 확인합니다. Work item과 `-design`은 그 현재 기준의
canonical owner가 될 수 없습니다.

## 번호 그룹

| 번호대 | 책임 |
| --- | --- |
| `100`~`190` | 모든 work item의 상태 label·본문·ID 문서 구조 |

## 작업 유형

- [features](features/000-index.md): 신규 제품 기능
- [bugs](bugs/000-index.md): 기대 동작과 다른 결함
- [improvements](improvements/000-index.md): 기존 시스템, 구조, workflow 개선
- [spikes](spikes/000-index.md): 제한 시간 안에 불확실성을 줄이는 조사
- [backlog](backlog/000-index.md): 아직 실행 작업으로 전환되지 않은 후보
- [templates](templates/000-index.md): 계획 lifecycle별 구조·철학·서술방식 기준

## 읽는 방법

1. Open backlog는 상단 `목적` 또는 `배경`, Proposed·Planned는 `상태`·`계획 단계` 바로 뒤의
   `현재 결론과 다음 행동`, planning-Blocked는 `상태` 바로 뒤의 같은 첫 화면에서 현재
   위치·닫힌 결과·actor·사용자 차례와 다음 전환을 확인합니다. In Progress·Done은 상단
   `결과`, Closed backlog는 상단 처분 `결과`에서 현재 위치와 새 owner를 확인합니다.
2. Proposed·Planned는 현재 계획 단계에 허용된 본문만 읽고, 다음 단계 section을 현재 결과로 추정하지 않습니다.
3. 범위와 확정 결정 색인을 확인합니다. 해결 설계 단계 이후에는 본문이 연결한 같은 번호의
   `-design`을 읽습니다. 더 이른 단계라도
   본문과 연도 router가 보존 기준선을 연결하면 design 첫 화면에서 현재 유효 범위·재검토
   범위와 소비 gate를 확인합니다. 재검토 대기 계약을 현재 단계의 새 결과나 구현 입력으로
   사용하지 않습니다.
4. 현재 기준과 이유는 연결된 영구 SOT에서 확인하고, 작업 당시의 선택 과정·대안·trade-off가
   필요할 때만 같은 번호의 `-design`을 읽습니다.
5. 과거 실패 증거가 필요할 때만 선택형 `-history`를 읽습니다.
6. 실제 운영 명령은 연결된 operations runbook을 사용합니다.

## 작성 기준

- 상태, 결과, 범위, 결정, 검증을 대화 없이 복원할 수 있어야 합니다.
- Proposed·Planned는 [작업 문서 생명주기](100-work-item-lifecycle.md#계획-단계와-최소-artifact)의 현재 단계 artifact만 만들고 선행 closure 전에 다음 단계 상세를 쓰지 않습니다.
- Feature·Bug·Improvement·Spike는 category별 형식이 아니라 [공통 계획·설계 템플릿](templates/000-index.md)을 사용합니다.
- `In Progress`·`Blocked`·`Done`과 `Closed`는 상단 `결과` 한 곳만 현재 상태를 소유합니다.
- 기술적 결정은 작업 당시의 질문·대안·trade-off를 보존하고 현재 기준은 영구 SOT로
  연결합니다. 실행 날짜·dirty 상태·반복 commit 기록은 Git으로 돌립니다.
- 현재 기준으로 남길 개념·구조·관계·불변식과 그 이해에 필요한 이유는 완료 시 적절한
  영구 SOT에 승격합니다.
- 상세 구조는 [작업 문서 생명주기](100-work-item-lifecycle.md)를 따릅니다.
