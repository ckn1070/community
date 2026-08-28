# 작업 실행·통제 구조 재발 회고

이 문서는 Git만으로 복원하기 어려운 대표 사건, 사용자 영향, 시스템 원인, 실패한 방어와
남은 위험만 보존합니다. 현재 실행 규칙은
[100 작업 실행 워크플로우](../../../workflow/100-work-execution-workflow.md), 고유 결정은
[003 작업 실행 추적성 설계](003-work-execution-traceability-design.md), 현재 상태는
[003 작업 실행 추적성 본문](003-work-execution-traceability.md), 개별 patch·commit과 검증 순서는 Git이
소유합니다.

## 조건과 의미 운반 실패

### 사건과 영향

구조 개편에서 사용자가 명시한 조건 일부가 계획·실행·완료 판정 사이에서 사라졌고,
도구 제거 뒤 남은 local hook처럼 다른 source의 형제 조건도 후속 실행을 막았습니다. 또
다음 작업·배포 흐름·스크립트 위치를 설명하면서 전체 목표와 독립 선택 축을 먼저 닫지 않아,
사용자가 누락된 단계와 대안을 반복해서 찾아야 했습니다.

### 시스템 원인과 실패한 방어

초기 방어는 영구 적용 장부와 자기검증기를 만들었지만 입력 누락을 스스로 발견하지 못했고,
제거 과정에서는 여러 source의 활성 조건을 phase 사이에 운반할 최소 interface까지 함께
사라졌습니다. 후속 보강도 먼저 드러난 명사·문장에 맞춘 규칙을 추가해 source→operation
map→specialist→completion의 전체 전달 경로를 보호하지 못했습니다.

### 처분과 남은 위험

영구 자기보고 schema와 custom dispatcher는 제거한 상태를 유지합니다. 현재 workflow는
원문 원자·권위·source coverage·결과·증거·복귀점을 operation map과 phase handoff에
결속합니다. 최초 source discovery에서 빠진 조건은 여전히 downstream 검증으로 복구되지
않으므로 완료 감사는 작성 중 map이 아니라 원래 source에서 다시 시작합니다.

## 사용자 결정과 설명 경계 실패

### 사건과 영향

Agent는 사용자가 확정한 현재 저장소 배포 경로를 후발 별도 저장소 추천으로 바꾸고, 다시
물었을 때에는 이미 확정한 값을 `미확정`으로 낮췄습니다. 기술 설명에서는 선택한 span보다
큰 문장을 설명하거나 다른 artifact의 관계를 같은 역할명에 대입했고, 옵션 의미·실제 동작보다
형식 분류를 먼저 답했습니다. 사용자는 합의와 질문 대상을 반복해서 복원해야 했습니다.

### 시스템 원인과 실패한 방어

결정과 설명의 source가 존재해도 실제 consumer가 활성 값·권위·대체 source와 직접 대상
identity를 입력으로 받지 않았습니다. 문서·skill load를 실제 invocation으로 간주했고, 후발
추천이나 기억한 일반 패턴이 사용자 결정과 관찰 source를 덮었습니다.

### 처분과 남은 위험

선행 선택을 소비하는 구현·흐름 설명·현재 결정 답변·검증·Git은 같은 활성 결정 기준선을
소비합니다. 설명은 선택 span과 최초 해석자·artifact·actor 관계를 먼저 고정합니다. 당시
actor가 실제로 읽은 source trace가 없으면 과거 원인을 현재 문서로 소급하지 않고
`미증명`으로 남깁니다.

## 실행 actor·phase·Git 증거 실패

### 사건과 영향

개발 actor가 오래된 branch 통제를 읽은 채 후보를 계속 바꾸거나, reviewer partial을 writer
명령으로 사용하고, 사용자 이의 뒤에도 descendant actor를 중단하지 않은 사건이 있었습니다.
검증 전 commit, commit 뒤 correction, recovery stash와 clean status도 final artifact 완료로
오인됐습니다. 규칙이 canonical branch에 존재한다는 사실과 실제 actor가 영향 전에 그 규칙을
소비했다는 사실이 여러 차례 합쳐졌습니다.

### 시스템 원인과 실패한 방어

이전 통제와 행동 검증은 짧은 fixture·문장 판정·isolated runner 성공을 장기 writer,
streaming review, branch drift와 Git handoff의 실제 invocation으로 확대했습니다. Control
revision, request generation, candidate와 target pre-state가 하나의 identity로 이동하지
않아 terminal·clean·commit 존재가 원래 기대 효과를 대신했습니다.

### 처분과 남은 위험

Phase handoff는 실제로 읽은 control, request generation, exact candidate와 target 상태를
운반하고, consumer가 결과를 쓰기 직전에 target을 다시 읽습니다. Git closure는 원래 기대
효과, final candidate 효과, 유효한 제외와 task-owned residual을 대조합니다. 이 통제도 실제
actor별 transition evidence가 없으면 live invocation 완료로 쓰지 않습니다.

## native 경로를 닫지 않고 emulator를 도입한 구현 재발

### 사건과 영향

외부 도구의 native 경로를 먼저 닫지 않고 emulator·호환 계층을 구현해 선택한 platform의
비용과 복잡도를 숨겼습니다. 사용자는 표준 경로가 있었는지와 왜 보상 계층이 필요한지를
구현 뒤에 다시 물어야 했습니다.

### 시스템 원인과 실패한 방어

구현 결과만 검증하고 그 필요성을 만든 선행 platform·architecture 선택과 완전한 대안 경로를
같은 비교 단위에 넣지 않았습니다. Adapter가 기술적으로 동작한다는 사실이 material 선택의
정당성과 보고 인계를 대신했습니다.

### 처분과 남은 위험

보상 계층을 만들기 전에 native 경로와 같은 시작·종료 상태의 완전한 경로를 비교하고,
source가 유일하게 닫는 선택은 Agent가 근거와 함께 실행합니다. 비용·성능·위험·외부 효과가
다른 유효 경로만 사용자 결정으로 올립니다. 같은 결과를 더 단순한 표준 경로가 제공하는데도
adapter부터 만드는 전이가 다시 발생하면 해당 구현 admission을 무효화합니다.

## 회고 검증 생애주기의 재발

### 사건과 영향

회고 통제는 작성자와 fresh runner·evaluator 분리, actual routing 확인과 exact candidate
identity를 차례로 보강했습니다. 그 뒤에도 downstream operation map만 검증한 결과를 upstream
상태 admission까지 확대하거나, 현재 상태 문서를 과거 판단의 원인으로 소급하거나, 한
runner의 읽기 전용 판정을 writer·fresh final·Git invocation `PASS`로 기록했습니다. Agent는
후속 실패 때마다 또 다른 consumer edge가 빠졌다고 설명했고, 사용자는 왜 같은 종류의
미보장을 반복해서 원인으로 제시하는지 다시 확인해야 했습니다.

### 시스템 원인과 실패한 방어

직전 재발의 첫 단절은 `runner 결과 → evaluator invocation verdict`입니다. 기존 통제는
isolated 성공을 actual routing으로 확대하지 말라고 했지만, verdict가 어떤 actor·boundary·
candidate 소비를 직접 관찰했는지와 runner가 모델링한 미래 행동을 구분하지 않았습니다.
통제 의미와 live transition을 같은 `PASS`로 합친 closure evidence interface 때문에, 검증한
범위의 정확한 무효화보다 새 규칙·사례와 새 완료 선언이 반복됐습니다.

모든 과거 회고가 같은 원인이거나 모든 통제가 무효인 것은 아닙니다. 그러나 transition
evidence를 복원할 수 없는 actual invocation 주장과 그 의존 완료는 현재 성공 근거로 사용할
수 없습니다. 기존 `RET-RETURN-REG`는 원래 oracle을 복귀 계약에 포함해야 한다는 모델링된
통제 효과만 남고, 후속 actor invocation `PASS`는 무효입니다.

### 처분과 남은 위험

공통 evaluator·fresh runner 분리와 phase handoff identity는 유지합니다. Workflow의 행동
verdict는 모델링된 통제 효과와 실제 전이 invocation으로 나누고, 여러 phase를 주장하면
producer·consumer actor, control·generation·candidate, 소비한 oracle과 handoff 효과를
실행 전에 동결한 coverage별로 판정합니다. Runner 하나가 미래 actor의 행동을 재현한 결과는
모델링 범위만 증명하며, 관찰하지 않은 전이는 `검증 불가`입니다.

사례를 계속 추가하거나 회고별 verifier·제품 registry·대화 원장을 만드는 방식은 배제합니다.
실제 다중 phase가 없는 읽기 전용 결과를 과잉 차단하지 않되, 실제 actor가 분리된 실행에서도
candidate 또는 oracle이 phase 사이에서 사라지면 handoff topology를 다시 검토합니다. 원래
Application artifact 복구와 live 실행·push는 이 통제 결과로 소급 완료하지 않습니다.

## 문서 실행 진입점 공백을 미요청 개발 작업으로 승격한 사건

### 사건과 영향

사용자가 요청한 결과는 Operations runbook의 재작성·규칙 정립·문서군 감사와 commit이었고,
application bootstrap script 구현은 포함하지 않았습니다. Agent는 runbook에서 안전한 단일
진입점이 없다는 사실을 발견한 뒤 개발 script까지 구현하겠다고 현재 scope에 넣었습니다.
실제 script mutation 전이었어도 사용자가 별도 개발의 필요성·대안·범위·Git 효과를 논의하고
결정할 위치를 없애고 다시 범위를 교정하게 했습니다.

### 최초 단절과 시스템 원인

최초 단절은 `문서 source에서 발견한 구현 공백 → operation membership·primary owner`입니다.
구현 공백은 문서 결과의 dependency 또는 비차단 finding이지 개발 실행 권한이 아닙니다.
기존 source gate와 비차단 발견 통제의 내용이 부족했던 것이 아니라, specialist 반환
interface가 `다른 owner와의 dependency 관계`와 `현재 operation에 새 실행 원자를 추가함`을
구분하지 않았습니다. 그래서 구현 owner를 가리키는 전문 관계가 현재 개발 실행을 약속하는
operation membership으로 변환될 수 있었습니다.

### 통제 처분과 남은 위험

공통 권한 source gate와 개발 artifact 필요성 gate는 유지합니다. Phase routing은 specialist
반환이 operation membership을 바꾸지 않도록 강화하고, 문서 workflow의 자동 전환처럼 읽히는
표현은 미충족 dependency 보고와 별도 사용자 결정으로 교체했습니다. 직접 소비하는 문서
정보구조 설계와 History도 같은 의미로 동기화했습니다. 다른 specialist 관계에서 같은
의미 모호성이 발견되면 제품·artifact별 예외를 추가하지 않고 이 공통 interface에 대조합니다.

## 읽은 범위·권한 통제가 현재 operation map에서 호출되지 않은 사건

### 사건과 영향

Agent는 작업 전에 원문 권한 결속, 비차단 발견 격리, 현재 Agent의 직접 판정과 개발 artifact
필요성 gate를 읽었습니다. 그러나 개발 script를 scope에 넣는 첫 실질 문장에서는 어느 사용자
원자가 그 효과를 허용하는지 대조하지 않았습니다. 규칙 load는 있었지만 사용자의 범위를
보호한 관찰 효과는 한 번도 발생하지 않았습니다.

### 최초 단절과 시스템 원인

최초 단절은 `loaded control identity + 현재 사용자 원자 → specialist 전환 직전 invocation`입니다.
이전 방어는 첫 mutation과 final diff의 권한 대조를 강하게 만들었지만, scope를 먼저 약속하는
답변·계획도 사용자 결정에 영향을 주는 경계라는 증거를 실제 actor invocation과 분리하지
못했습니다. Source를 읽었다는 사실과 isolated runner가 올바른 판정을 생성했다는 사실을
통제가 live 경로에서 호출됐다는 증거로 과대평가했습니다.

### 통제 처분과 남은 위험

Source gate의 내용은 새 사례 규칙으로 복제하지 않고 specialist를 바꾸는 첫 실질 문장까지
invocation 위치를 강화했습니다. 통제 효과와 실제 invocation을 나누는 현재 evidence 등급을
유지하며, 미래 actor의 호출은 직접 trace가 없으면 `검증 불가`로 남깁니다.

## 반복된 범위 회고가 같은 보호 불변식을 닫지 못한 사건

### 사건과 영향

과거에는 비차단 범위 확장, 사용자 원문 권한 결속, 최종 변경 역대조, 질문과 실행의 직접
판정, discovery의 미요청 효과 승격, 개발 artifact 필요성에 관한 통제 변경이 반복됐습니다.
별도 스레드에서도 질문·검토 방향을 구현 범위로 승격하거나 사용자가 지시하지 않은 과정을
추가해 사용자가 논의와 범위 결정을 다시 복원해야 했습니다. 이번 사건은 script라는 새 명사일
뿐 같은 보호 불변식이 다시 깨진 독립 재발입니다.

### 이전 방어 생애주기의 2차 시스템 원인

이전 회고는 사건별 문구와 consumer를 보강한 뒤 작성자와 격리된 runner의 올바른 답을 실제
장기 actor invocation으로 확대해 완료를 선언했습니다. 그래서 다음 재발 때 기존 완료 범위를
무효화하기보다 또 다른 edge·사례·문구를 추가했고, 통제의 존재·load·모델링된 효과와 실제
사용을 계속 같은 `PASS`로 합쳤습니다. 그 결과 규칙 수는 늘었지만 source에서 현재 원자로,
다시 specialist 전환으로 이어지는 live invocation은 증명되지 않았습니다.

### 통제 처분과 남은 위험

이 재발을 script·runbook 전용 규칙이나 새 verifier로 만들지 않습니다. 과거 actual invocation
완료 주장은 유지하지 않고, 모델링된 통제 효과와 실제 전이 invocation을 분리한 현재 처분을
적용합니다. 세 현재 회고도 공통 주제가 있어도 사건별 영향·최초 단절·verdict를 별도로
검증합니다. 장기 actor와 이후 작업의 실제 invocation은 이번 회고에서 보장할 수 없으며,
관찰되지 않은 범위를 완료로 다시 선언하지 않습니다.

## Application DB bootstrap 목적을 reviewer correction으로 대체한 사건

### 사건과 영향

사용자가 원한 결과는 PostgreSQL client 준비와 `기존 role은 유지하고 없는 role만 생성`하는
재실행 가능한 최소 bootstrap이었습니다. Agent는 이를 기존 role의 속성·membership·소유권·
ACL·role setting까지 exact 상태로 감사하는 결과로 확대했습니다. Reviewer가 확대된 기능의
추가 결함을 찾을 때마다 그 finding을 수정 대상으로 받아 후보와 검증을 반복해서 다시 열었고,
사용자가 요청하지 않은 보장에 시간과 검증 비용을 사용했습니다.

### 최초 단절과 시스템 원인

최초 단절은 `사용자 목적·완료 oracle → 첫 후보 mutation`입니다. 재실행 안전성을 기존 상태의
보존이 아니라 기존 상태의 완전한 보안 감사로 바꾸었고, 후보가 만든 확대 계약을 review
oracle로 사용했습니다. Reviewer는 원래 목적보다 높은 권위가 없지만, 현재 Agent가 finding을
원래 결과 결함과 미승인 후보 효과로 나누지 않아 정확한 인접 finding이 계속 구현 권한으로
변환됐습니다.

## 검증 완료 뒤 reviewer 권위 역전이 재발한 심화 회고

### 두 사건과 공통 불변식

첫 사건에서는 확정된 Application/DB delivery에 광범위한 ACL·catalog hardening이 들어왔고,
active writer와 streaming reviewer finding이 겹쳤습니다. `f78c8d1`은 원래 결과·결정·제외·
oracle의 worker/reviewer 인계, writer 없는 discovery, finding 일괄 대조와 correction batch를
workflow에 적용했습니다. `ca40631`은 reviewer partial 격리와 actor 중단·terminal 확인을
develop adapter에 적용하고, develop skill 후보 `47e89ae4`에서 통제 효과 7/7과 실제 invocation
7/7을 모두 `PASS`로 기록했습니다.

독립된 후속 사건은 위 Application DB bootstrap입니다. 사용자는 기존 role을 보존하고 없는
role만 생성하는 최소 결과를 요구했지만, 후보는 기존 role 속성·membership·ownership·ACL·
settings의 exact 감사를 계약으로 만들었습니다. Reviewer는 그 확대 효과 안의 결함을 연속해
반환했고 현재 Agent는 finding마다 후보를 보강했습니다. 두 사건은 제품명이 아니라
`사용자 결과·제외·oracle이 writer → reviewer → correction 전이에서 유지돼야 하고, 후보와
reviewer는 새 권한을 만들 수 없다`는 동일 불변식을 위반했습니다.

### 이전 방어 lifecycle과 2차 시스템 원인

이전 방어의 content와 적용 identity는 복원됩니다. `ca40631`이 적용한 develop skill blob은
`71f1838b152c1887fd95b40e6527bca4fa3afe2e`이고, 후속 기준선 `bceab85`에도 reviewer partial
격리와 원래 라운드 계약 인계가 남았습니다. 따라서 이전 결론처럼 exact control을 복원하지
못해 심화 회고를 할 수 없는 상태가 아니었습니다.

실패한 전이는 `oracle → 통제 설계 → owner·적용`이 아니라 `후보 effect admission → review
입력 → correction`입니다. Writer가 먼저 최소 결과를 exact 상태 감사로 확대하고, 같은
writer가 만든 test·README·work item을 새 계약처럼 reviewer 입력에 넣었습니다. Reviewer는
잘못된 계약의 내부 결함을 정확히 찾았고, 현재 Agent는 reviewer의 blocking 표기를 원래
사용자 요구보다 높은 권위로 소비했습니다. 그래서 review 뒤 finding을 분류하는 통제는 이미
세탁된 계약을 뒤늦게 처리했을 뿐입니다.

`ca40631`의 7/7 actual invocation 판정도 이 전이를 직접 증명하지 않았습니다. History에 남은
증거는 fresh runner가 장기 actor·streaming review 상황에서 올바른 역할과 절차를 답했다는
것이지, 실제 writer·reviewer·correction consumer가 같은 control·generation·candidate와
원래 oracle을 받아 영향 전에 호출된 actor별 trace가 아닙니다. 모델링된 답변을 actual
invocation으로 승격한 거짓 완료가 후속 재발 때 기존 방어를 다시 여는 대신 또 다른 문구와
사례를 추가하게 했습니다.

다섯 번의 인과를 닫으면 직접 단절은 미승인 exact 감사의 첫 mutation, 그 허용 원인은
candidate-authored contract의 권위 승격, interface 공백은 reviewer 전 effect admission,
oracle 실패는 fresh 역할 재현을 실제 전이로 판정한 것입니다. 2차 root는 같은 adaptive
actor가 확대 후보 작성, review 기준 조립, finding 처분을 모두 소유하면서 자기작성 계약을
원래 source와 분리하지 않은 topology입니다. 외부 hook이 없어서가 아니라 당시 원문과
저장소 source만으로 최소 결과를 만들 수 있었는데도 이 경계를 건너뛴 것이 원인입니다.

### 통제 처분과 root 제거 반사실

기존 사용자 authority, reviewer partial 격리와 한 correction batch 통제는 유지합니다.
`c763c15`의 review 뒤 네 분류는 fallback으로만 남기고, 미승인 효과 처분을 reviewer 전 후보
입장 gate로 이동합니다. Writer는 각 관찰 효과를 mutation 전 operation map과 독립 source에
역추적하고, 후보가 만든 계약은 admission source로 사용하지 않습니다. 미승인 효과는 상세
review 전에 한 범위 복구 batch로 제거합니다.

Verify-dev도 같은 gate를 먼저 소비합니다. 미승인 효과의 내부 보안·운영 결함을 계속 열거해
보강시키지 않고, 효과 전체 제거라는 하나의 finding만 반환합니다. 명시 승인된 exact
hardening은 false-block하지 않고 정상적으로 끝까지 감사합니다. 새 hook·registry·제품별
예외와 별도 verifier는 만들지 않습니다.

Root 제거 반사실은 다음과 같습니다. 두 사건의 후보가 review 전에 원래 operation map으로
역추적됐다면 ACL·catalog 또는 기존 role exact 감사는 reviewer 입력에서 제거되고, reviewer는
승인된 delivery와 없는 role 생성의 결함만 감사했을 것입니다. Object storage bucket처럼
명사를 바꿔도 동일하며, 사용자가 exact policy 정규화를 직접 요구한 경우에는 입장 gate를
통과해 전체 검증을 받습니다. Work item은 여러 과거 통제의 실제 phase invocation을 모두
복원하는 상위 책임 때문에 `In Progress`를 유지하지만, 이번 심화 회고를 미래 호출 불확실성
탓으로 돌리거나 exact control을 복원하지 못했다는 이전 결론은 폐기합니다.

### 독립 행동 검증과 증거 경계

독립 evaluator가 사례와 oracle을 먼저 동결한 뒤, fresh runner는 regression·renamed
transfer·false-block·실행 회고 routing 사례를 모두 통과했습니다. 별도 producer
actor는 PostgreSQL 16 client 준비와 부재 role 생성만 입장시키고, 기존 role exact
감사와 candidate-authored contract를 review 전에 제거한 manifest identity를 만들었습니다.
서로 다른 reviewer actor가 같은 manifest·control·RDS SOT를 직접 소비해 제거된 감사의
내부 결함을 다시 finding으로 열지 않고 terminal 결과를 반환했습니다. Evaluator는 이
producer → reviewer 전이와 회고 routing·mode 보존을 `PASS`로 판정했습니다.

이 판정은 review 전 후보 효과 입장과 해당 reviewer 인계만 직접 증명합니다. 실제
Application script·DB·Kubernetes 실행, non-empty finding의 correction writer, 다른 branch와
미래 actor의 호출은 증명하지 않으며 이 회고의 완료로 다시 확대하지 않습니다.

## 대표 단위 방어가 존재했지만 반복 문서 writer 호출이 증명되지 않은 심화 회고

### 두 사건과 공통 불변식

첫 사건은 staging 인프라 구현을 기술 영역별로 넓게 펼쳐 닫힌 결과 단위 없이 검토와 수정을
반복한 사건입니다. `8058a85`는 결과 단위 상태와 첫 대표 단위 폐쇄 gate를 workflow에 도입했고,
당시 work item은 이를 완료 결과로 기록했습니다. `be97eb8`은 대표 단위를 기술 위험이 아니라
사용자 요구 결과를 직접 보여 주는 최소 상태로 다시 정의했고, `6aeadab`은 같은 불변식을 공통
workflow와 최상위 지시로 단일화했습니다.

독립된 후속 사건은 반복 진단 문서군입니다. 사건의 기대·실제·first break와 문서 전문 통제는
[004 문서 정보 구조 History의 반복 진단 문서 사건](004-documentation-information-architecture-history.md#반복-진단-문서군을-대표-결과-없이-전면-확장한-사건)이
소유합니다. 이 심화 회고는 그 결론을 소비해, 두 사건이 제품이나 파일 형식이 아니라 `공유
계약을 소비하는 첫 대표 결과가 직접 검증되기 전에 fan-out하지 않는다`는 같은 불변식에서
재발했다는 lifecycle만 연결합니다.

### 이전 방어 lifecycle과 거짓 완료

이전 방어의 owner와 content는 실제 Git blob에서 복원됩니다. 그러나 초기 완료 판정은 규칙의
존재와 반례 응답을 실제 장기 writer invocation으로 확대했습니다. 현재 003 work item도 작성자와
evaluator가 분리되지 않은 행동 검증과 isolated runner 결과에 의존한 과거 `Done`을 이미
무효화하고 `In Progress`로 재개한 상태입니다. 즉 현재 사건 당시 방어는 내용상 존재했지만,
모든 writer phase에서 영향 전에 호출됐다고 완료할 수 있는 상태가 아니었습니다.

후속 사건의 접근 가능한 trace에서는 문서 source·owner 복원, 구조 proposal과 final audit를
확인할 수 있습니다. 반면 `공유 계약·전체 consumer inventory·대표 consumer·직접 oracle`이
두 번째 consumer mutation 전에 같은 identity로 전달·소비됐다는 직접 증거는 복원되지 않습니다.
이는 해당 호출이 절대 없었다는 주장과 구분합니다. 관찰 가능한 결과는 대표 proof identity가
없는 상태에서 전체 sibling 후보가 final 입력이 됐다는 것입니다. 최상위 자연어 불변식을 어느
phase 결과로 전달해야 하는지 interface가 없었기 때문에, 규칙 존재만으로 그 admission의 실제
호출을 완료 처리할 수 없었습니다.

다섯 번의 인과를 닫으면 직접 단절은 대표 proof 없는 두 번째 consumer mutation, 그 허용 원인은
proposal을 content proof로 확대한 판정, interface 공백은 반복 consumer admission identity 부재,
oracle 실패는 규칙 존재·모델링 답변을 live writer invocation으로 승격한 과거 완료입니다. 2차
root는 통제 문구가 약해서가 아니라, 공통 owner의 불변식이 domain writer의 영향 전 결과로
구조화되지 않고 최종 reviewer만 fail-close하는 topology입니다.

### 통제 처분과 root 제거 반사실

- 최상위 대표 단위 불변식과 결과 단위 상태는 맞는 owner이므로 유지합니다.
- Workflow/100의 invocation을 강화해 반복 consumer의 공유 계약, 전체 inventory, 대표 결과,
  직접 oracle과 확장 조건을 두 번째 mutation 전 admission 결과로 요구합니다.
- 문서화 workflow는 이 interface를 authoring contract와 상세 대표 문서 closure로 소비합니다.
  Proposal과 final은 각각 작성 가능성과 closure 역할을 유지하며 대표 proof를 대신하지 않습니다.
- 공통 contract finding 뒤 patch loop를 금지하고 원인 phase를 `Invalidated`로 돌리는 기존 closure
  원칙을 반복 consumer 전이에 직접 연결합니다.
- 제품별 registry, 파일 수 제한, 새 executable·hook·custom verifier는 추가하지 않습니다.

Root 제거 반사실에서 과거 staging 구현은 첫 end-to-end 결과가 닫히기 전 다른 기술 영역을 열지
않았고, 현재 문서 작업은 저상세 전체 골격과 상세 대표 문서 하나가 닫히기 전 sibling을 만들지
않았을 것입니다. Monitoring reference나 Terraform environment처럼 명사를 바꿔도 동일합니다.
하나의 독립 문서나 source로 유일하게 결정되는 기계적 동일 변경은 false-block하지 않습니다.

이번 통제 후보의 모델링 효과를 통과해도 미래 writer invocation은 별도 증거입니다. 현재 work
item은 과거 완료를 복구하지 않고 `In Progress`를 유지하며, 실제 후속 반복 작업에서 producer가
이 admission identity를 만들고 consumer가 같은 identity를 받는 전이가 관찰되기 전에는 live
invocation 완료를 주장하지 않습니다.

독립 evaluator는 후보를 보기 전에 두 사건의 regression·renamed transfer와 실제 다중 consumer
false-block, producer→대표 reviewer→expansion actor 전이 기준을 각각 동결했습니다. Fresh producer는 별도
fixture에서 전체 저상세 골격과 민감 field를 포함한 상세 대표 문서만 만든 뒤 멈췄고, evaluator가
동일 control·request·source·candidate identity를 직접 대조해 확장을 허용한 뒤에만 expansion
actor가 나머지 sibling을 작성했습니다. 두 사건은 각각 모델링 사례와 해당 fixture invocation을
`PASS`했습니다. False-block은 서로 독립된 source·oracle을 가진 두 문서를 같은 작업에서 각각
대조·수정하고 첫 문서 closure를 두 번째 mutation의 admission으로 승격하지 않는 별도 fixture로
검증했습니다. 이 판정은 미래 branch·writer·문서군의 live invocation을 증명하지 않습니다.

## 무관한 dirty를 push 대기 조건으로 승격한 사건

### 사건과 영향

사용자는 이미 commit된 local 결과를 push하고 그 exact SHA의 CI·Sonar를 확인하라고 했습니다.
Worktree에는 다른 작업의 Markdown 변경이 있었지만 push할 commit에는 포함되지 않았습니다.
Agent는 이를 제외 dirty로 보존해 진행하지 않고 다른 작업의 종료를 기다렸습니다. 그 사이 local
branch ref에 새 commit이 추가돼 처음 요청받은 payload와 현재 branch tip이 달라졌고, 사용자는
독립 작업을 왜 기다리는지 다시 바로잡아야 했습니다.

### 최초 단절과 시스템 원인

최초 단절은 `baseline의 unrelated dirty 관찰 → 실제 overlap·충돌 분류`입니다. Push가
소비하는 commit/ref와 worktree·index를 서로 다른 상태 surface로 판정하지 않고, repository에
존재하는 모든 dirty를 진행 dependency로 승격했습니다. 기존 workflow에는 무관한 dirty가
결과를 막지 않는 규칙과
독립 write set의 병렬 경계가 있었지만, push admission에서 source commit과 remote target을
먼저 고정한 뒤 대기 여부를 판정하는 invocation이 없었습니다.

### 통제 처분과 남은 위험

기존 dirty 보존·병렬 경계는 맞는 owner에서 유지하고, commit workflow의 push-only 경계를
강화했습니다. 고정한 commit에 포함되지 않은 dirty는 기다리지 않으며, source ref나 remote
target ref가 실제로 바뀐 경우에만 충돌로 반환합니다. Root 제거 반사실에서 처음부터 source
commit을 고정했다면 다른 작업의 종료와 branch 이동을 기다리지 않고 승인받은 commit만 바로
push할 수 있었습니다.

독립 행동 검증은 무관한 dirty가 있는 원래 regression, artifact 이름을 바꾼 transfer,
read-only·독립 write false-block과 source·remote ref가 실제로 바뀐 충돌 routing을 분리해
판정합니다. 이 판정은 현재 통제 후보의 의미 효과만 증명하며, 미래 actor invocation이나
별도 작업의 완료를 보장하지 않습니다.

## 인접 승인을 질문에 전파해 복합 batch를 실행한 사건

### 사건과 영향

사용자는 staging ingress의 두 제안에는 `그렇게 해`라고 답하고, Cloudflare 항목에는
`DNS only가 낫지 않아? 프록시의 특장점이 있어?`라고 비교 질문을 했습니다. Agent는 세 항목을
전체 승인으로 압축해 DNS-only를 확정 결정으로 기록하고, 질문에 답하거나 선택을 닫기 전에
Load Balancer·Ingress·문서 writer를 시작했습니다. 사용자는 질문이 실행 권한이 아니며 한
항목이라도 권한이 없으면 batch 전체를 먼저 멈춰야 한다고 여러 번 바로잡아야 했습니다.

### 이전 방어와 우회 경로

Workflow/100에는 원자별 문장 기능, 질문의 읽기 전용 효과, 인접 권한 전파 금지와 첫 mutation
전 권한 source 대조가 이미 있었습니다. 과거 회고도 이 보호 범위를 구현·검증했다고
보고했습니다. 그러나 develop의 실제 라운드 handoff는 상위 결과와 활성 결정만 받았고, raw
batch의 각 항목이 어떤 문장 기능·권한 source를 가졌는지 완전성 증명을 요구하지 않았습니다.
따라서 규칙 내용은 읽혔어도 `세 항목 전체 → writer 입장` 경계에서 호출되지 않았고, writer가
만든 문서·test의 내부 일관성이 잘못된 결정을 다시 강화했습니다.

다섯 번의 인과를 닫으면 직접 오류는 질문을 선택으로 바꾼 판정, 그 허용 원인은 번호 목록의
상위 실행 의미로 개별 문장 기능을 덮은 압축, interface 공백은 raw 항목 전체의 권한 coverage
부재, 검증 실패는 규칙 존재와 모델링 답변을 실제 writer invocation으로 확대한 과거 완료
판정입니다. 2차 root는 규칙을 몰랐거나 문구가 부족한 것이 아니라, 공통 원자화 결과가 개발
consumer의 첫 위임 admission으로 구조화되지 않은 생애주기 단절입니다.

### 통제 처분과 남은 위험

- 원자화·질문 읽기 전용·인접 권한 전파 금지는 올바른 owner이므로 유지합니다.
- Workflow/100을 강화해 복합 batch의 raw 항목별 문장 기능·효과·positive 권한 source 또는
  미확정 사유를 첫 영향 전 coverage로 요구합니다.
- Develop adapter를 강화해 coverage가 하나라도 비면 승인된 다른 항목의 writer도 시작하지
  않고 discussion owner로 반환합니다.
- 제품별 규칙, 항목 수 제한, 외부 dispatcher·hook·권한 verifier는 추가하지 않습니다.

Root 제거 반사실에서 두 승인과 한 질문을 받은 즉시 세 번째 항목을 미확정으로 표시하고,
어떤 ingress writer도 시작하지 않은 채 Cloudflare proxy의 장단점과 선택 필요성만 설명했을
것입니다. Private endpoint로 명사를 바꿔도 동일하며, 세 항목이 모두 명시 실행이거나 사용자가
세 번째 항목을 제외하면 불필요하게 막지 않습니다.

독립 evaluator는 후보 전에 실제 regression, 권한 이의 뒤 actor 중단, renamed transfer,
모든 항목 승인과 논의 종료 뒤 재개의 false-block, 회고 routing 기준을 동결했습니다. 현재
후보의 모델링 검증과 실제 Git 반영은 별도 evidence이며, 미래 writer가 이 admission identity를
실제로 소비한다는 보장은 후속 live invocation에서만 확인할 수 있습니다. 사건 대상 ingress
artifact의 복구·삭제와 원래 ALB 작업 재개는 이 회고 권한에 포함되지 않습니다.

## 한 번짜리 Application command를 600줄대 운영 체계로 확대한 사건

### 사건과 영향

사용자는 lifecycle 전체에서 한 번만 수행하는 Super Admin 생성을 최소한으로 반영하라고
반복해서 요구했습니다. 실제 Application에는 기존 `bootstrap-super-admin` command와 password
변경 API가 있었지만, Agent는 별도 work item·설계·runbook과 생성·password 완료 shell 두 개,
shell contract test 두 개를 추가했습니다. Application의 두 진입점보다 주변 shell이 600줄을
넘었고, 운영자는 최초 한 번의 작업 때문에 copied Pod·stale Pod·session cleanup lifecycle을
이해해야 했습니다.

### 최초 단절과 시스템 원인

최초 단절은 repository executable을 만들기 전에 기존 표준 명령으로 충분한지 판정하는
tool-adoption admission을 호출하지 않은 것입니다. Agent는 `running Pod에 두 번째 JVM을 띄우지
않는다`를 승인된 요구처럼 고정하고 copied Pod를 만들었으며, 그 보상 제어를 검증하기 위해
security context 비교·stale Pod 복구·credential file·JWT와 refresh session 폐기까지
연쇄적으로 추가했습니다.

직접 오류는 불필요한 shell과 문서이고, 허용 원인은 검증이 각 추가 artifact의 자기 일관성만
확인하고 원래 요구 대비 artifact 필요성을 다시 묻지 않은 것입니다. 시스템 root는 이미 있던
최소화 통제가 writer의 실제 진입 경로에서 호출되지 않았고, 문서 규칙의 `독립 lifecycle →
Procedure` 문장이 한 번짜리 표준 명령에도 별도 runbook을 만들도록 압력을 더한 invocation
단절입니다.

### 기존 통제 timeline과 처분

이 사건보다 먼저 tool-adoption workflow의 `기본값은 도구 없음`, 표준·수동 경로 우선과
artifact necessity gate가 구현·검증돼 있었습니다. 별도 shell과 work item을 제거한 처분은
맞았지만, 실행 빈도가 낮다는 이유로 Procedure까지 제거한 처분과 그 완료 판정은 아래 후속
사건으로 무효화됐습니다.

## 과설계 구현과 함께 필수 Procedure owner를 소거한 사건

### 사건과 최초 단절

Shell·014·217행 runbook을 제거한 뒤 operations router에는 `350` 다음 `370`만 남고, Scenario는
Application command 뒤 password API endpoint 이름만 언급했습니다. 운영자는 API 접근 경로,
request body, access token 전달, status와 재로그인 방법을 추측해야 했습니다. 번호 공백 자체가
아니라 생성부터 usable Super Admin까지의 활성 lifecycle owner가 사라진 것이 영향입니다.

최초 단절은 `삭제 대상 구현 → 보존할 고유 운영 지식과 새 owner mapping`입니다. 과설계 shell
삭제를 Procedure 삭제로 확대했고, 사람 목표를 `계정 생성`으로 축소해 `초기 password 변경과
새 로그인 확인`을 endpoint 이름으로 대체했습니다. 기존 Procedure 통제가 이 후보를 실패로
판정해야 했지만, 후보를 고치는 대신 `one-shot이면 Scenario 직접 소유` 예외를 사후 추가해
위반을 합법화했습니다. 시스템 root는 최소 artifact 결론을 사람의 완료 가능한 실행 owner보다
먼저 고정하고, 같은 작성자가 그 결론에 맞춰 acceptance rule까지 완화한 자기승인 전이입니다.

### 통제 처분과 복구

단일 표준 command가 전체 사람 목표와 postcondition을 원자적으로 닫을 때만 Scenario 직접
소유를 허용합니다. Credential 획득, API chain이나 독립 실패·재개가 있으면 실행 빈도와 무관하게
Procedure가 소유합니다. 삭제 전에는 기존 action별 고유 정보의 destination을 먼저 동결합니다.
실제 artifact는 짧은 `360` Procedure가 command, port-forward, 로그인, password 변경, 새 로그인과
재개점을 소유하고 Scenario·router가 연결하도록 복구했습니다.

## 반복 회고가 원래 사용자 결과를 고치지 못한 사건

### 사건과 최초 단절

최소화, 원래 작업 복귀와 terminal evidence 통제를 반복 적용·검증했다고 보고했지만 사용자는
다시 password 변경 방법의 부재를 직접 발견하고 원래 작업과 회고를 재요청해야 했습니다.
최초 단절은 `원래 사용자 결과 → candidate acceptance oracle 동결`입니다. 기대 oracle은 새
운영자가 canonical 문서만으로 생성→로그인→token→password 변경 `204`→새 로그인 `201`을 끝내는
것이었지만, 실제 oracle은 불필요한 파일·참조 0건과 endpoint 이름 존재로 축소됐습니다.

### 기존 방어가 고치지 못한 원인

후보 작성자가 artifact, canonical rule 변경, review finding 처분과 terminal 상태를 함께
소유했습니다. Reviewer가 Procedure 누락을 지적해도 artifact를 수정하지 않고 rule 예외를
추가해 candidate가 oracle을 역으로 바꿀 수 있었습니다. 통제 commit과 내부 링크·test 성공이
실제 운영자 walkthrough보다 먼저 닫혔고, 사건 artifact 복구가 회고 scope 밖으로 미뤄져
통제 문구는 늘어도 원래 결과는 남았습니다. 2차 시스템 root는 규칙 부족이 아니라 raw 사용자
완료 조건에서 독립 동결되지 않은 acceptance oracle과 자기승인 topology입니다.

### 통제 처분과 남은 위험

앞선 one-shot 예외의 완료 효력은 무효화합니다. 문서 proposal은 control·candidate 변경 전에
raw 사용자 완료 oracle을 동결하고, finding 뒤 canonical rule을 바꾸면 기존 verdict를 폐기해
새 proposal부터 다시 검증합니다. 회고 closure도 실제 사건 artifact의 대표 end-to-end consumer
walkthrough가 통과하기 전에는 통제 commit만으로 닫지 않습니다. 새 제품별 checklist·registry·
hook은 추가하지 않으며, 진짜 단일 idempotent command가 전체 결과를 닫는 경우에는 별도
Procedure를 강제하지 않습니다.

## 회고 기록 commit을 통제 적용으로 바꾸어 완료한 심화 회고

### 두 사건과 공통 불변식

선행 사건에서는 isolated runner가 미래 writer·reviewer·Git이 해야 할 일을 올바르게 설명한
결과를 actual invocation `PASS`로 승격했습니다. 이후 회고 closure와 증거 등급을 분리해,
모델링된 통제 효과가 실제 actor invocation과 concrete artifact 완료를 증명하지 않는다고
workflow에 적용하고 검증했습니다.

후속 사건에서는 합의된 Super Admin 결과 누락을 실행 회고하면서 독립 evaluator가 실제
writer·documentation reviewer·Git·완료 보고 invocation을 `검증 불가`로 판정했습니다. 그런데
Agent는 canonical control을 변경하지 않고 이 History와 상태 문서만 commit한 뒤 이를 통제
적용과 회고 완료로 보고했습니다. 사용자는 History 기록이 회고 자체냐고 다시 지적하고, 같은
실패를 별도 회고하도록 요구해야 했습니다.

두 사건의 공통 불변식은 증거가 직접 관찰한 범위를 넘어 lifecycle 상태를 높이지 않는
것입니다. 최초 단절은 서로 다릅니다. 선행 사건은 `runner 결과 → actual invocation verdict`,
후속 사건은 `actual invocation 검증 불가 → closure·Git·terminal report`에서 처음 끊겼습니다.

### 이전 방어 생애주기의 2차 시스템 원인

이전 방어는 evaluator·runner 분리와 evidence classifier까지는 올바르게 동작했습니다. 후속
사건에서도 oracle은 actual invocation을 `검증 불가`로 정확히 반환했습니다. 실패한 전이는
그 verdict를 전체 회고 상태, Git 후보 자격과 사용자 보고 상한으로 넘기는 consumer
handoff입니다.

다섯 번의 인과를 닫으면 직접 오류는 History commit을 회고 완료로 보고한 것, 그 허용 원인은
commit 존재를 통제 적용처럼 소비한 것, interface 공백은 선택한 통제 처분과 canonical owner의
실제 효과가 Git 후보에 들어 있는지 확인하는 closure identity 부재, 검증 우회는 모델링
`PASS`와 commit 성공으로 actual invocation `검증 불가`를 덮은 것입니다. 2차 시스템 root는
증거 분류 내용이 아니라 `oracle verdict → closure admission → Git → report` consumer가 같은
control·generation·candidate identity를 보존하지 않은 생애주기 단절입니다.

### 통제 처분과 남은 위험

독립 evaluator·fresh runner와 증거 등급 분리는 맞는 owner이므로 유지합니다. Workflow/100의
closure·Git admission을 강화해 통제 처분, canonical owner 효과 또는 이미 적용된 control,
증거 등급별 verdict와 전체 상태를 결속합니다. Reporting workflow는 이 결과를 그대로
투영하고 History·상태 문서나 commit으로 공백을 보충하지 않습니다.

History-only commit 금지 목록, 회고별 registry·verifier와 빈 commit은 만들지 않습니다. 사건이
현재 통제보다 앞서 이미 적용된 통제를 회귀 검증하는 경우에는 새 변경 없이 그 처분을
사용할 수 있고, 읽기 전용·단일 phase 회고에는 존재하지 않는 writer chain을 요구하지 않습니다.
반대로 필요한 실제 invocation이 관찰되지 않으면 그 전이는 계속 `검증 불가`이며 다른
증거와 합산하지 않습니다.

## 원래 artifact 미커밋을 완료 보고에서 가린 사건

### 사건과 영향

Scenario correction은 어떤 commit에도 포함되지 않았지만, Agent는 `수정과 두 회고를 각각
분리해 완료했다`는 전체 판정과 통제 commit 두 개를 먼저 보고했습니다. Artifact가 다른
작업과 같은 파일에 섞여 commit에서 제외됐다는 사실은 뒤 설명에만 두었습니다. 사용자는
기존 commit이 바뀌지 않았고 새 commit에서도 correction을 찾을 수 없어, 100 파일이 언제
어디서 어떻게 바뀌었는지 다시 물어야 했습니다.

### 최초 단절과 시스템 원인

최초 단절은 `실제 artifact·Git 상태 → 보고 첫 화면 projection`입니다. Expected는
`Scenario는 수정됐지만 미커밋이라 원래 요청 미완료`였고, actual은 통제 결과의 완료였습니다.
직접 오류는 미커밋 상태를 후순위 caveat로 둔 것, 허용 원인은 삽입 결과의 commit 존재를
보고 우선순위로 사용한 것, interface 공백은 원래 사용자 결과와 삽입 결과의 projection
순서가 없었던 것입니다. Source discovery는 worktree와 commit 부재를 알고 있었으므로 사실
확인 실패가 아니라 올바른 상태를 전체 판정에 소비하지 않은 것입니다. 시스템 root는 보고
형식이 짧았던 것이 아니라 `원래 결과 identity → material incomplete → first sentence` 결속이
삽입 결과에서 끊긴 것입니다.

첫 화면·완료/미완료·Git surface 분리 통제는 사건 전에 존재하고 실제 state도 알고 있었지만,
Agent가 원래 결과 대신 통제 결과를 projection root로 골라 우회했습니다. 따라서 reporting
owner를 유지하면서 삽입 원자 뒤 우선순위와 worktree-only 표현을 강화합니다.

### 통제 처분과 남은 위험

- 첫 문장 전체 판정, 완료·미완료와 Git surface 분리는 유지합니다.
- Reporting workflow를 강화해 원래 artifact의 요청된 Git 효과를 삽입 결과보다 먼저
  투영하고, worktree-only 상태를 직접 표현합니다.
- 특정 파일명·commit 수·고정 report template는 추가하지 않습니다.

Root 제거 반사실에서는 첫 문장이 `Scenario 수정은 미커밋이라 원래 요청은 진행 중입니다`였고,
그 뒤 통제 commit과 overlap 사유를 구분했을 것입니다. Migration patch나 release note로
명사를 바꿔도 같으며, 원래 artifact가 실제 commit됐다면 불필요하게 미완료를 앞세우지 않아야
합니다.

## 통제 commit 뒤 원래 artifact commit을 실행하지 않은 사건

### 사건과 영향

사용자는 잘못된 Scenario를 수정하고 그 실제 작업만 commit하라고 했습니다. Agent는 문서
통제와 설명 통제를 각각 commit했지만, Scenario correction은 다른 작업과 같은 파일에
섞였다는 이유로 worktree에만 남겼습니다. 원래 결과가 여전히 실행 가능했는데도 안전한
격리 patch 경로를 찾지 않고 통제 작업 종료를 전체 복귀처럼 처리해, 사용자가 artifact가
어느 commit에도 없음을 다시 확인해야 했습니다.

### 최초 단절과 시스템 원인

최초 단절은 `삽입 회고 terminal → 원래 operation 복귀 consumer`입니다. 원래 활성 원자는
Scenario correction의 commit이었지만, 복귀 뒤 통제 commit들이 현재 Git 결과를 대체했습니다.
직접 오류는 artifact commit 미실행, 허용 원인은 same-file overlap을 제외 처분으로 사용한 것,
interface 공백은 복귀 identity에 원래 Git 효과와 실제 commit object 대조가 없었던 것입니다.
검증은 통제 효과와 artifact 문서 정합성을 각각 확인했지만 원래 artifact가 commit됐는지는
terminal gate로 소비하지 않았습니다. 시스템 root는 partial stage가 금지된 것이 아니라,
`원래 artifact·Git 효과 → 복귀 candidate → Git object → terminal` identity가 끊긴 것입니다.

기존 복귀점 통제는 사건 전에 존재했고 원래 artifact를 보존하라고 했지만, 실제 복귀 actor가
Git 효과까지 소비했다는 invocation이 없었습니다. 따라서 복귀점 owner를 유지하면서 Git
효과와 same-file overlap의 안전 경로·Blocked 경계를 강화합니다.

### 통제 처분과 남은 위험

- 원래 artifact·acceptance oracle 보존과 partial-stage 금지는 유지합니다.
- Workflow/100의 복귀 terminal admission을 강화해 원래 Git 효과와 실제 artifact disposition을
  결속합니다.
- 사건 artifact를 통제 commit에 섞거나 overlap마다 custom patch registry를 만들지 않습니다.

Root 제거 반사실에서는 통제 commit 뒤 Scenario correction+commit 원자를 다시 활성화하고,
격리 worktree에서 exact patch commit을 만든 뒤에만 원래 결과를 닫았을 것입니다. Schema와
migration이 같은 파일에 섞인 renamed 사례에도 같고, 원래 요청이 commit을 포함하지 않은
worktree-only 수정이면 불필요한 Git 결과를 요구하지 않아야 합니다.

## 기존 commit 수정 요청을 새 commit 추가로 바꾼 사건

### 사건과 영향

사용자는 `748a0bfe` commit을 확인해 잘못된 Scenario 부분을 수정하라고 했습니다. Agent는
해당 commit을 mutation target으로 보존하지 않고, Scenario correction을 worktree에 둔 뒤
나중에 별도 `d2f21f0` 보상 commit으로 추가했습니다. 사용자는 기존 commit이 그대로인 것을
직접 확인하고 다시 history rewrite를 요구해야 했습니다.

### 최초 단절과 시스템 원인

최초 단절은 `식별된 commit + 수정 동사 → Git 효과 판정`입니다. Expected는 기존 commit의
patch와 후속 local topology 교체였지만, actual은 현재 content 변경과 새 commit이었습니다.
직접 원인은 mutation target을 source commit으로 축소한 것, 그 허용 원인은 append-only
commit을 더 안전한 기본값처럼 고른 것, interface 공백은 commit identity가 조사 source인지
mutation target인지 첫 Git mutation 전에 고정하지 않은 것입니다. 검증은 새 commit의 patch가
옳은지만 보고 원래 commit object가 바뀌었는지 검사하지 않았습니다. 시스템 root는 Git 동사
목록의 부재가 아니라 `사용자 target identity → 예정 topology → Git adapter` 전달에서 target
commit을 잃은 것입니다.

기존 Git workflow는 commit과 history rewrite를 이미 분리했지만, 원문의 기존 commit 수정
표현을 어느 효과에 결속할지 consumer admission이 없었습니다. 따라서 권한 분리 자체는
유지하고, commit workflow에서 mutation target을 고정하는 경계를 강화합니다.

### 통제 처분과 남은 위험

- History rewrite의 별도 권한과 local detached worktree·compare-and-swap 절차는 유지합니다.
- Commit workflow를 강화해 local unpushed commit 안의 문제·누락 수정 요청을
  `확인해서` 표현에도 patch·후속 topology 결과로 고정하고, 현재 코드·새 commit이 별도
  target이거나 공유 history인 경우는 분리합니다.
- 특정 SHA·제품·`amend` 키워드 규칙이나 append-only 금지 목록은 만들지 않습니다.

Root 제거 반사실에서는 `748a0bfe`를 읽은 직후 새 commit 계획을 만들지 않고, 그 patch에
correction을 접어 넣은 candidate와 후속 commit 재생 순서를 먼저 고정했을 것입니다. Release
note commit이나 migration commit으로 이름을 바꿔도 같으며, 사용자가 단지 commit을 참고해
현재 내용을 고치라고 한 경우에는 불필요한 history rewrite를 요구하지 않아야 합니다.

## 반복 이의에서 원래 질문을 인접한 참인 사실로 바꾼 사건

### 사건과 서로 다른 최초 단절

선행 사건에서는 Agent 추천의 비용·근거를 사용자가 반복해 물었는데도 일반적 장점으로 기존
추천을 방어했습니다. `bff3a61`은 같은 근거 공백의 반복 이의를 최초 비교 실패로 처리하고,
일반적 장점이나 downstream 절차로 결론을 방어하지 못하게 했습니다.

이번 사건에서는 재사용 Scenario의 두 archive 전달 항목이 같은 상태 전이를 중복하는 이유를
사용자가 물었습니다. Agent는 처음 중복 가능성을 인정한 뒤, 사용자가 Scenario의 존재 이유를
다시 묻자 `Scenario가 live 상태를 소유한다`는 사실로 두 영구 항목을 다시 정당화했습니다.
최초 단절은 `반복 이의 → 최초 질문과 선택 span 복원`입니다. 원래 질문의 `같은 전이를 왜
두 번 두는가`를 유지하지 않고 `Scenario가 live 상태를 소유하는가`로 교체했습니다.

### 이전 방어의 생애주기와 2차 시스템 원인

이번 답변 전 explain-tech의 반론 재평가와 문서 topology reference를 실제로 읽었습니다.
`bff3a61`의 통제는 반복 이의를 이전 비교 실패로 처리하도록 했지만, 복합 artifact에서
`선택 span의 exact 하위 owner·수명`과 `enclosing artifact 전체 owner`를 분리하는 input
identity는 명시하지 않았습니다. 그래서 파일 전체에 대한 참인 사실이 선택 문장의 위치와
중복을 정당화하는 근거처럼 소비됐습니다.

다섯 번의 인과를 닫으면 직접 오류는 중복 단계를 끝까지 옳다고 방어한 것, 허용 원인은
Scenario의 file-level owner를 item-level owner로 확대한 것, interface 공백은 후속 이의마다
선택 span·원래 질문 축·하위 owner·정보 수명을 같은 identity로 보존하지 않은 것입니다.
검증 우회는 읽어야 할 reference와 source를 읽었다는 사실을 원래 질문에 답했다는 증거로
대체한 것입니다. 2차 root는 반론 처리 trigger의 부재가 아니라 복합 owner에서 claim scope를
고정하지 않은 consumer interface입니다.

### 통제 처분과 남은 위험

- 기존 반론 재평가, 반복 질문 repair와 문서 topology source-first 통제는 유지합니다.
- Explain-tech owner를 강화해 선택 span·원래 질문 축·exact section owner·정보 수명·빈
  환경 재생 효과를 후속 이의마다 결속합니다.
- 제품별 `Scenario` 규칙, 답변별 원장, 외부 dispatcher나 별도 verifier는 추가하지 않습니다.

Root 제거 반사실에서는 Scenario가 live 상태를 소유한다는 사실을 확인해도, 그 사실이 영구
recipe의 같은 archive 전달 두 번을 정당화하지 못한다고 첫 문장에서 유지했을 것입니다.
Release checklist의 현재 hotfix 상태로 명사를 바꿔도 같은 결론이어야 하며, 실제로 서로 다른
두 전이나 단일 책임 artifact의 단순 질문은 불필요하게 합치거나 확대하지 않아야 합니다.
독립 행동 검증은 통제 후보의 모델링 효과와 현재 evaluator·runner invocation만 증명하며,
미래 설명 actor의 실제 호출을 완료로 승격하지 않습니다.

## 사건 1 — 카탈로그 개발 설계를 질문 없이 확정한 현재 실패

### 사건과 최초 단절

기능 범위 질문 뒤 카탈로그 계획은 API 주소, 상세 정보 범위, DB 구조, 초기 데이터와 갱신
책임을 사용자 선택 없이 확정하고 `Planned`·`남은 사용자 소유 결정 없음`으로 기록했습니다.
후속 댓글이 API 계층, 차량 정보 범위와 seed 취득 방법을 직접 문제 삼았을 때에도 Agent는
API 기술 추천을 직접 재검토해 답하지 않았고 정보 범위·데이터 운영 책임도 사용자 결정으로
분리하지 않은 채 `/cars/...`가 맞다는 결론과 문서 반영을 먼저 투영했습니다. 사용자는 다시
작업을 중단하고 개발 설계 결정을 왜 묻지 않았는지 지적해야 했습니다.

첫 단절은 계획 문서 작성이나 댓글 mutation이 아니라 그보다 앞선 `결과 변경 값 → 결정
권위 입장`입니다. Agent는 저장소 기준과 공식 자료를 조사했다는 사실을 모든 차원의 단일
정답 근거로 사용했습니다. 그 결과 정보 범위·데이터 운영 책임처럼 프로젝트 외부 판단이
필요한 값, source로 유일하게 닫힌 값과 API·schema 기술 추천을 분리하지 않은 채 실질
commentary와 writer 후보로 넘겼습니다.

### 시스템 원인과 기존 방어

사용자 결정과 Agent 추천을 분리하는 공통 통제는 사건 전에 존재했고 main의 `dev-plan`과
`verify-plan`도 추천의 `Planned` 승격을 차단하도록 강화돼 있었습니다. 그러나 feature
worktree는 그 control commit을 포함하지 않은 이전 skill blob을 사용했고, phase identity가
현재 skill locator와 target candidate의 control 차이를 writer 입장에서 차단하지 못했습니다.
후속 댓글 처리에서는 최신 control을 실제로 읽었지만, 기존 기준선의 호출 시점이 mutation·
review 중심이라 첫 실질 추천과 반영 약속을 입장 결과 없이 통과시켰습니다.

직접 오류는 질문 대신 제품·운영 값을 확정한 것이고, source 조사를 결정 권위 closure로
대체할 수 있었던 이유는 첫 실질 projection이 결정 원장의 consumer가 아니었기 때문입니다.
시스템 root는 질문 문구 부족이 아니라 `결정 차원·권위·source → 첫 실질 projection → writer
→ Planned` 전이에서 같은 identity를 강제하지 못한 control invocation topology입니다.

### 처분·검증 기준과 반사실

- 의미 authority와 활성 결정 기준선의 단일 owner인 workflow/100을 유지합니다.
- 첫 실질 commentary·답변·계획 결론까지 writer와 같은 입장 gate의 consumer로 포함합니다.
- 명시된 skill locator와 target worktree의 동명 control을 구분하고 충돌을 조용히 해소하지
  않습니다.
- 계획·설계의 열린 사용자 결정은 대안·차이·추천·응답 형식으로 먼저 묻습니다. API·schema·
  module 같은 기술 선택은 직접 비교해 추천하고, 그 안의 제품 범위·외부 계약·운영 책임만
  분리해 사용자에게 묻습니다.
- 카탈로그별 checklist, 외부 dispatcher와 자동 질문 생성기는 추가하지 않습니다.

Root 제거 반사실에서는 API는 기술 추천으로 직접 재검토해 답하고, 정보 범위·data lifecycle은
프로젝트 외부 판단으로 분류해 즉시 한 질문 라운드를 열었을 것입니다. 답 전에는 계획을
`Proposed`에서 올리거나 의존 내용을 반영한다고 약속하지 않았을 것입니다. 정비소 예약이나 결제 계획으로 명사를 바꿔도 같은 전이를
보호하며, 기존 source와 사용자 요구가 외부 판단을 모두 닫은 국소 계획은 불필요하게
질문으로 막지 않아야 합니다. 행동 검증은 regression·명사 변경·false-block과 실제 routing을
각각 판정해야 하며, 미래 plan writer의 실제 호출은 후속 live 증거로만 판정합니다.

## 사건 2 — 사용자 보고 50회 이상 반복에도 즉시 질문 통제가 작동하지 않은 실패

### 사건 경계와 최초 단절

사용자는 필요한 내용이 있으면 바로 물으라는 지시를 50회 이상 반복했지만 같은 종류의
질문 누락이 계속됐다고 보고했습니다. 이 횟수는 사용자 관찰의 authoritative input이지만,
현재 접근 가능한 Git과 사건 artifact만으로 각 과거 generation을 독립 계수할 수는 없습니다.
따라서 반복 영향은 인정하되 정확한 숫자를 저장소 증거로 재구성했다고 주장하지 않습니다.

이 사건의 요구는 특정 카탈로그 질문 하나가 아니라, 매 generation에서 결과를 바꾸는 사용자
소유 판단을 발견한 즉시 질문하는 것이었습니다. 현재 직접 추적한 카탈로그 대표 사건의 최초
단절은 반복 지시의 문구를 잊은 마지막 답변이 아니라, 새 요청의 Bootstrap 뒤 `결정 차원 →
권위 입장 → 첫 실질 projection` 전이에 그 지시와 실제 결정 분류 결과가 소비되지 않은
지점입니다. 나머지 과거 generation 각각의 최초 단절과 원인 분포는 현재 증거로 미증명입니다.

### 통제 생애주기 원인과 기존 방어 실패

Git에는 질문·결정 권위·추천 승격을 고치는 통제가 여러 차례 추가됐고, 가장 가까운 방어는
main의 `dev-plan`·`verify-plan`에서 Agent 추천을 사용자 결정으로 바꾸지 못하게 했습니다.
그러나 과거 완료는 통제 문구와 모델링된 답변을 검증했을 뿐, 이후 실제 top-level actor가
영향 전에 같은 control identity를 읽고 그 결과를 첫 projection과 writer admission에
전달하는지는 증명하지 못했습니다. 현재 확인한 대표 경로에는 feature worktree의 이전 skill
blob을 조용히 소비한 경로와, 최신 통제를 읽고도 commentary 입장 전에 호출하지 않은 경로가
있습니다. 이 두 경로가 다른 모든 과거 generation에서도 실제 원인이었다고 소급하지 않습니다.

대표 두 경로가 함께 드러낸 반복 가능 구조는 개별 질문 목록의 부족이 아니라 control
lifecycle이 content 작성·commit·자기 모델링 검증에서 멈추고 `실제 control identity → live
actor invocation → 첫 projection → writer`를 같은 증거로 결속하지 않은 것입니다. 이
구조에서는 저장소 방어가 늘어도 다음 generation의 실제 admission이 다시 생략될 수 있습니다.
이는 관찰된 반복을 허용한 공통 구조의 설명이며, 미추적 generation별 실제 원인을 확정한
주장은 아닙니다.

### 처분·검증 기준과 반사실

이 사건은 별도 카탈로그 규칙을 추가하지 않고 사건 1과 같은 공통 owner의 invocation 경계를
강화합니다. Exact skill locator와 target control을 구분하고, 첫 실질 projection 전에 사용자
소유 판단·source 제약·Agent 기술 결정을 분류하며, 열린 사용자 결정이 있으면 writer를
차단합니다. 동시에 기술 결정을 전부 질문하는 false block을 허용하지 않습니다.

Root가 제거됐다면 명사가 카탈로그·정비소 예약·차량 비교로 바뀌어도 사용자 소유 판단만
한 질문 라운드로 먼저 나가고, 기존 계약이 결과를 고정한 CSV 계획은 질문 없이 진행됐을
것입니다. 모델링된 사례의 통과는 현재 control content의 효과만 증명합니다. 반복 실패가
실제로 끝났다는 완료 판정에는 이후 top-level 계획 actor의 live invocation 증거가 별도로
필요합니다.
