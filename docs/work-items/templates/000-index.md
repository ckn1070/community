# 작업 문서 템플릿

작업 유형에 맞는 `Proposed/결과 정의`의 최소 골격만 복사합니다.
해당하지 않는 선택 섹션은 제거하고 workflow 전문을 작업 문서에 복사하지 않습니다.
각 template의 `현재 결론과 다음 행동`을 actual source로 먼저 채워 구조 candidate를 만들고,
`plan-structure` closure 전에는 상세 prose를 작성하지 않습니다. 사용자 입력이 다음 안정
행동일 때만 활성 결정 block을 유지하며, 닫힌 결정은 결정 색인으로 옮기고 다시 묻지 않습니다.

- [feature-template.md](feature-template.md)
- [bug-template.md](bug-template.md)
- [improvement-template.md](improvement-template.md)
- [spike-template.md](spike-template.md)
- [backlog-template.md](backlog-template.md)

선행 단계가 닫힌 뒤에는 template에 다음 단계 heading을 덧붙이지 않고 [작업 문서 생명주기](../100-work-item-lifecycle.md#계획-단계와-최소-artifact)의 현재 artifact 구조로 전환합니다.
상태별 Plan·Status·Result 전환과 design/history 분리 기준도 같은 문서를 따릅니다.
`Done` 전에는 [문서화 워크플로우](../../workflow/150-documentation-workflow.md)의 결과 게이트와 영구 SOT 대조를 수행합니다.
검토 checklist와 실행 기록을 작업 문서에 복사하지 않고 현재 결과와 남은 책임만 유지합니다.
각 template의 `관련 문서`에는 링크만 나열하지 않고 `현재 기준`, `결정 근거`, `구현 source`, `실행 절차`, `작업 상태`, `대체 문서`처럼 실제 관계를 표시합니다. 관계가 없으면 빈 section을 제거합니다.
