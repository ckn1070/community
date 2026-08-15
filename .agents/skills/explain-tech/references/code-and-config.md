# 코드·설정·line-by-line 설명

코드나 설정 문자열의 효과부터 말하지 말고, 그 문자열이 **누가 정한 어떤 종류의 값이며
누가 처음 해석하는지**를 닫는다.

## Artifact 분류축

파일·설정·test가 `배포 artifact`, `migration 파일`, `운영 입력`처럼 어느 범주인지 묻거나
그 분류가 필요성·배치 결론의 근거가 되면 다음 두 축을 먼저 나눈다.

```text
내용·책임의 본질: 이 artifact가 직접 표현하는 정보·행동·보장은 무엇인가
현재 lifecycle 역할: 현재 revision에서 누가 어떤 형태로 운반·변환·소비하는가
```

예를 들어 SQL source가 Kustomize에 읽혀 ConfigMap data로 변환되고 deployment hook Job에서
실행될 수 있다. 이때 내용상 SQL source이며 Kubernetes manifest는 아니지만, 현재 배포
revision이 운반·소비하는 deployment-time input이다. `배포 artifact`를 넓게 배포 revision의
입력 전체로 쓰면 포함되고, 좁게 Kubernetes API에 적용되는 object·manifest만으로 쓰면 원본
SQL은 포함되지 않는다. 어느 정의를 쓰는지 먼저 밝히고 `맞다→아니다`로 결론을 뒤집지 않는다.

Path·directory·확장자는 책임 분류의 단독 근거가 아니다. 현재 caller가 있다는 사실도
lifecycle 역할은 증명하지만 그 배치가 유일하거나 권장 owner라는 결론은 증명하지 않는다.
현재 연결 사실, 해당 보장의 필수성, exact artifact·topology의 필수성과 권장 배치를 따로
판정한다. 현재 사용 경로를 설명하는 질문에 대체 owner·삭제·리팩터를 붙이지 않고, 사용자가
설계 비교를 요청한 경우에만 그 판정을 별도로 연다.

## 설명 dependency와 repair

사용자가 선택한 표현을 처음 보는 독자에게 설명할 때는 답변 전에 필요한 연결을 다음
순서로 확인한다. 이 순서는 내부 dependency map이며 답변 목차가 아니다.

```text
원문 literal과 현재 존재하는 문법 요소
→ 각 token·구분자가 가리키는 실제 대상과 역할
→ 이름·값을 정한 owner와 왜 이 exact 이름·값을 선택하는가
→ parser·consumer의 concrete 판정과 행동
→ 현재 코드가 조회·추가·삭제·변경하는 exact 상태
→ 변경 전·후와 이 코드에서 그 변경이 필요한 이유
```

- 사용자가 code의 이름을 붙여 넣거나 발음했다는 사실은 뜻을 안다는 증거가 아니다.
  직접 선행 설명을 받아들였거나 사용자가 의미를 사용해 판단한 범위만 known으로 둔다.
- 일반 문법보다 현재 literal을 먼저 분해한다. 선택 field가 있는 schema라면 현재 표현에
  그 field가 있는지부터 밝히고 `key[=value]:effect`처럼 선택성을 표시한다. 현재 `=`가
  없는데 `key=value:effect`를 현재 구조처럼 제시하는 설명은 실패다.
- 인과의 전제로 새 용어를 쓰려면 최초 사용과 같은 문장이나 바로 앞에서 다른 미정의
  용어에 기대지 않는 쉬운 뜻을 붙인다. 정의하지 않을 용어는 경로에서 제거한다.
- 명령 종류·객체 종류·역할명으로 정확히 분류했다는 사실만으로 설명이 닫히지 않는다.
  그 분류명이 결론에 필요하면 사용자가 이미 아는 일상어와 concrete 동작으로 먼저
  연결한다. 분류명이 없어도 같은 뜻을 정확히 말할 수 있으면 첫 답에서 제거한다.
- `무엇인가`만 물어도 직접 선행 상위 목표가 `왜 이 exact 대상을 조회·삭제하는가`까지
  포함하면 역할·consumer 행동·변경 이유를 같은 단위에서 닫는다. 반대로 상위 목표가
  없는 순수 문법 질문에는 관련 없는 domain 전체를 새로 펼치지 않는다.
- 조건식의 반환과 `그 이름·값이 있어야 하는가, 없어야 하는가`를 함께 물으면 두 질문을
  합치지 않는다. 조건식에는 concrete 입력·status를, 상태 질문에는 `대상은 무엇인가 →
  exact 이름이 어떤 역할을 식별하는가 → 현재 존재 여부 → 원하는 최종 상태 → 유지 시
  현재 consumer의 최초 실패`를 각각 답한다. 이 다섯 값은 짧은 card나 동일하게 분명한
  문장으로 첫 설명 단위에 모두 둔다. 후자를 `taint가 있으므로 제거한다`, `제약이므로
  없앤다`처럼 아직 정의하지 않은 이름이나 목적의 반복으로 축약하면 실패다.

사용자가 답변에 처음 등장한 이름을 다시 묻거나, 직전 답변이 설명 없이 전제로 쓴 source
하위 표현을 선택하거나, 같은 `무엇·왜`를 반복하면 직전 답변의 독립 보충 질문으로
계산하지 않는다. 다음 순서로 explanation unit을 repair한다.

```text
재질문이 드러낸 최초 missing dependency
→ 그 dependency에 의존한 이전 문장 무효화
→ 원래 선택 표현과 상위 이해 목표 복원
→ 현재 선택 span의 직접 답
→ missing dependency부터 원래 결론·이유까지 전체 unit 재작성
→ repair가 새로 도입한 용어의 first-use 재감사
→ line-by-line이면 기존 완료·잔여·다음 line 복구
```

`toleration이 뭐다`, `key는 이것이다`처럼 지적받은 한 단어만 보충하고 원래 질문의
`이 표현은 무엇인가 → 왜 이 key인가 → 무엇을 바꾸는가 → 왜 바꾸는가`를 다시 연결하지
않으면 repair가 아니다.

설명 단위는 직전 답변을 보지 않고도 처음 접한 독자가 현재 질문에 해당하는 다음 값을
답할 수 있고, 그 답에 쓰인 필수 명사에 다시 `그게 뭐야?`가 남지 않을 때 닫힌다. 모든
field를 사용자에게 출력하는 것은 완결 조건이 아니다.

```text
이 exact 표현은 어떤 문법·artifact인가?
선택 span 전체를 일상어로 바꾸면 무슨 뜻인가?
사용자가 물은 각 token은 그 전체에서 무엇을 바꾸는가?
concrete 입력 하나에서 무엇이 관찰되는가?
`왜`를 물었으면 이 단계가 없을 때 무엇이 처음 달라지는가?
```

선택 span 밖의 producer·consumer와 handoff admission은
`SKILL.md`에서 먼저 routing하는 `causal-span.md`가 소유한다. 이 문서는 그 admission을
통과한 span의 token·option·line 상세만 소유한다.

## Token과 값

선택 영역이 있으면 다음 경계를 먼저 고정한다.

```text
선택한 exact span | enclosing line·block | span 전체의 문법·artifact 종류
| span의 구성 token | 전체를 처음 해석하는 parser·schema
```

선택 span이 line보다 작으면 line 전체를 설명 대상으로 승격하지 않는다. `NAME=value`,
`key: value`, `annotation(...)`처럼 여러 token이 하나의 문법 단위를 만들면 각 조각의
효과보다 먼저 전체 구조를 분류한다. 사용자가 정체를 물으면 `명령이 아니라 X가 해석하는
Y 문법이다`처럼 혼동 후보를 첫 문장에서 직접 닫고, enclosing line은 그 문법이 어디에
쓰이는지 보여 주는 context로 뒤에 둔다.

그다음 선택한 token마다 다음을 확인한다.

```text
원문 token | 문법상 위치 | artifact 종류
| 언어 예약어 / platform·schema 고정 key / 작성자 정의 ID
  / 작성자 정의 value / runtime data / 일반 path
| 정의 owner | 최초 parser·schema | 해석 전 concrete 값
| 해석·변환 후 값 | 최종 consumer의 행위 | 결과·실패
| 필요한 이유·없을 때의 최초 차이
```

- YAML parser가 mapping을 읽는 것과 GitHub Actions 같은 후속 schema가 위치별 key를
  판정하는 것을 분리한다.
- 표시 label, 내부 ID와 runtime value가 다르면 별도 token으로 설명한다.
- source literal에서 fully-qualified 이름이나 path가 만들어지면 각 segment의 owner와
  변환자를 실제 값 하나로 연결한다.
- 여러 parser를 거치면 shell 확장 전·후처럼 경계별 값을 나눈다.
- 복합 문법의 parser가 내부 이름과 값의 업무 의미까지 정한다고 가정하지 않는다. 문법
  owner, 이름·값의 계약 owner, 값을 전달하는 actor와 최종 consumer를 분리한다.
- `parser`, `producer`, `consumer`는 component의 고정 별명이 아니라 특정 artifact를
  기준으로 한 역할이다. 관계를 설명할 때는 다음 tuple을 먼저 닫는다.

  ```text
  actor | 직접 행위 | 직접 대상·전달 artifact | 상대 actor | phase | source 위치와의 관계
  ```

  같은 함수가 한 값을 consume하고 내부에서 다른 값을 parse할 수 있으므로 역할명만으로
  대상을 대체하지 않는다. `앞의`, `뒤의`가 단지 정의 위치라면 실행·전달 방향과 분리한다.
- 이전 설명의 관계문을 정정할 때 exact tuple이 성립하지 않으면 `연결`을 형식 호환이나
  간접 영향이라는 새 뜻으로 구제하지 않는다. 잘못된 관계를 폐기하고 실제 독립된 두
  동작 또는 실제 artifact 전달 경로로 교체한다.
- 선택 span이 enclosing command의 환경·인자·입출력처럼 실행 context를 준비하면 source
  표기 순서와 runtime phase를 구분한다. `parse·분류 → 값 확장·실행 입력 준비 → consumer
  호출 → consumer 사용`을 실제 순서로 연결하며, consumer 호출 뒤 prerequisite를
  전달했다고 설명하지 않는다.
- owner·parser·consumer를 확인하지 못하면 익숙한 의미로 추측하지 않는다.

## 관찰 표면과 공식 계약

console·IDE·web 화면, screenshot과 명령 출력의 표시를 묻는 경우에는 주장별 source
owner를 먼저 분리한다.

| 주장 | authoritative source |
| --- | --- |
| 현재 표시 문자열·위치·선택 상태 | 사용자가 제공한 관찰 snapshot 또는 확인한 live 표면 |
| option의 동작·제약·지원 계약 | 적용 version의 공식 1차 자료 |
| 관찰 표현과 공식 용어가 같은 항목이라는 mapping | 두 source의 구조·설명·동작을 직접 대조한 근거 |

표시 literal 후보마다 최초 출처를 유지한다. 이전 Agent 답변이나 공식 문서에서 나온
문자열을 사용자가 `어디 있어?`처럼 되물어도 독립된 화면 관찰이 되지 않는다. 사용자가
현재 화면에서 읽었다고 명시한 문자열과, 현재 snapshot·live 표면에서 독립적으로 판독한
문자열만 관찰 근거로 사용한다. 긴 전체 screenshot은 전체를 표시한 결과가 원본 pixel을
보존해도 target text를 축소할 수 있으므로, exact literal을 답에 쓰기 전에 원본의 대상
영역을 별도로 잘라 읽을 수 있는 크기로 확인한다. 사용자 artifact는 변경하지 않고
임시 진단 사본만 사용한다. 전체 view, image dimension 확인과 결과 없는 OCR은 target
영역 판독을 대신하지 않는다. 이 gate를 실행하지 않았거나 대상 영역에서도 글자 구분이
답을 바꾸면 기대 문자열로 판독하지 않고 위치·선택 상태만 중립적으로 설명한 뒤 exact
literal을 다시 확인한다.

답변의 조작 지시는 사용자가 실제로 찾을 수 있는 관찰 표현을 먼저 사용한다. 공식
자료의 용어가 다르면 `관찰 표현(공식 문서의 용어)`처럼 mapping을 명시하고, 공식 용어를
현재 화면의 literal로 바꾸지 않는다. 화면을 판독할 수 없거나 mapping 근거가 닫히지
않으면 보이는 것처럼 단정하지 않고, 결론을 바꾸는 exact 문자열·위치만 다시 확인한다.

## Option

CLI option은 먼저 사용자의 질문 목표를 다음 중 필요한 값으로 고정한다.

```text
표기와 기억할 의미의 연결 | short·long의 관계 | 실제 동작
| 필요한 이유 | 없을 때의 차이
```

`왜 이 글자인가`, `무슨 뜻인가`, `어떻게 기억하는가`를 물으면 첫 답을 다음 연결로
만든다.

```text
원문 표기 → 공식 계약으로 뒷받침되는 기억용 단어 → 실제 동작
```

공식 문서가 글자의 유래를 명시하지 않아도, 공식 동작이나 long form이 기억용 단어를
직접 뒷받침하면 `공식 어원은 확인되지 않지만 이렇게 기억할 수 있다`고 근거 수준을
분리한다. 유래 확인 불가만 답하거나 동작부터 설명해 사용자가 글자와 의미를 다시
연결하게 하지 않는다. 뒷받침되는 연결이 없으면 익숙한 추측을 만들지 않고 확인할 수
없다고 답한다.

그 뒤 필요한 사실을 내부에서 확인한다.

```text
소유 명령·API | short·long 구분 | 공식 이름·long form
| 글자의 공식 유래 | 기억용 mnemonic의 근거 수준
| 실제 동작 | 없을 때 달라지는 결과
```

공식 long form이나 유래가 없으면 `확인 불가`로 둔다. 관용적인 기억법을 공식 이름으로
만들지 않는다. short·long 구분과 소유 명령은 해석 대상을 식별하는 근거이며, 사용자가
그 구분을 묻지 않았고 결론도 바꾸지 않으면 첫 답보다 앞세우거나 고정 목차로 노출하지
않는다.

## 연산자와 조건식

연산자는 언어별 정확한 계약을 사용한다.

```text
피연산자 허용 값·type | 암묵 변환·truthy/falsy
| 평가 순서·단락 여부 | 반환 값·type
```

대표 입력마다 다음을 실제 값으로 계산한다.

```text
좌항 값·type | 좌항 판정 | 우항 평가 여부와 값·type
| 중간 결과 | 전체 식의 값·type
```

`a && b || c`처럼 이어진 식은 앞 연산 결과가 다음 연산의 어느 피연산자가 되는지
연결한다. `논리 AND` 같은 일반 이름이 실제 반환 계약을 가리게 하지 않는다.

## Line-by-line

Line-by-line은 파일·commit을 함수별로 요약하는 형식이 아니라 요청 snapshot의 모든 물리
line을 원래 순서대로 account하는 coverage 계약이다. 다만 coverage는 설명 dependency와
`causal-span.md` admission을 통과한 설명에만 부여한다. 각 line은 원문을 제시하고 다음
순서를 따른다.

```text
원문 line
→ 문법 요소와 각 token의 종류
→ 최초 해석자와 concrete 입력
→ 계산·변환된 값
→ actor가 직접 수행하는 행위와 대상
→ 관찰 결과·실패
→ 전체 흐름에서의 위치
→ 필요한 이유와 제거 시 최초 차이
```

빈 line과 닫는 delimiter도 separator·scope 종료 역할로 account하며 소리 없이 건너뛰지
않는다. 여러 물리 line이 하나의 명령, 조건식, here-document나 값 전달을 이룬다면 실행
의미를 인위적으로 자르지 않고 함께 설명할 수 있다. 이때 모든 구성 line을 원문 순서대로
보이고 각 line의 continuation·인자·redirection·delimiter 역할을 설명한다. 함수명과 line
range만 제시한 요약, 여러 line의 결과만 압축한 설명과 `논리 Line-by-Line`이라는 재명명은
line coverage가 아니다. 반복되는 형제 line은 공통 규칙을 먼저 설명하되 각 line의 실제
literal, 값과 차이는 생략하지 않는다.

복합 unit은 먼저 최소 causal slice와 concrete before→after를 설명한 뒤, 구성 line을 원래
순서대로 account한다. `Lx: 이 option을 설정한다`, `Ly: 결과를 출력한다`처럼 결과만 한
줄씩 붙인 code gloss는 모든 line을 언급해도 line-by-line 완료가 아니다. 독자가 뒤
설명에서야 앞 line의 용어를 알게 되는 구성도 first-use 실패이므로 앞 line의 coverage를
무효로 한다.

범위가 한 응답의 같은 품질을 넘으면 초안을 압축한 뒤 나누지 않는다. 답변 작성 전에
전체 requested line 범위와 끊지 말아야 할 causal slice를 확인하고, dependency가 닫히는
함수·문법 단위에서 응답만 나눈다. 분할 경계는 설명 granularity를 바꾸지 않는다. 각
부분의 끝에는 다음 세 값을 exact path와 line으로 밝힌다.

```text
이번에 완료한 범위 | 아직 설명하지 않은 범위 | 다음 시작 line
```

아직 설명하지 않은 뒤쪽을 함수·range 요약으로 미리 설명하거나 전체 완료로 보고하지
않는다. 진행 중 사용자가 이미 설명했거나 아직 남은 한 line·token을 국소 질문하면 그
질문이 이전 답변에서 처음 소개한 용어인지 먼저 판정한다. 그렇다면 그 token만 보충하지
않고 해당 복합 unit의 coverage를 무효화한 뒤 설명 dependency 절의 repair 순서로 unit
전체를 다시 닫는다. 그 밖의 독립 국소 질문만 먼저 닫되, 직접 선행 문맥의 전체
line-by-line 요청과 진행 상태를 유지한다. repair 또는 국소 답의 끝에서 기존 완료·남은
범위와 다음 시작 line을 다시 제시하며, 사용자가 원래 대상과 line을 반복 입력하게 하지
않는다. 사용자가 명시적으로 전체 요청을 취소·대체하지 않았다면 `계속`, `다음` 같은
표현은 저장한 다음 line부터 같은 품질로 재개한다.

선택 span이 line보다 작으면 위 순서를 line 전체에 적용하기 전에 span의 전체 분류·구성·
해석자·consumer를 먼저 닫는다. line의 나머지 token이 선택 span의 첫 설명을 앞지르지
않는다.

## 결과 경로의 완결성

코드·설정에서 열거한 항목이 전부인지 판정할 때는 이름 검색 결과의 개수를 세지 않고
공통 결과를 기준으로 경로를 복원한다.

```text
질문의 공통 결과·postcondition
→ 현재 값을 직접 바꾸는 경로
→ 다음 실행에서 값을 다시 만드는 persistent source
→ 성공 전에 거부·실패시키는 검증 경로
→ 각 경로의 caller·조건·대상·처분
```

entrypoint와 관련 함수의 caller, sibling branch, preflight와 최종 postcondition을 같은
snapshot에서 읽고, test·SOT는 기대 범위와 빠진 경로를 대조하는 데 사용한다. 변경하는
경로와 발견만 하고 실패시키는 경로를 같은 동작으로 합치지 않되, 사용자가 무엇을
신경 써야 하는지 묻는다면 둘 다 결과에 미치는 차이를 포함한다.

사용자가 두 항목만 자세히 설명해 달라고 해도 같은 결과를 바꾸는 세 번째 경로가 있으면
`두 항목이 전부`라고 답하지 않는다. 세 번째 경로의 존재와 `변경·검증·차단` 중 역할만
짧게 밝힌 뒤 요청한 두 항목의 상세로 돌아간다. 관련 source 전체를 닫지 못했으면
`확인한 것은 두 항목`이라고 제한하고 `딱 두 개`라는 폐쇄 집합으로 승격하지 않는다.

## Test와 fixture

fixture는 test가 consumer에 넘기는 고정 입력 또는 준비 상태다. 다음 관계를 구분한다.

- 대상 artifact의 실제 동작을 검증하는 test
- validator·wrapper 자체를 검증하는 self-test
- test caller가 fixture를 선택해 consumer에 넘기는 행위

test가 fixture를 사용한다는 사실을 production 대상이 fixture를 선택·참조하는 관계로
바꾸지 않는다. fake·stub 기반 self-test는 인자 조립과 실패 전파를 증명할 수 있지만 실제
외부 시스템 호출을 증명하지 않는다.
