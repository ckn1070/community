# 작업 문서 템플릿

템플릿은 작업 category가 아니라 문서 lifecycle로 선택합니다.

- 계획 본문: [plan-template.md](plan-template.md)
- 조건부 설계 결정 문서: [design-template.md](design-template.md)
- 계획 전 Open 후보: [backlog-template.md](backlog-template.md)

앞의 두 문서는 Feature·Bug·Improvement·Spike가 함께 사용하는 구조·철학·서술방식의 절대
기준입니다. 실제 독자 질문과 정보 관계를 더 정확히 드러내는 작은 변형은 허용하지만,
[100 작업 문서 생명주기의 기준 편차 gate](../100-work-item-lifecycle.md#기준-템플릿과-편차-gate)를
통과해야 합니다. 기준 자체를 흔드는 변경이 필요하면 작성·검증을 중단하고 사용자와
논의합니다.

각 template의 `현재 결론과 다음 행동`을 actual source로 먼저 채워 구조 candidate를 만들고,
`plan-structure` closure 전에는 상세 prose를 작성하지 않습니다. 사용자 입력이 다음 안정
행동일 때만 활성 결정 block을 유지하며, 닫힌 결정은 결정 색인으로 옮기고 다시 묻지 않습니다.

선행 단계가 닫힌 뒤에는 template에 다음 단계 heading을 덧붙이지 않고 [작업 문서 생명주기](../100-work-item-lifecycle.md#계획-단계와-최소-artifact)의 현재 artifact 구조로 전환합니다.
상태별 Plan·Status·Result 전환과 design/history 분리 기준도 같은 문서를 따릅니다.
`Done` 전에는 [문서화 워크플로우](../../workflow/150-documentation-workflow.md)의 결과 게이트와 영구 SOT 대조를 수행합니다.
검토 checklist와 실행 기록을 작업 문서에 복사하지 않고 현재 결과와 남은 책임만 유지합니다.
각 template의 `관련 문서`에는 링크만 나열하지 않고 `현재 기준`, `결정 근거`, `구현 source`, `실행 절차`, `작업 상태`, `대체 문서`처럼 실제 관계를 표시합니다. 관계가 없으면 빈 section을 제거합니다.
