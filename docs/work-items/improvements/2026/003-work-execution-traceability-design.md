# 작업 실행 추적성과 회고 구조 설계

## 설계 목표

작업 조건과 사용자 결과를 source에서 실행·증거·완료까지 보존하면서도, 실패 사례마다
규칙·field·fixture·verifier를 추가하지 않는 구조를 만듭니다. 공통 책임은 한 owner에
모으고 전문 workflow·skill은 실제 차이만 소유합니다.

## 결정 1. 공통 lifecycle은 한 owner만 둡니다

### 배경

요청·권한·단위 상태·병렬·검증·Git·보고 알고리즘이 `AGENTS.md`, workflow, skill과
metadata에 반복됐습니다. 같은 안전 규칙을 여러 곳에 복사하면 일부만 호출되거나 서로
다르게 갱신돼 새 실패 표면이 생깁니다.

### 선택

다음 계층을 사용합니다.

```text
AGENTS: 최상위 불변식과 진입점
→ workflow/100: 요청·권한·결과 단위·차단·통제 변경·공통 closure
→ domain workflow: 설계·문서·개발·test·Git·worktree의 고유 판단과 mechanics
→ skill: trigger + canonical source 로딩 + 고유 분석·실행 adapter
→ metadata: discovery
→ reference·script: 조건부 전문 지식 또는 실제 executable
```

공통 lifecycle은 요청 결과가 phase를 소유하고, 다음 consumer가 사용할 수 있는 결과를
상태 단위로 닫으며, 진척은 그 상태 전이로만 계산합니다. final closure 실패는 활성
요청을 자동 종료하지 않고 후보와 원인 phase를 무효화합니다. 코드 작업의 remote
CI·Sonar terminal gate와 Git mutation 권한은 분리해, push 권한이 없으면 local 결과만
닫고 전체 코드 결과는 미완료로 유지합니다.

Specialist는 현재 결과의 미충족 dependency와 다른 owner를 반환할 수 있지만 operation
membership을 바꾸지 않습니다. 다른 specialist 결과는 현재 사용자 원문이 독립 원자로
포함할 때만 활성화합니다. 포함하지 않은 결과는 현재 결과를 막는지에 따라 사용자 결정
경계 또는 비활성 finding으로 남기며, 기술적 필요성이나 owner 반환을 실행 권한으로
승격하지 않습니다.

회고와 심화 회고도 공통 통제 상태를 다시 정의하지 않습니다. 일반 회고는 단일 사건의
수평·수직 인과, 심화 회고는 두 사건과 이전 방어 생애주기의 증거를 workflow/100에
전달합니다.

### 근거와 영향

한 규칙의 변경·제거 위치가 하나가 되고, skill trigger와 UI prompt가 실행 권한이나
완료 상태를 바꾸지 못합니다. 대신 domain workflow가 실제 차이를 충분히 소유해야
하므로 단순히 모든 파일을 짧게 만드는 것을 목표로 삼지 않습니다.

### 배제 대안과 재검토 조건

안전을 위해 모든 skill에 공통 규칙을 복사하는 방식은 drift와 선택적 적용을 늘려
배제합니다. workflow/100이 다시 domain별 사례를 흡수하거나 전문 adapter들이 공통
상태를 재정의하면 owner 경계를 재감사하고 중복을 제거합니다.

## 결정 2. 조건은 최소 원장으로 운반하고 source-first로 닫습니다

### 배경

영구 JSON·catalog와 자기검증기는 높은 유지비와 false confidence를 만들었지만, 여러
source의 독립 조건을 기억과 마지막 checklist에만 맡기면 대화 압축과 긴 실행에서
누락됩니다.

### 선택

독립 증거·상태·책임·권한을 가진 조건만 세션 또는 `/tmp`의 최소 활성 조건 원장에
둡니다.

```text
원문 조건 → 의미·권위 → 결과 단위 → 실행·증거 → 완료 상태
```

완료 감사는 원장을 oracle로 사용하지 않고 원래 요청, 최신 정정, 현재 SOT와 실제
source에서 조건을 다시 복원합니다. 광범위 문서 coverage는 actual filesystem을
수집해 owner·owner 공백·근거 있는 제외로 직접 분류합니다.

`/tmp` 원장은 활성 작업이 끝나거나 세션 밖으로 인계될 때 현재 결과·남은 책임·재개
조건만 승인된 기존 work item 또는 사용자 보고에 정규화한 뒤 삭제합니다. 임시 원장을
장기 기록으로 유지하거나, 원장 보존을 위해 새 work item을 자동 생성하지 않습니다.

같은 작업에서 새로 쓴 policy·SOT·test·fixture와 실패 뒤 회고는 선행 요구를 다시
정의하는 oracle이 아닙니다. 사용자 확정 변경이나 독립 authoritative source만 oracle을
갱신할 수 있고, 이름·분류만 바꿔 같은 관찰 상태를 정상화하는 결과는 실패입니다.

### 근거와 영향

조건 전달은 관찰할 수 있지만 repository schema·parser·fixture 부채는 만들지
않습니다. 한 증거로 닫히는 작은 작업에는 원장을 강제하지 않고, 인계 뒤 임시 원장이
남기는 별도 상태 owner도 만들지 않습니다.

### 배제 대안과 재검토 조건

기억만 사용하거나 모든 작업에 work item·JSON을 강제하는 선택은 각각 누락과 형식
노동을 키워 배제합니다. 원장이 단순 작업까지 비대하게 만들거나 전문 skill이 공통
field를 복제하면 적용 범위를 줄입니다.

## 결정 3. 실패는 첫 단절과 유효 통제 timeline으로 분류합니다

### 배경

새 사건을 option, SHA, trigger, fixture 같은 표면 명사로 이름 붙인 뒤 그 사례를
막는 규칙을 추가했습니다. 구조 우선 원칙은 이미 국소 원인이 선택된 뒤에 호출돼
append-only 수정을 막지 못했습니다.

### 선택

통제 변경의 입력은 observable impact, 최초로 끊어진 전이, 보호 불변식과 당시
유효했던 통제의 owner·배치·invocation·oracle입니다. workflow/100은 이 증거에서
다음을 구분합니다.

- 관련 통제가 없던 첫 실패
- 현재 통제보다 앞선 regression 사건
- 통제가 영향 전에 호출되지 않은 routing·invocation 실패
- 통제가 호출됐지만 같은 불변식이 실패한 심화 회고 대상
- 첫 단절과 불변식이 실제로 다른 새 문제

표면이 같아도 first break와 owner가 다르면 분리하고, 표면이 달라도 같은 전달 경로와
불변식이면 한 계열로 봅니다.

### 근거와 영향

새 규칙을 고르기 전에 기존 방어의 실제 효과와 완료 범위를 확인합니다. 완료 범위의
불변식이 다시 실패하면 후속 작업을 만들어 숨기지 않고 같은 work item을 재개합니다.

### 배제 대안과 재검토 조건

금지어·제품명·edge-case catalog와 더 긴 checklist는 새로운 이름이 등장할 때마다
무력해져 배제합니다. 서로 다른 first break를 지나치게 하나로 합치면 domain owner별로
다시 분리합니다.

## 결정 4. 기존 통제 처분 뒤에만 새 통제를 허용합니다

### 배경

기존 규칙이 있는 상태에서 새 문장을 추가하면 중복 owner와 stale rule이 함께
남습니다. 작성자가 만든 fixture와 형식 검사는 새 규칙의 존재만 증명하고 실제
사용자 영향을 막았는지는 증명하지 못했습니다.

### 선택

영향받는 기존 통제마다 유지·강화·교체·병합·제거 중 하나를 먼저 선택합니다. 새
rule·field·fixture·verifier·문서는 다음 조건을 모두 만족할 때의 마지막 수단입니다.

- 시스템 root가 직접 증거와 반사실로 닫혔습니다.
- 기존 통제의 재배치·강화·구조 교체·병합·제거로 해결되지 않습니다.
- 단일 owner와 영향 전 invocation, 작성자와 분리해 사전 동결·판정하는 독립 oracle과
  제거 조건이 있습니다.
- 구조·표준 도구·review보다 생애주기 순효과가 큽니다.

### 근거와 영향

additive-only diff를 기본 해법으로 사용할 수 없고 stale 통제를 함께 정리하게 됩니다.
새 executable은 별도로 도구 도입 workflow의 순효과 기준을 통과해야 합니다.

### 배제 대안과 재검토 조건

실패를 객관화할 수 있다는 이유만으로 verifier와 fixture를 추가하지 않습니다. 사람이
판단하는 통제가 반복해서 같은 공백을 만들면 사례별 assertion이 아니라 owner·interface와
독립 oracle을 다시 설계합니다.

## 결정 5. 요청 해석 책임은 현재 Agent가 직접 소유합니다

### 배경

명시적 회고 명령을 질문으로 축소한 뒤, 이 실패를 현재 Agent의 판정 오류가 아니라 외부
enforcement 부재로 재분류했습니다. 그 결과 모든 요청에 별도 reviewer를 강제하고,
dispatcher·hook과 `/execute` 문법이 없으면 작업을 진행할 수 없다고 판단했습니다.

그러나 reviewer는 현재 원문의 명시적 동사를 대신 읽는 실행 주체가 아닙니다. 기본
판정 책임을 외부 gate에 넘기면 단순한 질문과 실행도 추가 시스템이 없다는 이유로
막히고, 판정 실패가 생길 때마다 더 많은 enforcement를 추가하는 순환이 생깁니다.

### 선택

모든 사용자 요청의 기본 전이는 다음과 같습니다.

```text
현재 사용자 원문 source lock
→ 현재 Agent의 원자별 문장 기능·결과·권한 직접 판정
→ 명시적 실행은 실행, 질문은 답변, 실제 미확정 값만 질문
→ 결과 종류와 위험에 따라 domain 전문 검증
```

현재 Agent는 명시된 동사와 직접 선행 문맥을 기준으로 실행과 질문을 구분합니다.
`회고해`, `수정해`처럼 대상과 결과가 확정된 실행 동사는 해당 범위를 실행합니다.
`왜 그랬어?`, `무엇을 수정해야 해?` 같은 질문은 답만 합니다. `진행해`처럼 지시어가
직접 선행 문맥에서 exact 대상을 하나로 고정하면 계속하고, 그렇지 않을 때만 그 대상만
질문합니다.

fresh 독립 검토는 계획, 개발 후보, 중대한 문서처럼 결과의 오류 비용과 전문 검증 축이
있는 경우에 관련 domain workflow가 적용합니다. specialist는 동결 산출물을 검증하며,
사용자에게 없는 실행 권한을 만들거나 현재 문장의 기능을 바꾸지 않습니다. 회고 통제
후보의 행동 효과와 완료 판정은 일반 요청 admission이 아니라 결정 17의 별도 closure
경계에서 작성자와 분리합니다.

회고 진입점은 `회고해`와 `왜 그랬는지 회고해`를 실행 mode로, `왜 그랬는지 설명해`를
읽기 전용으로 mapping합니다. 회고 대상 여부와 기존 회고의 원인·보고 형식·완결성 질문도
retrospective domain의 읽기 전용 검토로 routing하며, 질문 기능을 skill 미호출 근거로
사용하지 않습니다. 일반 회고가 심화 회고로 전환돼도 원문에서 확정한 실행 mode를
보존합니다.

### 근거와 영향

명시된 사용자 동사를 현재 Agent가 직접 처리하므로 외부 시스템이 없다는 이유로 정상
요청을 막지 않습니다. 검토가 필요한 고위험 산출물에는 기존 specialist를 선택적으로
사용해 오류 비용을 줄이되, 요청 해석 책임과 결과 검증 책임을 혼동하지 않습니다.

이 선택은 짧은 질문과 실행의 latency·false block을 줄이고, 판정 실패마다 새 도구와
blocker를 추가하는 과잉 통제를 차단합니다.

### 배제 대안과 재검토 조건

모든 사용자 요청에 공통 admission·final을 강제하는 방식, 외부 lifecycle dispatcher,
project hook과 별도 `/execute` 문법은 기본 판정 책임을 대체하고 정상 요청까지 막으므로
배제합니다. 이 배제는 회고 통제를 완료로 인증하는 판정까지 작성자에게 맡긴다는 뜻이
아닙니다. 자연어 사례를 영구 fixture·custom verifier로 계속 추가하는 방식도 같은 책임
전가를 반복하므로 배제합니다.

runtime이 자연어 해석과 독립된 구조화된 권한 신호를 제공하고 사용자가 그 workflow를
명시적으로 원할 때만 기계적 정책 경계를 별도 도구 도입으로 재검토합니다. 그 경우에도
구조화된 권한 enforcement와 현재 문장의 의미 판정은 서로 대체하지 않습니다.

## 결정 6. 대표 단위 뒤에만 독립 write set을 병렬화합니다

### 배경

전면 개편을 모두 직렬화하면 시간이 불필요하게 늘고, 공통 계약과 consumer를 동시에
병렬화하면 서로 다른 기준으로 구현됩니다.

### 선택

공통 owner topology와 workflow/100을 먼저 고정하고, reporting workflow와
`report-task`를 첫 end-to-end 대표 단위로 구현·검증합니다. 그 뒤
`SKILL.md + openai.yaml + 직접 reference·script`를 한 원자 write set으로 묶어
입력·수용 기준·증거와 파일이 독립적인 문서, 개발·계획, Git·worktree 축만
병렬화합니다.

공유 router·work item과 통합 final audit은 writer가 없는 동결 snapshot에서
직렬로 처리합니다.

### 근거와 영향

대표 단위가 owner 경계의 손실을 먼저 드러내고, 이후 병렬 작업은 같은 계약을
소비하므로 속도와 일관성을 함께 확보합니다.

### 배제 대안과 재검토 조건

파일이 다르다는 이유만으로 producer·consumer를 병렬화하거나 모든 mutation을
직렬화하는 극단을 모두 배제합니다. 병렬 결과가 공유 계약을 다시 열면 그 묶음을
무효화하고 직렬 단계로 돌아갑니다.

## 결정 7. 설명은 하나의 최소 인과 모델을 사용합니다

### 배경

기술 설명 실패를 option·식별자·권한·SHA·검증 깊이 등으로 나눠 선수 조건을 계속
추가했습니다. 설명은 길어졌지만 초심자가 결론까지 가는 첫 연결은 계속 빠졌습니다.

### 선택

`explain-tech`는 모든 비자명한 설명을 다음 모델로 조사합니다.

```text
출처·생산자 → 값·artifact → 필요한 해석·변환·판정
→ 소비자의 실제 행동 → 관찰 결과와 사용자 판단
```

답변에는 질문에 필요한 연결만 노출합니다. 단순 정의는 직접 답과 concrete 사례
하나로 끝낼 수 있고, 여러 값·actor가 섞일 때만 경로를 펼칩니다. code, history와
comparison reference는 이 core를 대체하지 않고 유형별 상세만 소유합니다.

상세 reference의 source·분류 field는 내부 근거이며 답변 목차가 아닙니다. 특히 option
글자의 뜻·이유·기억법을 묻는 질문은 `표기 → 근거 있는 기억용 의미 → 실제 동작`을 첫
결론으로 사용합니다. short·long 분류와 공식 유래 확인은 이 연결의 근거 수준을 정하지만,
질문하지 않은 형식 구분이 사용자의 이해 목표보다 앞서지 않습니다. 공식 어원이 확인되지
않아도 공식 동작이 기억용 의미를 뒷받침하면 둘을 분리해 설명하고, 확인 불가 판정으로
기억 연결 자체를 생략하지 않습니다.

사용자가 선택한 span은 직접 설명 대상이며 이를 포함한 line·block은 context입니다.
정체를 묻는 질문은 enclosing 흐름보다 먼저 span 전체의 문법·artifact 종류와 최초
해석자를 분류합니다. span이 여러 token의 복합 문법이면 전체 구조를 먼저 정의한 뒤
구성요소를 나누고, 문법 owner와 내부 이름·값의 계약 owner, 전달 actor와 최종 consumer를
분리합니다. 실행 context를 만드는 span은 parse·분류, 값 확장·입력 준비, consumer 호출,
consumer 사용의 실제 phase 순서를 보존합니다. line-by-line은 이 국소 연결이 닫힌 뒤에만
enclosing context로 확장합니다.

`parser`, `producer`, `consumer`는 component의 고정 분류가 아니라 특정 artifact에 대한
관계입니다. 관계 설명은 actor, 직접 행위, 직접 대상·전달 artifact, 상대 actor와 phase를
같이 결속하며 source의 정의 위치를 호출·전달 방향으로 바꾸지 않습니다. 이전 설명의
모호한 관계를 사용자가 되물으면 주변 source에 맞는 새 의미로 구제하지 않고, 실제 관계가
없으면 원문을 폐기한 뒤 검증된 독립 동작이나 전달 경로로 교체합니다.

commit hash·과거 diff·선택 line은 focal snapshot을 지정할 뿐 현재 시간축을 제외하지
않습니다. 현재 축은 사용자가 당시만 보라고 명시했을 때만 닫고, 그 밖에는 현재 판정·현재
구현, snapshot의 지속 기여와 당시 한계별 현재 처분을 상세보다 먼저 연결합니다. 후속
수정·교체·제거가 있으면 exact handoff source를 표시하며, 현재 상태 확인을 사용자의 별도
질문으로 넘기지 않습니다.
첫 문단만으로 현재 문제 여부·지속 기여·handoff를 답할 수 있어야 snapshot code와 당시
한계의 상세를 시작할 수 있습니다. snapshot 명칭이나 line의 정정은 이 admission을
대체하지 않습니다.

관찰 가능한 화면·첨부·live 출력의 literal·위치·선택 상태는 해당 snapshot이 소유하고,
외부 공식 자료는 동작·계약을 소유합니다. 둘의 용어가 다르면 사용자 표면의 표현을 먼저
사용해 mapping을 명시합니다. 하나의 질문이 표시 option 설명과 추천처럼 여러 유형을
함께 요구하면 적용되는 reference를 모두 읽으며, 한 source나 reference가 다른 역할을
대신하지 않습니다. 이전 Agent가 만든 용어를 사용자가 되묻는 것은 독립 관찰이 아니며,
exact literal은 판독 가능한 관찰 표면이나 사용자의 명시적 관찰에서만 확정합니다. 긴
screenshot은 대상 영역을 읽을 수 있는 크기로 별도 확인하고, 전체 화면을 열었다는
사실만으로 exact text 검증을 대신하지 않습니다. 후보 literal의 최초 출처, 읽을 수 있는
target 증거와 확정 상태를 답변 전 gate로 결속해, crop 미실행과 실패한 OCR이 current
literal claim으로 넘어가지 못하게 합니다.

다음 작업이나 구축 순서를 추천할 때는 마지막으로 본 단위가 아니라 상위 결과와 현재
확정 상태에서 출발합니다. 상위 결과의 필수 능력과 의존성 전체를 source에서 저상세로
찾아 지금 필요·나중에 필요·조건부·해당 없음·미확정으로 판정한 뒤 한 단위를 고릅니다.
답변에는 이 상태 지도를 짧게 제시하고 다음 한 단위만 상세화합니다. `한 단계씩`이라는
실행·답변 단위가 발견 범위를 줄이지는 않습니다.

사용자가 `선택지`나 `추천`을 말하지 않았어도 흐름 설명이 미확정 actor·실행 위치·상태
기준·artifact 형식·전달 경로·반복 운영 주체를 하나 고르면 비교 원자를 함께 엽니다.
비교는 같은 시작·종료 상태의 구조 후보군을 먼저 닫고 초기 1회·반복 운영·목표 상태를
분리합니다. 독립 선택 축과 추천의 미충족 전제는 따로 닫으며, 사용자가 선택지 누락이나
추천의 보편성을 지적하면 직전 후보를 보충하지 않고 원래 비교 단위와 추천을 한 번에
재구성합니다.

### 근거와 영향

새 제품명이 등장해도 같은 인과와 의존 경계를 사용하며, 필요하지 않은 edge case와 긴
표를 강제하지 않습니다. 선택 문장·line-by-line·현재 사용 여부 같은 전문 결과는 기존
reference에서 유지합니다.

### 배제 대안과 재검토 조건

사례별 설명 checklist와 모든 질문에 고정된 여섯 항목을 노출하는 방식은 각각
append-only와 과잉 설명을 만들어 배제합니다. 새 설명 실패가 생기면 새 용어 규칙을
추가하기 전에 core 인과의 어떤 전이가 호출되지 않았는지 확인합니다. 제품별 화면 label
catalog나 공식 문서와 UI 번역표는 version drift와 중복 owner를 만들므로 두지 않습니다.
정상적인 절차 설명까지 선택지 전수조사로 확대하지 않으며, source와 사용자가 경로를 이미
확정했거나 결론을 바꾸는 대안이 없으면 직접 흐름만 설명합니다.

## 결정 8. 현재 규칙·결정·사건·Git 이력을 분리합니다

### 배경

work item history가 923줄의 사건별 통제와 과거 상태 전이를 누적해 현재 skill과
경쟁하는 stale rule owner가 됐습니다. 기존 `Done`은 후속 동일 범위 실패에도
재개되지 않았습니다.

### 선택

- 현재 반복 규칙은 canonical workflow·skill만 소유합니다.
- design은 선택, 근거, 영향, 배제 대안과 재검토 조건만 보존합니다.
- history는 Git으로 복원할 수 없는 사건·영향·시스템 원인·실패한 방어·남은 위험만
  보존합니다.
- 작업 순서, 개별 patch·검증·commit과 폐기된 상태 전이는 Git이 소유합니다.
- 원래 완료 범위의 누락이나 방어 실패는 같은 work item을 `In Progress`로 재개합니다.

### 근거와 영향

현재 규칙 owner가 하나가 되고 history가 새 규칙을 우회해 호출되는 문제를 없앱니다.
완료 상태도 선언이 아니라 원래 범위와 행동 검증에 연결됩니다.

### 배제 대안과 재검토 조건

사건별 changelog를 영구 보존하거나 완료 결함을 새 후속 work item으로 넘기는 방식을
배제합니다. history가 다시 현재 절차 전문이나 commit 목록을 소유하면 Git·SOT로
환원하고 문서를 축소합니다.

## 결정 9. 단계별 상호작용과 실행 결과 범위를 분리합니다

### 배경

상위 결과의 필수 능력 전체를 먼저 찾는 통제는 다음 작업 추천 경로에 적용됐지만, 짧은
후속 승인이 실제 mutation으로 전환되는 경로에서는 마지막으로 논의한 하위 항목이 전체
실행 단위로 축소됐습니다. 사용자는 단계별로 설명받으려 했지만 Agent는 이를 단계별로
조사하고 일부만 구현해도 된다는 범위 변경으로 사용했습니다.

### 선택

- `한 단계씩`, `하나씩 논의`는 설명과 승인 노출 단위만 제한합니다.
- 첫 mutation 전에 상위 결과의 의존 경계와 완료 조건을 끝까지 조사하고, 필수 단위의
  각 항목을 근거로 유일하게 닫히는 Agent 판단, 실행 중 관찰할 환경 분기, 의미·비용·
  위험·외부 효과를 바꾸는 사용자 소유 결정으로 분류합니다.
- Agent 판단과 안전한 결과가 하나인 환경 분기는 구현·검증하고 선택 이유를 보고하거나
  canonical 문서에 남깁니다. 완결성 전달을 위해 이를 선택지나 승인 질문으로 만들지
  않습니다.
- `논의`는 둘 이상의 유효한 결과가 남은 사용자 소유 결정을 해결할 때만 열고, 그런
  결정이 0개면 질문 없이 실행합니다. 필요한 결정만 한 번에 모아 닫습니다.
- 답변 뒤에는 승인된 상위 결과의 안전한 단위를 모두 실행하며 임의의 하위 결과에서
  사용자에게 다음 작업을 되묻지 않습니다.
- 짧은 후속 승인도 `$develop` 진입점으로 처리하고 Bootstrap에서 동결된 상위 결과와
  남은 단위를 복원합니다. 이 trigger는 개발 변경에만 적용하고 문서·Git 등 다른
  mutation은 원래 전문 owner로 돌려보냅니다.

### 근거와 영향

사용자가 한 번에 받아야 하는 상세량은 제한하면서도 Agent의 discovery와 실행 책임은
보존됩니다. 결정이 아닌 필수 기술 항목은 보고·문서로 빠짐없이 전달하고, 실제 사용자
결정만 상호작용으로 올리므로 활동을 보여 주기 위한 질문과 임의의 부분 완료가 줄어듭니다.
새 결정이 실행 중 생겨도 안전한 작업을 계속한 뒤 실제로 남은 결과를 막는 입력만 다음
라운드에 모읍니다.

### 배제 대안과 재검토 조건

`진행해`, `ㄱㄱ` 같은 표현 목록을 늘리는 사례별 규칙과 모든 요청에 별도 dispatcher를
강제하는 방식을 배제합니다. 후속 승인에서 상위 결과가 다시 소실되거나 정상적인 작은
수정까지 불필요한 질문 라운드로 막히면 trigger·실행 라운드 경계를 함께 재검토합니다.

## 결정 10. 작업 이의 제기는 전체 상태 owner로 복귀합니다

### 배경

부분 구현 뒤 사용자가 현재 행동을 이의 제기했지만 Agent는 새 Bootstrap에서 상위 결과와
artifact를 복원하지 않았습니다. 대신 자신이 추측한 잘못과 직전 하위 행동을 여러 답변으로
나눠, 사용자가 작업 전체의 실제 상태와 단절을 직접 조립해야 했습니다.

### 선택

- 진행 중인 작업의 주체·범위·현재 행동에 대한 이의 제기와 짧은 상태 확인은
  `$report-task` 진입점입니다.
- 답변 전 pending mutation을 멈추고 Bootstrap에서 상위 결과, 완료·미완료 단위, 실제
  artifact·검증·Git 상태를 복원합니다.
- 첫 답변에서 전체 판정과 현재 행동 이유를 한 projection으로 연결합니다. 원인 회고나
  기술 설명이 별도로 필요하면 이 상태를 고정한 뒤 해당 owner로 인계합니다.

### 근거와 영향

사용자는 상태 조각을 질문으로 하나씩 끌어내지 않고 한 번에 범위·진척·남은 책임을
판단할 수 있습니다. 이의 제기가 mutation 승인이나 새 상태 판정을 만들지는 않습니다.

### 배제 대안과 재검토 조건

특정 감탄사·질문 표현 목록과 모든 답변에 긴 상태 보고를 강제하는 방식은 배제합니다.
정상 기술 질문을 상태 보고로 오라우팅하거나 이의 제기 뒤 전체 상태가 다시 분절되면
`report-task` trigger와 Bootstrap 인계를 함께 재검토합니다.

## 결정 11. 도구 승인 결과와 사용자 의미 권한을 분리합니다

### 배경

명령형 실행 회고가 통제 변경의 local commit까지 허용하고 회고 closure도 commit 완료를
요구했지만, commit 명령의 sandbox approval이 거절되자 Agent는 이를 실행 경로 제한이
아니라 사용자 commit 권한 부재로 재해석했습니다. 이미 확정된 같은 승인을 사용자에게
다시 요구해 원래 실행 회고를 임의로 중단했습니다.

### 선택

- 사용자 원문과 workflow가 판정한 의미 권한은 operation map의 유일한 source입니다.
- sandbox·tool approval의 허용·거절은 해당 경로의 실행 가능성 증거이며, 기존 권한을
  생성·취소·대체하지 않습니다.
- 유효한 행동이 거절되면 허용 범위 안의 더 안전한 경로를 우선 사용하고, 없을 때는
  공통 차단 gate에서 거절 주체·막힌 효과·재개 조건을 보고합니다.
- 새 효과나 사용자만 제공할 수 있는 입력이 생기지 않았다면 같은 승인을 다시 묻지
  않습니다.

### 근거와 영향

도구 정책은 실제 실행 가능성을 통제하면서도 사용자 요청의 의미 owner가 되지 않습니다.
실행 실패가 권한 질문으로 변형되지 않으므로 승인 반복과 허위 사용자 책임을 막고,
실제 환경 차단은 정확한 blocker로 남길 수 있습니다.

### 배제 대안과 재검토 조건

특정 sandbox 문구, 승인 reviewer 종류와 명령별 예외 목록을 추가하지 않습니다. 승인
거절 뒤 operation map이 다시 소실되거나, 반대로 권한 없는 Git 효과를 도구 허용만으로
실행하면 workflow와 Git adapter의 경계를 함께 재검토합니다.

## 결정 12. 상시 예외를 실행 경계까지 투영합니다

### 배경

재발 통제 구조를 단일화한 `6aeadab`은 실행 회고의 local commit 계약을 회고 skill의
description과 UI prompt에서 제거하고 공통 workflow의 상시 예외로 옮겼습니다. canonical
owner는 유지됐지만 실제 Git·approval 호출에는 사용자 원문과 예외의 결합 근거가 전달되지
않았습니다. `2668a60`은 도구 거절 뒤 operation map을 보존하라고 보강했지만, 외부 gate가
그 map을 받는 실제 호출 경로는 검증하지 않았습니다.

### 선택

- 상시 예외의 권한 owner는 공통 workflow 한 곳에 유지합니다.
- 예외를 선택하는 즉시 사용자 원문 원자, 예외, exact 효과·path와 제외 효과를 operation
  map에 결속하고 실제 adapter·approval 호출까지 운반합니다.
- 일반·심화 회고 skill의 metadata와 body는 실행 회고의 전체 결과가 통제 반영·행동
  검증·통제 변경만의 local commit까지임을 투영하되, 독립 권한 규칙을 만들지 않습니다.
- Git adapter는 일반 승인 문구가 아니라 원문 원자와 직접 Git 요청 또는 상시 예외를
  호출 증거로 전달합니다.
- 이 근거를 받은 외부 gate가 거절하면 실제 환경 blocker로 남기며, 이미 충족한 사용자
  의미 권한을 다시 요구하지 않습니다.

### 근거와 영향

canonical source를 중복하지 않으면서도 권한을 소비하는 경계가 필요한 입력을 직접
받습니다. `회고해`의 결과 계약이 분석·문서 변경 뒤 Git 단계에서 잘리지 않고, 읽기 전용
회고나 사건 artifact 복구·push로 확대되지 않습니다.

### 배제 대안과 재검토 조건

tool reviewer별 문구 목록, commit 자동화 helper와 skill 자체의 별도 권한 판정기를 만들지
않습니다. exact 근거가 실제 호출에 전달됐는데도 같은 실행 회고가 사용자 commit 권한
부재로 재분류되거나, 반대로 읽기 전용 요청이 commit되면 workflow와 환경 admission의
계약을 다시 검토합니다.

## 결정 13. 완결성 판정은 결과 경로를 먼저 닫습니다

### 배경

기술 설명은 현재 구현·wiring·caller·test를 `답을 바꾸는 범위`까지 추적하도록 했지만,
무엇이 답을 바꾸는지는 이미 만든 후보 답에 의존했습니다. 사용자가 두 항목이 전부인지
물었을 때 Agent는 그 두 이름만 설명한 뒤 `딱 두 개`라고 확정했고, 같은 script의
preflight·postcondition이 검사하는 독립 경로는 사용자가 코드를 제시한 뒤에야 찾았습니다.

### 선택

- `전부`, `오직`, `딱 N개`, `다른 경로 없음`을 닫힌 집합 주장으로 분류합니다.
- 닫힌 집합의 universe는 사용자가 먼저 열거한 이름이 아니라 그 항목들이 함께 만드는
  결과·postcondition에서 정합니다.
- 현재 값을 바꾸는 경로, 다음 실행에서 다시 만드는 persistent source와 성공을 거부하는
  검증 경로를 caller·branch·precondition·postcondition에서 찾고 역할 차이를 보존합니다.
- 사용자가 상세를 일부 항목에 한정해도 결론을 바꾸는 나머지 경로는 존재와 역할만 먼저
  밝힌 뒤 요청한 상세로 복귀합니다.
- 경계를 닫지 못하면 확인한 후보와 미확인 범위를 말하고 exact 개수를 주장하지 않습니다.

### 근거와 영향

source 탐색 범위가 후보 답에 순환 의존하지 않고 사용자가 판단하려는 관찰 결과에서
시작합니다. 설명 분량은 요청대로 좁게 유지하면서도, 사용자가 반례 코드를 찾아온 뒤에야
완결성 결론이 바뀌는 일을 막습니다.

### 배제 대안과 재검토 조건

swap, systemd나 특정 개수별 규칙과 전체 repository 전수조사를 강제하지 않습니다.
공통 결과가 하나로 닫히는 현재 artifact의 관련 경로만 추적합니다. 이 통제가 작은 정의
질문까지 불필요하게 확장하거나, 다른 이름의 독립 경로가 다시 사용자의 지적 뒤에만
발견되면 universe·invocation·oracle을 함께 재검토합니다.

## 결정 14. 남은 작업은 소비 단계가 증명한 edge로만 연결합니다

### 배경

상위 결과의 필수 능력 전체를 찾는 통제가 적용된 뒤에도 Agent가 클러스터 bootstrap과
애플리케이션 배포 작업을 하나의 실행 순서로 직렬화했습니다. 클러스터 실행 결과는 manifest
적용과 통합 검증에는 필요하지만 image·manifest·CI/CD의 설계와 작성에는 필요하지
않았습니다. 해야 할 항목의 coverage는 넓어졌지만 항목 사이의 관계 종류와 소비 시점은
검증되지 않았습니다.

### 선택

- `A가 B보다 먼저다`라는 edge는 B의 exact 단계가 A의 결과를 실제 입력으로 소비할 때만
  허용하고, producer 결과와 처음 막히는 consumer 단계를 함께 밝힙니다.
- 필수 선행조건, 독립, 합류 시 필요, 권장 순서와 리마인더를 분리합니다. 권장 순서는
  실행 편의의 추천이며 다른 트랙의 진입조건이 아닙니다.
- 각 트랙은 지금 시작 가능 여부, 처음 막히는 단계와 늦어도 필요한 시점을 가집니다.
  둘 이상의 독립 트랙은 하나의 번호 목록으로 직렬화하지 않고 최초 합류점을 표시합니다.
- 후반 적용·실행·통합 검증의 전제를 앞선 설계·작성·제작 단계로 소급하지 않습니다.
  사용자가 순서의 필연성을 이의 제기하면 직전 문장이 아니라 의존 지도 전체를 다시
  판정합니다.

### 근거와 영향

항목을 빠짐없이 나열하는 것과 항목 사이에 필수 순서를 만드는 것은 다른 판정입니다.
consumer 기반 edge는 실제 차단 지점만 보존하면서 외부 환경을 기다리지 않고 진행할 수
있는 저장소 설계·작성 작업을 드러냅니다. 리마인더도 `지금 먼저`가 아니라 실제 합류점
전의 완료 기한으로 전달할 수 있습니다.

### 배제 대안과 재검토 조건

Kubernetes·배포처럼 제품별 단계 순서를 저장하거나 모든 작업에 고정된 phase taxonomy를
강제하지 않습니다. 단순한 단일 선행관계에는 불필요한 트랙 표를 요구하지 않습니다. 다른
도메인에서도 후반 consumer의 전제가 앞선 독립 작업까지 차단하거나, 반대로 실제 hard
prerequisite를 권장 순서로 완화하면 edge 판정과 false-block 경계를 함께 재검토합니다.

## 결정 15. 스크립트 배치는 사용 생애주기가 먼저 소유합니다

### 배경

스크립트 계약에는 caller와 실행 시점이 있었지만 디렉터리 규칙은 `scripts/<영역>/`만
요구했습니다. 그 결과 최초 노드 구성, 반복 점검과 운영 변경이 `ecr`, `gitops` 같은 기술
이름 아래 함께 놓였고, 독자는 각 파일을 열어야 사용 시점을 알 수 있었습니다. 설명
경로에는 초기 1회·반복 운영을 구분하는 통제가 있었지만 스크립트의 canonical 배치 규칙과
연결되지 않아 설치 순서를 사용 생애주기로 잘못 해석할 수 있었습니다.

### 선택

- 최상위 경로는 `bootstrap`, `operations`, `verify`, `quality` 중 caller·실행 시점·대상
  상태·반복 여부가 결정하는 하나의 생애주기를 표현합니다.
- 기술 영역은 같은 생애주기 안에서 구분이 필요할 때만 두 번째 축으로 둡니다.
- 둘 이상의 생애주기를 함께 수행하는 executable은 경로를 타협하지 않고 관찰 결과,
  실패·재실행과 side effect 경계로 분리합니다.
- 설명 skill은 저장소 script의 역할·배치 질문에서 일반 비교 규칙만 사용하지 않고 도구
  생애주기와 스크립트 엔지니어링 가이드를 canonical source로 읽습니다.

### 근거와 영향

경로만으로 최초 구축용인지, 반복 운영 변경인지, 점검인지 구분할 수 있고 기술별 탐색도
하위 디렉터리에서 유지됩니다. 구현과 설명이 같은 owner를 소비하므로 서로 다른 규칙이
같은 script를 다르게 분류하지 않습니다. `bootstrap`은 전체 순서의 앞부분이 아니라 target
초기 상태를 만드는 사용 계약이므로 Kubernetes 뒤에 설치되는 ECR credential provider도
초기 구축 책임이면 같은 생애주기에 놓입니다.

### 배제 대안과 재검토 조건

기술 이름만 최상위에 두고 README에서 사용 시점을 설명하는 방식은 탐색 전에 파일을 읽게
하므로 배제합니다. 모든 one-shot 작업을 bootstrap으로 부르거나 번호만으로 생애주기를
표현하지 않습니다. 같은 경로에 서로 다른 caller·반복 계약이 다시 섞이거나 기술 하위
분류가 불필요하게 깊어지면 분류 축과 분할 경계를 함께 재검토합니다.

## 결정 16. 활성 결정 기준선은 모든 소비 경계가 공유합니다

### 배경

확정 결정을 문서 재작성에서 보존하는 상세 delta gate는 계획 편집 경계에 있었지만, 같은
결정을 구현하는 첫 mutation, 그 결정을 전제로 흐름을 설명하는 경계와 현재 결정 상태를
답하는 경계에는 연결되지 않았습니다. 공통 workflow의 의미 authority는 명시적으로 바꾼
차원만 갱신하라고 했지만, consumer가 활성 값과 뒤의 대체 source를 실제로 복원하는 입력
형식과 호출 시점을 소유하지 않았습니다. 그 결과 후발 Agent 추천이 구현에서는 사용자
결정을 대체했고, 반대 방향의 상태 답변에서는 활성 결정을 미확정으로 강등했습니다.

### 선택

- 모든 결정 consumer는 `결정 차원 → 활성 값 → 권위·근거 source → 최신 명시 대체 source
  또는 없음 → 유지·갱신·미확정` 기준선을 먼저 복원합니다.
- 상태 변경의 첫 mutation, 선행 선택을 전제로 한 흐름 설명과 `무엇을 정했는가`를 답하는
  첫 실질 문장, 전문 검증과 Git 인계가 같은 기준선을 소비합니다.
- 제안이나 식별 값을 직접 가리킨 승인은 해당 결정을 선택하지만, 일반 실행 승인은 활성
  결정을 구현할 권한일 뿐 충돌하는 후발 추천의 결정 교체로 사용하지 않습니다.
- 서로 독립적으로 유지·변경할 수 있는 값은 별도 결정 차원으로 나누고, 명시적으로 겨냥한
  값 이외를 복합 제안과 함께 바꾸지 않습니다.
- 이름 붙인 대안을 선택했다는 증거가 없더라도 다른 활성 값이 있으면 그 결정을 먼저
  답합니다. 활성 값이 없는 차원만 후보 비교와 사용자 결정을 다시 엽니다.
- reviewer는 원래 기준선과 유효한 변경 source를 독립 대조하며, 같은 작업에서 만든 test와
  문서의 내부 일치를 원래 결정의 oracle로 사용하지 않습니다.

### 근거와 영향

계획·구현·설명·검증이 서로 다른 기억이나 후발 제안을 기준선으로 쓰지 않습니다. 결정
resolver가 먼저 확정·미확정을 판정하므로, 기존 비교 통제는 실제 미확정 차원에서만
호출되고 이미 닫힌 결정을 다시 여는 false-block도 줄어듭니다. 구현과 상태 답변의 실패가
반대 방향이어도 `명시 대체 source 없이 활성 값을 변경했다`는 같은 위반으로 차단됩니다.

### 배제 대안과 재검토 조건

GitOps 저장소처럼 사건의 명사를 금지 목록으로 만들거나 별도 decision registry·JSON·
자기기입 verifier를 추가하지 않습니다. 계획 편집 전용 gate나 설명의 비교 trigger만으로
다른 consumer를 대신하지도 않습니다. 명시적인 사용자 대체 결정을 막거나, 선행 결정이
없는 신규 추천까지 기준선이 있는 것처럼 처리하면 authority와 false-block 경계를 함께
재검토합니다.

## 결정 17. 회고 통제의 완료 판정은 작성자와 분리합니다

### 배경

기존 행동 검증은 진단·기대 답을 숨긴 fresh context에서 새 답변을 생성했지만, 통제
작성자가 사례와 held-out 기대를 고르고 그 결과의 합격과 `Done`까지 판정할 수 있었습니다.
fresh context는 생성 상태를 분리했을 뿐 독립 evaluator를 만들지 않았고, 실제 routing에서
reviewer 호출을 같은 Agent가 건너뛸 수 있는 fail-open 경계도 남았습니다. 모든 일반 요청의
admission·final을 외부 gate에 넘긴 과잉 통제를 제거하면서, 회고 통제의 closure에만 필요한
독립 판정까지 함께 제거한 것이 반복 복구의 원인이 됐습니다.

### 선택

일반 요청의 문장 기능·권한은 결정 5처럼 현재 Agent가 직접 판정합니다. 회고가 통제를
유지·변경하고 효과·완료를 주장하는 순간에만 다음 역할을 분리합니다.

```text
통제 작성자: 후보와 raw 사건·source pool 동결, 최종 합격 판정 금지
→ 독립 evaluator: 후보 결과를 보기 전 case·oracle·verdict 기준 동결
→ fresh runner: 후보와 raw 입력·source로 실행, 기대 결과는 받지 않음
→ 독립 evaluator: 사전 기준으로 통과·실패·검증 불가 판정
→ 공통 closure: evaluator verdict와 실제 invocation 증거로만 상태 전이
```

검증은 후보를 명시적으로 제공한 isolated 통제 효과와, 통제 이름·작성자 진단을 주입하지
않은 실제 선행 문맥·후속 표현의 routing 효과를 분리합니다. evaluator가 없거나 작성자·
runner와 분리되지 않았거나, 기준이 실행 뒤 바뀌거나, 판정이 갈리거나, 후보가 판정 뒤
바뀌면 `검증 불가`입니다. 작성자는 실패·검증 불가를 통과로 바꾸지 못합니다.

closure에는 후보 식별자, raw 사례 provenance, runner·evaluator 역할, 사례별 verdict와
남은 위험을 연결합니다. 대화 원문·내부 추론·실행 로그를 repository에 복사하지 않고,
현재 상태 문서에는 재사용할 판정과 미완료 책임만 정규화합니다. Repository 지시만 있고
현재 실행에서 evaluator를 실제 호출하지 않았다면 `검증 불가`입니다. 반대로 현재 Agent가
작성자·runner와 분리된 evaluator의 사전 기준·fresh 실행·최종 verdict와 실제 invocation
증거를 확보했다면 그 범위의 closure 입력으로 사용합니다. Runtime이 호출을 강제하지 않는
사실은 남은 fail-open 위험이지만 현재 결과의 외부 blocker나 수동 verdict 무효 조건으로
승격하지 않습니다.

같은 불변식이 이 closure 뒤 다시 실패하면 이전 verdict와 `Done`뿐 아니라 사례를 추가해
통과시키던 검증 전략 전체를 무효화합니다. 누적된 사용자 정정과 시간 비용은 새 사건마다
초기화하지 않고 기존 전략의 직접 영향으로 취급합니다.

### 근거와 영향

요청 해석 책임을 reviewer에게 넘기지 않으면서도, 통제를 만든 주체가 자신의 효과를
인증하는 순환을 끊습니다. 추가 latency와 fresh 역할은 회고 통제의 closure에만 발생하고
일반 질문·실행에는 적용되지 않습니다. 같은 model family의 correlated error와 fail-open
runtime 위험은 남으므로 둘을 독립성의 보증으로 과장하지 않습니다.

### 배제 대안과 재검토 조건

모든 요청의 공통 admission, 영구 자연어 fixture catalog, 작성자가 채우는 receipt와
형식 validator를 독립 효과 증거로 사용하는 방식을 배제합니다. runtime이 회고 closure에
non-bypassable evaluator verdict를 구조화된 상태로 제공하면 invocation 보증 owner를 그
경계로 이관합니다. 분리된 evaluator도 같은 실패를 통과시키면 같은 사례를 더하지 않고
evaluator 다양성·외부 관측과 전체 validation topology를 다시 설계합니다.

## 결정 18. 첫 target mismatch는 현재 결과의 관련 외부 계약을 다시 엽니다

### 배경

스크립트 변경 전 downstream consumer와 공식 계약을 조사하고 질문을 한 번에 모으라는
통제는 이미 있었습니다. 그러나 구현에서는 writer가 예상한 command status·JSONPath·파일
후보 집합을 test double도 그대로 반환해, source와 test가 같은 가정 안에서 일치했습니다.
첫 live 실패 뒤에도 해당 오류만 수정하고 같은 구축 결과의 뒤 단계와 같은 불충분한 oracle을
사용한 관련 guard를 다시 감사하지 않아, 사용자가 재전달·재실행할 때마다 다음 가정이
차례로 실패했습니다.

### 선택

- 외부 interface를 해석하는 guard·classifier·parser는 실제 downstream consumer의 후보
  선택과 의미를 공식 source나 동등한 지원 target에서 먼저 복원합니다. Test는 그 분류
  자체를 실행하며, writer가 고른 후보 집합으로 helper 전체를 대체하지 않습니다.
- 첫 target mismatch는 현재 사용자에게 반환할 상위 결과 또는 runbook handoff의 dependency
  closure에서 같은 불충분한 source·test oracle로 판정한 외부 interface와 local
  target-compatibility 판정을 무효화하는 recovery trigger입니다. 이미 실행된 interface도
  같은 oracle에 의존했다면 제외하지 않습니다.
- Agent는 무효화한 interface와 남은 preflight·mutation·postcondition 경로를 끝까지 조사하고
  source로 닫을 기술 판단을 먼저 모두 처리합니다. 실제 host에서만 알 수 있는 값만 한
  묶음의 비변경 진단으로 요청하며, 설치·적용·수동 재현을 진단으로 바꾸지 않습니다.
- Live evidence 뒤에는 영향받은 script·test·README·runbook과 관련 interface 전체를 한
  후보로 다시 검증합니다. 같은 복구 라운드의 관련 mismatch가 이어지면 국소 patch 전략
  자체를 다시 엽니다.

### 근거와 영향

작성한 fake와 구현의 내부 일치가 실제 target 계약을 대신하지 않습니다. Source로 닫히는
문제를 사용자에게 묻지 않으면서도, 정말 host에만 있는 값은 여러 번 나누지 않고 한 번에
수집합니다. 첫 반례가 같은 결과와 검증 전략을 공유하는 다른 단계에도 정보를 준다는 점을
복구 경계에 반영해 재전달·재실행·부분 수정 라운드를 줄입니다.

### 배제 대안과 재검토 조건

Kubernetes·Calico·systemd의 이름별 예외 목록, 영구 external-interface registry와 새 custom
verifier는 추가하지 않습니다. 무효화 범위를 전체 저장소나 target inventory로 넓히지 않고
현재 반환할 결과의 dependency closure와 같은 불충분한 oracle을 실제로 공유한 interface로
제한합니다. 단순 script의 계약이 이미 공식 source와 지원 target 검증으로 닫혔으면 질문
없이 진행합니다. 실제 host에만 존재하는 값까지 Agent가 추측하거나, 한 진단 묶음이
secret·변경 명령을 포함해야만 성립하면 진단 경계와 실행 위치를 다시 설계합니다.

## 결정 19. 상태 보고 전에 전체 operation map을 동결합니다

### 배경

독립 트랙과 실제 차단을 판정하는 규칙은 이미 있었지만, 상태 보고 진입 뒤 새 다음 행동을
답변 작성 중 감지해 다른 skill로 넘긴다는 자연어 handoff는 실행 경계가 아니었습니다.
반대로 비교 owner가 호출돼도 현재 milestone을 상위 결과의 종료 범위로 사용하면 나중
milestone에서 합류하는 현재 실행 가능 원자를 제거할 수 있었습니다. 다음 행동의 domain
관계보다 먼저 `현재도 상위 결과에 속하는 원자 전체`를 공통 상태로 고정해야 합니다.

### 선택

- 작업 실행 workflow는 사용자에게 승인된 상위 결과의 모든 원자, 권위 source, 상태,
  milestone, consumer·합류점, canonical scope와 현재 응답 scope를 operation map으로 소유합니다.
  `Closed`, canonical 상위 scope에서 명시적으로 취소·제외·대체된 원자 또는 권위 source가
  상위 결과와 무관함을 확정한 원자만 활성 집합에서 빠집니다.
- `이번 답변에서는 제외`처럼 projection만 좁힌 원자는 현재 응답 집합에서만 빼고 canonical
  활성 집합과 상태는 유지합니다. Specialist에는 canonical map에 보존된 상위 결과 관련 원자
  전체와 `Closed`·비활성 상태·증거를 dependency context로, 현재 응답 집합의 활성 원자를
  추천·출력 후보로 따로 전달합니다. 현재 milestone 자체는 두 집합 어느 쪽의 제외 source도
  아닙니다.
- 새 상태는 직접 영향을 받은 원자만 갱신합니다. 현재 milestone이나 직전 consumer는 후속
  milestone의 원자를 제거하는 범위 source가 아니며, specialist의 반환값에서 생략된 원자도
  기존 map에 남습니다.
- `$report-task`는 답변 조립 중 자기 감지로 다른 skill을 호출하지 않습니다. 진입 시점에
  동결 map을 입력받고, canonical exact 다음 행동이 현재 응답 scope에도 있으면 그대로
  투영합니다. 이번 답변에서 제외됐으면 canonical map에만 유지합니다. 현재 scope에 exact
  next가 없고 둘 이상의 활성 원자 또는 열린 관계·추천이 있을 때 explain-tech skill과
  comparison reference를 direct resource edge로 읽고 canonical dependency context와 현재
  응답 후보 집합을 손실 없이 전달합니다.
- 전체 상태 projection 없이 다음 작업·구축 순서·추천 자체만 묻는 질문은 report-task가
  소비하지 않고 explain-tech가 활성 결정과 comparison reference를 읽습니다. Canonical exact
  next가 있으면 reference invocation은 그 결정을 재개방하지 않고 목적·완료 oracle을 설명하는
  경계로만 사용합니다.
- Comparison reference는 받은 원자 사이의 exact consumer·독립·합류·리마인더·추천을
  판정하지만 membership·scope·상태를 다시 정하지 않습니다. 현재 Agent가 반환 관계를 기존
  map에 병합하며, 누락은 삭제가 아닙니다. Dependency context의 `Closed`·비활성 원자는
  충족된 선행 증거·무효·충돌 판정에 사용하되 추천 후보로 되살리지 않습니다. 필요한 결과가
  canonical map에 아예 없을 때에만 comparison 출력으로 membership을 만들지 않고 workflow/100의
  source discovery·membership 단계로 돌아갑니다. 이미 map에 있는 결과는 workflow/100이 기존
  증거의 유효성, `Invalidated` 재개 또는 canonical 제외와 현재 원자의 충돌을 판정합니다.
  현재 응답에서만 제외된 원자는 dependency context에 있으므로 hard edge 판정에는 유지하고
  출력 후보로 복구하지 않습니다.
- `지금 진행할 수 있는 것` 같은 상태 조건은 runnable 추천 후보를 고르지만 같은 명시 범위의
  blocked 원자를 scope에서 제거하지 않습니다. Report-task는 현재 범위 각 원자의 canonical
  source를 실제 읽고 runnable 결과와 직접 대비되는 blocker·재개 조건을 함께 투영합니다.
  대상·상태 설명을 명시적으로 제외한 경우에만 현재 projection에서 생략합니다.
- 완료 및 작업 보고 workflow는 완성된 map을 상세 행동보다 먼저 사용자용 상태로
  투영합니다. 새 추천이 필요하지 않으면 상태만 보고합니다.

### 근거와 영향

Operation membership·상태, domain 관계·추천과 사용자 projection을 서로 다른 owner에
둡니다. 공통 workflow가 전체 operation map과 상태·증거를 보존하므로 specialist 호출이
누락된 원자를 암묵적으로 삭제할 수 없고, report skill은 dependency 판단을 재구현하지
않습니다. 동시에
comparison reference의 direct link와 진입 시점 admission으로 plain `$explain-tech` 문구를
실제 호출처럼 취급하던 fail-open을 제거합니다.

### 배제 대안과 재검토 조건

모든 상태 보고에 전체 로드맵이나 comparison 호출을 강제하지 않습니다. Exact 한 단위만
묻거나 canonical map이 다음 행동을 이미 하나로 확정했다면 그 범위만 답합니다. 사용자가
canonical 상위 scope를 명시적으로 줄였거나 원자가 `Closed`이면 활성 집합에 억지로 남기지
않습니다. 현재 답변만 좁혔다면 그 projection에서는 제외하되 canonical 계획을 삭제하지
않습니다. 실제 hard edge도 병렬화를 위해 완화하지 않습니다.

답변 중 자기 감지 뒤 plain skill tag로 넘기는 topology와 comparison owner에게 operation
membership까지 맡기는 topology는 독립 행동 검증에서 각각 실패해 폐기했습니다. 사례별
출력 field·제품 목록·custom verifier로 보강하지 않습니다. 이 owner 경계도 새 독립 사전
사례에서 실패하면 문구를 더 붙이지 않고 공통 operation map interface를 다시 검토합니다.

## 결정 20. 보상 mechanism의 선행 선택을 구현 전에 다시 엽니다

### 배경

단일 ARM64 image라는 종료 상태만 확정됐는데 구현은 x86 runner를 먼저 선택하고 그 차이를
QEMU로 보상했습니다. Workflow source는 Action SHA를 고정했지만 자체 test는 target output과
trust 계약만 확인했고, QEMU의 필요성·native 대안·Action pin 자체를 선택 oracle로 검증하지
않았습니다. 도구 심사도 그 필요성을 만든 runner 선택을 이미 고정 입력으로 받았습니다.
설명 단계에서 미확정 배포 경로 하나를 먼저 제시한 사건과 구현 단계에서 미확정 runner
경로 하나를 먼저 mutation한 사건은
`미확정 완전 경로를 비교하기 전에 하나를 현재 절차로 승격했다`는 불변식을 공유합니다.

### 선택

- 첫 mutation 전 operation map은 공통 시작·종료 상태를 만족하는 실질 구현 경로를
  포함합니다. 기본 template·현재 code·익숙한 순서는 platform·architecture·format·protocol·
  actor 선택의 권위 source가 아닙니다.
- Tool·adapter·호환 계층이 Agent가 고른 선행 선택과 목표의 차이를 변환·우회·중계한다면,
  그 선행 선택을 다시 열어 보상 계층이 없는 표준 경로와 포함한 경로를 완전한 경로끼리
  비교합니다. 외부 도구의 책임·비용·oracle은 도구 도입 workflow가 계속 소유합니다.
- Authoritative source와 현재 요구가 한 경로를 유일하게 닫으면 Agent가 질문 없이 선택·
  구현·검증하고 이유와 배제한 더 단순한 경로를 기록합니다. 유효한 경로가 둘 이상이며
  비용·성능·위험·외부 효과가 달라질 때만 사용자 결정을 첫 mutation 전에 한 번에 닫습니다.
- 구현·검증·문서와 Git 인계는 같은 선택 기준선을 소비합니다. 완료 보고는 선택한 경로,
  배제한 더 단순한 표준 경로와 근거를 canonical 결정 source에서 가져오며 file·test 목록을
  그 근거로 대신하지 않습니다.

### 근거와 영향

도구 이름에서 심사를 시작하지 않고 도구가 필요해진 upstream premise까지 역추적하므로,
architecture 뒤의 emulator뿐 아니라 runtime 뒤의 transpiler, format 뒤의 converter와
network path 뒤의 proxy에도 같은 경계를 적용합니다. 기술적으로 유일한 판단을 사용자에게
전가하지 않으면서도 material trade-off는 사용자가 모르는 사이 mutation되지 않습니다.

### 배제 대안과 재검토 조건

QEMU 금지 규칙, 제품별 allow-list, 별도 decision registry와 새 custom verifier를 추가하지
않습니다. 이미 경로가 확정됐거나 결론을 바꾸는 대안이 없으면 비교를 다시 열지 않습니다.
반대로 보상 계층을 추가했는데 선행 선택의 권위·배제 경로·근거가 구현과 최종 보고에서
역추적되지 않으면 공통 invocation과 도구 인계를 다시 검토합니다.

## 결정 21. Operation map의 상태는 source coverage를 닫은 증거만 받습니다

### 배경

상태·실행 결과 입력에서 닫힌 원자와 남은 원자를 함께 보존하는 통제는 이미 있었습니다.
그러나 기존 interface는 operation membership과 specialist handoff를 상세히 정의하면서도,
각 원자의 `Closed`·미실행·미증명을 어떤 상태 source와 coverage에서 만드는지는 구분하지
않았습니다. 그 결과 Agent가 workflow를 실제로 읽고도 같은 thread의 실행 이력 대신
runbook·project 문서와 선택적 검색을 먼저 사용하고, 그 source의 누락을 미실행으로
승격할 수 있었습니다.

### 선택

- Operation map의 상태 entry에는 주장, 직접 상태 source, 환경·시점·단계 coverage와 더
  최신인 성공·실패·무효화 증거를 결속합니다.
- 같은 thread의 직접 실행 결과와 현재 live 상태의 Operations Scenario는 해당 환경·시점의
  상태 source입니다. Project SOT·runbook·script README·구현 존재는 구조와 절차 source이며
  live 실행 여부의 negative source가 아닙니다.
- 선택한 source나 검색 범위에서 기록을 찾지 못한 경우는 `미증명`입니다. 전체 관련 실행
  구간을 덮는 직접 상태 source 없이 `미실행`·`아직 안 함`·재실행으로 바꾸지 않습니다.
- 같은 thread의 완료 증거는 더 최신의 직접 실패·삭제·무효화 관찰이 있을 때만 낮춥니다.
  오래된 project 문서나 specialist 반환의 누락으로 상태를 되돌리지 않습니다.
- 최초 생성·일회성 bootstrap·기존 state를 fail-close하는 작업은 미증명 상태에서 실행
  후보로 만들지 않습니다. 접근 가능한 thread와 canonical live owner를 한 번에 대조하고,
  actual 환경에만 증거가 남은 경우에만 read-only 확인을 하나의 묶음으로 요청합니다.
- Workflow·skill을 읽은 trace는 control load 증거일 뿐입니다. 상태·다음 작업의 각 주장이
  상태 증거 entry로 역추적될 때만 operation map invocation으로 인정합니다.

### 근거와 영향

기존 통제가 보호한 operation membership·독립 트랙·specialist relation을 유지하면서 그
upstream producer인 상태 증거 admission을 같은 canonical owner에 연결합니다. 그래서
repository에 실행 기록이 없다는 이유로 live 완료를 되돌리는 경로와, 규칙 파일을 읽기만
하고 실제 map을 만들지 않는 경로를 함께 차단합니다. 동시에 실제 미실행 증거가 있는
정상 다음 작업은 그대로 추천할 수 있습니다.

### 배제 대안과 재검토 조건

제품·script별 완료 목록, 영구 대화 ledger, 새 hook이나 custom verifier를 추가하지
않습니다. 상태를 직접 소유하는 source가 하나로 정해진 작업에는 불필요한 전체 thread
재검색을 강제하지 않습니다. 이 interface를 실제 호출한 뒤에도 source 누락을 negative
fact로 승격하거나 완료 원자를 재실행 후보로 만들면 operation map의 producer·consumer
owner를 다시 검토합니다.

## 결정 22. 과거 원인 attribution은 source의 시간 방향을 먼저 닫습니다

### 배경

현재 상태의 source coverage를 operation map에 결속하는 통제는 적용됐지만, 그 통제는 현재
완료·미완료·다음 작업 판정만 행동 검증했습니다. 이후 과거 Agent 답변의 실제 근거를 묻는
설명에서, 답변 뒤에 생성된 Operations Scenario를 당시 판단의 원인이라고 소급했습니다.
현재 상태를 설명할 수 있는 source와 과거 actor가 실제 소비한 causal input을 같은
evidence entry로 취급한 것입니다.

### 선택

- 과거 답변·판단·행동의 근거·원인을 설명하기 전에 결과 발생 시각, 후보 source의 생성·
  유효·접근 가능 시각과 당시 actor의 실제 read·invocation trace를 결속합니다.
- 결과 뒤에 생성·유효해진 source는 causal input에서 제외하고 후발 관찰·정정 source로만
  분류합니다.
- 현재 artifact의 내용 일치나 존재는 과거 actor가 이를 소비했다는 증거가 아닙니다. 당시
  trace coverage가 닫히지 않으면 원인을 `미증명`으로 남깁니다.
- 공통 workflow가 시간 방향과 증거 admission을 소유하고, explain-tech의 과거 실행·이력
  reference가 이전 답변·판단의 원인 질문에서 이 계약을 첫 실질 문장 전에 호출합니다.

### 근거와 영향

상태 evidence 통제를 버리지 않고, 같은 source coverage 불변식을 역사적 인과 consumer까지
강화합니다. 현재 문서는 현재 상태와 정정에는 계속 사용할 수 있지만 자신보다 앞선 판단의
원인이 될 수 없습니다. 제품명·문서 이름을 바꿔도 `원인보다 늦게 생긴 source는 원인이
아니다`라는 시간 방향으로 같은 오류를 차단합니다.

### 배제 대안과 재검토 조건

특정 Operations Scenario를 금지하거나 대화 전체를 영구 provenance registry로 만들지
않습니다. 모든 짧은 현재 상태 질문에 Git timestamp 조사를 강제하지 않고, 과거 원인·근거를
확정하는 설명에서만 적용합니다. 시간 순서가 맞더라도 실제 소비 trace 없이 인과를 확정하거나,
반대로 사용자 원문처럼 직접 선행 source가 충분한데도 항상 `미증명`으로 막으면 source 역할과
false-block 경계를 함께 재검토합니다.

## 결정 23. 회고 복귀점은 원래 acceptance oracle을 다시 활성화합니다

### 배경

실행 회고는 사건 artifact 복구를 통제 변경 commit에 섞지 않고 원래 작업의 복귀점으로
보존합니다. 기존 operation map과 결과 계약은 미완료 원자·완료 oracle·consumer·복귀점을
보존하고 specialist 결과·후속 gate도 handoff identity에 결속했습니다. 그러나 삽입 원자가
terminal일 때 복귀점을 활성화한 뒤, 이미 보존한 원래 계약을 활성 원자로 복원해 다음
candidate·review·Git가 실제 소비하는지 대조하는 return-consumer edge는 명시하지 않았습니다.
그 결과 통제 회고는 닫혔지만 원래 artifact는 원래 oracle 소비가 미증명인 상태에서도 다시
완료 후보가 될 수 있었습니다.

### 선택

- 기존 operation map·결과 계약·specialist handoff가 보존한 원자와 gate를 유지하고, 복귀
  계약에서 미완료 기대 효과, exact artifact·외부 상태, 원래 acceptance oracle·배제 조건,
  재개 specialist phase와 후속 gate를 한 identity로 복원합니다.
- 통제 변경·행동 검증·commit 완료는 사건 artifact의 완료 증거가 아닙니다.
- 복귀점을 활성화할 때 보존한 계약을 operation map의 활성 원자로 복원하고, 후속 candidate·
  review·Git 인계가 같은 oracle과 specialist gate를 실제 소비해야 합니다.
- 일반적인 `계속 진행`, 관련 work item의 `Done`, 새 후보의 source·link 내부 일관성은 원래
  artifact의 acceptance oracle을 대신하지 않습니다.

### 근거와 배제 대안

회고가 사건 artifact를 함께 고치게 하면 원인 통제와 제품 변경이 한 commit에 섞이고 독립
검증이 무너집니다. 반대로 terminal 뒤 위치로만 돌아가고 기존 oracle의 후속 소비를 대조하지
않으면 같은 artifact를 다시 다룬다는 사실만으로 완료 membership이 복구될 수 있습니다.
따라서 분리는 유지하되 이미 보존한 원래 oracle을 return-consumer handoff에 다시 결속합니다.

제품별 미완료 registry, 회고마다 별도 ledger와 사건 artifact 자동 수정은 추가하지 않습니다.
현재 operation map과 specialist handoff가 원래 계약을 운반합니다. 실제로 이 계약을 소비한
뒤에도 artifact가 좁은 내부 검증으로 완료되면 복귀점 producer·consumer topology를 다시
검토합니다.

## 결정 24. 회고 verdict는 통제 효과와 실제 전이 invocation을 분리합니다

### 배경

기존 독립 행동 검증은 fresh runner와 evaluator를 분리하고 actual routing도 확인하도록
요구했습니다. 그런데 한 runner가 원래 oracle을 writer·fresh final·Git gate에 전달해야 한다고
올바르게 판정한 읽기 전용 결과를, 세 actor가 실제로 호출되고 인계한 invocation `PASS`로
확대할 수 있었습니다. 통제 의미와 실제 생애주기 효과가 하나의 verdict에 합쳐져, 이후
artifact 실패 때마다 새로운 consumer edge가 빠진 것처럼 원인을 다시 만들었습니다.

### 선택

- 모델링된 통제 효과는 동결 후보가 회귀·전이·false-block 입력에서 올바른 판단을 만드는
  범위만 증명합니다.
- 실제 전이 invocation은 주장한 producer·consumer actor, 현재 control·generation·candidate,
  소비한 oracle과 terminal 또는 다음 handoff 효과를 실제 trace에서 관찰한 범위만
  증명합니다.
- Runner 하나의 역할 재현, 미래 phase 설명과 isolated read-only 판정은 actual invocation이
  아닙니다. 관찰하지 않은 전이는 `검증 불가`로 남기고 다른 `PASS`로 보충하지 않습니다.
- 최종 closure와 보고의 범위는 필요한 전이 중 가장 낮은 증거 등급을 넘지 않습니다.
- 실제 다중 phase가 없는 읽기 전용·단일 actor 결과에는 존재하지 않는 전이 증거를 요구하지
  않습니다.

### 근거와 배제 대안

사례를 더 늘리거나 회고별 verifier를 만들면 같은 runner가 여러 actor를 흉내 내는 구조는
바뀌지 않습니다. 제품별 registry도 증거의 종류를 바로잡지 못합니다. 따라서 공통 행동
verdict interface에서 transition coverage를 동결하고 실제 actor별 소비를 판정합니다.
실행 로그 전문을 영구 문서에 보존하지 않고, work item에는 verdict의 정확한 범위와 남은
미검증 전이만 남깁니다.

실제 actor가 분리된 실행에서도 candidate identity 또는 원래 oracle이 phase 사이에서
사라지면 handoff topology를 다시 검토합니다. 반대로 단일 phase를 계속 과잉 차단하면
필요 transition 산정 경계를 재검토합니다.

## 결정 25. 후보 효과는 review 전에 원래 목적의 입장 gate를 통과해야 합니다

### 배경

Reviewer는 후보의 위험을 넓게 발견할 수 있지만 사용자에게 없는 결과·보장·수정 권한을
만들 수 없습니다. Writer가 원래 범위를 확장한 후보를 만든 뒤 reviewer가 그 확장 기능의
불완전성을 찾으면, finding 자체는 정확하더라도 그 기능을 계속 보강하는 것은 사용자 목적을
더 멀리 벗어납니다. 후보가 만든 test와 문서를 oracle로 사용하면 이 확대가 내부적으로만
일관된 correction loop가 됩니다. Review 뒤 finding을 분류하는 것만으로는 이미 확대된
계약을 reviewer 입력으로 보낸 비용과 자기정당화를 막지 못합니다.

### 선택

Writer가 멈추면 discovery review보다 먼저 후보의 관찰 효과 전체를 mutation 전 operation
map과 독립 source로 역추적합니다. 같은 작업에서 만든 test·README·SOT·work item은 구현
evidence일 뿐 admission source가 아닙니다. 연결되지 않은 효과는 reviewer에게 완성도를
묻지 않고 한 범위 복구 batch로 제거하며, 제거가 사용자 소유 선택을 요구하면 구현하지 않고
별도 논의로 돌립니다.

입장 gate를 통과한 효과만 원래 결과·oracle·독립 source와 함께 discovery reviewer에게
전달합니다. Reviewer가 gate에서 누락된 미승인 효과를 발견하면 내부 결함을 상세 열거하지
않고 효과 전체 제거를 닫는 조건으로 반환합니다. 그 밖의 finding은 다음처럼 처분합니다.

| 분류 | 원래 요청과의 관계 | 현재 처분 |
| --- | --- | --- |
| 원래 결과 결함 | 승인된 결과가 원래 source·oracle을 직접 위반 | 한 correction batch에서 수정 |
| 미승인 후보 효과 | 원래 operation map에 없던 효과가 입장 gate를 통과함 | 내부 결함을 확장하지 않고 후보를 무효화해 효과 전체 제거 |
| 인접 제안 | 유용할 수 있지만 원래 완료를 막지 않음 | 현재 변경에서 제외하고 필요한 사용자 결정으로 분리 |
| 무관·증거 불충분 | 원래 결과와 실패 oracle에 연결되지 않음 | 제외 |

각 finding은 `원래 결과·source·oracle과의 관계·위반 여부 → 분류 → 처분`을 가져야 합니다.
Reviewer의 권고와 Best Practice는 근거가 될 수 있지만 권위가 아니며, 후보가 같은 작업에서
만든 확대 계약만으로 원래 결과 결함을 증명하지 못합니다. 인접 제안이 범위·비용·보안·운영
선택을 바꾸면 Agent가 구현을 결정하지 않고 사용자에게 영향과 대안을 제시합니다.

### 근거와 영향

검토를 약화하지 않고도 승인된 기능의 결함 수정과 승인되지 않은 기능의 완성을 분리합니다.
특히 미승인 효과를 review 전에 제거하므로 reviewer가 잘못된 계약의 보안·운영 완성도를
상세 감사하며 correction 권한을 키우는 경로를 닫습니다. Finding별 patch와 재review 대신
한 번의 입장 대조와 correction batch를 사용하므로 사용자 시간과 검증 비용도 원래 결과에만
씁니다. 활성 조건 원장이나 세션의 처분으로 충분해 별도 registry·verifier·제품별 예외는
만들지 않습니다.

### 배제 대안과 재검토 조건

Reviewer finding을 모두 blocking으로 처리하거나, 반대로 사용자 원문에 글자 그대로 없는
안전 결함을 모두 무시하는 극단을 배제합니다. 원래 승인 결과가 authoritative source를 실제로
위반하면 사용자에게 다시 결정시키지 않고 correction batch에서 고칩니다. 입장 gate가 실제
reviewer 호출 전에 실행됐는데도 미승인 효과가 review 대상으로 들어가거나, 명시 승인된
hardening을 제거하면 effect map의 producer·reviewer 입력과 false-block 경계를 다시
검토합니다.

## 결정 26. Push 대기는 고정한 Git target의 충돌만 근거로 합니다

### 배경

Push 대상은 remote ref에 반영할 commit graph이지만, Git phase가 repository의 모든 dirty를
같은 진행 조건으로 취급하면 고정한 commit과 무관한 작업도 대기 dependency가 됩니다. 이때
다른 actor가 독립 작업을 끝내는 동안 local branch ref가 오히려 이동해, 처음 승인받은 payload와
실제 push 후보가 달라질 수 있습니다.

### 선택

- 새 stage·commit 없이 기존 source를 push하는 Git phase는 CI·Sonar 검증의 동반 여부와
  무관하게 source commit/ref와 remote target ref를 먼저 고정합니다.
- 고정한 commit에 포함되지 않은 index·worktree 변경은 제외 dirty로 보존하며, 그 존재나
  다른 actor의 진행을 기다리지 않습니다.
- 고정 뒤 source ref 또는 remote target ref가 바뀐 경우만 실제 충돌 후보로 판정하고,
  이전·현재 상태와 현재 요청의 충돌 처리 제약을 공통 workflow에 반환합니다. 현재 요청이
  실제 충돌에서 질문을 요구하거나 원문이 이전 commit과 새 ref 중 하나를 유일하게 정하지
  않으면 push 전에 차이를 보고하고 사용자가 대상을 결정합니다.
- 다른 작업의 완료를 관찰하거나 새 commit을 자동 포함하는 방식으로 충돌을 해소하지 않습니다.

### 근거와 배제 대안

이 경계는 dirty 보호를 약화하지 않습니다. 제외 변경은 그대로 보존하고, push payload는 commit
object와 ref로 검증하므로 서로 다른 상태 surface를 섞지 않습니다. 반대로 repository나 branch
이름이 같다는 이유만으로 대기하면 독립 작업을 불필요하게 직렬화하고 payload drift를 만듭니다.

스레드 이름별 lock, 모든 dirty가 사라질 때까지의 polling과 새 branch 자동 생성은 배제합니다.
고정한 target이 실제로 바뀌었는데도 진행하게 만들거나, 무관한 dirty만으로 계속 대기하게 되면
source/target freeze와 충돌 판정의 호출 위치를 다시 검토합니다.

## 결정 27. 반복 consumer 확장은 대표 결과의 admission 뒤에만 허용합니다

### 배경

첫 대표 단위를 닫기 전 확장하지 않는 불변식은 존재했지만, 반복 artifact 작업에서 어떤
공유 계약과 전체 consumer를 고정하고 무엇을 대표 결과로 증명해야 하는지 phase handoff에
남지 않았습니다. Writer는 proposal·outline과 파일 생성을 대표 단위처럼 취급했고, 최종
reviewer가 공통 구조 결함을 한 항목씩 발견하는 동안 전체 consumer를 계속 수정했습니다.

### 선택

- 둘 이상의 consumer가 같은 형식·계층·관계·coverage 계약을 사용하면, 두 번째 consumer
  mutation 전에 공유 계약, 전체 consumer·source inventory, 대표 consumer, 직접 oracle과
  확장 조건을 operation map의 admission 결과로 고정합니다.
- 전체 범위의 관계가 사용자 결과이면 저상세 전체 골격을 먼저 닫고, 공통 계약을 실제로
  소비하는 상세 consumer 하나를 별도의 대표 단위로 닫습니다.
- Proposal·계획과 final finding은 대표 consumer의 직접 source·검증을 대신하지 않습니다.
  Reviewer가 공통 계약 결함을 처음 발견하면 나머지 consumer를 보완하지 않고 공유 계약과
  그 계약을 소비한 후보를 무효화해 대표 단위부터 다시 닫습니다.
- 서로 독립된 source·oracle을 가진 결과와 source로 유일하게 결정되는 기계적 동일 변환은
  반복 consumer gate의 대상이 아닙니다.

### 근거와 배제 대안

기존 규칙을 실제 영향 전 interface로 바꾸므로 제품별 template·파일 개수 제한·새 verifier를
추가하지 않습니다. 모든 반복 작업을 직렬화하거나 final reviewer가 완성될 때까지 사양을
추가하는 방식도 배제합니다. 같은 공유 계약이 대표 proof 없이 다시 fan-out되거나, 독립
consumer가 불필요하게 막히면 inventory·대표 oracle과 false-block 경계를 함께 재검토합니다.

## 결정 28. 복합 실행 batch는 모든 항목의 권한 coverage가 닫힌 뒤에만 입장합니다

### 배경

원자별 문장 기능·권한 분리와 인접 권한 전파 금지는 이미 공통 workflow에 있었습니다. 그러나
여러 항목을 한 실행 결과로 다루는 개발 handoff에는 raw 항목 전체의 권한 coverage가 명시적
입력으로 없었습니다. 그 결과 두 항목의 `그렇게 해`와 한 항목의 비교 질문을 전체 승인처럼
압축했고, 질문에 답하기도 전에 같은 batch의 writer를 시작했습니다.

### 선택

- 현재 Agent는 복합 batch의 각 raw 항목에 `문장 기능·요청 효과·positive 권한 source 또는
  미확정 사유`를 연결하고, 첫 계획·위임·review·mutation 전에 coverage를 완성합니다.
- 질문·비교·우려·미확정 선택이 하나라도 남으면 승인된 항목만 부분 실행하지 않습니다.
  Batch를 멈추고 미확정 이유와 선택 차원을 설명한 뒤, 사용자가 선택·제외·취소해 논의를
  닫은 새 generation에서만 실행합니다.
- Develop adapter는 이 coverage와 batch admission을 라운드 계약으로 소비합니다. Writer는
  일부 승인 원자나 인접 문장으로 빈 권한을 보정하지 않습니다.
- 모든 항목이 명시 승인됐거나 미확정 항목이 batch에서 제외됐으면 추가 승인이나 별도 실행
  문법 없이 진행합니다. Dependency·write set·완료 oracle이 다른 독립 결과까지 전역
  차단하지 않습니다.

### 근거와 배제 대안

기존 원자화 규칙의 owner는 맞으므로 유지하고, 실제 영향 전 consumer interface만 강화합니다.
DNS·Cloudflare 같은 제품별 예외, 항목 수 제한, 외부 dispatcher와 새 권한 verifier는 만들지
않습니다. 명사를 private endpoint로 바꾼 질문도 같은 경계에서 멈추고, 세 항목이 모두 명시
실행이면 바로 진행해야 합니다. 논의를 닫은 뒤에도 과거 미확정을 이유로 계속 막거나, 서로
독립된 다른 결과까지 함께 멈추면 batch 경계와 false-block 조건을 재검토합니다.

## 결정 29. 실행 회고의 통제 효과와 terminal evidence를 결속합니다

### 배경

회고 행동 검증은 모델링된 통제 효과와 실제 전이 invocation을 분리하도록 강화됐습니다.
그런데 독립 evaluator가 actual invocation을 `검증 불가`로 정확히 판정한 뒤에도, 같은 Agent가
History·상태 문서 commit을 통제 적용처럼 소비하고 회고를 완료로 보고했습니다. Evidence
classifier는 올바르게 작동했지만 그 결과를 closure·Git·report consumer가 같은 identity로
받지 않아 downstream에서 상태를 다시 높일 수 있었습니다.

### 선택

- 행동 검증 뒤 `사건·mode·routing → 통제 처분·canonical owner → 실제 적용 효과 또는
  이미 적용된 control identity → 증거 등급별 verdict → Git 자격·전체 상태`를 하나의
  closure identity로 Git과 reporting owner에 넘깁니다.
- 사건이 현재 통제보다 앞서고 현재 통제가 이미 적용된 경우에는 그 exact control과 회귀
  검증을 사용합니다. 새 commit을 만들기 위해 evidence 문서를 통제 변경으로 바꾸지 않습니다.
- 새 통제 변경이 필요한 실행 회고 commit은 선택한 처분의 canonical owner 효과를 반드시
  포함합니다. History·design·상태는 companion이며 단독으로 Git terminal을 만들지 않습니다.
- 전체 회고 상태와 보고는 필요한 전이 중 가장 낮은 verdict를 넘지 않습니다. Commit 성공은
  통제 적용·행동 검증·actual invocation 중 비어 있는 상태를 채우지 않습니다.

### 근거와 배제 대안

이 선택은 독립 evaluator나 사례를 더 만들지 않고 이미 올바르게 나온 verdict를 실제 lifecycle
consumer에 결속합니다. 회고별 registry·영구 closure 장부·custom verifier는 추가하지 않으며,
History 자체를 금지하지도 않습니다. History는 고유 사건을 보존할 수 있지만 canonical control
effect와 completion evidence를 대신하지 않습니다.

읽기 전용 회고와 통제 변경이 필요 없는 현재 통제 이전 사건에는 writer·Git을 강제하지
않습니다. 반대로 필요한 canonical owner 변경 없이 evidence-only commit이 다시 회고 완료로
쓰이거나, 실제로 닫힌 단일 phase 회고가 존재하지 않는 다중 actor 증거 때문에 막히면 closure
identity의 Git admission과 transition 산정 경계를 함께 재검토합니다.

## 결정 30. 반복 이의에서도 선택 span과 원래 질문 축을 유지합니다

### 배경

재사용 Scenario의 `EC2 실행 파일 전달`과 `확장 EC2 실행 파일 재전달`이 같은 전이를
중복한다는 질문에 Agent는 처음에는 중복 문제를 일부 인정했습니다. 사용자가 Scenario의 존재
이유를 다시 묻자, Agent는 원래 질문을 유지하지 않고 `Scenario가 live 상태를 함께 소유한다`는
인접한 참인 사실로 바꿔 두 항목의 영구 배치를 다시 정당화했습니다. 사용자가 recipe에서 같은
소금을 두 번 넣는 것과 같다고 세 번째로 지적한 뒤에야 원래 질문으로 복귀했습니다.

### 선택

- 선택한 문장·항목과 최초 질문 축을 후속 이의가 끝날 때까지 같은 identity로 유지합니다.
- 복합 artifact에서는 선택 span의 exact section owner, 정보 수명과 빈 환경에서의 재생 효과를
  파일 전체의 owner와 분리합니다.
- Enclosing artifact의 참인 책임은 선택 span의 위치·중복·필요성을 자동으로 정당화하지
  않습니다. 직전 결론을 유지하려면 원래 질문을 이 exact 하위 계약으로 직접 통과시킵니다.
- 사용자가 같은 결론을 반복해서 묻는다면 최신 문장만 별도 질문으로 만들지 않고 최초 질문과
  선택 span부터 다시 대조합니다.

### 배제 대안과 재검토 조건

특정 Scenario나 `재전달`이라는 명사를 금지하지 않습니다. 실제로 서로 다른 actor·대상·상태
전이·완료 조건이면 별도 단계일 수 있고, 단일 책임 artifact의 단순 사실 질문까지 복합 owner
분석으로 확대하지 않습니다. 선택 span이 없는 일반 추천 반론은 기존 comparison 통제를 그대로
사용합니다.

## 결정 31. 식별된 기존 commit 수정은 그 patch와 topology를 대상으로 합니다

### 배경

사용자가 특정 commit을 확인해 잘못된 부분을 수정하라고 했는데, Agent는 commit을 조사
source로만 소비하고 현재 HEAD에 새 보상 commit을 추가했습니다. Git workflow는 commit과
history rewrite를 구분했지만, 자연어에서 식별된 commit이 mutation target인지 source인지
고정하는 admission이 없어 Agent가 더 익숙한 append-only 경로를 선택할 수 있었습니다.

### 선택

- Local unpushed 기존 commit을 식별하고 그 안의 문제·누락을 수정하라는 표현은
  `확인해서`가 포함돼도 해당 patch와 후속 local topology의 history rewrite로 보존합니다.
- 사용자가 현재 코드·새 commit을 별도 target으로 명시하거나 읽기 전용 설명을 요구하면
  그 결과를 따릅니다. 공유·pushed history는 자동 rewrite하지 않고 안전 경계로 반환합니다.
- Commit이 조사 source인지 mutation target인지 원문에서 닫히지 않을 때만 첫 Git mutation
  전에 그 차이를 질문합니다.
- Mutation target이 닫혔으면 변경 뒤 patch, 유지할 후속 commit, 제거할 임시·보상 commit,
  최종 branch tree와 제외 dirty를 하나의 예정 결과로 검증합니다.
- 새 commit 추가는 기존 commit 수정의 안전한 대체나 중간 완료가 아닙니다.

### 배제 대안과 재검토 조건

특정 SHA나 `amend` 단어를 요구하는 규칙은 만들지 않습니다. 사용자가 내용만 고치거나 새
보상 commit을 명시한 경우에는 기존 commit을 자동 재작성하지 않습니다. 이미 공유된 remote
history는 기존 안전 경계에 따라 자동 rewrite하지 않으며, local unpushed topology에서만
확정된 mutation target을 직접 복구합니다.

## 결정 32. 삽입 원자 뒤 원래 artifact의 Git 효과까지 복원합니다

### 배경

Scenario correction과 commit이 원래 결과였지만, 실행 회고의 통제 변경·검증·commit이 끝난
뒤 원래 artifact는 worktree에만 남았습니다. 기존 복귀점 계약은 원래 artifact와 acceptance
oracle을 보존했지만, 복귀 consumer가 원래 Git 효과를 candidate·index·commit object에서
직접 닫는 terminal admission은 없었습니다. 같은 파일 overlap을 발견한 상태가 원래 결과를
미완료로 유지하는 대신 제외 근거처럼 소비됐습니다.

### 선택

- 원래 요청에 Git 효과가 있으면 삽입 원자 뒤 artifact 내용과 해당 Git 효과를 같은 활성
  원자로 복원합니다.
- 통제 commit과 partial-stage 금지는 원래 artifact commit을 대신하지 않습니다.
- Same-file overlap은 격리 candidate나 source-backed 분리 경로를 먼저 사용하며, 실제로
  안전한 경로가 없을 때만 exact blocker로 전환합니다.
- Terminal admission은 원래 결과·artifact·Git 효과를 실제 candidate·index·commit 또는
  rewrite 결과와 대조하며, worktree-only artifact는 `Active`로 유지합니다.

### 배제 대안과 재검토 조건

모든 overlap에 partial stage를 허용하거나 control commit에 사건 artifact를 섞지 않습니다.
항상 새 worktree를 만들라는 제품별 절차도 추가하지 않고, 현재 안전한 최소 경로를 사용합니다.
원래 요청이 worktree 수정뿐이거나 사용자가 artifact commit을 명시적으로 취소하면 존재하지
않는 Git 효과를 강제하지 않습니다.

## 결정 33. 원래 artifact의 Git 미완료를 첫 화면에서 우선합니다

### 배경

Scenario correction은 worktree에만 있고 요청한 commit에는 없었지만, Agent는 통제 commit 두
개와 수정 내용을 먼저 완료로 보고했습니다. 미커밋 사실은 같은 파일 overlap 설명 뒤에
묻혀 사용자가 원래 결과가 어느 commit에 있는지 다시 확인해야 했습니다. Reporting workflow는
가장 중요한 미완료를 첫 문장에 두도록 이미 요구했지만, 삽입 결과와 원래 결과 사이의 우선
순위를 명시하지 않아 통제 commit이 전면을 차지할 수 있었습니다.

### 선택

- 삽입 회고·검증·통제 뒤에는 원래 사용자 결과의 상태를 먼저 투영합니다.
- 요청된 Git 효과가 없으면 첫 문장을 `원래 artifact Git 결과 미완료`로 시작합니다.
- Worktree-only artifact는 `수정됨·미커밋`으로 직접 표시하고, 통제 commit과 구분합니다.
- Overlap과 보존은 사유·상태일 뿐 전체 완료가 아니며, 다음 책임과 재개 조건을 같은 첫
  화면에 둡니다.

### 배제 대안과 재검토 조건

모든 보고를 장문 checklist로 만들거나 모든 dirty 파일을 첫 문장에 나열하지 않습니다.
원래 결과가 실제로 닫혔으면 삽입 결과보다 먼저 미완료를 발명하지 않습니다. 사용자가 현재
보고 범위에서 원래 결과를 명시적으로 제외한 경우에는 그 projection에서만 생략합니다.

## 서로 다른 first-break family의 owner

모든 과거 사건을 하나의 막연한 원인으로 합치지 않습니다.

| 계열 | 최초 단절 | canonical owner |
| --- | --- | --- |
| 요청·권한·완료·통제 생애주기 | source 의미가 공통 결과 상태로 전달되기 전 끊김 | workflow/100 |
| 초심자 기술 설명 | 질문·근거를 reader causal orientation으로 만들기 전 상세 조립 | explain-tech |
| 계획 결정 | 설계 source·결정 owner가 계획 artifact에 안정적으로 보존되기 전 끊김 | dev-plan·설계 workflow |
| 개발 실행 | 검증 가능한 결과가 consumer 진입 조건으로 닫히기 전 확장 | develop·개발 workflow |
| Git 원자성 | 영속 의미 효과가 exact patch·subject·revert 경계로 투영되기 전 끊김 | commit workflow |
| 상태 보고 | 확정 상태가 사용자 판단용 projection으로 바뀌는 경계 | reporting workflow |

각 owner는 공통 lifecycle을 복제하지 않고 이 표의 차이만 소유합니다.

## 전체 재검토 조건

다음 중 하나가 발생하면 사례 규칙을 추가하지 않고 owner topology부터 다시 봅니다.

- 같은 보호 불변식이 통제 호출 뒤 다시 실패합니다.
- metadata·reference·history가 실행 권한이나 현재 상태를 결정합니다.
- workflow/100이 domain 사례를 계속 흡수합니다.
- skill body가 canonical workflow를 다시 서술합니다.
- fresh regression·transfer·false-block 중 하나가 실패합니다.
- 완료 work item의 원래 범위가 충족되지 않았음이 확인됩니다.
- 같은 작성·fresh 생성·작성자 판정 전략으로 복구한 불변식이 다시 실패합니다.
