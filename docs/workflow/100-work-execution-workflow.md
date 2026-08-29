# 작업 실행 워크플로우

이 문서는 요청의 의미와 상태 변경 권한, 결과 단위, 의존·병렬 경계, 통제 변경
생애주기와 공통 완료 판정을 소유합니다. Domain별 설계·개발·문서·Git 절차와 skill의
고유 판단은 관련 owner가 소유하며 이 문서에 복제하지 않습니다.

## 1. 요청 계약

### Bootstrap과 source lock

모든 사용자 요청은 현재 원문, 직접 선행 문맥, `AGENTS.md`와 결과를 바꿀 authoritative
source만 읽는 Bootstrap에서 시작합니다. 이 phase에서는 아래 계약을 복원하기 위한
source discovery 외의 조사·계획·답변 작성·mutation을 시작하지 않습니다.

새 사용자 메시지가 들어오면 진행 중인 작업의 다음 답변·도구 호출·review·
mutation 전에 이 Bootstrap으로 다시 진입합니다. 먼저 현재 메시지의 원자를
고정하고, 직접 선행 문맥은 각 원자가 명시적으로 연결한 의미만 보완합니다.
이전 요청과 주제가 같거나 작업이 미완료였다는 사실, Agent가 기존 작업을
완료하려는 의도는 과거 실행 권한을 현재 원자에 전파하지 않습니다.

상태 변경 경로의 positive source는 현재 generation에서 그 효과를 독립적으로 요청한
원문의 exact span입니다. 직접 선행 문맥은 이 span이 있을 때 대상·입력·제약을 보완할 수
있지만, 이전 generation의 실행 동사로 현재 질문·비교·이의의 권한을 채우지 않습니다.
목록 제목, `request` 같은 구획 label과 skill trigger도 하위 문장의 문장 기능을 실행으로
바꾸지 않습니다. 현재 generation에 positive source가 없으면 mutation admission은 닫힌
상태로 유지합니다.

현재 메시지의 `반영해`, `계속해`, `그 문서를 고쳐`처럼 실행 동사가 직접 선행 문맥의
미완료 결과를 가리키면 그 현재 span 자체가 해당 결과를 이어 가는 positive source입니다.
이때 직접 선행 문맥은 대상·기존 acceptance oracle·아직 닫히지 않은 결과를 복원하고,
현재 메시지가 명시적으로 바꾼 의미만 갱신합니다. 피드백 한 건이나 국소 section 수정이
끝났다는 이유로 원래 결과를 그 hunk로 축소하거나 checkpoint를 terminal로 만들지 않습니다.
반대로 현재 메시지에 실행 동사가 없거나 다른 결과로 대체·제외한 경우에는 같은 주제와
미완료 상태만으로 과거 권한을 부활시키지 않습니다.

현재 메시지가 작업 주체·범위·권한·안전성·사실을 이의 제기하거나 그 판단의 이유를
묻는다면 그 원자를 먼저 고정합니다. 같은 메시지에 상태 질문이 섞여 있어도 이의 대상의
과거 실행 권한을 유지하는 근거로 사용하지 않으며, 해당 원자의 답변·검토를 닫기 전에
pending 조사·review·mutation을 시작하거나 재개하지 않습니다. 이의가 없는 단순 상태
질문과 같은 메시지의 독립된 명시 추가·계속 요청만 상태 변경 권한 source gate에서 기존
권한의 유지·추가를 별도로 판정합니다.

- exact 대상과 사용자가 그 대상에 관해 요청한 문장 기능
- 달성할 결과와 관찰 가능한 완료 조건
- 포함·제외 범위와 순서
- 상태 변경, Git과 외부 부작용 권한
- 결과를 바꿀 수 있는 authoritative source와 미확정 값
- 원래 작업으로 돌아갈 복귀점

선택문·인용문·지시어나 생략이 있으면 접근 가능한 원본을 먼저 읽습니다. 원본으로도
결과를 바꾸는 대상이 하나로 확정되지 않을 때만 그 대상만 질문합니다. 대화 요약,
비슷한 이름, 주변 주제와 Agent의 기억은 누락된 대상을 대신하지 않습니다.

사용자가 현재 원자에 사용할 수 있는 skill을 이름·tag·link로 명시하면, 현재 Agent는 첫
실질 판단이나 실행 전에 그 snapshot의 exact `SKILL.md` 전체와 skill이 필수로 연결한 공통
owner를 실제로 읽습니다. 읽은 content identity `R`, 현재 원자 `G`, 선택한 specialist,
문장 기능·mode, 허용·금지 효과, 완료 계약과 복귀점을 같은 operation map에 결속한 뒤에만
원인·subtype·grouping·계획을 확정하거나 위임·review·mutation·Git·외부 효과를 시작합니다.
Catalog description, skill 이름, 이전 turn의 기억과 `읽었다`는 자기 선언은 실제 load와
결속 증거가 아닙니다.

한 tool 호출에서 여러 source를 함께 요청했거나 출력이 잘림·pagination·token 제한으로
끝까지 반환되지 않았으면 해당 source는 실제로 읽은 `R`이 아닙니다. 그 결과를 받은
consumer는 같은 discovery phase에 남아 source별로 끝까지 다시 읽고 content identity를
동결하기 전에는 specialist 결과 `P`를 만들거나 판단·writer·review·Git으로 인계하지
않습니다.

Skill 명시는 절차를 선택할 뿐 권한을 만들지 않습니다. 질문·언급이면 읽기 전용으로,
실행 동사면 그 원자의 실행 범위로 판정하고, 사용자가 더 좁은 권한이나 금지 효과를
명시하면 이를 보존합니다. Exact source를 먼저 읽겠다는 최소 안내는 읽을 source와
source read가 선행한다는 사실만 말할 수 있습니다. Specialist·mode·허용 효과·terminal,
grouping·subtype과 계획을 투영하면 이미 실질 효과입니다. 이 최소 안내와 Bootstrap source
read 자체는 실질 효과가 아니며, source가 실제로 접근 불가능할 때만 추측하지 않고 그
blocker를 반환합니다.

### 직접 판정 책임

현재 Agent는 Bootstrap에서 복원한 사용자 원문을 기준으로 대상, 문장 기능, 결과,
제약·제외, 권한, 의존 순서와 완료 oracle을 직접 판정합니다. 명시적 실행 동사는 해당
범위의 실행으로, 질문 동사는 답변으로 처리하며, 둘을 바꾸거나 기본 판정 책임을
reviewer·dispatcher·hook에 넘기지 않습니다.

현재 원자의 concrete 결과와 그 결과가 미래의 모든 경로에서 절대 우회되지 않게 만드는
보장은 별도 결과입니다. 사용자가 후자를 명시적으로 요구하지 않았다면 현재 결과를 먼저
만들며, 별도 runtime·orchestrator·hook이 없다는 사실을 현재 Agent가 만들 수 있는 결과의
차단 사유로 바꾸지 않습니다. review와 검증은 현재 결과의 근거를 보강하지만 원래 결과나
현재 Agent의 직접 책임을 대체하지 않습니다.

원문과 직접 선행 문맥으로 결과를 하나로 확정할 수 없을 때만 그 미확정 값만
질문합니다. 별도 review는 계획·개발 후보·중대한 문서처럼 관련 domain workflow가
독립 검증을 요구하는 산출물에 적용하며, 현재 요청의 권한을 만들거나 명시된 문장
기능을 다시 정의하지 않습니다.

### 요청 원자화 gate

메시지에 둘 이상의 문장·block이 있거나 대상, 문장 기능, 결과, 제약, 권한 또는
순서가 독립적으로 달라질 수 있으면 다음 단위로 나눕니다.

```text
원자 = 대상 + 문장 기능 + 요구 결과 + 제약·제외 + 권한 + 의존 순서 + 완료 oracle
```

같은 주제·파일·skill·인접 문장이라는 사실은 병합 근거가 아닙니다. 상위 실행 요청이
여러 하위 대상을 명시적으로 묶었다면 그 상위 결과는 보존하되, 각 하위 원자의 질문과
권한은 따로 판정합니다.

사용자가 번호·block마다 실행 동사나 skill을 반복해 결과를 따로 지정하면 각 표시는 별도
원자의 positive source입니다. 공통 source·owner·예상 원인, 조사 재사용과 작업량 절감은
원자 사이의 관계일 뿐 membership·완료 oracle·검증 verdict를 합치는 권한이 아닙니다.
사용자가 통합 결과를 직접 요구했을 때만 상위 grouping을 만들고, 그때도 하위 원자의
identity·권한·증거와 완료 상태를 보존합니다. `이것부터`처럼 순서만 바꾼 후속 지시는
선택한 원자를 현재 응답 scope의 첫 결과로 옮길 뿐 나머지 원자를 취소하거나 합치지
않습니다.

사용자가 상위 grouping을 직접 요구했다면 grouping 허용 여부를 다시 심사하거나 source
discovery 뒤로 미루지 않습니다. 첫 projection에서 상위 결과를 그대로 수락하고 보존할
하위 identity·증거·완료 oracle을 함께 고정합니다. 이후 source discovery는 이 확정된
구조의 내용을 채울 뿐 grouping 권한을 다시 열지 않습니다.

문장 기능은 최소한 다음처럼 구분합니다.

| 기능 | 기본 효과 |
| --- | --- |
| 질문·설명·원인·상태 확인 | 읽기 전용 결과 |
| 검토·감사·비교 | 읽기 전용 판정과 근거 |
| 계획 요청 | 계획 artifact와 결정 결과 |
| 구현·수정·정리·개편 | 명시 범위의 상태 변경 |
| stage·commit·push·외부 apply | 각각 별도 Git·외부 권한 |
| 중단·보류·대체·제외 | 명시한 대상과 의미 차원만 변경 |

질문을 실행 요청으로 확대하거나 실행 요청을 질문으로 축소하지 않습니다. 여러 원자의
처리 방식과 순서가 사용자 기대를 바꿀 수 있을 때는 실행 전에 짧게 되돌려 보여줍니다.

질문 원자는 답변을 작성하기 전에 원문의 `대상·술어·명시된 범위·빈도·시점 수식어`를
첫 직접 답변 명제에 그대로 투영합니다. 직접 선행 문맥의 상태·증거·추천은 답의 근거와
후속 설명을 보완할 수 있지만, 원문에 없는 수식어를 더하거나 원문 수식어를 제거해 다른
질문으로 바꾸지 않습니다. 첫 명제만 읽어도 원래 질문의 판정값이 닫혀야 하며, 조건·예외·
반복 여부 같은 보충 설명은 이 직접 답을 대체하거나 범위를 바꾸지 않는 별도 문장으로만
뒤에 둡니다.

답변을 내보내기 전에는 첫 명제를 원문으로 역대조합니다. 첫 명제의 대상·술어·수식어가
각각 현재 원문의 exact span이나 답에 필요한 authoritative fact로 돌아가지 않거나,
원래 질문의 판정 없이 문맥에서 파생한 더 좁거나 넓은 질문만 닫으면 답변 후보를 폐기합니다.

사용자가 한 메시지나 직접 선행 문맥에서 여러 항목을 하나의 실행 batch로 제시하면, 첫
실행 약속·mutation primary 경로 선택·계획·worker 위임·review·mutation 전에 다음 권한
coverage를 모든 항목에 대해 완성합니다. 새 사용자 메시지마다 이전 coverage를 폐기하고
현재 generation의 원문에서 다시 만듭니다.

```text
raw 항목 identity → 현재 generation의 exact span → 문장 기능 → 요청 효과
→ 같은 generation에 있는 상태 변경 권한의 positive source 또는 미확정·읽기 전용 사유
→ batch admission 상태
```

같은 batch에 질문·비교·우려·미확정 선택처럼 상태 변경 권한이 없는 항목이 하나라도 있으면
승인된 다른 항목만 먼저 실행하지 않습니다. Batch 전체를 영향 전에 멈추고, 권한이 없는
이유와 아직 닫히지 않은 차원을 설명한 뒤 사용자와 그 논의를 끝냅니다. 사용자가 해당
항목을 명시적으로 선택·제외·취소해 모든 항목의 처분이 닫힌 새 generation에서만 batch를
실행합니다. 인접한 `그렇게 해`, 같은 번호 목록, 공통 주제와 상위 구현 목적은 질문을
선택이나 실행 승인으로 바꾸지 않습니다.

현재 generation의 모든 항목이 질문·설명·비교이면 해당 owner에서 답변·근거·구체적
제안까지 읽기 전용으로 완결하고, mutation specialist를 선택하거나 `고치겠습니다`처럼
실행 scope를 확정하지 않습니다. 반대로 같은 generation이 exact mutation을 명시하면 그
span을 positive source로 연결해 승인된 범위를 실행하며, 이 gate를 이유로 재승인을
요구하지 않습니다.

서로 다른 메시지나 상위 결과에 속하고 dependency·write set·완료 oracle이 독립된 실행은
한 batch의 미확정 항목 때문에 전역 차단하지 않습니다. 모든 항목이 이미 명시적으로
승인됐거나 미확정 항목을 사용자가 batch에서 제외했다면, 항목 수만으로 추가 승인·외부
dispatcher·별도 실행 문법을 요구하지 않습니다.

### 상태·실행 결과 입력의 operation map 재구성

사용자가 상태나 실행 결과를 전달하면 답변의 source를 고르기 전에 Bootstrap으로
재진입합니다. 직전 하위 단위만 갱신하지 않고 현재 상위 결과의 operation map을 다음
경계로 복원합니다.

```text
상위 결과
→ 결과 원자 | 권위·근거 source | 상태 | 현재 milestone
→ exact consumer·합류점 | canonical scope | 현재 응답 scope·유효 기간
```

각 원자의 상태를 넣기 전에 상태 증거의 source와 coverage를 먼저 복원합니다. 같은 thread의
사용자 실행 결과·직접 관찰, 현재 live 상태의 canonical Operations Scenario, repository의
구현·절차·작업 상태 문서는 서로 다른 주장을 증명합니다. Repository에 script나 procedure가
존재한다는 사실은 live 실행 여부를 증명하지 않고, 선택한 문서·검색 결과·요약에 기록이
없다는 사실도 미실행을 증명하지 않습니다.

```text
결과 원자
→ 상태 주장 | 상태를 직접 관찰한 source | source가 덮는 환경·시점·단계
→ 더 최신인 성공·실패·무효화 증거 | 현재 판정 또는 미증명
```

같은 thread에서 사용자가 전달한 성공 결과나 현재 live scenario의 완료 상태를 찾았으면,
그보다 오래되거나 live 상태를 소유하지 않는 project SOT·runbook·script README의 누락으로
`Closed`를 `미실행`으로 낮추지 않습니다. 더 최신의 직접 실패·삭제·무효화 관찰이 있을 때만
해당 상태를 갱신합니다. 반대로 직접 상태 source를 읽지 못했거나 검색 범위가 전체 실행
구간을 덮지 않으면 `미증명`으로 유지합니다. `기록이 없음`, `아직 안 함`과 재실행 추천으로
확대하지 않습니다.

특히 최초 생성·일회성 bootstrap·기존 state를 fail-close하는 작업은 `미증명`만으로 다음
실행에 넣지 않습니다. 현재 접근 가능한 같은 thread와 canonical live owner를 한 번에
대조해 상태를 닫고, 그래도 actual 환경에만 증거가 남아 있으면 필요한 read-only 확인을
하나의 묶음으로 요청합니다. 이미 확인된 완료 작업을 다시 실행하는 명령은 제시하지
않습니다.

Read-only 확인·inventory·preflight를 활성 operation이나 다음 단계의 필수 gate로 넣기
전에는 각 항목을 `관찰값·상태 → 그 값을 실제로 소비하는 후속 입력·분기·안전조건·rollback·
postcondition`에 연결합니다. 이미 완료된 상태를 다시 보여 주기만 하고 이 연결이 없는
확인은 선택 진단이며, 미실행을 중단·미완료·blocker로 판정하지 않습니다. 같은 invariant를
canonical script가 상태 변경 전에 직접 조회해 fail-close하면 사람의 중복 선행 확인도
선택 진단입니다. 다만 script 밖에서 상태 변경 대상을 고르거나 rollback 값을 확보하는
확인은 그 값을 소비하는 변경 직전의 필수 gate로 유지합니다. 필수와 선택 진단은 runbook·
scenario·실행 안내의 같은 checklist나 완료 gate에 섞지 않습니다.

이 분류를 답변·operation map·runbook 실행 단위에 투영하기 전에 다음 소비 원장을 현재
generation의 source에서 먼저 닫습니다.

```text
확인 항목·관찰값
→ 최초 소비자와 값에 따른 행동 차이
→ 증거의 resource·환경·유효 기간
→ 현재 접근 가능한 기존 증거와 재사용 가능 여부
→ 필수 1회 / 조건부 필수 / 선택 진단
```

필수로 분류한 항목은 최초 소비자·실패 시 중단 지점과 기존 증거가 언제 충족하는지를 함께
표시합니다. 같은 resource·환경의 유효한 결과가 이미 있으면 재실행을 요구하지 않습니다.
소비자가 없거나 값을 보아도 다음 행동이 달라지지 않는 항목, 다른 필수 gate가 같은 결과를
직접 증명한 항목은 선택 진단으로 내려 별도 section에 둡니다. 이 원장을 닫지 못했거나 필수·
선택 항목을 같은 순차 실행 block에 둔 후보는 사용자 실행 안내나 canonical runbook
writer로 넘기지 않습니다.

과거 답변·판단·행동이 왜 발생했는지 설명할 때에는 현재 상태 source를 과거 actor의 입력으로
소급하지 않습니다. 첫 원인 주장 전에 다음 시간 방향과 실제 소비 증거를 복원합니다.

```text
설명할 과거 결과·발생 시각
→ 원인 후보 source의 생성·유효·접근 가능 시각
→ 당시 actor의 실제 read·invocation trace 또는 그 부재가 덮는 범위
→ causal input / 후발 관찰·정정 source / 미증명
```

원인 후보가 결과 뒤에 생성·유효해졌으면 그 source는 앞선 결과의 원인이 될 수 없습니다.
후발 source는 현재 상태를 관찰하거나 앞선 판단을 반박·정정하는 근거일 수 있지만, 당시
actor가 읽었다는 증거로 바뀌지 않습니다. 부분 trace나 현재 artifact 존재만으로 과거
invocation을 만들지 않으며, 당시 접근 가능한 source와 실제 소비를 닫지 못하면 원인을
`미증명`으로 보고합니다. 이 대조는 과거 원인·근거를 답하는 첫 실질 문장 전에 수행합니다.

Operation map은 닫힌 결과와 남은 결과를 함께 보존합니다. Dependency context는 이 map에
보존된 상위 결과 관련 원자 전체와 각 원자의 상태·증거입니다. `Closed`, `Invalidated`,
canonical 제외·대체처럼 현재 실행·추천 대상이 아닌 원자도 이미 충족된 선행 결과인지,
무효화된 입력인지 또는 현재 활성 원자와 충돌하는지를 판정할 때에는 context에 남습니다.
그중 활성 operation universe는
사용자가 승인한 상위 결과에 속하고 `Closed`가 아니며, canonical 상위 scope에서 명시적으로
취소·제외·대체되지 않은 원자 전체입니다. `이번 답변에서는 제외`처럼 현재 projection만
좁힌 원자는 canonical 활성 집합과 dependency context에 남기고 그 응답의 추천 후보·
출력에서만 제외합니다. `Blocked`·`Invalidated`
원자와 현재 milestone보다 뒤에서 합류하는 원자도 활성 집합에 남습니다. 현재 milestone,
방금 성공·실패한 script와 다음 consumer 하나는 다른 원자를 자동 제외하는 범위 source가
아닙니다.

`지금 진행할 수 있는 것`처럼 상태 조건으로 결과를 고르는 질문은 그 조건에 맞는 원자만
추천하라는 문장 기능이지, 같은 명시 범위의 `Blocked` 원자와 blocker·재개 조건을 답변
scope에서 삭제하라는 지시가 아닙니다. 사용자가 특정 원자나 상태 설명까지 명시적으로
제외하지 않았다면 runnable 결과와 직접 대비되는 non-runnable 결과의 exact blocker를 같은
projection에 남깁니다. 반대로 `M2는 이번 답변에서 제외`처럼 대상·유효 기간을 명시한 제외는
현재 응답 scope 변경으로 처리합니다.

새 상태는 직접 영향을 받은 원자의 상태·증거만 갱신합니다. 원자를 활성 집합에서
제거하려면 사용자가 canonical 상위 scope에서 해당 원자를 명시적으로 취소·제외·대체했거나
그 원자의 권위 source가 상위 결과와 무관함을 확정한 근거가 필요합니다. 현재 응답 scope
제외에는 그 유효 기간만 기록하며 canonical 상태를 바꾸지 않습니다. 보고·전문 owner가
반환값에서 원자를 생략했다는 사실은 제거 근거가 아닙니다. 아직 승인되지 않은 미래 작업을
완결성을 위해 새 원자로 만들지도 않습니다.

다음 행동·순서·대기·전체 handoff를 판정할 때에는 이 동결 집합을 입력으로 사용합니다.

- canonical 상태가 하나의 exact 다음 행동을 이미 확정했고 그 원자가 현재 응답 scope에도
  포함되면 그 결과를 그대로 투영하고 열린 비교로 바꾸지 않습니다. 현재 응답에서 그
  원자를 명시적으로 제외했다면 canonical map에는 유지하되 이번 projection에는 넣지 않고
  현재 응답 scope의 남은 원자로 판정을 계속합니다.
- 그렇지 않고 현재 응답 범위의 활성 원자가 둘 이상이거나 관계·추천 판정이 필요하면 해당
  specialist에게 `canonical map에 보존된 상위 결과 관련 원자·상태·증거 전체 = dependency
  context`, `현재 응답 scope의 활성 원자 = 추천·출력 후보`로 구분한 두 집합을 손실 없이
  전달합니다. Specialist는 domain source로 exact
  consumer·독립·합류·리마인더·추천만 판정하며 기존 원자의 membership·scope·상태를
  삭제하거나 다시 정하지 않습니다. 응답에서 제외된 원자는 hard edge 판정에는 사용하되
  추천·리마인더·출력 후보로 되살리지 않습니다.
- Specialist가 dependency context에 이미 있는 비활성 원자를 선행 결과로 확인하면 그 원자의
  상태·증거를 사용해 충족·무효·충돌 관계를 반환하되 추천 후보로 되살리거나 상태를 바꾸지
  않습니다. 현재 Agent가 기존 `Closed` 증거의 유효성, `Invalidated` 재개 또는 canonical
  제외와 현재 활성 원자의 충돌을 workflow의 상태 규칙으로 판정해 map에 병합합니다.
  필수 결과가 canonical operation map에 아예 표현되지 않은 경우에만 relation 결과로 새
  membership을 만들지 않고 작업 실행 workflow의 source discovery·membership 단계로
  돌아갑니다. Map을 갱신·재동결한 뒤 relation 판정을 새로 시작합니다.
- 현재 Agent는 specialist가 반환한 source-confirmed 관계만 기존 map에 병합합니다. 누락된
  원자는 그대로 유지한 뒤 완료 및 작업 보고 workflow에 넘깁니다.
- 새 추천이 필요하지 않으면 확인된 상태만 보고하고 임의의 다음 행동을 만들지 않습니다.

상태·다음 작업 답변에서 각 완료·미완료·재실행 주장은 위 상태 증거 entry로 역추적돼야
합니다. Workflow나 skill file을 읽었다는 사실, source끼리 내부적으로 일치한다는 사실은
operation map을 실제로 복원했다는 invocation evidence가 아닙니다. 상태 증거 coverage를
닫지 않은 원자는 `미증명`으로 남기고, 그 원자를 다음 실행으로 승격하지 않습니다.

이 section은 operation membership·상태와 owner routing만 소유합니다. 기술 관계·추천의
내용은 해당 specialist, 출력 순서는 완료 및 작업 보고 workflow가 소유합니다.

### 의미 authority

다음 의미를 서로 대체하지 않습니다.

1. 사용자 요구 결과
2. 사용자 명시 제약·제외
3. 사용자의 우려·질문·평가 기준
4. authoritative source의 사실과 계약
5. Agent의 가정·추천

후속 표현은 명시적으로 바꾼 차원만 갱신합니다. 우려를 새 요구사항으로 승격하거나,
추천을 확정 결정으로 바꾸거나, 하위 순서 변경으로 상위 결과를 지우지 않습니다.

어떤 값을 `사용자 결정·요구·승인`이라고 귀속하려면 사용자가 그 차원의 값을 긍정적으로
선택한 원문 source가 있어야 합니다. 설명 질문, 우려·평가 기준, 인접 차원의 승인, 값을
가리키지 않은 일반 진행 요청과 Agent의 추천·선언은 그 source가 아닙니다. Authoritative
source가 구현 상세를 유일하게 고정하면 질문 없이 소비할 수 있지만, 이때 권위는 해당
source와 Agent 판단이며 사용자 결정으로 바꿔 쓰지 않습니다.

질문·비교·형식 협의로 시작해 상태 변경 권한이 없는 논의에서는 후속 generation이 선호,
제약, 대안이나 추가 사양만 갱신해도 그 의미는 결정 후보의 갱신이며 mutation 요청이
아닙니다. 결정 후보가 하나로 좁혀지거나 사용자가 그 값을 선택한 사실도 artifact를 지금
변경하라는 권한을 만들지 않습니다.

이 논의에서 상태 변경으로 전환하려면 현재 generation의 원문이 직접 선행 문맥으로 대상을
보완할 수 있을 뿐 아니라, 변경할 효과와 그것을 지금 실행하라는 문장 기능을 함께 완결해야
합니다. 실행 의도를 완결하지 않은 추가 사양·연결 표현이면 답변·후보만 갱신하고 writer·
mutation specialist·상태 변경 tool을 호출하지 않습니다. 반대로 완결된 실행 요청이면
논의 중이었다는 이유로 재승인을 요구하지 않고 exact 범위만 실행합니다.

### 활성 결정 기준선

선행 논의의 선택을 소비해 계획·변경·검증하거나, 그 선택을 전제로 흐름을 설명하거나,
`무엇을 정했는가`를 판정할 때에는 행동이나 답변 전에 결정 차원별 활성 기준선을
복원합니다.

Source discovery만 하는 최소 안내를 제외하고, 결과를 바꾸는 값을 선택·유지·교체하거나
`이 값이 맞다`, `이대로 반영한다`, `남은 결정이 없다`와 동등한 결론을 처음 투영하는
commentary·답변·계획·writer 호출 전에도 같은 기준선을 먼저 닫습니다. 각 값은 다음 입장
결과 중 하나여야 합니다.

| 입장 결과 | 필요한 근거 | 다음 행동 |
| --- | --- | --- |
| 사용자 선택 | 결정 차원과 exact 값을 긍정적으로 선택한 사용자 원문 | 선택한 값만 동결해 소비 |
| source 제약 | 독립 authoritative source가 허용 값을 하나로 제한함 | source와 파생 범위를 표시하고 질문 없이 소비 |
| Agent 판단·추천 | 적용 specialist가 Agent 책임으로 분류한 기술 표준, 내부 구조·schema·API 실현, test 방식과 그 밖의 구현 값 | 사용자 결정으로 귀속하거나 승인 질문으로 넘기지 않고 근거·trade-off·재검토 조건을 제시. 계획이 결과이면 추천 상태로 writer가 기록할 수 있음 |
| 열린 사용자 결정 | source가 하나로 제한하지 못하고 제품 동작·범위·UX 우선순위, 법·개인정보 감수 수준, 예산·외부 계약·운영 책임, 출시 범위·일정처럼 프로젝트 외부 판단이 필요한 값 | 관련 결정을 한 라운드에 모아 질문하고 그 값에 의존하는 projection·writer·review를 시작하지 않음 |
| 미증명 | 필요한 원문·source·현재 상태를 읽지 못함 | 추측하지 않고 공백·영향과 안전한 독립 단위를 판정 |

API 경로, module 경계, schema 정규화처럼 내부 기술 결과가 크거나 되돌리기 어렵다는 이유만으로
`열린 사용자 결정`으로 승격하지 않습니다. 그 기술안이 제품 동작·정보 범위·외부 계약이나
운영 책임 중 어느 값을 함께 선택해야만 닫히는 경우에는 그 외부 판단만 사용자 결정으로
분리하고 기술안은 Agent 추천으로 연결합니다.

계획·설계가 현재 결과이면 Agent 추천은 사용자 확정 결정으로 바꾸지 않고 `Proposed`의 기술
후보로 기록할 수 있습니다. 모든 사용자 소유 값이 닫히고 적용 specialist가 요구한 source·
대안·trade-off와 독립 검증을 갖춘 Agent 기술 결정은 `Planned`의 기술 계약으로 소비할 수
있지만 사용자 선택으로 귀속하지 않습니다. 질문·반론·댓글이 기존 Agent 추천의 전제나 결과를 문제 삼아도
사용자가 대체 값을 선택한 것은 아닙니다. 먼저 해당 추천을 다시 검토해 직접 답하고, 그 안에
프로젝트 외부 판단이 있으면 그 차원만 `열린 사용자 결정` 또는 `미증명`으로 되돌립니다.
열린 사용자 결정에는 대안·결과 차이·추천·한 줄 응답 형식을 제시하며, 답변 전에 그 값에
의존하는 추천을 새 확정값으로 바꾸거나 `반영하겠다`고 약속하지 않습니다.

```text
결정 차원 → 활성 값 → 권위·근거 source → 뒤의 명시적 대체 source 또는 없음
→ 유지·갱신·미확정
```

- 사용자가 제안 자체나 그 제안의 식별 값을 직접 가리켜 승인하면 선택한 차원에서 사용자
  결정이 됩니다. 선택되지 않은 후발 Agent 추천은 여전히 추천이며, `그 작업을 진행해`처럼
  추천 값을 가리키지 않은 일반 실행 승인은 기존 사용자 결정과 충돌하는 추천을 대체
  결정으로 승격하지 않습니다.
- 결정 차원은 한 값만 독립적으로 유지·변경할 수 있는 의미 단위로 나눕니다. 저장소 위치와
  그 안의 path처럼 하나만 바꿀 수 있는 값은 별도 차원이며, 복합 제안 전체를 직접
  대체한다는 source가 없으면 겨냥한 값 이외를 함께 바꾸지 않습니다.
- 질문에서 이름 붙인 대안을 선택했다는 source가 없다는 이유만으로 결정 차원 전체를
  미확정으로 강등하지 않습니다. 활성 값, 명시적으로 배제한 값과 미래 재검토 조건을 함께
  복원한 뒤 현재 결정을 답합니다.
- 더 최신 사용자 원문이 값을 직접 취소·대체하면 그 차원만 갱신합니다. source에 접근할 수
  없어 대체 여부를 판정할 수 없으면 기존 값을 추측으로 바꾸지 않고 source 공백과 영향을
  밝힙니다.
- 활성 값이 없는 차원만 미확정 비교·추천 절차로 보냅니다. 이미 확정된 차원을 다시 후보로
  열거나, 비교 절차가 기준선 복원을 대신하게 하지 않습니다.

이 기준선은 위 첫 실질 projection 전, 상태 변경의 첫 mutation 전, 전문 검증과 Git phase
인계 전에 각각 대조합니다. 검증 입력에는 원래 기준선과 유효한 대체 source를 포함하며,
reviewer는 같은 작업에서 만든 test·문서의 내부 일치만으로 활성 결정을 재정의하지 않습니다.

여러 독립 조건이나 source가 있으면 세션 또는 `/tmp`의 활성 조건 원장에만 다음을
연결합니다.

```text
상위 concrete 결과·완료 oracle
→ 결정 차원별 활성 값·권위·근거 source
→ 제외·미승인 질문·추천과 그 상태
→ 실제 control 기준선·요청 generation·target 후보 identity
→ phase·역할·exact write set·허용 효과
→ 실행·증거·완료 상태·복귀점
```

이 원장은 사고 보조물이며 repository schema, JSON이나 자기기입 verifier로 만들지
않습니다.

Worker·reviewer·Git처럼 phase를 넘는 actor handoff에는 다음 identity를 세션 원장에
결속합니다.

```text
I = 실제로 읽은 control 기준선 R
  + 현재 요청 원자의 generation G
  + 대상 candidate identity C
  + actor 호출 직전 target 상태 S0
  + 앞 phase가 확정한 전문 결과와 후속 gate P
```

- `R`은 현재 Agent와 actor가 실제로 읽은 canonical control source의 commit·tree·blob 또는
  동결 content digest입니다. 다른 branch나 main에 최신 규칙이 존재한다는 사실은 해당
  invocation이 그 규칙을 소비했다는 증거가 아닙니다. Target ref를 바꿀 권한이 없으면
  exact `R`을 격리된 입력으로 전달하고 target branch를 임의 merge·reset하지 않습니다.
  현재 요청에 skill locator가 명시돼 있으면 그 exact locator의 content를 `R`에 포함하며,
  target worktree의 같은 이름 파일로 조용히 대체하지 않습니다. Target 경로를 지배하는
  추가 `AGENTS.md`가 있으면 둘을 함께 소비합니다. 두 control identity의 충돌이 입장 결과를
  바꾸면 더 편한 쪽을 고르지 않고 control source 충돌과 영향 범위를 먼저 반환합니다.
- `G`는 Bootstrap이 확정한 현재 사용자 원자와 carry-forward·무효화 관계입니다. 이의나
  대체로 무효화된 원자의 이전 generation 결과는 내용이 유용해도 현재 evidence가 아닙니다.
- `C`는 repository·worktree, fixed base, `HEAD`, index, tracked diff, untracked path·content와
  적용되는 외부 상태를 포함한 exact snapshot입니다. Writer가 후보를 바꾸면 새 `C`를
  동결하고 이전 review·Git 인계를 승계하지 않습니다.
- `P`는 설계·문서 구조 proposal·도구 처분·검증처럼 앞 phase의 specialist가 확정한
  결과 identity, 다음 phase가 보존할 제약과 아직 필요한 후속 gate입니다. Owner·topology·
  write set을 바꾸는 전문 결과는 권고가 아니라 candidate의 dependency입니다. 다음 phase는
  그 결과가 candidate 어디에 반영됐는지 대조하고, 충돌하거나 누락되면 해당 결과와 이후
  review를 `Invalidated`로 돌립니다. 비슷한 축을 본 generic review나 reviewer 이름은 `P`의
  exact 결과·phase·verdict를 대신하지 않습니다.
- 결과에는 입력 `I`를 귀속합니다. 현재 Agent는 결과를 소비하기 직전에 target 상태 `S1`을
  다시 읽고 `S0`과 대조합니다. 설명되지 않은 drift나 identity 불일치는 기존 candidate와
  그 review를 `Invalidated`로 바꾸며 actor·command를 추측하거나 clean 상태를 완료로 쓰지
  않습니다.

관찰 가능한 invocation evidence가 없다는 사실은 `호출되지 않음`이 아니라 `미증명`입니다.
해당 시간 구간과 actor를 빠짐없이 기록한 authoritative trace를 실제로 읽은 경우에만
호출 부재를 확정하며, 부분 trace·Git history·현재 파일 존재로 음의 사실을 만들지 않습니다.

이 identity는 상태를 바꾸는 다중 phase handoff의 실행 증거입니다. 짧은 읽기 전용 질문이나
한 phase 안의 직접 source 확인을 별도 ledger·review·repository artifact로 확대하지 않습니다.

첫 worker 위임·mutation·review 전에 현재 Agent는 이 원장을 유한하게 채우고 해당 phase의
인계 입력으로 동결합니다. Worker와 reviewer에게는 같은 상위 결과·권위·포함·제외·oracle을
손실 없이 전달합니다. 하위 역할은 operation membership이나 권위를 다시 정하지 않으며,
새 finding은 mutation하지 않고 현재 Agent에게 반환합니다. 긍정 선택 source가 없는 추천을
`사용자 결정`으로 쓰거나 그 추천에 기반한 write set을 만들면 인계가 성립하지 않은
것이므로 해당 phase를 시작하지 않습니다.

세션 원장은 별도 artifact가 아니며, `/tmp` 원장은 활성 작업 동안만 유지합니다. 작업이
완료되거나 세션 밖으로 인계될 때에는 다음 실행자가 필요한 현재 결과·남은 책임·재개
조건만 기존 또는 생성이 승인된 work item과 사용자 보고에 정규화하고 임시 원장을
삭제합니다. 대화 원문, 내부 판단 과정과 이미 닫힌 조건은 인계 문서에 복사하지
않습니다. 기존 work item이 없고 생성 권한도 없으면 보고로만 인계하며 임시 artifact를
영구 상태처럼 남기지 않습니다.

### 완료 oracle 출처

완료 oracle은 원래 활성 요구가 제기된 시점의 사용자 원문과 당시 유효한 독립
authoritative source에서 고정합니다. 같은 작업에서 새로 만든 정책·SOT·test·fixture,
실패한 시도와 그 후속 회고는 변경 결과나 증거 후보일 수 있지만 선행 요구를
재정의하거나 스스로 충족한 근거가 될 수 없습니다.

oracle은 사용자가 기준 변경을 직접 확정했거나 원래 요구와 독립된 authoritative
source가 의미를 유일하게 결정할 때만 그 영향 범위에서 갱신합니다. 지적된 관찰 상태를
그대로 둔 채 이름·분류·정의만 바꿔 정상으로 재분류하면 실패입니다. 완료 감사는 후발
self-authored source를 제외한 원래 요구와 유효한 변경 결정에서 기대 결과를 다시
복원하고, 실제 artifact의 관찰 상태가 달라졌는지 또는 원래부터 옳았음이 독립적으로
증명되는지를 확인합니다.

작업 시작 전에 존재한 negative contract test·validator·정책 검사는 그 assertion이
보호하는 불변식과 authoritative owner를 baseline oracle에 포함합니다. 구현이 이 oracle에
막혔다는 이유로 assertion을 삭제·완화하거나 예외를 추가하는 것은 test 동기화가 아니라
기준 변경입니다. Writer는 더 최신 사용자 결정이나 독립 authoritative source가 기존
불변식을 명시적으로 대체한 경우에만 그 source와 영향 범위를 인계하고 oracle을 변경할 수
있습니다. 그런 source가 없으면 구현을 기존 계약에 맞추며, 구현·test·문서를 함께 바꿔
서로 일치시킨 결과를 완료 증거로 사용할 수 없습니다.

작업 결과의 완료는 clean status나 commit 개수가 아니라 원래 효과와 final artifact를
대조해 판정합니다.

```text
E = 원래 활성 요구의 기대 효과
K = final candidate·commit에서 실제 관찰되는 효과
X = 최신 유효 권한으로 취소·제외된 효과
D = candidate 밖에 남은 task-owned staged·tracked·untracked·외부 residual

closure ⇔ E = K ∪ X
       ∧ D = ∅
       ∧ K가 final artifact에서 실제로 관찰됨
```

`D = ∅`은 task-owned Git·recovery·외부 상태 전체의 authoritative source를 실제로 읽은
뒤에만 확정합니다. 기대 효과나 actor 결과의 provenance가 없거나 어느 상태 surface도
읽지 못했다면 `D`는 `0`이 아니라 `미확정`이며, clean Git 상태로 그 공백을 닫지 않습니다.

Task 변경이 사라져 `D`가 비어 보여도 `K`에 포함되지 않았다면 완료가 아닙니다. 반대로
unrelated dirty가 남아도 task-owned residual이 없고 경계가 보존되면 local 결과를 막지
않습니다.

## 2. 권한

### 상태 변경 권한 source gate

- 질문, 설명, 읽기 전용 검토와 diff 확인은 상태 변경 권한이 아닙니다.
- 명확한 실행 요청은 지정된 대상·효과·제약 안에서 끝까지 수행할 권한입니다.
- skill 이름·tag·link는 절차를 선택할 뿐 권한을 만들지 않습니다.
- 한 원자의 실행 권한을 인접한 질문이나 다른 대상에 전파하지 않습니다.
- 작업 주체·범위·권한·안전성·사실 판정에 대한 이의가 없는 단순 진행 상태 질문만 기존
  실행 권한을 유지합니다.
- 이의와 상태 질문이 함께 있으면 이의 판정을 먼저 적용해 해당 대상의 과거 실행 권한을
  무효화합니다. 상태 질문은 그 권한을 유지·부활시키지 않으며, 같은 메시지의 독립된 새
  실행 요청만 별도로 유효합니다.
- 새 제품·운영 의미, 외부 부작용이나 결과를 바꾸는 사용자 소유 결정이 필요하면
  추측하지 않습니다.

각 상태 변경 행동은 첫 mutation 전에 현재 유효한 사용자 원문의 exact 원자 또는 그
결과에 불가피한 기술적 선행 관계에 연결합니다. source에서 발견한 문제, 도구 사용
가능 여부, sandbox·tool approval, Agent가 만든 계획·handshake·요약은 권한을 생성·확대·
부활시키지 않습니다. 새 artifact나 의미 효과가 생기면 같은 gate를 다시 통과합니다.

복합 batch의 개별 행동에 권한 source가 있어도 위 권한 coverage에 미확정 항목이 남아 있으면
그 batch의 상태 변경 행동은 입장되지 않습니다. 이 차단은 개별 행동의 기술적 안전성이나
가역성으로 우회하지 않으며, discussion을 끝내기 전 writer·reviewer를 일부라도 시작하지
않습니다.

sandbox·tool approval의 허용·거절은 이미 판정한 의미 권한을 생성·취소·대체하는 사용자
결정이 아니라 현재 실행 경로의 가능 여부를 보여 주는 증거입니다. 유효한 권한이 있는
행동이 도구에서 거절되면 권한을 다시 묻지 않고, 허용 범위 안의 더 안전한 실행 경로를
우선 사용합니다. 그런 경로가 없을 때만 실제 차단 gate로 판정해 거절 주체·막힌 효과·
재개 조건을 보고합니다. 사용자가 새 권한이나 결과를 바꾸는 값을 제공해야 하는 경우가
아니면 같은 효과의 승인을 반복해서 요구하지 않습니다.

검증, stage와 완료 보고 전에는 최종 diff와 그 밖의 상태 변경을 독립적인 의미 효과로
다시 나누고 다음 관계를 대조합니다.

```text
의미 효과 → exact path·hunk·외부 상태 → 현재 유효한 사용자 원문 원자
→ 불가피한 선행 관계 또는 제외 판정
```

같은 파일·작업 이름에 있거나 기술적으로 옳다는 이유로 효과를 합치지 않습니다. 사용자
원문과 불가피한 선행 관계 어느 쪽에도 연결되지 않는 효과는 검증·stage·보고 후보에서
제외합니다. 이번 작업의 변경이면 안전하게 되돌리고, 기존 dirty와 섞여 분리할 수 없으면
더 바꾸지 않고 정확한 충돌을 보고합니다.

상태 변경, stage, commit, push, history rewrite와 외부 apply는 각각 독립 권한입니다.
Git 명령 의미와 안전한 실행은 [커밋 워크플로우](300-commit-workflow.md)가 소유합니다.

저장소의 상시 예외는 다음 두 가지뿐입니다.

- `$dev-plan`이 계획 자체를 결과로 작성할 때 검증된 계획 checkpoint와 필수 router를
  exact path로 local commit합니다.
- 사용자가 재발 방지 회고의 실행을 명시적으로 요청했을 때 통제 owner 변경만
  exact path로 local commit합니다.

두 예외 모두 다른 작업 변경, 사건 대상 artifact 복구와 push 권한을 만들지 않습니다.

상시 예외를 적용하면 해당 효과를 첫 mutation 전에 끝나는 내부 판정으로만 두지 않습니다.
사용자 원문 원자, 선택한 예외, exact 효과·path와 제외 효과를 operation map에 결속하고,
그 효과를 실행하는 adapter와 sandbox·tool approval 호출까지 같은 근거를 전달합니다.
호출자는 외부 gate가 repository 문서를 다시 추론할 것이라고 가정하거나 일반적인 승인
문구만 보내지 않습니다. 도구가 이 근거를 받은 뒤에도 거절하면 실제 실행 차단으로
처리하되, `commit을 따로 말하지 않았다`처럼 이미 충족한 의미 권한의 부재로 바꾸지
않습니다.

## 3. 결과와 실행 단위

### 결과 계약

각 원자에 다음 계약을 결속합니다.

```text
상위 목적
→ 현재 결과
→ 관찰 가능한 완료 oracle
→ 포함·제외와 권한
→ 의존 결과·다음 consumer
→ 현재 실행자·사용자에게 필요한 입력 또는 행동 없음
→ 영구 SOT 또는 승격 불필요 근거
→ 복귀점
```

현재 결과가 미완료이면 `다음 작업`만으로 책임을 표현하지 않습니다. 그 작업을 지금 수행할
actor와 사용자가 제공해야 할 결정·입력·외부 행동을 분리합니다. 사용자 소유 항목이 0개이면
`사용자 행동 없음`과 현재 Agent가 이어서 만들 exact 결과를 operation map에 함께 둡니다.
이 상태에서 같은 결과의 중간 문서 수정, 검증, checkpoint commit이나 상태 projection은
terminal이 아니며, 실제 blocker·사용자 소유 결정·현재 요청이 정한 결과 완료 중 하나가
성립할 때까지 현재 실행자가 남은 안전한 단위로 복귀합니다.

복귀점은 삽입된 현재 원자가 terminal 상태에 도달한 뒤 재개할 위치를 보존하는
dependency metadata이며, 현재 실행할 다음 원자가 아닙니다. 현재 원자의 후보·계획·검증이
실패하거나 변경을 원복한 상태, commit이 없는 상태와 시간 제한에 따른 일시 중단은 복귀점을
활성화하지 않습니다. 이때 실패한 전략은 폐기하고 현재 원자를 `Invalidated` 또는
`Active`로 유지한 채 최초 단절이나 해결 계획 phase로 돌아가 다른 안전한 경로를 찾습니다.

복귀점은 현재 원자가 `Closed`에 도달했거나 사용자가 canonical scope에서 그 원자를
명시적으로 취소·제외·대체했을 때만 활성 operation universe의 다음 원자로 돌아옵니다.
현재 원자가 실제 차단 gate를 통과해 `Blocked`이면 복귀점이 아니라 blocker 해제 입력과
현재 원자의 재개 phase가 다음 행동입니다. 이 경계는 현재 원자의 결과를 소비하는 원래
작업에 적용하며, dependency·공유 계약·write set이 독립이고 별도 실행 권한이 있는 원자까지
전역 차단하지 않습니다.

복귀점은 위치 이름만 보존하지 않고 다음 원래 계약을 함께 보존합니다.

```text
미완료 기대 효과
+ 사건 또는 삽입 원자와 분리한 exact artifact·외부 상태
+ 원래 acceptance oracle과 배제 조건
+ 재개할 specialist phase와 아직 필요한 gate
```

삽입 원자의 통제 변경·검증·commit 완료는 이 원래 계약의 완료 증거가 아닙니다. 복귀점을
활성화할 때 위 계약을 현재 operation map의 활성 원자로 복원하고, 같은 artifact를 다시
다루는 candidate·review·Git 인계가 원래 oracle과 필수 specialist gate를 소비하는지
대조합니다. `원래 작업으로 복귀`, 일반적인 `계속 진행`이나 새 candidate의 내부 일관성은
그 인계를 대신하지 않습니다. 원래 artifact가 아직 acceptance oracle을 충족하지 않으면
통제 회고가 여러 번 닫혔거나 관련 work item이 `Done`이어도 원래 결과를 완료로 보고하지
않습니다.

원래 요청에 artifact의 stage·commit·history rewrite 같은 Git 효과가 포함됐으면 복귀
consumer는 artifact 내용뿐 아니라 그 Git 효과를 같은 원자에 다시 결속합니다. 삽입 원자의
통제 commit, 같은 파일의 다른 작업 dirty와 partial stage 금지는 원래 Git 효과의 완료나
취소가 아닙니다. 같은 파일 overlap이 있으면 원래 결과를 조용히 worktree에 남기지 않고,
검증된 exact patch를 격리 worktree에서 재구성하거나 source에서 안전하게 분리하는 현재
허용 경로를 끝까지 찾습니다. 안전한 경로가 실제로 없을 때만 원래 원자를 `Blocked`로
전환하고 exact 충돌·남은 Git 효과·재개 조건을 보고합니다.

복귀 consumer의 terminal admission은 다음 identity를 직접 대조합니다.

```text
원래 사용자 결과·artifact·Git 효과
→ 삽입 원자 뒤 실제 candidate·index·commit 또는 rewrite 결과
→ 제외 dirty와 acceptance oracle
→ 원래 원자의 Closed·Blocked·Active 판정
```

원래 artifact가 worktree에만 남았거나 요청한 commit object에 없으면 `Active`입니다. 통제
commit 존재, 해당 artifact의 내용 수정 완료와 삽입 원자의 `Closed`로 이 상태를 높이지
않습니다.

사용자 결과가 구현인 요청을 임의의 계획 완료로 바꾸지 않습니다. 반대로 계획 자체가
요청 결과이면 구현을 시작하지 않습니다. 상위 결과의 의존 경계와 사용자 소유 결정이
닫힌 뒤 구현 가능한 가역 단위가 있고 모든 단위를 막는 공통 결정이 없으면 실행을
진행합니다.

### 실행 라운드와 질문 수집

`한 단계씩`, `하나씩 논의`와 같은 표현은 사용자에게 노출하는 설명·승인 단위를
제한합니다. 사용자가 범위 축소나 제외를 별도로 명시하지 않았다면 상위 결과, source
discovery와 의존 경계를 줄이지 않습니다.

첫 mutation 전에 현재 상위 결과에 필요한 의존성, 완료 조건과 실질 구현 경로를 저상세로
끝까지 찾습니다. 기본 template, 현재 code와 익숙한 구현 순서는 실행 platform·architecture·
format·protocol·actor 같은 선행 선택의 권위 source가 아닙니다. 어떤 tool·adapter·호환
계층이 Agent가 고른 선행 선택과 목표 사이의 불일치를 보상할 때에는 그 선행 선택을 고정
입력으로 두지 않습니다. 같은 시작·종료 상태에서 보상 계층을 없애는 표준 경로와 보상
계층을 포함한 완전한 경로를 먼저 비교하고, 외부 도구이면
[도구 도입 워크플로우](130-tool-adoption-workflow.md)로 인계합니다.

그다음 각 경로와 독립 선택 축을 authoritative source·현재 요구·저장소 관례로 유일하게
닫히는 Agent 판단, 실행 중 실제 상태를 관찰할 환경 분기, 둘 이상의 유효한 결과가 남아
의미·비용·위험·외부 효과를 바꾸는 사용자 소유 결정으로 분류합니다. Agent 판단은 구현·
검증하고 선택 이유와 배제한 더 단순한 경로를 보고 또는 canonical 문서로 전달하지,
완결성을 보여 주기 위한 질문이나 승인 항목으로 바꾸지 않습니다. 환경 분기도 관찰 뒤
안전한 결과가 하나면 같은 방식으로 닫습니다.

이 workflow에서 `논의`는 source로 유일하게 닫히지 않는 사용자 소유 결정을 해결하는
행위입니다. 그런 결정이 있을 때만 관련 질문·대안·추천과 응답 형식을 첫 mutation 전에
한 번에 모으고, 0개이면 논의 라운드를 열지 않고 실행합니다. 답변을 받으면 확정 결정을
동결 입력으로 사용해 승인된 상위 결과의 안전한 단위를 모두 실행하며, 임의의 하위 단위
하나를 끝낸 뒤 다음 작업을 다시 묻지 않습니다.

구현을 포함한 한 실행 라운드는 다음 phase를 순서대로 닫습니다.

1. 동결한 결과·결정·제외·oracle 안에서 writer가 후보와 local evidence를 만듭니다.
2. 모든 writer를 멈춘 뒤 최종 diff와 실제 caller에서 후보가 추가·변경한 관찰 효과를
   원래 operation map으로 역추적합니다. 후보와 같은 작업에서 만든 test·README·SOT·work
   item은 구현 evidence일 뿐 이 효과의 admission source가 아닙니다. 각 효과가 mutation 전
   동결한 사용자 결과·authoritative source·불가피한 선행 관계 중 하나에 연결되지 않으면
   reviewer에게 그 효과의 완성도를 묻지 않고 미승인 효과 전체를 한 번에 제거합니다. 제거가
   사용자 소유 선택을 요구하면 mutation하지 않고 원래 결과와 분리해 논의하며, 제거 뒤에도
   미승인 효과가 남으면 현재 라운드를 `Invalidated`로 돌립니다.

   같은 대조에서 후보가 추가·변경한 artifact 종류와 전문 owner도 다시 분류합니다. 최초
   operation map에 없던 specialist-governed surface가 생겼으면 그 결과와 이후 review를
   `Invalidated`로 돌리고 해당 specialist의 판단·검증을 후보 dependency `P`에 먼저
   결속합니다. Path·확장자·상위 skill 이름으로 분류하지 않으며, 새 executable·hook·자동
   verifier와 hard gate는 도구 workflow의 도입·존치 판정을 통과하지 않으면 후보에서
   제거합니다.

   Reviewer finding이나 correction이 기존 후보의 actor·배치·영향 범위 또는 외부 효과를
   바꾸면 finding consumer가 그 새 효과를 현재 positive source와 decision authority에
   다시 mapping하고 위 입장 gate를 직접 호출합니다. Reviewer의 중요도·기계적 차단 추천과
   앞 후보의 검증 성공은 이 재입장이나 specialist 결과 `P`를 대신하지 않습니다.
3. 입장 gate를 통과한 관찰 효과, 원래 결과·제약·oracle과 각각의 독립 source를 reviewer
   입력으로 고정하고 후보 식별자를 동결한 뒤 read-only discovery review를 한 번 수행합니다.
   후보가 만든 test·문서는 구현과 설명 evidence로 검토하되 요구 source로 승격하지 않습니다.
4. 현재 Agent가 discovery finding 전체를 원래 결과·목적·oracle과 권위 source에 한 번에
   대조합니다. 수정 전에 각 finding의 `원래 결과·source·oracle과의 관계·위반 여부 → 분류
   → 처분`을 활성 조건 원장이나 세션에 남기고 다음 네 종류로 분류합니다.
   - **원래 결과 결함**: 승인된 결과가 원래 oracle을 직접 위반하므로 blocking이며
     correction batch에서 수정합니다.
   - **미승인 후보 효과**: 입장 gate가 놓친 보장·감사·자동화를 reviewer가 발견했습니다.
     그 효과 내부의 추가 결함을 correction 목록으로 확장하지 않고 후보를 무효화해 효과
     전체를 제거한 뒤 입장 gate부터 다시 시작합니다.
   - **인접 제안**: 더 강한 보장이나 미래 hardening이지만 원래 결과를 막지 않습니다. 현재
     mutation에서 제외하고, 범위·비용·보안·운영 선택이 필요하면 원래 결과와 분리해
     사용자에게 판단 근거와 함께 제안합니다.
   - **무관·증거 불충분**: 원래 결과와 실패 oracle에 연결되지 않으므로 현재 작업에서
     제외합니다.
   같은 작업에서 후보가 새로 만든 test·문서·확대 계약만 finding의 근거라면 원래 결과
   결함으로 승격하지 않습니다. Reviewer의 중요도나 Best Practice 평가는 사용자 목적과
   권위 source를 대체하지 않습니다.
5. 원래 결과 결함과 미승인 후보 효과가 모두 0개면 같은 후보 식별자로 closure review를
   한 번 수행합니다. 둘 중 하나 이상이면 원래 결과 결함의 수정과 미승인 후보 효과의
   제거를 한 correction batch로 수행하고 새 후보 식별자를 동결한 뒤 closure review를
   한 번 수행합니다.

Finding이 도착할 때마다 active writer에게 patch를 지시하거나 review와 mutation을 겹치지
않습니다. TDD의 Red·Green, 변경 지점의 local test와 writer의 자체 대조는 후보 evidence이며
독립 discovery·closure 횟수를 늘리지 않습니다. Closure가 실패하면 같은 라운드에서
patch·review를 반복하지 않고 후보와 원인 phase를 `Invalidated`로 돌려 새 라운드를
구성합니다.

이 순서는 설명용 목록이 아니라 actor 호출 전 admission state입니다. Review를 사용하는
라운드는 세션 원장에 다음 상태 중 하나만 가지며, 현재 Agent는 각 tool 호출 직전에 허용된
전이인지 대조합니다.

```text
Writing
→ Frozen(candidate identity·digest, active writer=0)
→ DiscoveryRunning(active review set)
→ FindingsCollected(active review=0, 전체 finding·처분 동결)
├─ correction=0 → ClosureRunning(same candidate, active review set)
└─ correction>0 → Correcting
   → Frozen(new candidate identity·digest, active writer=0)
   → ClosureRunning(new candidate, active review set)
ClosureRunning → Closed 또는 Invalidated
```

- 첫 discovery·closure reviewer 호출 전에 `Frozen`의 candidate identity·digest와
  `active writer=0`을 실제 상태에서 확인합니다. 하나라도 없으면 reviewer를 호출하지
  않습니다.
- `DiscoveryRunning`·`ClosureRunning`에서는 read-only source 확인과 reviewer 대기만
  허용합니다. 첫 finding이 도착해도 나머지 active review가 terminal이 되기 전에는
  writer·patch를 시작하지 않습니다.
- 후보가 review 중 바뀌면 진행 중인 review 전체와 그 finding을 `Invalidated`로 처리합니다.
  변경 뒤 도착한 finding을 새 후보의 correction 입력이나 완료 증거로 재사용하지 않습니다.
- `FindingsCollected`는 예정한 reviewer가 모두 terminal이고 현재 Agent가 finding 전체의
  권위·blocking·처분을 한 번에 동결한 상태입니다. 수정할 finding이 0개이면 같은 candidate로
  closure review를 호출하고, 하나 이상이면 이 상태에서만 한 correction batch로 전이합니다.
- 단순 진행 상태 질문은 현재 phase를 관찰하는 edge이며 위 상태를 바꾸지 않습니다. 답변을
  위해 active reviewer를 interrupt하거나 writer·review 원자를 중단 상태로 바꾸지 않고,
  보고 뒤 같은 candidate·review set·남은 terminal gate에서 계속합니다. 사용자가 현재
  작업의 중단·취소·대체나 주체·범위·권한 이의를 명시한 경우에만 Bootstrap 판정 결과에
  따라 기존 phase를 중단하거나 `Invalidated`로 전이합니다.

이 phase state는 세션 원장의 기존 `I`와 `C`를 소비하며 별도 repository artifact·registry·
자동 verifier를 만들지 않습니다. Review tool의 실행 성공, reviewer 한 명의 조기 finding,
상태 보고와 commentary는 phase 전이 증거를 대신하지 않습니다.

실행 중 처음 드러난 source·환경·검증 증거는 이 문서의 새 발견·실제 차단 판정으로
분류합니다. 안전한 단위는 계속 수행하고, 새 증거로 생긴 사용자 소유 결정이 남은 결과에
필요하면 그 입력 전체를 한 번에 모아 다음 결정 라운드를 엽니다. 첫 mutation 전에 이미
발견할 수 있던 결정을 후속 라운드로 미루지 않습니다. 답변 뒤에는 Bootstrap으로 다시
진입해 사용자가 바꾼 차원만 갱신하고 남은 상위 결과 전체를 재개합니다. 하위 단위의
검증은 상위 결과의 완료를 대신하지 않습니다.

### 요청 결과의 phase routing과 인계

| 현재 결과 | primary 경로 |
| --- | --- |
| 기술 설명, 다음 작업·구축 순서·추천 질문 | `$explain-tech` |
| 구현 전 결정·계획이 결과 | `$dev-plan`, 설계 workflow |
| 코드·설정·인프라·운영 변경 | `$develop`, domain workflow |
| 문서 자체의 작성·재구성 | `$documentation` |
| 기존 artifact의 종합 개선 | `$improvement` 뒤 관련 specialist |
| 검증된 로컬 변경의 Git 마무리 | `$git-finalize` |
| 상태·완료·차단 projection | `$report-task` |

Primary 경로는 specialist가 조사 중 발견한 공백이나 더 좋은 구현이 아니라 현재 사용자
원문에서 확정한 문장 기능과 결과가 선택합니다. Artifact가 어떻게 바뀌어야 하는지를 묻는
질문은 설명·비교 결과이며, 그 artifact의 mutation 경로를 선택하는 근거가 아닙니다.
Specialist가 다른 owner의 결과가 필요하다고 반환해도
그 반환은 새 operation 원자나 실행 권한이 아닙니다. 현재 원문이 그 결과를 독립적으로
포함할 때만 별도 원자로 mapping하고 해당 owner를 호출합니다. 포함하지 않은 결과가 현재
결과를 실제로 막으면 미충족 dependency와 선택지·영향을 보고해 사용자 결정 경계로
돌려보내고, 막지 않으면 비활성 finding으로 분리합니다. 어느 경우에도 `필요한 owner로
돌려보낸다`는 전문 관계를 `그 owner의 변경을 지금 수행한다`는 실행 약속으로 바꾸지
않습니다.

다른 primary 경로·artifact 종류·실행 효과를 답변·계획·handoff에 처음 넣기 전에도 상태
변경 권한 source gate를 다시 적용합니다. Exact 사용자 원자나 불가피한 선행 관계가 없으면
`구현하겠습니다`, `함께 수정합니다`처럼 현재 scope에 포함된 결과로 투영하지 않습니다.
이 gate는 실제 mutation 직전만이 아니라 사용자가 작업 범위를 선택하는 첫 실질 문장 전에
적용합니다.

전체 상태 projection 없이 다음 작업·순서·추천 자체만 묻는 질문은 canonical exact next가
이미 있어도 `$report-task`로 바꾸지 않습니다. `$explain-tech`가 활성 결정 source와
비교·추천 reference를 읽되, 닫힌 exact next를 열린 대안으로 다시 만들지 않고 목적·완료
oracle과 함께 직접 설명합니다. 상태 보고가 이미 확정된 다음 책임을 함께 투영하는 경우에만
`$report-task`가 그 관계를 소비합니다.

한 요청에 개발 변경과 Markdown 신설·대규모 재작성·owner 또는 topology 변경이 함께
있으면 이를 하나의 개발 결과로 흡수하지 않습니다. 첫 mutation 전에 개발 결과와 문서
결과를 독립 단위로 나누고, 개발 owner는 바뀐 계약과 authoritative source를 제공하며
문서 owner는 `$documentation`으로 독자 질문·정보 유형·canonical owner·path·link를
확정합니다. 구현 기술이나 이번에 만든 script·workflow의 이름을 먼저 runbook path로
옮기는 것은 이 인계를 대신하지 않습니다. 기존 owner가 명확한 국소 동기화도 문서화
workflow의 의미·결과 gate를 적용하되, 불필요한 별도 artifact로 확대하지 않습니다.

Bootstrap에서 확정한 요청 계약과 전문 owner가 만든 결정·계획·검증 결과는 다음
phase의 동결 입력입니다. 다음 phase가 이를 임의로 다시 해석하지 않습니다. 새 증거로
입력을 바꾸면 그 결과에 의존한 계획·변경·검증과 적용된 review를 모두 `Invalidated`로
돌리고 원인이 생긴 phase부터 다시 시작합니다.
계획 결함이 한 결과 단위만 막으면 그 단위만 보류하고 안전한 단위는 계속합니다. 모든
안전한 단위를 막는 공통 결정일 때만 사용자 결정 대기로 전환합니다.

Git phase에 넘기는 동결 입력에는 각 결과 단위의 원래 요구, 적용된 authoritative
workflow의 포함·제외·부정 조건, exact diff·test·문서와 그 조건의 대조 결과가 있어야
합니다. 앞 phase의 전문 결과마다 candidate 반영 위치와 후속 gate verdict가 함께 있어야
하며, 결과가 있었지만 후보에서 사라졌거나 필요한 specialist phase를 generic audit로
대체했다면 인계는 실패합니다. 실행 test가 통과했다거나 작성 후보끼리 일치한다는 사실은
이 인계를 대신하지 않습니다. 적용할 specialist 검토를 commit 뒤에 수행하도록 남겼거나,
검토에서 사용한 조건을 원래 source가 아니라 같은 작업에서 만든 test·문서로만 복원했다면
후보는 `Locally verified`가 아니므로 Git phase를 시작하지 않습니다.

현재 generation이 existing candidate의 stage·commit·push만 요청하고 candidate content
수정을 독립적으로 요청하지 않았다면 Git handoff가 그 content를 read-only로 동결합니다.
이후 discovery·검증에서 결함이나 미완료 보완을 발견해도 과거 plan·compaction summary·
pending operation·review finding을 근거로 writer phase를 호출하거나 patch를 만들지 않습니다.
원래 결과를 깨는 finding이면 후보를 `Invalidated`로 돌려 exact blocker를 보고하고, 그 밖의
finding은 해당 owner의 보고 계약과 사용자가 지정한 시점을 함께 따릅니다. 같은
generation의 exact span이
content 수정과 Git 효과를 각각 명시했거나, 후속 generation이 content 수정을 새로 요청한
경우에만 해당 결과 원자를 writer phase로 보내며 Git 요청 자체로 수정 권한을 채우지
않습니다.

후보가 새 artifact 종류나 실행 효과를 도입했는데 초기 routing만 재사용하거나, 행동
검증·generic review가 누락된 domain specialist 판정을 대신했다면 같은 이유로 Git phase에
인계하지 않습니다. 특히 skill resource 안에 있다는 이유로 executable·verifier를 문서나
skill content로만 분류하지 않습니다.

실행 회고의 Git 인계는 4절의 closure identity를 추가로 소비합니다. 선택한 통제 처분의
canonical owner 효과가 final staged patch에 없거나, 필요한 행동 verdict가 전체 회고 상태와
맞지 않으면 History·상태 companion만 stage하지 않고 회고를 해당 phase의 `Active` 또는
`Invalidated`로 돌립니다.

### 실제 차단 판정

미확정 값, 검증 실패나 사용자 입력이 유용해 보인다는 이유만으로 작업을 차단하지
않습니다. 다음을 모두 만족할 때만 `Blocked` 또는 결정 대기로 전환합니다.

1. 다음 exact 결과와 그 결과의 canonical owner가 확정됐습니다.
2. 현재 source·권한·환경만으로는 그 결과를 안전하게 만들 수 없습니다.
3. 허용 범위의 조사·비교·조건부 설계와 독립 결과를 모두 수행했습니다.
4. 남은 입력이 결과를 실제로 바꾸며, 사용자나 외부 실행자가 독립적으로 답하거나
   제공할 수 있는 형태입니다.
5. 그 입력이나 외부 상태 변화 없이는 모든 안전한 다음 단위가 막힙니다.

하나라도 충족하지 않으면 `진행 중`입니다. phase 전환, 후보 무효화, 검토 실패와
현재 mutation 범위에 이미 포함된 선택은 그 자체로 사용자 결정이 아닙니다. 계획
결정의 질문·대안·추천·응답 형식은 `$dev-plan`이 만들고, 이 gate는 그 입력이 실제
차단인지 판정합니다.

차단할 때는 함께 같은 결과를 막는 입력을 한 번에 모아 다음 exact 결과, 필요한
응답·권한·외부 상태와 재개 조건을 연결합니다. 안전한 대안이 있으면 먼저 수행합니다.

### 단위 상태와 dependency

파일·기술 영역이 아니라 다음 consumer가 사용할 수 있는 결과로 단위를 나눕니다.

```text
Mapped → Active → Locally verified → Closed
             ↘ Invalidated → Mapped 또는 Active
Mapped·Active·Locally verified → Blocked → 이전 유효 상태
```

- `Mapped`: 결과, owner, 입력, oracle과 consumer가 정해졌습니다.
- `Active`: 허용된 변경이나 조사를 수행 중입니다.
- `Locally verified`: source와 직접 검증은 통과했지만 후속 gate가 남을 수 있습니다.
- `Closed`: 문서·SOT와 적용되는 terminal gate까지 닫혀 consumer가 사용할 수 있습니다.
- `Invalidated`: source·범위·계획·통제 또는 검증 근거가 무효여서 이전 완료를 사용할
  수 없습니다. 원인이 생긴 phase의 `Mapped` 또는 `Active`로 돌아갑니다.
- `Blocked`: 실제 차단 gate를 통과해 사용자 입력·새 권한·외부 상태 변화 없이는
  어떤 안전한 다음 결과도 만들 수 없습니다. 재개 조건이 충족되면 이전 유효 상태로
  돌아갑니다.

현재 단위의 source, 직접 검증, 문서 책임과 증거가 닫히기 전에 그 결과에 의존하는
다음 단위를 열지 않습니다. 구조 개편은 공통 계약을 먼저 고정하고, 그 계약을 실제로
소비하는 첫 대표 end-to-end 단위를 검증한 뒤 확장합니다.

둘 이상의 artifact·section·환경이 같은 형식·계층·필수 관계·coverage 기준을 소비하면
이를 파일 묶음이 아니라 하나의 `공유 계약 → 대표 consumer → 나머지 consumer` 전이로
mapping합니다. 두 번째 consumer를 mutation하기 전까지 다음 admission 결과를 실제
operation map 또는 현재 세션에서 복원할 수 있어야 합니다.

```text
공유 계약과 사용자 품질 차원
→ 전체 consumer·source inventory와 명시 제외
→ 첫 대표 consumer와 그 선택 이유
→ 대표 consumer의 직접 closure oracle
→ 나머지 consumer로 확장할 조건
```

전체 관계·범위 coverage 자체가 결과이면 먼저 전체 범위의 저상세 골격을 대표 단위로
닫고, 그 골격의 공통 계약을 실제로 소비하는 상세 consumer 하나를 두 번째 대표 단위로
닫습니다. Proposal·계획·outline 승인, 파일 생성과 최종 reviewer의 finding은 이 두
대표 단위의 직접 증거를 대신하지 않습니다.

대표 consumer가 공유 계약을 복원하지 못하거나 reviewer가 공통 계약의 누락·계층 오류를
처음 발견하면, 같은 계약을 소비한 나머지 후보와 그 검증을 `Invalidated`로 돌립니다.
Finding마다 전체 consumer를 고치고 final을 다시 여는 대신, 원인이 생긴 계약·inventory·
대표 oracle을 한 번 재구성하고 대표 consumer부터 다시 닫습니다. 반대로 독립 source와
oracle을 가진 consumer나 source로 유일하게 결정되는 기계적 동일 변환에는 이 확장 gate를
강제하지 않습니다.

### 병렬 경계

다음 조건을 모두 만족할 때만 병렬화합니다.

- 어느 결과도 다른 결과의 입력·수용 기준·증거를 바꾸지 않습니다.
- write set과 canonical owner가 겹치지 않습니다.
- 공유 계약과 첫 대표 단위가 이미 동결됐습니다.
- 각 worker가 독립적으로 검증·인계할 수 있습니다.

`SKILL.md + agents/openai.yaml + 해당 reference·script`처럼 함께 일치해야 하는
artifact는 하나의 원자 단위입니다. producer와 consumer, 공유 계약의 두 구현,
retrospective와 deep-retrospective처럼 같은 전이를 소비하는 묶음, 동결 후보를 다시
열 수 있는 검토는 직렬화합니다. 최종 독립 감사는 writer가 없는 동결 snapshot에서만
수행합니다.

Reviewer는 동결 snapshot을 읽고 finding만 반환합니다. 현재 Agent가 활성 조건 원장에서
권위와 blocking 여부를 분류하기 전에는 reviewer의 권고·Best Practice·인접 위험을 현재
write set이나 correction batch로 전달하지 않습니다. Active writer에게 finding을 실시간
전달해 후보를 계속 다시 여는 topology는 병렬화가 아니라 금지된 producer·reviewer
중첩입니다.

## 4. 통제 변경 생애주기

이 절은 지시 위반, 잘못된 완료, 반복 실패와 workflow·skill·정책 개선에서 기존
통제를 분류·처분하고 새 통제를 받아들이는 유일한 공통 owner입니다. 회고 skill은
사건별 증거와 인과 분석을 제공하며 이 상태 전이를 다시 정의하지 않습니다.

### 입력

해결책을 고르기 전에 다음을 source와 Git에서 복원합니다.

- 사용자가 관찰한 영향과 기대 결과
- 영향으로 이어진 첫 번째 끊어진 전이
- 보호해야 할 불변식과 실제 실패 경로
- 관련 기존 통제의 도입·변경·완료 시점
- 통제의 owner, 배치, invocation, oracle과 실제 호출 여부
- 이전 완료가 선언한 범위와 후속 요구에 의한 폐기 여부

통제 변경은 이 입력에서 사건별 최초 단절, 이전 방어 생애주기, 시스템 root와 기존
통제 처분까지 닫은 뒤 시작합니다. 현재 Agent는 사용자 원문·Git·통제 source를 직접
대조하며, 회고 대상인 자신의 잘못된 결론을 근거로 사용하지 않습니다. 변경 결과가
중대한 계획·개발·문서 후보에 해당하면 관련 domain workflow의 독립 검증을 추가합니다.

사용자가 둘 이상의 사건을 별도 항목·block으로 회고하라고 명령했으면 회고 owner가 만든
다음 사건별 coverage를 통제 변경의 admission 입력으로 사용합니다.

```text
raw 사건 identity와 순서
→ raw 상위 사건과 내부 child 영향 경로의 parent-child 관계
→ 요구 결과·실제 결과·사용자 영향
→ 수평 경로와 최초 단절
→ 최소 5회의 인과·시스템 root
→ 기존 통제 timeline·실제 invocation·oracle
→ root 제거 반사실·형제 경로
→ 사건별 처분 입력·행동 검증 oracle·복귀점
```

모든 raw 사건에 이 coverage가 있고 각 행의 인과가 닫히기 전에는 해결책 선택, 통제
mutation, 행동 검증, Git과 terminal 보고에 진입하지 않습니다. 같은 문장·root·owner가
반복돼도 별도 명령된 행을 병합하거나 공통 분석·통제 후보로 대체하지 않습니다. 누락된
행이 발견되면 후보를 보완하는 대신 회고를 원인 분석 phase의 `Active`로 돌립니다. 하나의
raw 상위 사건이 내부의 여러 영향 경로·child 사건을 가리키면 parent와 child를 모두 보존하며,
child coverage의 합으로 parent의 요구·영향·최초 단절과 개별 verdict를 대체하지 않습니다.

사건에 등장한 제품명, option, 파일명, 숫자와 잘못된 마지막 문장은 첫 단절이나
불변식이 아닙니다. 명사를 바꾸어도 같은 전달 경로에서 실패하면 공통 전이를
추적합니다. 서로 다른 첫 단절과 owner를 가진 사건을 하나의 막연한 계열로 합치지
않습니다.

### 분류와 routing

다음 순서로 한 번 분류합니다.

1. **관련 기존 통제가 없음**: 일반 회고가 첫 단절과 시스템 원인을 닫은 뒤 통제
   admission을 검토합니다.
2. **사건이 현재 통제보다 앞섬**: 회귀 사례로 사용하되 현재 통제의 재발로 부르거나
   같은 규칙을 추가하지 않습니다.
3. **통제가 영향 전에 호출되지 않음**: 새 내용 규칙을 만들지 않고 기존 owner의
   trigger·routing·배치 시점을 교체하거나 강화합니다.
4. **통제가 호출됐지만 같은 보호 불변식이 실패함**: 심화 회고로 전환하고 이전
   완료·검증 범위와 통제 topology를 무효화합니다.
5. **첫 단절과 불변식이 실제로 다름**: 기존 통제로 표현할 수 없을 때만 새 통제
   admission을 검토합니다.

2~4번은 사건 당시의 source·Git timeline에서 통제 도입 시점과 영향 전 실제 invocation이
증명된 경우에만 선택합니다. 사건 뒤 작성한 현재 후보의 통제 존재, 반복 횟수, 같은 skill이나
공통 주제는 그 증거가 아닙니다. 이 timeline이 아직 없으면 실행 mode와 retrospective owner만
유지하고 subtype은 `미확정`으로 둔 채 일반 회고가 증거를 복원합니다. 현재 후보만 보고
심화 회고로 먼저 전환하지 않습니다.

이 분류에서 과거 `Done`의 원래 범위가 충족되지 않았거나 그 범위의 방어가
무효화되면 [작업 문서 생명주기](../work-items/100-work-item-lifecycle.md)에 따라 같은
work item을 `In Progress`로 다시 엽니다. 독립된 새 목표일 때만 후속 work item을
만듭니다.

### 기존 통제 처분

영향받는 기존 통제마다 다음 중 하나를 선택합니다.

| 처분 | 적용 조건 |
| --- | --- |
| 유지 | 맞는 owner에서 영향 전에 작동하고 독립 oracle이 유효함 |
| 강화 | 같은 owner·불변식이며 invocation 또는 표현 경계만 부족함 |
| 교체 | 책임 계층이나 interface를 바꿔 root를 더 직접 제거함 |
| 병합 | 같은 불변식을 둘 이상이 소유해 drift하는 중복 owner임 |
| 제거 | stale·무효·자기검증·순효과 음수이거나 다른 owner가 완전히 대체함 |

각 처분은 canonical owner, invocation, oracle, 실패 경계와 재검토·제거 조건에
연결합니다. 기존 불변식의 재발인데 기존 통제는 그대로 두고 새 규칙만 덧붙이는 diff는
실패입니다.

### 새 통제 admission

새 규칙·field·fixture·verifier·문서를 추가하는 것은 다음을 모두 만족할 때의 마지막
수단입니다.

1. 첫 단절과 시스템 root가 직접 증거로 닫혔습니다.
2. 기존 통제의 이동·강화·교체·병합·제거로 해결되지 않습니다.
3. 구조·interface·표준 도구·review·관측보다 생애주기 순효과가 큽니다.
4. 단일 canonical owner, 영향 전 invocation과 독립 oracle이 있습니다.
5. 새 실패 표면과 갱신·제거 조건을 감당할 수 있습니다.

새 executable, hook, 자동 verifier라면
[도구 도입 워크플로우](130-tool-adoption-workflow.md)의 기준도 통과해야 합니다.
객관화할 수 있다는 이유만으로 자동화를 추가하지 않습니다.

### 독립 행동 검증과 회고 통제 closure

자연어 통제와 routing은 문구 존재, 작성자의 자기일관성이나 새 context의 답변 생성만으로
검증하지 않습니다. 이 절의 독립 closure는 회고가 통제를 유지·변경한 뒤 효과와 완료를
판정할 때 적용하며, 짧은 질문·일반 실행의 사전 admission으로 확대하지 않습니다.

통제 후보를 작성한 Agent는 후보와 raw source pool만 동결하고 최종 합격을 판정하지
않습니다. raw source pool에는 가공하지 않은 사용자 입력, 당시 정상 접근 가능했던
authoritative source, 실제 이전 방어와 우회 경로를 포함하고 작성자의 진단·root·해결책·
기대 답은 넣지 않습니다. 그다음 서로 다른 fresh 역할을 다음 순서로 사용합니다.

검증 identity는 파일 전체나 회고 전체가 아니라 이번 처분에서 유지·변경한 exact 통제
단위와 그 단위가 직접 소비하는 routing·owner dependency로 동결합니다. 이 surface 밖의
history 문구·독립 hunk가 바뀌었다는 이유로 유효한 verdict를 폐기하거나 모든 사례를 다시
실행하지 않습니다. 반대로 surface 안의 content나 dependency가 바뀌면 영향받은 verdict만
무효화하며, evaluator가 그 경계를 후보와 결과를 보기 전에 고정합니다. 검증은 현재 원자의
근거일 뿐 operation membership이나 새 보장 작업을 만들고 concrete 결과를 대신하지 않습니다.

1. **독립 evaluator**는 후보와 실행 결과를 보기 전에 raw source pool에서 보호 불변식,
   regression·renamed transfer·false-block 사례, 실제 routing 사례와 관찰 가능한
   `통과`·`실패`·`검증 불가` 기준을 동결합니다. 작성자가 고른 사례가 불변식의 경계를
   대표하지 못하면 교체하거나 보완합니다.
2. **Fresh runner**는 동결 후보, evaluator가 고른 가공하지 않은 입력과 정상 source만
   받고 각 사례를 실행합니다. evaluator의 기대 결과·실패 조건은 받지 않습니다.
3. **독립 evaluator**가 미리 동결한 기준으로 runner의 실제 결과를 판정합니다. 통제
   작성자는 이 판정을 통과로 바꾸거나 누락 사례를 사후 정상화하지 않습니다.

사례 집합은 아래 필수 표면을 관찰할 수 있는 가장 작은 대표 입력으로 실행 전에 고정하며,
한 입력이 여러 표면을 독립적으로 관찰하면 중복 사례를 만들지 않습니다. 실행 중 finding,
파일 수·사건 수와 표현 차이는 사례 추가 권한이 아닙니다. Runner·도구 infrastructure가
실패하면 같은 candidate·사례를 더 안전한 경로로 한 번만 다시 실행하고, 다시 실패하면
`검증 불가`로 반환해 실제 차단 gate를 판정합니다. 새 harness·runner·사례를 연쇄 생성하거나
검증 실패를 검증 시스템 확장 작업으로 바꾸지 않습니다.

검증은 다음 두 표면을 모두 포함합니다.

- **통제 효과**: regression은 실제 과거 영향을 차단하고, renamed transfer는 명사를
  바꾼 형제 경로를 같은 불변식으로 처리하며, false block은 정상 질문·읽기 전용 검토·
  허용된 실행을 과잉 절차로 막지 않습니다.
- **실제 invocation**: 통제 이름·작성자의 진단·기대 답을 prompt에 주입하지 않은 채
  실제 사건과 같은 선행 문맥·후속 표현·routing 경계에서 올바른 owner가 영향 전에
  호출되는지 별도로 확인합니다. 후보를 직접 지정한 isolated 성공을 실제 routing
  성공으로 확대하지 않습니다.

행동 verdict는 증거 등급을 섞지 않고 다음처럼 분리합니다.

| 증거 등급 | 직접 증명하는 범위 | 증명하지 않는 범위 |
| --- | --- | --- |
| 모델링된 통제 효과 | 동결 후보를 받은 runner가 회귀·전이·false-block 입력에서 올바른 판정을 만드는지 | 실제 writer·reviewer·Git actor가 그 통제를 호출했는지와 concrete artifact 완료 |
| 실제 전이 invocation | 실제 actor가 현재 generation·control·candidate identity를 받아 영향 전에 호출되고 다음 actor에 같은 identity와 결과를 넘겼는지 | 관찰하지 않은 다른 phase·branch·artifact·live 환경의 효과 |

Runner 하나가 writer·reviewer·Git actor의 미래 행동을 답변으로 재현하거나, 읽기 전용
operation map에서 각 actor가 해야 할 일을 올바르게 나열한 결과는 첫 번째 등급입니다. Fresh
context라는 사실만으로 두 번째 등급이 되지 않습니다. 여러 phase의 invocation을 보호
불변식이나 완료 근거로 주장하려면 evaluator는 실행 전에 다음 transition coverage를
동결하고, 실제 실행 뒤 각 행의 직접 증거를 따로 판정합니다.

```text
주장한 전이
→ producer actor와 입력 identity
→ 실제 consumer actor 호출
→ consumer가 관찰한 candidate·oracle·결과
→ 다음 phase 인계 또는 terminal 효과
```

Transition coverage는 선택한 통제가 이번 결과에서 실제로 바꾸는 첫 영향부터 그 결과의
terminal까지만 포함합니다. 아직 오지 않은 사용자 답변, 그 답변 뒤 새 generation이나 현재
결과가 주장하지 않는 writer·reviewer·Git은 미래 consumer이지 현재 필수 전이가 아닙니다.
Evaluator가 사례를 만들기 쉽다는 이유로 이 미래 전이를 필수 행에 넣거나, 그 미실행을 현재
결과의 실패·차단으로 판정하지 않습니다.

한 전이라도 실제 실행되지 않았거나 actor·candidate identity를 직접 대조할 수 없으면 그
전이의 invocation verdict는 `검증 불가`입니다. 모델링된 효과의 `통과`로 채우거나 다른
전이의 `통과`와 합산하지 않습니다. 특히 회고 뒤 원래 작업으로 복귀하는 미래 전이는 같은
회고 안의 runner가 대신 수행할 수 없습니다. 실제 복귀 전까지는 `통제 효과 통과·복귀
invocation 미검증`으로 남기고 사건 artifact나 return-consumer closure를 완료로 쓰지
않습니다.

반대로 하나의 실제 actor와 직접 source만으로 끝나는 읽기 전용 판정·단일 phase 작업에는
존재하지 않는 writer→reviewer→Git 전이를 요구하지 않습니다. Verdict와 완료 보고는 실제로
주장한 결과가 필요로 하는 전이 중 가장 낮은 증거 등급보다 넓을 수 없습니다.

Evaluator는 동결한 행별 증거 verdict만 소유합니다. Evaluator가 전체 회고의 완료·차단이나
사용자에게 물을 다음 행동까지 제안해도 현재 Agent는 이를 finding으로 받아, 각 행을 원래
결과·통제 효과·실제 terminal에 다시 mapping한 뒤에만 closure identity를 만듭니다. 동결
범위 밖의 미래 전이는 후속 위험으로 분리하고 현재 verdict에 합산하지 않습니다. 범위가
과도했다면 현재 후보를 바꾸지 않고 case contract를 바로잡아 해당 행만 다시 평가합니다.
재평가가 닫히기 전에는 상태 보고·질문·`Blocked`로 전환하거나 Git phase로 건너뛰지 않습니다.
재평가를 통과한 뒤 실제 차단 gate를 통과하지 않은 안전한 통제 적용·후속 검증·Git phase를
계속 수행합니다.

행동 verdict를 받은 뒤 실행 회고를 Git·보고 phase로 넘기기 전에는 다음 closure identity를
현재 operation map에 결속합니다.

```text
사건별 coverage와 개별 인과 closure verdict·실행 mode·routing
→ 통제별 처분과 canonical owner
→ owner에서 실제 관찰되는 적용 효과 또는 이미 적용된 exact control identity
→ 모델링된 통제 효과와 필요한 실제 invocation의 개별 verdict
→ Git 자격과 전체 회고 상태
```

- 사건이 현재 통제보다 앞선 2번 routing이고 현재 통제가 이미 canonical owner에 적용돼
  있으면, 그 exact control identity와 회귀 행동 검증을 처분 결과로 사용합니다. 새 변경이나
  commit을 만들기 위해 History·상태 문서를 통제처럼 수정하지 않습니다.
- 통제를 강화·교체·병합·제거했다면 Git 후보는 선택한 처분이 canonical owner에서 만드는
  실제 효과를 포함해야 합니다. History·design·상태 문서는 사건·결정·현재 상태의 companion
  evidence일 뿐, canonical owner 효과가 없는 후보를 실행 회고 commit으로 입장시키지 않습니다.
- 필요한 실제 invocation 하나가 `실패` 또는 `검증 불가`이면 전체 회고 상태도 그보다
  높일 수 없습니다. 다른 증거 등급의 `통과`, companion 문서 변경과 commit 성공으로 빈
  전이를 채우지 않습니다.

Git phase는 이 closure identity와 final staged effect를 대조한 뒤에만 실행 회고 후보를
받습니다. 현재 처분에 새 canonical control 변경이 필요하지 않고 worktree 변경도 없으면 빈
commit이나 evidence-only commit을 만들지 않으며, 검증된 처분 상태와 남은 invocation 경계를
그대로 보고 phase에 전달합니다.

evaluator를 작성자·runner와 분리할 수 없거나, 사전 기준을 동결하지 못했거나, 판정이
갈리거나, 후보가 판정 뒤 바뀌면 결과는 `검증 불가`입니다. `quick_validate`, link·syntax·
diff 검사는 구조 검증일 뿐 이 행동 판정을 대신하지 않습니다. closure 입력에는 동결 후보
식별자, raw 사례의 provenance, runner·evaluator 역할, 사례별 verdict와 남은 위험을
연결하되 대화 원문과 실행 로그를 repository 문서에 복사하지 않습니다.

통과 뒤 같은 보호 불변식이 다시 실패하면 이전 verdict, 통제 topology와 그에 의존한
`Done`을 함께 무효화합니다. 누적된 사용자 정정과 시간 비용을 새 국소 사건마다 초기화하지
않고 기존 전략의 직접 영향으로 계산하며, 같은 작성자·사례 선택·fresh 생성·작성자 판정
topology에 사례만 추가해 `Done`을 복구하지 않습니다. repository 지시만 있고 독립
evaluator를 호출하지 않았다면 해당 회고는 `검증 불가`이며 진행 중입니다. 이 호출과
verdict 확보는 현재 실행 Agent의 closure 책임이고 사용자나 별도 runtime의 선행 책임이
아닙니다. 외부에서 호출을 강제하는 상태 전이가 없다는 사실만으로 현재 회고를 차단하거나
외부 기능 구현을 완료 조건으로 추가하지 않으며, 실제 verdict의 증명 범위와 후속 재발 시
무효화 조건을 그대로 유지합니다.

실패하면 발견 전체를 모아 owner와 topology를 한 번 보완합니다. 통제 하나마다 test를
추가하거나 사례별 규칙을 다시 쌓지 않습니다.

## 5. 실행 범위

### 시작과 dirty 변경

상태 변경 전 `git status --short --branch`로 baseline을 확인합니다. 작업 중에도
외부 변경이 생길 수 있으므로 편집·검증·stage 전 다시 확인합니다.

- 이번 요청에 귀속되는 exact path만 변경합니다.
- 기존 dirty와 다른 작업자의 변경을 수정·정리·stage하지 않습니다.
- 겹치는 파일을 피할 수 없으면 작업을 멈추고 정확한 충돌을 알립니다.
- 파일·폴더 이동이나 삭제는 실제 filesystem과 inbound/outbound link를 함께
  검증합니다. Git이 추적하지 않는 빈 디렉터리도 별도로 확인합니다.

### 설계와 구현

관련 source, caller, test와 canonical 문서를 읽고 작업 성격에 맞는 workflow만
적용합니다. 범위, 보안, 데이터 무결성, 유지보수 비용에 의미 있는 대안은 실행 전에
비교합니다. 기존 패턴으로 명확한 작은 선택은 저장소 관례에 맞춥니다.

TDD나 별도 domain gate가 필요하면 관련 workflow를 따릅니다. TDD가 부적절할 때는
구현 전에 이유와 대체 검증을 정합니다. 구현 중 목표·범위·권한·핵심 관계·수용
기준이 무효임을 발견하면 봉합하지 않고 해당 계획 단계로 돌아갑니다.

### 새 발견

발견이 원래 결과의 안전한 완료를 막는지 먼저 판정합니다.

- 원래 활성 결과나 authoritative source의 완료 oracle을 직접 위반해 막으면 현재 단위를
  재개하고 원인 owner에서 해결합니다.
- 막지 않으면 원래 작업을 끝낸 뒤 모아서 보고합니다.
- 많거나 중대한 비차단 발견만 최소 Open backlog 후보로 보존합니다.
- 발견 자체를 새 실행 권한이나 완료 결과로 바꾸지 않습니다.

더 강한 보장, 미래 확장, 별도 hardening과 인접 운영 개선은 중요도만으로 blocking이 되지
않습니다. 현재 Agent는 각 blocking finding을 원래 결과·권위 source·실패하는 oracle에
역추적해 correction batch에 넣고, 이 연결이 없으면 현재 mutation에서 제외합니다.

## 6. 검증과 closure

각 결과 단위는 관련 source, 직접 test·build·verifier, 문서·SOT와 consumer 관점에서
검증합니다. 실행하지 못한 검증은 `미실행`, 실패는 `실패`, 판정할 수 없으면
`검증 불가`입니다.

작성자의 직접 검증이 끝나면 답변 또는 artifact·diff 후보를 원래 요청과 authoritative
source에 다시 대조합니다. 관련 domain workflow가 독립 final을 요구하는 계획·개발·
중대한 문서 후보는 동결한 뒤 해당 specialist에게 검증을 맡깁니다. review는 사용자
권한과 문장 기능을 만들거나 바꾸지 않으며, review 뒤 후보가 바뀌면 해당 판정은
무효입니다.

회고가 통제를 유지·변경한 후보는 산출물 종류와 별개로 4절의 독립 행동 검증 verdict를
closure 입력으로 사용합니다. 이는 현재 요청을 해석하는 admission이나 domain specialist
검토를 대신하지 않습니다.

1. 구현 후보는 앞선 실행 라운드의 후보 효과 입장 gate를 통과한 뒤에만 discovery audit에
   들어갑니다. 그 밖의 후보도 같은 작업에서 만든 test·문서를 요구 source로 사용하지 않고,
   해당 specialist가 정한 admission을 먼저 닫습니다. 그다음 최초 목적·source에서 시작하는
   discovery audit으로 발견을 포화하고, 현재 Agent가 전체 finding을 원래 활성 결과·권위
   source·실패 oracle에 대조해 blocking 여부를 분류합니다.
2. Blocking finding이 0개면 같은 후보를 유지합니다. 하나 이상이면 해당 finding 전체만
   한 correction batch로 반영하고 새 후보를 동결합니다. 비차단 finding은 현재 mutation에서
   제외합니다.
3. 유지하거나 새로 동결한 후보에 같은 기준의 closure audit을 한 번 수행합니다.
4. Closure가 실패하면 반복 patch loop를 열지 않고 후보와 원인 단계의 계획을
   `Invalidated`로 바꿔 해당 phase로 돌아갑니다.

첫 closure 실패는 활성 실행 요청을 종료하거나 사용자 결정을 요구하는 근거가
아닙니다. 현재 source와 권한으로 새 후보를 만들 수 있으면 계속합니다. 원인을
재계획·검증한 새 후보에서도 같은 실패 계열이 다시 발생하면 현재 전략을 더 반복하지
않습니다. 그때도 안전한 다른 경로가 있는지 실제 차단 gate로 판정하고, 없을 때만
`Blocked`로 전환합니다.

짧은 질문과 명시적 실행 요청은 현재 Agent가 source-first로 직접 판정해 답하거나
실행합니다. 별도 공통 admission·final을 모든 요청에 강제하지 않습니다. 계획은
`$verify-plan`, 개발 결과는 `$verify-dev`, 중대한 문서 결과는
`$verify-documentation`이 각 산출물의 위험과 전문 축을 독립 검증합니다. 같은 동결
후보를 이미 적합한 specialist가 감사했다면 이름만 다른 review를 중복하지 않습니다.
공통 상태 전이와 closure 횟수는 각 verifier가 다시 정의하지 않습니다.

다음을 모두 만족해야 전체 결과를 완료로 판정합니다.

- 모든 요청 원자의 결과와 명시 제약이 닫혔습니다.
- 적용되는 terminal gate와 외부 책임이 모두 닫혔습니다. 권한·환경 때문에 닫을 수
  없으면 허용된 local 결과만 닫고 전체 결과는 미완료로 유지하며 남은 owner와
  재개 조건을 분리합니다.
- 영구 SOT가 동기화됐거나 승격 불필요 근거가 있습니다.
- 기존 dirty와 이번 변경의 경계가 유지됐습니다.
- Git과 외부 상태가 실제 source와 일치합니다.
- 저장한 복귀점의 원래 작업이 이어졌습니다.
- `/tmp` 활성 조건 원장의 필요한 인계가 끝났고 임시 파일이 삭제됐습니다.

코드 작업의 terminal gate는 commit·push 요청 범위와 별개입니다. final exact SHA의
required CI 성공, Sonar revision 일치, Quality Gate `OK`와 overall `open_issues` 0건이
확인돼야 코드 작업 전체가 완료됩니다. push 권한이 없거나 하나라도 판정할 수 없으면
local 구현 결과만 닫고 전체 코드 작업은 미완료로 유지합니다.

## 7. 보고

진척은 미리 고정한 결과 단위와 terminal gate의 상태 전이로만 계산합니다. 분모와
가중치를 먼저 정하지 않았다면 임의 백분율을 만들지 않습니다. 파일·줄·명령·시간과
token은 활동량이며 진척 증거가 아닙니다.

상태 변경 결과를 terminal 사용자 응답으로 닫기 전에는 현재 응답 범위의 결과·검증·Git·
외부 상태를 [완료 및 작업 보고 워크플로우](140-completion-reporting-workflow.md)에 실제로
인계하고 그 원문을 읽은 같은 projection에서 최종 답변을 작성합니다. 전문 owner의 내부
결과, Git phase의 full receipt와 이전 답변 형식을 사용자 보고로 직접 직렬화하지 않습니다.
Reporting owner가 영향 전에 호출됐다는 evidence가 없으면 보고를 완료 결과로 소비하지 않고
현재 결과와 source를 유지한 채 projection 단계로 돌아갑니다.

전체 판정, 완료·미완료와 사유, 책임, 다음 행동, 검증과 Git 상태의 사용자용
projection은 [완료 및 작업 보고 워크플로우](140-completion-reporting-workflow.md)가
소유합니다. 안전하게 계속할 허용 작업이 남으면 보고로 중단하지 않습니다.

## 관련 문서

- **문서 목차**: [Workflow 목차](000-index.md)
- **설계**: [설계 워크플로우](110-design-workflow.md)
- **도구 생애주기**: [도구 도입 워크플로우](130-tool-adoption-workflow.md)
- **문서화**: [문서화 워크플로우](150-documentation-workflow.md)
- **개발**: [개발 작업 워크플로우](200-development-workflow.md)
- **Git**: [커밋 워크플로우](300-commit-workflow.md)
- **작업 문서 상태**: [작업 문서 생명주기](../work-items/100-work-item-lifecycle.md)
