# 문서화 워크플로우

이 문서는 저장소 Markdown의 source-first 의미·구조 대조, 작성·이관과 문서 관계·링크·
번호 검증 깊이를 소유합니다. 문서 유형·canonical owner·보존·관계 표현·번호 체계는
[100 문서 정보 구조 가이드](../100-documentation-guide.md), 요청·권한·공통 상태·의존·최종
discovery와 closure는 [100 작업 실행 워크플로우](100-work-execution-workflow.md)가
소유합니다.

## 1. Source와 owner 후보 복원

exact write target은 아직 고정하지 않습니다. 다음을 편집 전에 실제 디스크에서 읽어
owner 후보를 복원합니다.

1. `AGENTS.md`, [000 문서 목차](../000-index.md)와 관련 그룹 router
2. 문서 정보 구조 가이드와 관련 directory의 실제 문서 topology
3. 주장을 결정하는 코드·설정·schema·공식 계약·live 관찰 같은 authoritative source
4. 같은 독자 질문이나 사실을 소유할 수 있는 기존 문서와 inbound·outbound link
5. 번호가 있으면 direct-directory router, 같은 범위의 sibling과 실제 directory
   topology; 상위 router가 없는 고정명 artifact면 canonical entrypoint와 consumer

문서화 판단에 필요한 다음 연결만 세션에 둡니다.

```text
독자 질문
→ 필요한 정보와 authoritative source
→ 기존 owner 후보 또는 비보존 후보
→ 현재 topology·link·번호·consumer
```

현재 작업에서 마지막으로 읽었거나 수정한 문서, 다음 단계가 적힌 문서와 사용자가 지정한
파일은 모두 변경 표면 또는 owner 후보일 뿐 자동으로 write target이 되지 않습니다.
현재 문서에 새 의미를 쓸 수 있는지는 작업 실행 워크플로우의 의미 authority와 문서
source를 함께 따릅니다.

현재 개념을 설명하거나 변경하는 문서에서는 source를 읽은 뒤 다음 owner 관계를 먼저
닫습니다.

```text
현재 정의·구조·관계·작동 원리·불변식·제품 범위와 이를 이해하는 데 필요한 현재 이유
→ project·product·domain·architecture·tech-stack 등의 영구 SOT
→ 구현 source
→ 작업 상태 또는 선택 과정이 필요할 때만 work item·design
```

Work item과 작업 `-design`은 현재 개념을 발견·설계한 source 후보일 수 있지만 canonical
owner는 아닙니다. 영구 Reference/SOT가 자신의 mental model·경계·관계·현재 이유를 생략하고
work item 또는 그 design을 `현재 기준`, `논리 모델`, `설계 이유`, `조회 계약`의 owner로
가리키는 관계는 source 복원이 끝나지 않은 상태입니다. 반대로 작업 문서가 현재 영구 SOT를
가리키거나, 영구 SOT가 작업 당시의 선택 과정·배제 대안만 design으로 연결하는 관계는
허용합니다.

## 2. 변경 깊이 선택

작업 실행 워크플로우에서 요청 계약과 변경 범위를 확정하고 앞 절에서 source·owner 후보를
복원한 뒤, 파일 이름이나 줄 수가 아니라 현재 확인한 문서의 의미·topology 효과로 초기
깊이를 잠정 선택합니다. 구조 gate에서 owner·관계·번호·이동 범위를 닫은 뒤 이 깊이를
반드시 다시 확정합니다.

| 깊이 | 실제 효과 | 문서 고유 검증 |
| --- | --- | --- |
| 국소 | 오탈자, 한 링크, source로 확정된 현재 값처럼 의미 owner와 구조가 유지됨 | 변경 단위, 직접 source와 영향받는 인접 구조를 편집 전후 대조 |
| 일반 | 한 문서 또는 연결된 소수 문서의 내용·outline이 바뀌지만 canonical owner와 topology는 유지됨 | 의미·구조 gate, 관련 source·문서 범위와 작성자 두 번째 source-first 대조 |
| 중대한 final | canonical SOT·workflow·router·runbook의 신설·대규모 재작성, 문서 간 owner·관계 변경, 전체 적용·coverage 주장 | 작업 실행 workflow가 동결한 검증 대상·영향 범위의 fresh 독립 final 감사 |
| 중대한 구조 | 다문서 병합·분리·이동·삭제, canonical entrypoint·번호 group·탐색 topology 변경, 광범위 corpus 개편 | actual filesystem 전수 분류, 작성 전 fresh proposal 감사와 동결 후보 final 감사 |

모든 깊이에서 현재 Agent가 원래 요청과 source를 직접 복원하고 작성 뒤 두 번째로
대조합니다. 국소·일반 변경은 이 대조로 닫으며 `$verify-documentation`을 추가 호출하지
않습니다. 중대한 final·구조 변경만 fresh 독립 전문 감사를 적용합니다. 계획·개발 결과의
일반 검증은 routine 동반 문서를 함께 감사할 수 있지만, 중대한 owner·topology·coverage
후보의 `$verify-documentation` phase와 verdict를 대신하지 않습니다. 같은 reviewer가 두
역할을 수행해도 각 phase의 독립 입력·감사 축·verdict가 실제 trace에 없으면 중대한 문서
전문 감사가 수행된 것으로 판정하지 않습니다.

### 문서 삭제 admission

문서 전체 삭제나 이에 준하는 대규모 제거가 포함되면 구조 gate 전에 다음 순서로
admission을 판정합니다.

1. [문서 정보 구조 가이드의 보완과 삭제](../100-documentation-guide.md#보완과-삭제)에 따라
   보완과 영향 범위 재검토를 기본 후보로 둡니다.
2. 파일 존치나 정보의 고유성·Git 복원 가능성과 무관하게, 제거되는 핵심 독자 질문,
   canonical owner 계약과 상태·결정 section 범위를 판정합니다. 그 범위의 모든 정보,
   inbound link·consumer를 수집합니다.
3. 목적·owner가 사라졌거나 대체 문서가 책임과 고유 정보를 완전히 이어받았는지, 또는
   부분 보완으로는 현재 source와 양립시킬 수 없는지 증명합니다.
4. 삭제하지 않는 대안, 삭제 뒤 owner·topology, 재작업과 복구를 사용자에게 설명하고
   [작업 실행 워크플로우의 문서 삭제 권한](100-work-execution-workflow.md#문서-삭제-권한)이
   현재 generation의 exact target에 결속됐는지 확인합니다.
5. 위 조건이 하나라도 닫히지 않으면 파일과 link를 유지하고 `보존된 기준선`, `부분 재검토
   대기` 또는 `전체 재검토 대기`로 표시합니다.

삭제 승인은 보완 권한이나 구조 감사 verdict로 대신할 수 없습니다. 승인 전에 writer가
삭제 patch를 만들거나 router에서 문서를 먼저 제거하지 않습니다.

`누락 없이`, `전체 구조`, `전면 이관`처럼 집합 전체의 완결성이 결과이면 실제
filesystem에서 대상 root와 include·exclude 범위를 수집합니다. 변경하지 않을 문서도
판정 대상에 포함하되 inventory·coverage map은 작업 중 증거로만 사용하고 repository에
새 catalog로 저장하지 않습니다.

## 3. 의미 gate

outline을 고르기 전에 다음 두 방향을 닫습니다.

```text
독자 질문·source → 필요한 정보와 owner: 누락 없음
남길 정보 → 독자 필요·source·보존 이유: 임의 확장 없음
```

각 주장은 현재 사실, 결정, 계획, 절차, 결과 또는 사건 중 무엇인지 구분하고 그 유형에
맞는 source를 연결합니다. 문서끼리 일치한다는 사실을 repository 실행 사실로, local
정적 검증을 live 환경 성공으로, 절차의 존재를 외부 책임 완료로 확대하지 않습니다.

`결정`은 다시 현재 개념을 이해·변경하는 데 필요한 이유와 작업 당시의 선택 과정으로
나눕니다. 전자는 책임 Reference/SOT의 현재 설명이며, 후자만 work item design의 결정
기록입니다. 둘을 모두 `결정 이유`로 묶어 work item에 넘기지 않습니다.

설명 문서의 coverage는 문서 유형별 증거를 분리해 판정합니다.

- Reference/SOT는 현재 개념, 구성요소, lifecycle, trigger, actor, artifact·전달값,
  소비자, 결과와 실패 관계를 설명해야 합니다.
- Decision/Design은 선택 이유와 대안의 근거이지 현재 설명의 대체 owner가 아닙니다.
- Procedure는 실제 순서·명령의 source가 될 수 있지만 Reference/SOT의 설명 coverage로
  계산하지 않습니다.
- 구현 source가 wiring을 증명해도 독자가 source를 직접 해독해야만 전체 관계를 알 수
  있으면 설명 공백입니다.

실질적 의미 변화는 다음처럼 다룹니다.

| 분류 | 처리 |
| --- | --- |
| 보존·재배치 | 기존 canonical 의미를 바꾸지 않고 새 구조로 옮김 |
| source 결정 수정 | authoritative source가 유일하게 결정하는 범위만 바로잡음 |
| 확정된 새 설계 | 사용자 요구나 승인된 design의 결정 범위만 반영함 |
| 미결정 발견 | 현재 사실로 쓰지 않고 정확한 공백·owner로 분리함 |

특히 Procedure에 새 actor·외부 mutation·credential 또는 증거 lifecycle·상태 전이·
보안·실패·rollback·복구 정책·orchestration을 추가하는 것은 문장 보완이 아니라 별도
설계 의미입니다. 문서 source만으로 확정되지 않으면 완결된 현재 절차를 만들어 공백을
숨기지 않습니다.

광범위한 source는 파일마다 prose를 만드는 대신 각 source를 `현재 owner`, `새 owner
필요`, `근거 있는 제외`로 분류합니다. 전체 흐름이나 corpus 이해가 목표이면 세부 문장
전에 단계·분기·actor·실행 위치·목적·현재 owner·handoff의 저상세 관계 골격을 전체
범위에서 한 번 대조합니다. source에 없는 exact 절차는 `현재 실행 불가`, 결정 owner와
확정에 필요한 source로 경계를 표시합니다.

사용자가 검토하고 선택해야 하는 Plan·Decision·Design은 첫 화면, heading과 결정 카드에서
무엇을 만들거나 선택하는지, 무엇이 달라지는지와 왜 먼저 다루는지를 일상어로 먼저
설명합니다. 내부 설계 분류나 영어 pattern 이름이 이 질문의 직접 답을 대신하지 않습니다.
파일명, 접미사, 본문·companion과 현재 계획 단계는 이 첫 화면 계약의 적용 여부를 바꾸지
않습니다. 계획 목적 artifact의 제목과 상태·계획 단계 바로 다음에는 현재 결론, 닫힌 결과,
남은 gate와 source-backed 상태, 지금 actor와 exact 행동, 사용자 차례의 조건·응답 방식,
현재 행동 뒤 결과와 다음 단계 전환 조건을 둡니다. 사용자보다 먼저 닫을 Agent gate가 있으면
그 상태를 숨긴 채 사용자의 확정을 현재 행동으로 앞당기지 않고, 모든 gate가 닫혔으면 새
질문이나 중복 확정을 만들지 않습니다. 결론·gate 상태·actor·다음 행동이 바뀌는 후보 작성,
검증 결과, 재검토와 단계 전환에서는 사용자 검토나 다음 actor 인계 전에 첫 화면도 같은
candidate 상태로 갱신합니다.

초안에서 독자가 다시 `그 말은 무엇인가`라고 물을 가능성이 있는 명사를 표시하고, 각 명사는
다음 중 하나로 닫습니다.

- 직접 선행 사용자 문맥에서 뜻을 이해했다는 증거가 있음
- 최초 사용 문장 안에서 일상어와 concrete 대상·행동으로 뜻을 설명함
- 사용자 판단에 필요하지 않아 쉬운 표현으로 바꾸거나 뒤의 기술 상세로 옮김

뒤쪽 용어집이나 상세 절에서 처음 설명하는 것은 첫 사용을 닫은 것으로 계산하지 않습니다.
다른 미정의 영어·역할명으로 이름만 바꾼 것도 설명이 아닙니다. 반대로 API path, HTTP method,
field, enum과 표준 식별자처럼 구현·검증에서 exact 값이 필요한 literal은 번역하거나 제거하지
않고, 필요한 경우 그 literal이 가리키는 대상과 역할만 같은 단위에 붙입니다. 사용자가 직접
의미를 사용해 이해를 보인 용어도 매번 반복 정의하지 않습니다.

## 4. 구조 gate

의미 gate를 통과한 정보에 문서 정보 구조 가이드를 적용합니다.
신규 문서나 번호 corpus 개편은 path·이름을 후보로 만들기 전에 다음 순서로
topology를 고정합니다.

```text
direct directory의 실제 문서 inventory
→ direct router의 번호 group·읽기 순서
→ 기존 owner와 새 책임의 일반·특수 포함 관계
→ 기존 owner의 모든 inbound link·consumer
→ 기존 문서 확장·병합·신설·이동·삭제 후보 선택
```

일반 owner가 같은 독자 행동·source·갱신 주기에서 특수 상황을 포함하면 기존
번호·owner의 일반화와 짧은 하위 절을 먼저 선택합니다. 별도 특수 문서가 필요한
예외에서는 [문서 정보 구조 가이드의 새 문서와 분리 기준](../100-documentation-guide.md#새-문서와-분리-기준)이
요구하는 근거·관리 비용·비분리 대안을 한 번에 설명하고, 현재 generation의 명시적
사용자 승인을 받기 전에는 둘을 따로 작성하지 않습니다.

1. 독자 행동과 정보 수명에 맞는 문서 유형을 선택합니다.
2. 독자 질문, source, 갱신 주기와 보존 기간으로 병합·분리를 정합니다.
3. canonical owner를 고르고 기존 owner 전체를 대조합니다. 새 owner라면 router·번호·
   consumer 관계를 먼저 연결합니다.
4. 현재 기준·결정 근거·구현 source·작업 상태·실행 절차·대체 문서의 방향을 정합니다.
5. 번호가 있으면 가이드의 번호 class와 scope를 실제 sibling·router group map 또는
   work item body·companion에서 복원합니다. 새 numbered document link 또는 현재 작업에서
   target·anchor·표시 이름 중 하나를 실질적으로 수정하는 numbered document link는 대상
   번호와 책임이 표시 이름에 함께 드러나는지 확인합니다.
6. 이동·삭제가 있으면 old path, 새 owner, before/after topology와 모든 inbound link의
   변경 범위를 연결합니다.
7. 앞선 owner·관계·번호·topology 대조가 끝난 뒤 exact write target을 고정합니다.
8. 각 section이 답할 질문을 하나로 정하고 일반 개념에서 판단·행동 순으로 배열합니다.
9. 정보 단위의 순서·분기·계층·호출·의존·값 전달·상태·경계 관계를 판정하고 가장
   작은 표현을 고릅니다.
10. 정리·브리핑·보고가 주목적인 block은 고정 label·비교 차원이 둘 이상이면 표, 한 차원의
    독립 항목이면 목록을 주된 표현으로 사용합니다. 독자가 문장을 순서대로 읽지 않아도
    현재 판정·핵심 결과·남은 책임·다음 행동을 행 label이나 목록 marker로 찾을 수 있는지
    확인합니다. Container를 고르기 전에 정보를 우선순위·의미 분류·원자 항목·요약값·상세
    owner로 나누며, 한 행·항목에는 하나의 상태값·결론·행동만 둡니다. 독립적인 판단·근거·
    조건·예외가 함께 있으면 행을 나누거나 상세 section으로 이동합니다.
11. 같은 독자 질문은 하나의 주된 표현이 소유하게 하고 표·그림·본문의 반복을 없앱니다.
12. 사용자 검토용 Plan·Decision·Design은 heading만 읽어도 질문의 일상어 뜻을 알 수 있는지,
    첫 화면만 읽어도 현재 결론·닫힌 결과·남은 gate 상태·지금 actor와 exact 행동·사용자
    차례의 조건과 응답 방식·다음 전환을 설명할 수 있는지 대조합니다. 사용자 입력이 현재
    필요하면 선택할 값·차이·추천·영향도 같은 첫 화면에서 복원해야 합니다. 미정의 용어나
    하단 section·다른 문서 link가 이 의미를 대신 운반하면 구조 gate를 통과하지 않습니다.
13. Work item은 [작업 문서 생명주기](../work-items/100-work-item-lifecycle.md#계획-단계와-최소-artifact)에서
    현재 단계, 허용 artifact와 선행 closure를 확인합니다. 선행 결과에 따라 달라지는 다음
    단계 문서·section·후보 상세가 있으면 exact write target에서 제외합니다.

시스템 설명과 운영 절차가 함께 있는 corpus를 신설·개편할 때는 exact write target을
고정하기 전에 다음 세션 매트릭스를 실제 문서 section 단위까지 작성합니다.

```text
운영 상황·생애주기
→ 사람의 trigger·목표·실행 위치·재개 지점
→ 필요한 개념 또는 절차 정보
→ 정보 유형과 canonical owner
→ 서로 넘겨줄 link와 기존 section의 새 owner
```

매트릭스는 최소한 다음 질문을 닫아야 합니다.

- Reference/SOT만 읽고 최초 구축·평소 반복·변경·복구에서 시스템의 actor, 구성요소,
  artifact·값 전달, 자동화·수동 경계와 결과를 복원할 수 있는가
- Operations router에서 현재 존재하는 각 운영 상황을 선택하고 한 목적의 runbook으로
  진입할 수 있는가
- 각 runbook의 상위 section이 사람이 시작·관찰·중단·재개·복구하는 행동에 필요한가
- 이동·삭제할 기존 section마다 고유 정보와 새 owner가 연결됐는가

새 executable이나 수동 절차가 최초 target 상태를 만들면 실제 caller, trigger, 입력·
credential, postcondition과 중단·재개 경계를 먼저 복원합니다. Archive에 포함됐거나
downstream consumer가 선행 조건으로 사용한다는 사실은 실행 완료나 canonical Procedure
owner의 근거가 아닙니다. 사람이 직접 시작하는 독립 lifecycle이면 그 사람 목표에서
Procedure와 router까지 하나의 owner path로 연결하고, README는 interface 설명과 canonical
Procedure link만 소유합니다.

Procedure를 쓰거나 실질적으로 확장하기 전에는 정상·실패 경로의 각 사람 행동을 다음
실행 계약으로 복원합니다.

```text
사람의 trigger·목표
→ actor·실행 host·현재 directory
→ exact entrypoint(script·workflow·Console·표준 단일 명령)
→ 사람이 준비할 입력·secret source와 각 입력이 들어갈 exact field·argument·변수
→ 직접 관찰할 기대 결과
→ 실패 시 중단·재실행 entrypoint
```

공용 Procedure 후보는 section을 쓰기 전에 현재 scope의 동급 대상 둘 이상을 raw source에서
선택해 다음 치환 대조를 수행합니다.

```text
공용 입력과 공통 시작 상태
→ 대상 A에 적용한 단계·actor·완료·복구
→ 대상 B로 이름만 바꿔도 같은 계약이 성립하는가
→ 성립하지 않는 최초 단계와 그 전용 owner
→ 공용 본문에 남길 공통 전이와 전용 owner로 보낼 handoff
```

한 대상의 현재 executable이 존재하거나 상세 절차가 이미 작성돼 있다는 사실은 공용 본문
편입 근거가 아닙니다. 치환 뒤 provider Console, resource 고유 schema, 별도 폐기나 기능 확인
때문에 계약이 달라지면 그 부분은 공용 Procedure에서 제거하고 전용 owner로 연결합니다.
동급 대상이 source에 하나뿐이면 가상의 이름만 바꿔 합격시키지 않고, 공용 범위를 입증할
두 번째 실제 대상이 생길 때까지 특수 Procedure로 유지합니다.

Runbook에 이 계약을 표현할 때에는 각 실행 단계를 다음 순서로 작성합니다.

```text
행동과 대상을 나타내는 단계 제목
→ 운영자가 그대로 수행할 Console 조작·표준 명령·script 진입점
→ 확인할 결과
→ 이유·판단 분기·중단·복구 설명
```

- 모든 executable Procedure는 첫 실행 단위 전에 문서의 실행 위치 적용 범위를 확정합니다.
  문서의 모든 실행 단위가 같은 actor·host·directory 계약을 사용하면 문서 상단에
  `기본 실행 위치`와 `기본 시작 directory`를 한 번 선언하고, 별도 override가 없는 전체
  실행 단위에 적용합니다. 같은 값을 각 명령 앞에 반복하지 않습니다.
  Filesystem directory가 없는 Console 조작에는 시작 directory field를 만들지 않고,
  terminal 명령이 어느 directory에서도 같으면 `제한 없음`을 명시합니다.
- 둘 이상의 실행 위치를 사용하는 문서는 문서 상단의 위치 목록이나 역할 요약만으로
  완료하지 않습니다. 위치가 같은 실행 단위를 묶는 가장 가까운 heading 아래에 `실행 위치`와
  `시작 directory`를 선언합니다. 이 선언은 해당 heading subtree에만 적용하며 sibling이나
  다음 section으로 암묵적으로 승계하지 않습니다. Nested section이 위치를 바꾸면 그
  section에서 override하고, 종료 뒤에는 가장 가까운 상위 선언으로 돌아갑니다.
- Heading 이름의 `Local`·`EC2`·`Console`, 도입 prose, 이전 단계의 SSH 접속 상태, 문서
  상단의 actor·위치 나열과 명령 내용은 실행 위치 선언의 보조 정보일 뿐 적용 범위를 대신하지
  않습니다. Router·Reference처럼 실행 단위가 없고 canonical Procedure로만 인계하는 문서는
  이 위치 선언 대상에서 제외합니다.
- 단계 제목 바로 아래에는 실행 위치·필수 입력과 실제 실행 단위를 먼저 둡니다. 배경·선택
  이유·상세 해석을 먼저 길게 서술해 운영자가 무엇을 실행할지 찾아야 하게 만들지 않습니다.
- `실행 위치`, `시작 directory`, `필수 입력`, `실행 조건`처럼 label과 `:`로 시작한 field의
  값에는 그 label이 맡은 정보 역할만 둡니다. 실제 위치와 금지 위치, 위치와 directory,
  조건과 위치처럼 역할이 다른 정보는 한 field에 문장으로 이어 붙이지 않습니다. 별도 정보가
  필요하면 고유 label이나 field 아래 설명으로 분리하고, positive 값만으로 실행 대상을
  확정할 수 있으면 같은 대상을 반대로 다시 나열하지 않습니다.
  Account·region·host·session처럼 실행 장소를 하나로 식별하는 qualifier는 위치와 같은
  역할이므로 별도 field로 기계적으로 분해하지 않습니다.
- 실행 계약의 `허용`, `권장`, `필수`, `금지` 강도는 authoritative source가 증명하는 결과
  차이와 맞춥니다. 둘 이상의 경로가 필수 결과를 충족하고 한 경로가 추가 증거·안전성·운영
  이점을 제공하면 모두 허용하되 더 강한 경로를 권장으로 표시하고 그 이점을 실행 단위 뒤에
  설명합니다. 추가 이점을 다른 유효 경로의 금지나 단일 필수 경로의 근거로 바꾸지 않습니다.
  필수나 금지는 다른 경로가 필수 결과를 충족하지 못하거나 실행 불가·안전 위험·unsupported
  상태라는 배제 근거가 source로 확인될 때만 사용하고, 그 결과와 근거를 같은 실행 단위에서
  식별할 수 있게 설명합니다.
- 필수 입력이나 placeholder는 같은 실행 단위에서 `어느 앞 단계·명령·Console field가 값을
  생산하는가 → 출력·선택값 중 무엇을 복사하는가 → 현재 명령의 어느 field·argument·변수에
  어떤 표기 제거·변환을 거쳐 넣는가`를 연결합니다. 공통 표나 떨어진 section에 source만
  적고 현재 실행 단위에서 이 handoff를 다시 연결하지 않으면 실행 가능한 입력 계약으로
  계산하지 않습니다.
- Console 단계는 이동 경로와 입력·선택 행동을 번호나 입력표로 먼저 제시하고, 완료 기준과
  이유·중단 조건은 그 아래에 둡니다.
- 한 단계에서 운영자가 연속 실행하고 결과를 각각 관찰해야 하는 독립 명령이 둘 이상이면
  한 code block을 유지합니다. 각 명령 앞에는 `무엇을 하는가`와 `확인할 값`을 각각 한 줄
  주석으로 쓰고, `printf '\n\n========== [N/M] <구분 이름> ==========\n\n'` 형식의
  출력 구분자를 둡니다. 상세한 실행 이유와 결과 해석은 code block 아래에서 같은 번호로
  필요한 핵심만 설명합니다.
- 같은 shell 결과를 만드는 변수 할당·pipeline·조건문·subshell·heredoc, 한 script 호출의
  인자와 line continuation은 독립 명령 목록으로 쪼개지 않습니다. 출력이 없는 helper마다
  구분자를 추가하거나 각 명령을 별도 heading·code block으로 분해하지 않습니다.
- 독립 명령마다 actor·실행 위치·입력·복구 경계가 실제로 달라지면 하나의 목록으로 합치지
  않고 실행 단계 자체를 나눕니다.

이 계약은 문서용 표를 만들기 위한 형식이 아니라 source와 구현의 실행 가능성을 판정하는
입력입니다. 반복 가능한 검증·값 조립·secret 전달·여러 command의 오류 처리처럼 하나의
결과를 만드는 shell logic이 필요한데 canonical repository entrypoint가 없으면, 그 공백을
긴 code block으로 구현해 Procedure에 넣지 않습니다. `현재 안전한 entrypoint 없음`과 필요한
구현 owner를 미충족 dependency로 반환합니다. 현재 사용자 원자가 해당 구현까지 독립적으로
포함하지 않으면 문서 writer는 개발 script를 계획·작성하거나 `$develop`을 호출하지 않고,
문서 결과의 차단 여부·선택지·영향을 보고해 별도 논의와 실행 결정을 기다립니다. 이 반환은
구현 결과와의 관계를 표시할 뿐 현재 작업 범위를 자동 확장하지 않습니다. 반대로 표준 도구의
단일 명령과 사람이 직접 판단해야 하는 Console 단계까지 무조건 wrapper script로 만들지
않습니다.

기술 이름이나 script 번호가 같다는 이유만으로 서로 다른 생애주기·사람 목표를 한
runbook에 합치지 않습니다. 반대로 같은 개념을 각 runbook에 복제하지 않고 개념 owner와
양방향으로 연결합니다. 이 매트릭스는 작업 중 coverage 증거이므로 repository에 별도
catalog로 저장하지 않습니다.

구조 gate에서 새 owner, 문서 이동·삭제, 번호나 router·inbound link topology 변경이
드러나면 2절의 변경 깊이를 다시 판정합니다. 초기 분류가 국소·일반이었더라도 실제 효과가
중대한 구조 변경이면 중대로 올리고, 아래 proposal 감사와 final 감사를 생략하지 않습니다.

중대한 구조 작업은 이 제안을 작성 전에 fresh reviewer의
`$verify-documentation proposal`로 감사합니다. proposal은 문장을 미리 승인하는 단계가
아니라 독자 질문·source·owner·보존·outline·topology가 작성 가능한지를 판정합니다.

둘 이상의 문서나 반복 section이 같은 정보 구조·설명 깊이·coverage 계약을 소비하면,
proposal 통과 뒤 작성 전에 다음 authoring contract를 작업 중 증거로 고정합니다.

```text
공통 독자 질문과 사용자 품질 차원
→ 공통 section·heading hierarchy와 각 level의 설명 책임
→ 전체 문서·section·source·고유 정보 inventory
→ 전체 범위의 저상세 골격
→ 상세 대표 문서 하나와 source-first closure oracle
→ 대표 문서가 닫힌 뒤 확장할 나머지 문서
```

상단 요약과 하단 상세처럼 두 단계 이상의 설명 깊이가 요구되면, 각 level이 답할 질문과
상·하위 관계를 contract에 포함합니다. 명령·field·option처럼 유한한 producer 집합을
설명하는 문서는 producer inventory와 `producer → 설명 위치 → 출력·판정·다음 탐색`의
coverage를 대표 oracle에 포함합니다. 개수 상한이나 만능 문서 template를 새로 만들지
않고, 사용자가 요구한 품질 차원과 실제 source가 요구하는 관계만 고정합니다.

전체 저상세 골격을 먼저 대조하고 상세 대표 문서 하나를 완성해 6절의 작성자 source-first
대조를 통과하기 전에는 같은 contract를 소비하는 나머지 문서를 작성하지 않습니다.
Proposal reviewer는 구조의 작성 가능성을 판정할 뿐 이 대표 결과를 대신하지 않으며,
final reviewer를 미완성 문장의 점진적 작성 oracle로 사용하지 않습니다.

## 5. 작성과 이관

### Runbook 결함 복구 gate

운영자가 runbook을 실행한 결과나 static source 대조에서 runbook 자체의 실행 불가·stale
절차·authoritative source 불일치가 직접 확인되면, 채팅을 임시 보정 계층으로 사용하지
않고 다음 순서로 canonical Procedure를 복구합니다.

1. 결함을 확정하는 source 대조가 끝난 직후 어떤 mutation보다 먼저 별도 user-visible
   commentary로 exact runbook·실패한 실행 단위·관찰 결과와 안전한 중단 지점을 알립니다.
   Source를 읽겠다거나 판정하겠다는 사전 안내는 이 통지를 대신하지 않습니다. 이 통지
   전후에 runbook과 다른 대체 명령·일부 token만 바꾼 명령·추측한 우회 절차를 실행 안내로
   제시하지 않습니다.
2. 작업 실행 워크플로우가 현재 generation의 원문에서 canonical runbook 수정의 positive
   source를 확인한 경우에만 `$documentation`으로 원문을 수정합니다. 결함 발견과 수정 금지의
   부재는 mutation 권한을 만들지 않습니다. 질문·설명·검토 원자이면 결함, 안전한 중단
   지점과 필요한 canonical 후속만 답하고 mutation하지 않습니다. 수정 권한은 있지만
   source로 안전한 절차를 확정할 수 없으면 명령을 만들지 않고 runbook에 현재 실행 불가
   상태, blocker, 결정 owner와 재개 조건을 기록합니다.
3. Writer를 호출하기 직전에 실패한 전체 실행 단위를 다시 읽고 다음 입장 원장을 고정합니다.

   ```text
   기존 실행 계약 field
   → 같은 실행 단위 안의 current exact value·producer→입력 위치 handoff·명시적 해당 없음
   → 현재 generation이 수정을 요청한 field·exact span
   → 그대로 보존할 field
   → writer 입력에서 제외할 Agent 발견·인접 제안
   ```

   현재 generation의 positive source가 변경하지 않은 actor·실행 위치·entrypoint·입력·
   관찰 결과·종료·재실행 계약은 보존합니다. Agent가 발견한 인접 개선이나 더 강한
   보장은 writer 입력으로 승격하지 않습니다. 그 발견이 요청된 수정을 실제로 막으면
   mutation 전에 exact blocker와 필요한 결정을 보고하고, 막지 않으면 요청 범위의 복구가
   끝난 뒤 별도 제안으로만 전달합니다.
   기존 단위에 필수 field가 없거나 placeholder의 producer→입력 위치 handoff를 주변
   section·대화·운영자 추론으로 채워야 하면 `current exact value`로 만들지 않습니다.
   요청된 복구로 함께 닫을 수 있으면 같은 실행 단위에서 복구하고, source로 닫을 수 없으면
   writer·실행 안내 전에 중단합니다.
4. 복구 과정에서 read-only 확인을 새로 넣거나 기존 확인을 필수로 올리기 전에는 각 확인을
   `관찰값 → 그 값을 실제로 소비하는 다음 입력·분기·중단·rollback·postcondition`에
   연결하고 3항의 입장 원장에 기록합니다. 다음 행동이 그 값에 따라 달라지지 않거나 다른
   canonical entrypoint가 같은 값을 이미 fail-close하면 선택 진단으로 분리하며, 요청된
   실행 block의 필수 입력·완료 조건·중단 조건에 넣지 않습니다.
5. 수정 diff는 source가 요구하는 최소 범위로 제한할 수 있지만 검증 범위는 실패한 문장만이
   아니라 그 문장이 속한 전체 실행 단위로 잡습니다. Actor·실행 host·현재 directory·exact
   entrypoint·입력 source·기대 결과·실패 중단·재실행 지점을 다시 대조하고, 같은 결함을
   공유하는 형제 명령과 반복 section도 확인합니다.
6. 작성자의 두 번째 source-first 대조와 적용 가능한 static·live 검증이 끝난 뒤에만
   runbook을 다시 실행 가능한 현재 절차로 안내합니다. Static 검증만 수행했다면 live
   성공·멱등성·복구 가능성까지 확인한 것으로 확대하지 않습니다.
7. 사용자에게 재개 명령을 전달할 때에는 검증한 current runbook에서 수정된 문장이 속한
   전체 실행 block을 다시 읽어 그대로 인용합니다. 기억으로 재작성하거나, 원문 밖에서
   편집한 축약본·부분 보정본을 실행 명령으로 전달하지 않습니다.

1. 반복 문서 contract가 있으면 전체 저상세 골격과 상세 대표 문서 하나를 먼저 작성·
   대조하고, 대표 oracle을 통과한 같은 contract로 나머지 문서를 확장합니다.
2. 첫 화면에 목적, 책임, 핵심 결론·관계와 다음 탐색 경로를 둡니다.
3. 독자의 판단·행동에 필요한 상세만 하위 계층에 둡니다.
4. 다른 owner의 현재 사실은 복제하지 않고 방향이 표시된 link로 연결합니다.
5. 새 내용을 append하기 전에 outline과 owner를 다시 보고 충돌·중복·폐기 결론을
   같은 범위에서 제거합니다.
6. 계획이 결과로 바뀌면 실행 기록을 누적하지 않고 Result/Status 책임에 맞게
   정규화합니다.
7. 병합·분리·이동·삭제 전에 모든 고유 정보의 새 owner를 연결합니다.
8. router, 제목, 번호, 상대 link·anchor, 용어와 대체 관계를 같은 변경에서
   동기화합니다.

이관 중 `유지`로 분류한 문서도 내용 검증 면제는 아닙니다. 현재 owner와 독자 질문을
actual source에서 복원할 수 있어야 합니다. 전체 적용을 주장하지 않는 변경은 건드리지
않은 corpus를 임의로 완료 범위에 포함하지 않습니다.

## 6. 결과 gate: 작성자의 두 번째 source-first 대조

모든 Markdown 변경은 편집 뒤 작성자의 기존 판단이나 완료표부터 보지 않고, 이 작업
깊이와 작업 실행 workflow의 검증 대상 admission에서 정한 변경 단위·직접 영향 범위와
source를 다시 수집해 두 번째로 대조합니다. 이 단계는 routine 변경의 문서 검증 owner이며
독립 verifier 호출로 대체하지 않습니다.

| 방향 | 확인할 내용 |
| --- | --- |
| source·독자 질문 → 후보 | 필요한 현재 사실과 답이 빠지지 않았는가 |
| 후보 section·예시 → source·독자 필요 | 근거 없는 주장, 반복·과잉 상세와 새 의미가 없는가 |
| router → canonical owner → 상세 source | 탐색 방향과 owner가 하나로 이어지는가 |
| 현재 사실 → 관련 문서 | 폐기 결론, 중복 owner와 현재성 충돌이 남지 않았는가 |

후보만으로 핵심 개념·순서·분기·계층·호출·값 전달·상태를 하나의 의미로 복원한 뒤
source의 구성요소·방향·조건·전달값과 비교합니다. static source·syntax 확인과 실제
환경 실행 증거를 구분합니다.

현재 개념을 다루는 후보는 각 section과 outbound link를 `현재 영구 owner`, `구현 source`,
`작업 상태`, `선택 과정·배제 대안` 중 하나로 역분류합니다. 다음 중 하나라도 있으면 결과
gate를 통과하지 않습니다.

- 영구 SOT가 현재 정의·구조·관계·작동 원리·불변식·제품 범위 또는 이를 이해하는 데 필요한
  현재 이유를 work item·backlog·작업 `-design`이 소유한다고 선언합니다.
- 영구 SOT만 읽어서는 현재 mental model을 복원할 수 없고 work item이나 design을 반드시
  읽어야 합니다.
- Work item이 현재 기준을 영구 SOT로 승격하지 않은 채 자신의 계획·설계를 현재 canonical
  contract로 가리킵니다.
- Router가 현재 개념을 찾는 독자를 work item으로 보내거나, 작업 당시의 선택 과정을 찾는
  독자를 현재 SOT와 구분하지 않습니다.

Markdown topology는 다음 깊이로 직접 확인합니다.

- local link target과 명시한 anchor가 존재합니다.
- 구조 번호는 actual sibling과 direct-directory router의 group map에서 다시
  계산합니다.
- work item 번호는 유형·연도 디렉터리의 actual body가 `001`부터 이어지고 companion이
  같은 번호·slug를 공유하는지 확인합니다.
- 이동·삭제는 old path의 모든 inbound link, 대체 owner, 최종 directory topology와
  의도하지 않은 빈 디렉터리를 확인합니다.
- 전체·특정 집합 적용 주장은 변경하지 않은 artifact까지 대조했거나 적용 제외와 남은
  이관 범위를 분명히 합니다.
- Proposed·Planned work item은 현재 `계획 단계`, 허용 artifact 수와 section, 선행 closure를
  lifecycle에 다시 대조합니다. 다음 단계 상세나 목적이 겹치는 owner가 하나라도 남으면
  문장 완성도와 무관하게 실패합니다.
- Plan·Decision·Design 목적 artifact의 첫 화면에서 현재 결론, 닫힌 결과, 남은 gate의
  source-backed 상태, 지금 actor·exact 행동, 사용자 차례와 응답 방식, 다음 전환 중 하나를
  하단 section이나 다른 문서에서 찾아야 하면 실패합니다. 일반 본문에는 적용하고 같은 계획
  목적의 `-design`·companion에는 적용하지 않거나, 모든 gate가 닫힌 문서에 새 결정·재확인을
  만들 때도 실패합니다.
- 정리·브리핑·보고가 주목적인 block의 고정 항목과 비교 차원을 prose에 묻어, 독자가 문장을
  순서대로 해석해야 현재 판정·핵심 결과·남은 책임·다음 행동을 구분할 수 있으면 실패합니다.
  각 값을 행 label이나 목록 marker로 바로 찾을 수 있어야 합니다.
- 기존 prose를 표의 셀이나 목록 항목으로 옮기기만 했거나, 한 행·항목이 독립적인 판단·근거·
  조건·예외를 함께 소유하면 실패합니다. 결론 한 문장과 행 label·요약값 또는 목록 marker만
  훑어 전체 판정, 닫힌 결과와 남은 일, 현재 actor·행동, 다음 전환·조건을 복원할 수 있어야
  합니다. 상세를 읽어야만 이 값을 분리할 수 있으면 정보 분류·압축부터 다시 수행합니다.
- 새 numbered document link 또는 현재 작업에서 target·anchor·표시 이름 중 하나를
  실질적으로 수정한 numbered document link는 대상 파일의 세 자리 번호와 책임을 함께
  표시합니다. 번호가 없는 artifact에는 임의 번호를 만들지 않고, 건드리지 않은 기존
  corpus는 점진 이관 범위에서 제외합니다.

새 자연어 schema·catalog·custom 의미 verifier를 만들어 이 대조를 자기일관성 검사로
바꾸지 않습니다. 결정적인 filesystem·link 사실은 기존 표준 도구로 확인하고 의미는
source-first 대조로 판정합니다.

시스템 설명과 운영 절차 corpus는 다음 중 하나라도 남으면 실패합니다.

- 시스템의 작동 원리·actor·artifact 전달·자동화 경계를 Procedure를 읽어야만 복원할 수
  있습니다.
- Runbook 하나가 서로 독립적인 최초 구축·반복 운영·변경·장애 복구 상황이나 사람
  목표를 함께 소유합니다. 같은 상황·목표 안의 단계별 복구 checkpoint는 여기에
  해당하지 않습니다.
- Runbook의 상위 section이 사람의 입력·행동·관찰·성공·복구가 아니라 자동화 내부 구현
  또는 선택되지 않은 설계를 주로 설명합니다.
- Runbook의 단계에서 actor·실행 host·현재 directory·exact entrypoint·입력 source·기대
  결과·재실행 지점 중 하나를 독자가 주변 section이나 shell 구현을 해독해 추론해야 합니다.
  입력 source가 있더라도 그 생산값 중 무엇을 현재 placeholder·field·argument·변수에 어떤
  표기 제거·변환으로 넣는지 같은 실행 단위에서 연결되지 않은 경우도 실패입니다.
- Operations corpus의 실행 위치 coverage를 확인할 때 각 Console 조작·표준 명령·script
  호출을 producer로 열거하고, 각 producer가 같은 문서의 유효한 document-level 기본 선언
  또는 가장 가까운 section-level override 하나로 역추적되는지 대조하지 않았습니다. 일부
  `실행 위치` field, heading·요약표나 변경한 section만 확인하고 나머지 실행 단위의 위치를
  추론해야 하는 상태에서 전체 적용을 주장하는 경우도 실패입니다.
- Runbook 단계의 실제 Console 조작·명령·script 진입점보다 배경·상세 설명이 먼저 나와,
  단계 제목에서 실행 단위까지 설명을 건너뛰어 찾아야 합니다.
- Runbook의 label field가 이름과 다른 정보 역할을 함께 소유합니다. `실행 위치`에 금지
  위치·directory·실행 조건을 잇거나 `실행 조건`에 terminal 위치를 포함해, 독자가 label의
  값과 뒤 문장을 다시 분해해야 실행 계약을 복원하는 경우를 포함합니다.
- Authoritative source가 둘 이상의 경로에서 필수 결과가 성립한다고 증명하는데 한 경로의
  추가 이점만으로 다른 경로를 금지하거나 단일 필수 경로로 제한했습니다. 반대로 실제
  실행 불가·결과 미충족·안전 위험·unsupported 근거가 있는 금지를 이유 없이 권장으로
  낮추거나, 권장 경로가 제공하는 추가 증거·이점을 설명하지 않은 경우도 실패입니다.
- 같은 실행 위치에서 결과를 각각 관찰할 독립 명령 목록을 명령별 heading·code block으로
  불필요하게 분해했거나, 하나의 code block에 두면서 각 명령의 행동·확인값 주석과 실행
  출력 구분자 또는 아래의 번호별 핵심 이유·해석 중 하나를 누락했습니다. 반대로 하나의
  shell 결과를 만드는 compound command 내부를 독립 명령 목록처럼 잘라 실행 의미를
  훼손한 경우도 실패합니다.
- 반복 가능한 shell orchestration이 Procedure code block에만 존재하고, 이를 실행·검증·
  재사용할 canonical repository entrypoint가 없거나 그 공백을 명시하지 않았습니다.
- Credential 획득, 둘 이상의 명령·API 상태 전이 또는 독립 실패·재개가 있는 사람이 시작하는
  lifecycle의 canonical Procedure·router가 없고, downstream Scenario·README를 찾아야만
  caller·입력·postcondition·재개 경계를 복원할 수 있습니다. 실행 빈도나 전체 줄 수는 이
  판정을 바꾸지 않습니다. 표준 명령 하나가 사람의 전체 목표와 postcondition을 원자적으로
  닫는 경우에만 Scenario 직접 소유를 허용합니다.
- Router가 기술 목록만 제공해 독자가 현재 운영 상황에 맞는 canonical procedure를
  선택할 수 없습니다.
- Operations Scenario의 실행 항목만 읽어서는 행동, actor·실행 위치, exact script·workflow·
  Console·표준 명령 진입점과 완료 조건을 복원할 수 없습니다. Repository script가 없는
  수동 단계인데 실제 진입점과 script 부재도 명시하지 않은 경우를 포함합니다. 안전한
  진입점이 미확정인데 실행 가능한 명령을 지어내거나, 실행 불가·결정 owner·중단 지점을
  표시하지 않은 경우도 실패합니다.
- Operations Scenario의 재사용 흐름을 빈 환경에서 순서대로 재생했을 때 actor·대상·상태
  전이·완료 조건이 같은 작업을 revision·시점·`확장`·`재전달` 이름만 바꿔 두 번 수행합니다.
  기존 환경에 새 inventory를 반영해야 하는 live delta를 영구 실행 항목으로 추가하거나,
  이전 완료 snapshot과 현재 미완료 상태를 checklist에 함께 누적한 경우를 포함합니다.
- 제목·최종 완료 조건이 다른 복합 항목 안에 같은 artifact·actor·effective target state의
  하위 전달 전이가 다시 나타나지만, 원자 행동을 대조하지 않아 중복을 통과시켰습니다.
- 이동·삭제한 기존 section의 고유 정보가 새 concept 또는 operation owner 어디에도
  대응되지 않습니다.

이 대조는 개념 문서와 operations corpus를 각각 독립적으로 읽은 뒤, 방향이 표시된
link로 서로 이동할 수 있는지도 확인합니다. 두 corpus를 합쳐 읽었을 때만 누락이
가려진다면 통과로 판정하지 않습니다.

## 7. 중대한 문서 전문 감사

중대한 구조 proposal과 작성자 대조가 끝난 중대한 final 후보만
`$verify-documentation`의 문서 전문 축에 보냅니다.

- proposal은 작성 전에 고정된 독자 질문·source·owner·보존·outline·topology 후보를
  감사합니다.
- final은 더 이상 의도된 수정이 없는 동결 artifact와 실제 source를 감사합니다.
- final이 여러 문서에 공통인 hierarchy·coverage contract 결함을 처음 발견하면 finding별
  patch loop를 열지 않습니다. 작업 실행 workflow에 공통 contract·inventory·대표 결과를
  `Invalidated`로 반환하고, 새 authoring contract에서 대표 문서부터 다시 닫습니다.
- Proposal·final finding 뒤 canonical rule을 변경하면 기존 verdict를 폐기하고, 후발 rule이
  아니라 원래 사용자 완료 oracle에서 proposal부터 다시 감사합니다. Candidate와 rule을 함께
  바꿔 서로 일치시킨 결과를 통과 근거로 사용하지 않습니다.
- Final은 대표 독자가 canonical entrypoint만 따라 입력 준비부터 postcondition까지 끝내는
  walkthrough를 포함합니다. 명령·API 이름의 존재, link 정합성과 하위 test 성공만으로 실행
  coverage를 대신하지 않습니다.
- reviewer는 작성 맥락·진단·기대 답을 받지 않은 fresh context에서 원래 목적과 source로
  전달받은 검증 대상의 직접 영향 범위를 독립 대조합니다. 대상 밖으로 전체 감사를
  확대해야 하면 감사하지 않고 작업 실행 workflow의 사전 보고·허락 gate로 반환합니다.
- routine 국소·일반 변경에는 이 전문 축을 추가하지 않습니다.
- 계획·개발 verifier의 일반 문서 축은 중대한 문서 final을 대체하지 않습니다. 해당
  verifier가 `$verify-documentation final`의 독립 입력·동결 대상 감사 축·verdict까지 명시적으로
  수행한 경우에만 같은 specialist phase를 이름만 바꾸어 중복하지 않습니다.

독립 감사의 축과 `통과`·`실패`·`판정 불가` 판정은 verify-documentation이 소유합니다.
감사 결과는 작업 실행 워크플로우의 discovery·closure에 반환하며, 이 문서에서 별도
상태 전이·반복 횟수·차단·완료 알고리즘을 만들지 않습니다.

## 8. 문서 검증 결과 인계

다음을 작업 실행 워크플로우의 최종 discovery·closure 입력으로 넘깁니다.

- 적용한 문서 깊이와 actual source·문서 범위
- 변경한 canonical owner·관계와 동기화한 router·link·번호
- 작성자의 두 번째 source-first 대조 결과
- 해당할 때 fresh proposal·final 감사 판정
- 전체 적용 주장의 제외 또는 남은 이관 범위
- static 확인과 live 실행 증거의 경계

전체 상태와 사용자 보고는 작업 실행 워크플로우와
[140 완료 및 작업 보고 워크플로우](140-completion-reporting-workflow.md)가 소유합니다.

## 관련 문서

- **문서 유형·owner·보존·관계·번호**: [100 문서 정보 구조 가이드](../100-documentation-guide.md)
- **요청·권한·공통 lifecycle·closure**: [100 작업 실행 워크플로우](100-work-execution-workflow.md)
- **작업 문서 전환**: [100 작업 문서 생명주기](../work-items/100-work-item-lifecycle.md)
- **사용자 보고**: [140 완료 및 작업 보고 워크플로우](140-completion-reporting-workflow.md)
