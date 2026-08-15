---
name: documentation
description: "저장소 Markdown을 작성·수정·동기화·리팩터링할 때 문서 정보 구조와 source-first 작성 workflow를 연결하는 primary adapter로 사용한다. 새 문서, SOT·work item·design·runbook·README·router, 오탈자·링크·상태 갱신, 중복 정리, 병합·분리·이동·삭제와 corpus 개편을 포함한다. 중대한 구조 proposal이나 동결된 중대한 final 후보의 독립 감사만 요청됐으면 verify-documentation을 사용하고 DOCX·Google Docs·PDF에는 사용하지 않는다."
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
4. 작성·이관과 작성자의 두 번째 source-first 대조를 수행한다. workflow가 중대한 final
   후보로 분류한 경우에만 동결 후보의 fresh final 감사를 연결한다. routine 국소·일반
   변경은 별도 final을 추가하지 않는다. `$verify-plan`·`$verify-dev`의 일반 문서 축은
   중대한 owner·topology·coverage 후보의 `$verify-documentation final`을 대체하지 않으며,
   같은 reviewer라도 이 specialist phase의 독립 입력·전체 감사 축·verdict가 없으면
   완료로 인계하지 않는다.
5. 문서 고유 검증 결과를 작업 실행 워크플로우의 최종 discovery·closure로 돌려보낸다.

문서화 workflow가 요구한 source·router·link·번호 범위는 줄이거나 자체 checklist로
대체하지 않는다. 반대로 workflow의 기준과 공통 권한·상태·Git·완료 규칙을 이 스킬에
복제하지 않는다.
