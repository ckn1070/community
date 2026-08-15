---
name: verify-plan
description: "완성 후보인 개발 계획을 구현 전에 최초 사용자 목적, 확정 결정과 관련 코드·문서·설정·데이터·운영 source부터 독립 검증할 때 사용한다. 계획 수립·수정 자체, 구현 결과 검증이나 반복적인 아이디어 탐색에는 사용하지 않는다."
---

# 계획 검증

완성 후보 계획을 구현 형태가 아니라 최초 목적에서 다시 평가한다. 계획을 직접 수정하지
않고 검토 축 전체의 발견을 포화시킨 뒤 `통과`, `실패`, `판정 불가`를 결정한다. 후보
동결, 공통 audit phase·closure와 상태 전이는
[작업 실행 워크플로우](../../../docs/workflow/100-work-execution-workflow.md)가 소유한다.

## 1. 입력과 snapshot

`AGENTS.md`, 관련 workflow·router, 최초 요청과 확정 변경, work item·design, 관련 source를 읽는다. 계획이 문서·SOT·router를 만들거나 바꾸면 `docs/100-documentation-guide.md`와 `docs/workflow/150-documentation-workflow.md`도 읽는다. 최초 목적을 복원할 authoritative input이 없으면 계획 문구로 추측하지 않고 판정 불가로 끝낸다.

`git status --short --branch`, 계획 문서와 핵심 source 상태를 확인한다. 검증 중 계획이나 판단 근거가 바뀌면 기존 결론을 재사용하지 않는다.

## 2. 최초 목적 계약

계획 상세를 평가하기 전에 다음을 세션의 coverage map으로 만든다.

| 목적·제약 | 수용 기준 | 설계 결정 | 구현 작업 | 검증 | 문서·SOT | 상태 |
| --- | --- | --- | --- | --- | --- | --- |

정방향으로 모든 목적이 결정·작업·검증·문서에 연결되는지, 역방향으로 모든 추가 작업과 도구가 목적에 정당화되는지 확인한다.

## 3. dependency closure와 검토 축

계획의 파일 목록에 한정하지 않고 caller, 외부 계약, data, build, deployment, operations, test와 책임 문서를 추적한다. 다음 축을 `충족`, `미충족`, `미검증`, `해당 없음`으로 닫는다.

- 최초 목적·범위와 양방향 추적성
- 정상·실패·경계 동작과 기존 회귀
- 구조·모듈·외부 계약
- data·transaction·동시성
- 보안·개인정보·신뢰 경계
- 안정성·복구·성능·자원
- 운영·rollout·rollback
- 기술·도구의 지원·비용·제거 조건
- 구현 순서와 독립 검증 가능성
- test·품질 gate의 충분성·중복·과잉
- 유지보수·소유권
- 문서·SOT·router·용어

문서 결과가 포함되면 독자 질문·actual source→owner의 누락과 owner→독자 필요·source의 과잉을 양방향으로 대조한다. 실행 순서·분기·호출·값 전달·책임 같은 관계를 계획 문서만으로 하나의 의미로 복원할 수 있는지도 판정한다. 현재 사실은 current source와 일치해야 하고, 아직 구현되지 않은 목표 관계는 확정 요구·결정과 계획된 구현·검증에 연결돼야 한다. 목표 관계의 구현 후 source 일치는 `verify-dev`와 최종 문서 감사가 판정한다. 별도 `$verify-documentation final`을 중복 호출하지 않는다. 다문서 topology 자체가 아직 제안 단계이면 그 산출물만 `$verify-documentation proposal`에 인계한다.

제품 표면과 무관한 축은 제외 이유를 남긴다. Best Practice는 이름난 패턴이 아니라 예방하는 실패, trade-off와 현재 source에 연결한다.

## 4. 발견 포화와 판정

첫 결함에서 멈추지 않고 모든 축을 닫은 뒤 발견을 다음 관계로 정규화한다.

```text
최초 요구 → 기대 계획 → 실제 계획 → source 근거
→ 영향 → 필요한 수정·결정 → closure 조건
```

- **통과**: 모든 필수 요구와 축이 충족 또는 근거 있는 해당 없음이고 열린 결함·공백이 없습니다.
- **실패**: 목적, 안전, 문서 계약이나 실행 가능성을 깨는 필수 결함이 있습니다.
- **판정 불가**: authoritative input, 근거, 환경이나 사용자 결정이 없어 필수 항목을 닫을 수 없습니다.

선택적 개선은 통과를 막지 않는다. coverage JSON과 이를 검사하는 custom verifier는 만들지 않는다. 검증의 독립성은 최초 목적에서 다시 시작하고 source·공식 근거를 직접 읽는 방식으로 확보한다.

## 5. 판정 결과

발견은 모든 검토 축을 닫은 뒤 `기대 → 실제 → source 근거 → 영향 → 닫는 조건`으로
한 묶음에 제공한다. 계획 artifact·결정의 결함인지 authoritative input·환경 공백인지
구분하되, verifier가 계획을 수정하거나 새 결정을 만들지 않는다.

이 판정을 `$dev-plan`과 작업 실행 workflow에 인계한다. 보완 가능 여부, 후보 무효화,
closure 횟수, `Planned` 전환과 구현 routing은 작업 실행 workflow가 판정하며 이 skill은
그 lifecycle을 다시 정의하지 않는다.

[완료 및 작업 보고 워크플로우](../../../docs/workflow/140-completion-reporting-workflow.md)의
검증 경로에는 판정, 목적 대비 필수 발견·공백, 실제 검증 범위와 snapshot의 문서·Git
사실만 제공한다.
