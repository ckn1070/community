# 완료 및 작업 보고 워크플로우

이 문서는 이미 확정된 작업 상태를 사용자가 판단할 수 있는 채팅 보고로 투영하는
방법만 소유합니다. 요청·권한·결과 단위·차단·완료 판정은
[작업 실행 워크플로우](100-work-execution-workflow.md), Git 실행 의미는
[커밋 워크플로우](300-commit-workflow.md)가 소유합니다.

## 1. 보고 입력

보고 전에 대화 요약이 아니라 canonical owner와 실제 artifact에서 다음을 읽습니다.

- 전체 목표와 현재 결과 단위의 상태
- 작업 실행 workflow가 동결한 operation map 전체, 보존된 상태·증거와 현재 응답 범위의
  활성 원자
- 현재 응답 범위 각 원자의 canonical owner source와 직접 상태 증거
- 완료·미완료 결과와 그 직접 증거
- terminal gate, 검증의 성공·실패·미실행·검증 불가
- material Agent 판단과 사용자 소유 결정의 선택·근거·배제 대안·재검토 조건
- AI·사용자·외부 실행자의 남은 책임과 재개 조건
- stage, commit, push, remote CI·Sonar와 live 적용 상태

보고는 상태를 새로 판정하거나 전문 owner의 결정을 다시 설계하지 않습니다. 입력이
모순되면 임의로 해소하지 않고 owner와 불일치를 밝힙니다.

계획 checkpoint가 `Required-Pending`이면 보고 actor는 이를 정상적인 `commit 없음`이나
사용자 차례로 투영하지 않습니다. 사용자 대기, Git 이외 phase handoff와 terminal 보고를
중단하고 `$git-finalize`로 반환합니다. 실제 commit object·parent·tree·patch·잔여 dirty가
확인된 `Committed` 또는 공통 blocker gate가 직접 증명한 `Blocked`만 다음 보고 상태로
소비합니다. 별도 commit 요청 부재와 push 미승인은 `Blocked`의 근거가 아닙니다.

실행 회고 보고는 작업 실행 workflow가 확정한 closure identity를 그대로 소비합니다.
통제별 처분, canonical owner의 실제 적용 효과 또는 이미 적용된 exact control identity,
모델링된 통제 효과와 필요한 실제 invocation의 개별 verdict, Git 자격과 전체 회고 상태 중
하나라도 없으면 History·상태 문서 변경이나 commit 존재로 보충하지 않습니다. 가장 낮은 필수
verdict가 `실패` 또는 `검증 불가`이면 첫 문장부터 회고를 `진행 중` 또는 `검증 불가`로
투영하고, 통제 적용·행동 검증·Git·복귀 중 닫히지 않은 exact phase를 분리합니다.

외부 tool·Action·실행 platform을 도입하거나 중요한 방식으로 바꾼 결과는
[도구 도입 워크플로우](130-tool-adoption-workflow.md)의 결정 기록에서 선택한 완전한 경로,
배제한 더 단순한 표준 경로와 선택 이유를 가져옵니다. 사소한 구현 선택을 모두 나열하지
않지만 비용·성능·위험·외부 dependency를 바꾼 판단이나 사용자가 이유 보존을 요구한 판단은
파일·test 목록으로 대체하지 않습니다. 이 입력이 없으면 선택 이유까지 보고했다고
표현하지 않습니다.

보고는 operation map에 없는 `다음 작업`, 추천 순서, 대기·전체 handoff를 답변 조립 중
새로 만들지 않습니다. 다음 행동이 canonical 상태로 하나로 확정됐거나 작업 실행 workflow가
전문 판정을 map에 병합한 뒤에만 그 관계를 투영합니다. 여러 실행 가능한 결과 중 하나를
임의로 고르거나, 전문 결과에서 생략된 원자와 현재 milestone 뒤의 원자를 범위 밖으로
판정하지 않습니다. 반대로 사용자가 현재 답변에서 명시적으로 제외한 원자는 우회해 다시
넣지 않으며, 그 제외를 canonical operation의 영구 삭제로 확대하지 않습니다.

저장된 복귀점은 현재 원자의 terminal 뒤에 사용할 위치이며 그 자체가 `다음 작업`의
source가 아닙니다. 현재 원자가 검증 실패·후보 원복·commit 없음·일시 중단으로
`Active`·`Invalidated`에 남거나 실제 차단 gate로 `Blocked`이면, 보고는 각각 현재 원자의
다른 안전한 해결 경로 또는 blocker 해제 뒤 재개 phase를 next로 투영합니다. 이 상태에서
복귀점을 handoff·추천·다음 단계로 쓰지 않습니다. 현재 원자의 `Closed` 또는 사용자의
canonical 취소·제외·대체가 확인된 뒤에만 복귀점을 runnable next로 투영합니다.

`지금 진행 가능한 것`은 runnable 추천 후보를 묻는 상태 조건이지 같은 명시 범위의
non-runnable 원자를 자동 제외하는 지시가 아닙니다. Canonical owner가 확인한 runnable 결과를
제시할 때 직접 대비되는 exact blocker와 재개 조건도 짧게 투영합니다. 사용자가 대상이나
상태 설명을 현재 답변에서 명시적으로 제외한 경우에만 그 projection에서 생략합니다.

저장소 Shell 변경을 보고할 때는
[스크립트 엔지니어링 가이드](240-script-engineering-guideline.md)의 완료 증거에서 다음을
가져옵니다.

- 실제 실행한 ShellCheck version과 local pin 상태
- 검사한 exact path 또는 모호하지 않은 path 집합과 통과·실패 결과
- 남아 있는 suppression의 rule·이유·제거 조건
- 실행하지 않은 integration·live 계층과 그로 인해 증명되지 않은 범위

ShellCheck를 실행하지 못했거나 진단이 남으면 static analysis 통과나 local 완료로
표현하지 않습니다. 해당하지 않는 작업의 보고에는 이 항목을 빈 형식으로 강제하지
않습니다.

둘 이상의 대상이나 `그때`·`나중에`·`현재` 상태를 비교하는 질문에서는 보고 단위를
`대상 + 질문이 가리킨 기준 시점 + 주장 단계`로 고정합니다. 주장 단계는 최소한 원인
분석, 상태 변경, 직접 검증과 lifecycle 완료를 서로 대신하지 않게 나눕니다. 각 단위는
그 시점의 직접 증거에서 복원하며, 나중에 생긴 artifact로 이전의 미실행 상태를 소급해
완료로 바꾸지 않습니다. 관련 commit이나 파일 변경은 해당 mutation의 증거일 뿐, 별도
증거가 없는 분석·검증·전체 lifecycle 완료까지 증명하지 않습니다.

## 2. 목적별 owner

| 목적 | primary owner | 보고가 보여줄 것 |
| --- | --- | --- |
| 계획·결정 | `$dev-plan`, `$improvement` 또는 설계 owner | 준비도, 열린 결정, 영향과 다음 입력 |
| 진행·완료·차단·인계 | `$report-task` | 전체 위치, 완료·미완료, 책임, 재개 경로 |
| 기술 설명 | `$explain-tech` | 직접 답, mental model, 근거와 경계 |
| 검증 | 해당 verify skill | 판정, 필수 발견, 영향과 closure 조건 |
| 회고 | `$retrospective`, `$deep-retrospective` | 시스템 원인, 통제 처분·적용, 검증과 복귀점 |

상태 보고가 전문 결과를 요약할 수는 있지만 그 결과 계약을 대체하지 않습니다.

## 3. 첫 화면

첫 문장에서 전체 판정과 가장 중요한 결과 또는 미완료 이유를 말합니다. 큰 작업은
다음 관계가 한 화면에서 보이게 압축합니다.

```text
전체 목표와 현재 판정
├─ 완료한 결과
├─ 미완료 결과와 이유
├─ 남은 책임과 다음 실행자
└─ 중요한 검증 공백과 Git 상태
```

하위 단위 완료를 전체 완료로 표현하지 않습니다. AI 작업만 끝났거나 외부 gate가
남았다면 repository 결과와 전체 결과를 분리합니다.

정리·브리핑·보고 block은 표 또는 목록을 기본 표현으로 사용합니다. 상태·완료 결과·미완료
gate·actor·다음 행동처럼 고정 label이나 비교 차원이 둘 이상이면 표를 사용하고, 하나의
차원에서 서로 독립적인 핵심 항목만 나열하면 목록을 사용합니다. Prose는 첫 문장의 전체
판정이나 표·목록만으로 전달할 수 없는 인과·전제·예외 설명에만 둡니다. 독자가 문장을
순서대로 읽지 않아도 행 label이나 목록 marker로 판정·남은 책임·다음 행동을 바로 찾을 수
있어야 합니다.

기존 서술문을 표의 셀이나 목록 항목에 그대로 옮긴 것은 구조화로 계산하지 않습니다.
보고 전에 정보를 우선순위와 `전체 판정·완료·미완료·현재 actor·다음 행동·검증·Git`으로
분류하고, 한 행·항목에는 하나의 상태값·결론·행동만 둡니다. 독립적인 판단·근거·조건·예외는
별도 행이나 상세 설명으로 분리합니다. 첫 결론과 행 label·요약값 또는 목록 marker만 훑어
현재 상태와 남은 책임·다음 행동을 복원할 수 없으면 보고를 다시 압축합니다.

삽입된 회고·검증·통제 변경 뒤 원래 사용자 결과로 복귀한 보고에서는 원래 결과의 상태를
삽입 결과보다 먼저 투영합니다. 원래 요청에 stage·commit·history rewrite가 포함됐는데
그 exact artifact가 요청된 Git object에 없으면 첫 문장은 `원래 artifact Git 결과 미완료`로
시작합니다. `수정 완료`, `worktree에 보존`, 통제 commit 목록과 같은 하위 사실을 전체 완료
표현 앞에 두거나, 뒤 문단의 caveat로 Git 미완료를 묻지 않습니다.

첫 화면에는 최소한 다음을 같은 결과 identity로 연결합니다.

```text
원래 요청 artifact·Git 효과
→ 실제 artifact 상태와 포함된 commit 또는 없음
→ 삽입 결과의 별도 상태
→ 원래 결과의 다음 책임·재개 조건
```

Artifact가 worktree에만 있으면 `수정됨·미커밋`이라고 직접 말하고, 요청한 commit과 통제
commit을 구분합니다. 같은 파일 overlap은 미커밋 사유일 수 있지만 완료 상태가 아니며,
사용자가 가장 먼저 판단해야 하는 Git 미완료를 낮은 우선순위로 내리지 않습니다.

비교 대상의 판정이나 기준 시점이 다르면 전역 `예`·`아니요`로 먼저 합치지 않고, 같은
비교 축에서 대상별 판정을 먼저 말합니다. 작은 질문은 `A는 당시 미실행, 현재 변경만
확인`, `B는 당시 미실행, 이후 검증 완료`처럼 한 문장으로 압축할 수 있습니다. 이때도
미확인 단계를 `처리됨`·`완료됨` 같은 상위 표현에 포함하지 않습니다.

## 4. 상태별 projection

- **완료**: 달성한 사용자 결과, 핵심 변경, 검증과 Git 상태를 연결합니다. 남은 책임이
  없으면 불필요한 다음 단계를 만들지 않습니다.
- **진행 중**: 같은 결과 기준으로 닫힌 단위와 남은 단위, 다음 실제 행동을 말합니다.
  현재 권한에서 계속할 수 있는 필수 작업이 있으면 terminal 보고로 멈추지 않습니다.
- **한 트랙 대기**: 대기 중인 exact 결과와 처음 영향을 받는 consumer를 밝히고, 동결된
  활성 원자의 현재 가능 여부·각 합류점·늦어도 필요한 시점·추천 이유를 다음 상세 행동보다
  먼저 투영합니다. 현재 milestone에 참여하지 않는다는 이유로 이후 합류 원자를 삭제하거나,
  권장 순서를 전체 hard prerequisite로 바꾸지 않습니다. 이 workflow는 membership·상태·
  edge를 다시 판정하지 않습니다.
- **차단·결정 대기**: canonical owner가 확정한 정확한 blocker, 필요한 입력·권한·외부
  상태와 응답 뒤 열리는 결과를 제시합니다.
- **중단·보류·범위 변경**: 효력이 미치는 범위, 안전한 정지 지점, 보존 결과와 재개
  조건을 제시합니다.
- **검증 실패·미실행**: 구현 여부와 완료 판정을 분리하고 실패·미실행 이유, 영향과
  해제 조건을 제시합니다.
- **비차단 발견**: 원래 결과 뒤에 문제·근거·영향과 필요한 결정만 분리합니다.

## 5. 진척 표현

진척은 작업 실행 워크플로우가 미리 고정한 결과 단위와 terminal gate의 상태를
투영합니다.

- 분모와 가중치가 첫 정량 보고 전에 고정된 경우에만 같은 기준의 백분율을 씁니다.
- 그렇지 않으면 `Closed N / 전체 M`, 닫힌 결과와 남은 gate로 표현합니다.
- 후속 보고는 새로 닫힘·재개된 결과와 phase·검증·Git 상태의 delta를 먼저 말합니다.
- 상태 전이가 없으면 활동이 있었어도 `완료 진척 없음`이라고 밝힙니다.
- 파일·줄·test·명령·검토·시간·token·비용은 요청받은 경우 별도 활동량으로만
  제시합니다.

실제 활동과 결과 closure를 구분합니다. 닫힌 결과가 없다고 수행한 조사·수정까지
`아무것도 하지 않음`으로 바꾸지 않습니다.

## 6. Git과 외부 상태

다음 projection을 합치지 않습니다.

- repository: source·직접 검증·문서가 닫힌 상태
- Git: stage·commit·push의 실제 상태
- remote: exact SHA의 CI·Sonar 상태
- live: 실제 외부 환경 적용과 검증 상태

권한이 없어 수행하지 않은 단계는 `미수행(권한 없음)`입니다. 비코드 artifact의 commit
자체가 요청 결과인 작업에서는 push를 임의의 필수 후속처럼 제시하지 않습니다. 코드
작업은 push 요청 여부와 별개로 exact SHA의 CI 결론과 URL, Sonar revision·분석 시각·
Quality Gate·Open issues를 terminal gate로 연결하며, push 권한이 없으면 local 결과와
전체 미완료를 구분합니다.

사용자 응답에서 Git commit을 설명·식별·나열하거나 근거로 제시할 때 hash가 나타나는
모든 지점은 실제 Git source에서 확인한 다음 형식을 사용합니다.

```text
<short SHA> — <commit subject> — <YYYY-MM-DD HH:MM:SS KST>
```

같은 commit을 다시 언급할 때 전체 정보를 반복하지 않으려면 hash 없이
`해당 commit`으로 지칭합니다. subject나 시각을 확인할 수 없으면 추측하지 않습니다.

커밋 workflow가 인계한 record에서 `internal identity`와 `user receipt`를 분리해 소비합니다.
Full SHA·parent·tree는 정확성 확인에만 사용하고, hash가 들어가는 terminal 문장은 record의
`user receipt` field를 변환하지 않고 그대로 투영합니다. 여러 commit은 receipt를 한 줄에
하나씩 먼저 둔 뒤 회고·변경 이유를 hash 없이 연결합니다. 같은 commit을 다시 가리키는
문장에도 internal identity를 복사하지 않습니다.

전송 전 hash가 있는 모든 줄을 인계받은 `user receipt` 집합과 완전히 대조합니다. 집합에 없는
hash 줄, receipt 밖의 hash 또는 subject·KST 시각이 달라진 줄이 있으면 보고를 보내지 않고
같은 record의 user receipt로 projection을 다시 만듭니다. 사용자가 full SHA를 직접 요구한
경우에만 commit workflow가 별도로 생성한 full-SHA receipt field를 같은 방식으로 사용하며,
이 예외도 internal identity를 손으로 직렬화하는 권한은 아닙니다.

## 7. 작성 점검

- 결과와 영향을 활동 목록보다 먼저 씁니다.
- 사실, 추정, 추천과 미검증을 구분합니다.
- verifier 성공을 제품 동작·운영 성공으로 확대하지 않습니다.
- 작은 보고에 빈 heading과 해당 없는 항목을 강제하지 않습니다.
- 사용자가 해야 할 일이 없으면 행동을 요구하지 않습니다.
- 정리·브리핑·보고 block의 고정 항목을 표나 목록으로 분리해, 문장을 순서대로 읽지 않아도
  판정·핵심 결과·남은 책임·다음 행동을 바로 찾을 수 있게 합니다.
- 기존 prose를 셀에 옮기지 않고 한 행·항목을 하나의 상태값·결론·행동으로 압축합니다.
  Label과 요약값만 훑어 전체 판정·완료·미완료·actor·다음 행동을 복원할 수 있어야 합니다.
- 완료·미완료, 실행자 책임과 Git·검증 상태가 실제 source와 모순되지 않습니다.
- material tool·platform 선택은 선택한 경로, 배제한 더 단순한 경로와 근거가 canonical
  결정 source에서 최종 보고로 이어집니다.
- 비교 보고는 각 대상의 기준 시점과 분석·변경·검증·완료 단계를 보존하고, 한 단계의
  증거를 다른 단계의 완료 증거로 확대하지 않습니다.

보고 전문은 일시적 projection입니다. 저장소 문서나 work item에 복사하지 않으며,
상세 근거는 canonical 문서 링크로 연결합니다.

## 관련 문서

- **공통 상태와 완료**: [작업 실행 워크플로우](100-work-execution-workflow.md)
- **문서와 보고의 책임**: [문서 정보 구조 가이드](../100-documentation-guide.md)
- **작업 문서 상태**: [작업 문서 생명주기](../work-items/100-work-item-lifecycle.md)
- **스크립트 검증과 완료 증거**: [스크립트 엔지니어링 가이드](240-script-engineering-guideline.md)
- **Git 실행**: [커밋 워크플로우](300-commit-workflow.md)
