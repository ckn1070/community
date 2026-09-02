---
name: documentation
description: "저장소 Markdown을 작성·수정·동기화·리팩터링할 때 문서 정보 구조와 source-first 작성 workflow를 연결하는 primary adapter로 사용한다. 새 문서, SOT·work item·design·runbook·README·router, 오탈자·링크·상태 갱신, 중복 정리, 병합·분리·이동·삭제와 corpus 개편을 포함한다. 사용자 값 선택·단계 확정용 plan artifact의 실질 변경은 dev-plan의 structure closure로 반환하고 exact map·digest를 소비해 content만 쓴다. 중대한 구조 proposal이나 동결된 중대한 final 후보의 독립 감사만 요청됐으면 verify-documentation을 사용하고 DOCX·Google Docs·PDF에는 사용하지 않는다."
---

# 문서화

저장소 Markdown 변경을 canonical source로 routing한다. 이 스킬은 문서 기준·공통
lifecycle·검증 판정을 다시 소유하지 않는 얇은 실행 adapter다.

## 실행

1. [작업 실행 워크플로우](../../../docs/workflow/100-work-execution-workflow.md)에서
   exact 요청·변경 범위·권한·결과와 dependency를 확정한다.
2. 실제 디스크에서 `AGENTS.md`, [문서 목차](../../../docs/000-index.md), 관련 그룹
   router, [문서 정보 구조 가이드](../../../docs/100-documentation-guide.md), 실제
   directory topology, 직접 source와 같은 독자 질문을 소유할 수 있는 기존 문서를
   읽는다. 이때 활성 문서나 사용자가 언급한 파일은 write target이 아니라 owner 후보로만
   둔다.
3. [문서화 워크플로우](../../../docs/workflow/150-documentation-workflow.md)에 따라 문서의
   현재 의미·topology 효과로 변경 깊이를 잠정 선택한 뒤, 의미·구조 gate에서 독자 질문과
   정보 유형, canonical owner와 병합·분리 경계를 정한다. 선택한 owner 전체와 관계·번호·
   이동 범위를 대조하고 exact write target을 고정한 뒤 변경 깊이와 독립 감사 필요성을
   다시 확정한다. 중대한 구조이면 작성 전에 proposal 감사를 먼저 통과한다.
   대상이 현재 계획 단계의 work item·허용된 `-design`이고 첫 화면·결정·단계별 계획 내용을
   실질 변경해 사용자의 값 선택이나 단계 확정에 인계할 결과라면 직접 작성하지 않고
   `$dev-plan`으로 반환한다. 읽기 전용, 상태 보고, 사용자 handoff가 없는 내부 계획,
   오탈자·link·source로 유일한 값의 국소 교정과 일반 Decision/Design은 반환 대상이 아니다.
4. 작성·이관과 작성자의 두 번째 source-first 대조를 수행한다. workflow가 중대한 final
   후보로 분류한 경우에만 동결 후보의 fresh final 감사를 연결한다. routine 국소·일반
   변경은 별도 final을 추가하지 않는다. `$verify-plan`·`$verify-dev`의 일반 문서 축은
   중대한 owner·topology·coverage 후보의 `$verify-documentation final`을 대체하지 않으며,
   같은 reviewer라도 이 specialist phase의 독립 입력·전체 감사 축·verdict가 없으면
   완료로 인계하지 않는다.
   Plan content writer로 호출됐으면 receipt의 candidate map·digest와 현재 Frozen을 먼저
   대조한다. 없거나 stale이면 작성하지 않고 `검증 불가`로 반환한다. 일치하면 동결된
   heading·first-screen field·활성 결정 block·결정 권위·상태·보존 관계를 유지하고 값과
   설명만 채운다. 작성 중 계획 의미나 구조를 바꿔야 하면 스스로 선택하지 않고
   `$dev-plan`으로 반환해 새 structure round를 요구한다.
5. 문서 고유 검증 결과를 작업 실행 워크플로우의 최종 discovery·closure로 돌려보낸다.

문서화 workflow가 요구한 source·router·link·번호 범위는 줄이거나 자체 checklist로
대체하지 않는다. 반대로 workflow의 기준과 공통 권한·상태·Git·완료 규칙을 이 스킬에
복제하지 않는다.
