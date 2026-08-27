# 문서 정보 구조 개편 설계

## 목적

이 문서는 문서 정보 구조, 문서화 절차와 독립 검토 경계를 선택한 이유를 보존합니다. 현재 규칙과 실행 절차는 [문서 정보 구조 가이드](../../../100-documentation-guide.md)와 [문서화 워크플로우](../../../workflow/150-documentation-workflow.md)가 소유합니다.

## 문제

기존 문서 체계는 위치와 보존 등급은 설명했지만, 목적이 다른 문서와 보고를 같은 구조로 다루고 작성 후 검증에 집중했습니다. 그 결과 다음 문제가 함께 발생했습니다.

- 영구 규칙, 결정 근거, 계획, 진행 상태, 실행 절차와 과거 이력이 한 문서에 누적됐습니다.
- 예시와 엣지케이스가 핵심 규칙보다 커져 문서를 읽어도 정수를 찾기 어려웠습니다.
- 상위 목차가 링크 목록에 머물고 문서 간 의존·소유·실행 관계가 드러나지 않았습니다.
- 문서 작성 뒤의 형식·링크 검증은 내용 누락, 잘못된 소유권과 불필요한 분량을 늦게 발견했습니다.
- 작업 문서와 대화 보고가 서로 복제되어 현재 상태와 Git 시점 정보가 영구 문서에 남았습니다.

## 결정 1. 형식보다 목적을 먼저 분류합니다

문서를 Router, Reference/SOT, Decision/Design, Plan, Procedure, Result/Status, History로 분류합니다. 각 종류는 한 가지 주된 독자 질문에 답하고, 다른 종류의 내용을 링크로 연결합니다.

하나의 만능 template는 간단하지만 목적별 핵심 순서가 흐려지고 빈 섹션과 중복을 만듭니다. 목적별 최소 구조는 문서 간 모양을 완전히 같게 만들지 않으면서도 독자가 필요한 내용을 예측하게 합니다.

재검토 조건은 현재 종류로 반복 분류할 수 없는 artifact가 생기는 경우입니다.

## 결정 2. 관계에는 방향과 소유권을 둡니다

- Router는 목적별 진입점만 가리킵니다.
- Decision은 선택 근거를 보존하고 현재 규칙은 SOT를 가리킵니다.
- Plan과 Status는 아직 닫히지 않은 실행만 소유합니다.
- Result는 완료 판정과 영구 SOT 승격만 남깁니다.
- Procedure는 실제 명령과 실패·복구 기준을 소유합니다.
- Reference/SOT는 현재 확정 사실과 규칙만 소유합니다.

상호 복사는 일시적으로 읽기 편하지만 여러 owner를 만들고 시간이 지나면 결론이 갈라집니다. 관계를 링크로 표현하면 각 정보의 변경 책임이 한 곳에 남습니다.

## 결정 3. 세 검토 관문을 분리합니다

1. 의미 관문은 작성 전에 독자 질문, authoritative source, 필수 내용, 비목표와 기존 owner를 검토합니다.
2. 구조 관문은 작성 전에 artifact 종류, 목차, 핵심과 보조 내용의 위치, 분할·병합과 링크 방향을 검토합니다.
3. 결과 관문은 작성 뒤 source coverage, 역방향 과잉, 현재성, 실제 링크와 전체 문서 관계를 다시 확인합니다.

한 번의 최종 검토로 합치면 잘못 선택한 범위와 구조를 문장 작성 뒤에야 발견하여 재작업이 커집니다. 반대로 세 관문을 모두 별도 문서나 승인 단계로 만들면 작은 수정까지 느려집니다. 따라서 기록과 독립 검토 강도는 변경 규모에 비례시킵니다.

## 결정 4. 핵심과 보조 내용을 분리합니다

정의, 불변식, 선택, 책임, 실패·복구 기준과 현재 상태를 먼저 둡니다. 예시, 대안의 세부, 참고 설명은 핵심을 이해하는 데 필요한 최소량만 가까이 두고, 길어지면 별도 decision·reference·appendix로 분리합니다.

예시 개수나 문서 길이에 일률적인 상한을 두지 않습니다. 같은 결정을 반복하거나 독립적인 독자 질문에 답하기 시작하는지가 분할 기준입니다.

## 결정 5. 대화 보고와 영구 문서를 분리합니다

대화 보고는 현재 판정, 완료·미완료, 책임, 다음 행동과 검증을 전달합니다. 영구 문서는 현재 규칙·결정·운영 절차·완료 결과를 보존합니다. branch, dirty 상태, commit 대기와 같은 순간 정보는 대화에서만 보고합니다.

작업 문서는 시작할 때 Plan, 진행할 때 Status, 완료할 때 Result 구조로 전환하고 이전 계획·체크리스트·실행 로그를 누적하지 않습니다. 상세 결정이 계속 필요하면 별도 design 문서에 보존합니다.

## 결정 6. Skill은 workflow를 복제하지 않습니다

`documentation`은 문서화 workflow를 적용하는 작성·개편 router이고, `report-task`는 상태 보고 workflow를 적용하는 adapter입니다. 판단 기준은 영구 문서가 소유하며 skill에는 trigger, 읽을 SOT, 실행 순서와 종료 조건만 둡니다.

중대한 owner·topology·coverage 변경은 작성 맥락과 분리해 source부터 다시 읽는
`verify-documentation` 경계를 둡니다. 이 경계는 검토를 돕는 skill이며 executable,
CI gate 또는 자동 통과 판정기가 아닙니다. Routine 동반 문서는 `verify-plan`이나
`verify-dev`의 문서 축으로 닫지만, 개발 결과에 포함된 중대한 문서도
`verify-documentation final`의 독립 입력·전체 감사 축·명시 verdict를 요구합니다. 같은
reviewer가 두 역할을 수행해도 이 specialist phase가 실제 trace에 있을 때만 중복하지
않은 것으로 판정합니다.

## 결정 7. 새 path는 기존 path가 아니라 pre-change topology에서 산출합니다

새 numbered document의 path를 고르기 전에 direct directory의 실제 file, 현재 책임과
inbound link를 수집합니다. 그 결과 새 책임 group·owner 분리·재번호·rename이 필요하면
구조 변경으로 다룹니다. 기존 router와 filename은 현재 상태를 설명하는 source 후보이지만,
번호·이름이 옳다는 독립 oracle은 아닙니다.

활성 책임 group은 현재 독자 순서에 따라 `100`부터 연속된 hundred block으로 다시
계산합니다. 기존 첫 문서가 `200`이라고 해서 새 책임을 `300`에 추가하지 않습니다.
Filename도 현재 canonical 책임과 독자 질문에 대조하고, 폐기된 system·delivery 범위를
나타내면 그 이름을 새 path의 pattern으로 복제하지 않습니다.

이 변경은 모든 문서 편집에 repository 전수조사를 강제하지 않습니다. Existing section의
내용만 바꾸고 owner·path·번호 topology가 유지되는 작업은 해당 문서와 인접 구조로
닫습니다. 반대로 새 file이 새 group을 만들거나 group map entry, owner 분리, 재번호와
rename이 하나라도 필요하면 line 수와 관계없이 구조 깊이와 before/after topology 검증을
적용합니다.

기존 router를 최종 정답으로 받아들이는 방식은 빠르지만 router와 file이 같은 과거 오류를
공유하면 self-consistency만 확인하게 됩니다. Pre-change topology와 상위 번호·이름 계약을
별도 입력으로 고정해 이 순환을 끊습니다.

## 결정 8. Procedure는 문장보다 사람의 실행 계약을 먼저 닫습니다

Procedure를 쓰거나 실질적으로 확장하기 전에 사람의 trigger·목표, actor, 실행 host·현재
directory, exact entrypoint, 입력·secret source, 기대 결과와 실패 뒤 재실행 entrypoint를
source에서 복원합니다. 작성 뒤에는 후보만 읽어 같은 실행 계약을 역으로 복원할 수 있어야
합니다.

반복 가능한 값 조립·검증·secret 전달·오류 처리 logic이 필요한데 repository entrypoint가
없으면 긴 shell block을 runbook에 넣어 구현 공백을 숨기지 않습니다. 실행 불가 경계와 구현
owner를 미충족 dependency로 반환합니다. 현재 사용자 원자가 구현을 독립적으로 포함하지
않으면 구현 phase를 호출하지 않고 차단 여부·선택지·영향을 보고해 별도 결정을 기다립니다.
표준 단일 명령이나 사람이 직접 판단하는 Console 단계까지 wrapper로 만들지는 않습니다.

모든 runbook에 고정 표를 강제하거나 code block 줄 수로 실패를 판정하는 대안은 배제합니다.
형식과 분량은 원인이 아니며, 사람이 정확한 entrypoint로 시작·관찰·재개할 수 있는지가
oracle입니다.

## 결정 9. 반복 문서군은 저상세 골격과 상세 대표 문서 뒤에 확장합니다

### 배경

중대한 구조 proposal은 owner·topology가 작성 가능한지를 검증했지만, 같은 문서군이 공유할
설명 level·heading hierarchy·producer coverage를 실제 문장으로 증명하지 않았습니다. 그
상태에서 전체 sibling을 먼저 작성하면 final 감사가 공통 구조와 누락된 출력 해석을 뒤늦게
발견하고, finding마다 모든 문서를 다시 여는 authoring loop가 됩니다.

### 선택

- 반복 문서를 쓰기 전에 공통 독자 질문, 각 설명 level의 책임, 전체 문서·section·source·
  고유 정보 inventory와 대표 문서의 closure oracle을 작업 중 authoring contract로 둡니다.
- 전체 탐색 관계의 저상세 골격을 먼저 대조하고, 공통 contract를 실제로 소비하는 상세 문서
  하나를 완성해 작성자의 두 번째 source-first 대조를 통과한 뒤 나머지 sibling을 확장합니다.
- 유한한 command·field·option 집합을 설명하면 대표 문서에서 producer, 설명 위치, 출력·판정과
  다음 탐색을 양방향으로 추적합니다. 이 구조는 특정 command 목록을 영구 template로 만들기
  위한 것이 아니라 사용자가 요구한 설명 깊이와 source coverage를 먼저 증명하기 위한 것입니다.
- Final 감사가 공통 hierarchy·coverage 결함을 처음 발견하면 patch를 반복하지 않고 authoring
  contract와 그 contract를 소비한 후보를 무효화해 대표 문서부터 다시 작성합니다.

### 배제 대안과 재검토 조건

모든 문서에 같은 section 이름을 강제하거나 문서·명령 개수 상한을 두지 않습니다. Routine
국소 편집과 source로 유일하게 정해지는 기계적 동일 변경에도 대표 문서 gate를 추가하지
않습니다. 대표 문서가 나머지 sibling의 고유 source를 가리거나, final을 authoring loop로
사용하는 회귀가 생기면 공통 contract와 per-document 고유 inventory 경계를 함께 재검토합니다.

## 결정 10. Scenario의 재사용 흐름과 live delta를 실행 전이로 구분합니다

### 배경

Staging Scenario는 재사용 가능한 전체 순서와 한 환경의 현재 상태를 함께 소유하지만, 새
Super Admin script가 기존 EC2에 없다는 live delta를 `확장 EC2 실행 파일 재전달`이라는 두
번째 영구 실행 항목으로 추가했습니다. 기존 전달 완료 snapshot도 그대로 남아, 빈 환경에서
Scenario를 재생하면 같은 archive 전달을 두 번 수행하는 recipe가 됐습니다.

### 선택

- 재사용 실행 항목과 복합 항목의 하위 행동을 actor·artifact·effective target state·상태
  전이·완료 조건으로 대조합니다. 이 값이 같고 revision·시점·임시 경로·`확장`·`재전달`
  표현만 다르면 기존 항목을 현재 계약으로 갱신합니다.
- 이미 구축한 환경에 새 inventory를 적용해야 하는 사실은 현재 live 상태·다음 행동·blocker가
  소유합니다. 실행 뒤에는 새 현재 상태로 교체하고 영구 단계나 과거 완료 snapshot으로
  남기지 않습니다.
- 작성자 대조와 specialist final은 후보를 빈 환경에서 순서대로 재생해 같은 상태 전이가 한
  번만 나타나는지 확인합니다.

### 배제 대안과 재검토 조건

특정 script 이름이나 재전달을 금지하지 않고, 실제로 독립된 상태 전이와 복구 checkpoint는
별도 항목으로 유지합니다. 서로 다른 작업을 이름이 비슷하다는 이유로 합치거나, 현재 live
상태를 Scenario 밖의 SOT에 옮겨 중복하면 이 경계를 재검토합니다.

## 배제한 대안

| 대안 | 배제 이유 |
| --- | --- |
| 문서별 고정 section을 늘림 | 목적이 다른 문서에 빈 섹션과 부차적 내용을 강제합니다. |
| 예시·금칙어를 계속 추가함 | 새로운 edge case마다 규칙이 팽창하고 의미 판단을 대체하지 못합니다. |
| Markdown schema·catalog·custom verifier | 작성자가 입력한 분류를 다시 읽는 자기일관성 검사가 되며 유지비와 false block이 큽니다. |
| 모든 문서를 독립 검토함 | 작은 국소 수정까지 비용을 늘려 순효과가 낮습니다. |
| 완료 work item에 전체 계획·로그를 보존함 | 현재 SOT와 Git 이력이 소유할 내용을 중복합니다. |
| 기존 router·filename을 새 path의 pattern으로 재사용 | 삭제·책임 변경으로 stale해진 구조를 self-consistent한 새 topology로 확대할 수 있습니다. |
| Runbook 안에 재사용 shell program을 직접 작성 | 실행·test·재사용 owner가 Markdown에만 남고 사람이 문맥을 조립해야 합니다. |
| 모든 단계에 wrapper script 강제 | 표준 단일 명령과 사람의 Console 판단까지 불필요한 구현으로 확대합니다. |

## 검증과 제거 조건

`verify-documentation`은 다음 중 하나가 확인되면 축소하거나 제거합니다.

- 고영향 문서에서도 독립 검토가 추가 결함을 거의 발견하지 못합니다.
- `verify-plan`·`verify-dev`와 호출 범위가 실질적으로 중복됩니다.
- 검토 지연과 문맥 재구성 비용이 예방 효과보다 커집니다.
- 더 단순한 peer review나 기존 workflow 단계가 같은 효과를 냅니다.
