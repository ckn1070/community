---
name: improvement
description: "사용자가 기존 문서, 코드, 테스트, 설정, 시스템, workflow 또는 skill의 현재 내용과 구조를 종합 검토해 더 나은 개선안을 도출하거나 실제 반영해 달라고 할 때 사용한다. 개선안 탐색이 필요 없는 exact 수정, 확정 계획의 단순 구현, 확인된 Codex 잘못의 원인 분석에는 각각 해당 전문 실행 skill이나 retrospective를 사용한다."
---

# 개선

사용자가 정한 목적을 현재 artifact와 연결 구조에 대조해 근거 있는 개선안을 선택하고,
필요한 전문 owner에 실행을 routing한다. 요청·권한·공통 결과 단위·병렬·차단과 final
discovery·closure는 [작업 실행 워크플로우](../../../docs/workflow/100-work-execution-workflow.md)가
소유한다. 이 skill은 공통 lifecycle이나 범용 구현 절차를 다시 정의하지 않는다.

## 1. 개선 대상과 현재 구조

작업 실행 workflow가 잠근 사용자 목적, 고정 제약, 범위와 결과 유형을 입력으로 받는다.
다음 source를 조사해 개선 판단의 근거를 만든다.

1. 저장소 `AGENTS.md`, router와 대상의 canonical owner
2. 대상 artifact 전체와 직접 caller·consumer
3. 관련 code, test, 설정, schema, 문서, 운영 절차와 자동 검증
4. 현재 구조가 의도적으로 보장하는 동작·호환성·안전·운영 조건
5. 사용자가 제안한 방법을 고정 제약인지 비교할 후보인지 판정할 근거

일부 snippet, 이름, 계획 문장이나 일반 Best Practice만으로 현재 상태를 추정하지 않는다.
모든 repository와 가상 실패를 넓게 조사하지 않고 결론을 바꾸는 dependency closure만
추적한다. 현재성이 결론을 바꾸는 외부 기술은 최신 공식 1차 근거를 확인한다.

현재 판단에는 다음을 연결한다.

| 항목 | 개선 판단 |
| --- | --- |
| 목적·고정 제약 | 개선 뒤 반드시 유지할 사용자 결과와 경계 |
| 현재 근거 | 실제 source와 연결 구조에서 확인한 상태 |
| 개선 후보 | 현재안, 사용자 제안, 의미 있는 대안과 변경하지 않는 선택 |
| 선택 개선 | 적용할 방법과 현재안보다 나은 이유 |
| 범위 | 영향받는 owner와 specialist, 제외할 독립 결과 |
| 수용 기준 | 개선 효과와 기존 보장 회귀를 확인할 관찰 결과 |

이 표는 작은 작업의 세션 판단이며 repository에 임시 원장으로 저장하지 않는다.

## 2. 개선 문제를 올바른 owner로 분류하기

- 확인된 Codex 지시 위반·잘못된 판단·완료 보고의 인과 분석은 `$retrospective`나
  `$deep-retrospective`에 맡긴다.
- 제품의 기대 동작과 실제 동작이 다른 결함, 새 사용자 가치와 제품 동작은 development·
  planning 경로에 맡긴다.
- 값·경로·방법이 이미 확정된 exact 변경은 해당 전문 실행 skill을 바로 사용한다.
- 현재 artifact의 품질·효율·안전·이해 가능성·운영 가능성·유지보수성을 종합 평가하고
  대안을 선택해야 할 때 이 skill이 개선 판단을 소유한다.

이름에 `개선`이 있다는 이유로 모든 refactor와 문서 수정에 이 skill을 추가하지 않는다.
반대로 현재 실패가 없다는 이유로 source가 뒷받침하는 개선 기회를 배제하지 않는다.

## 3. 도메인 관점으로 조사하기

문서, 코드·test, 시스템·운영, workflow·skill 또는 제품·domain 관점이 결론을 바꾸면
[`references/domain-review-lenses.md`](references/domain-review-lenses.md)를 처음부터 끝까지
읽고 관련 전문 lens만 적용한다. primary domain은 사용자가 원하는 결과를 직접 소유하는
영역으로 정하고, 그 결과의 정확성·안전성·실행 가능성을 좌우하는 영역만 supporting
domain으로 추가한다.

사용자가 전체 흐름·현재 위치·사람의 이해 가능성을 개선하려는 경우에는 machine-readable
catalog나 verifier보다 사람용 canonical entrypoint, 관계와 탐색 경로를 먼저 평가한다.
자동화는 별도 독립 oracle과 생애주기 순효과가 입증될 때만 후보로 둔다.

## 4. 대안을 비교하고 선택하기

현재안, 사용자 제안, 실행 가능한 대안과 변경하지 않는 선택을 다음 중 결론을 바꾸는
축에서 비교한다.

- 사용자 목적과 고정 제약 충족
- 정확성, data 무결성, security와 privacy
- 유지보수성, 응집도, 결합도와 이해 가능성
- 안정성, 실패 격리, 복구와 운영 가능성
- 성능, 자원, 비용과 장기 소유 부담
- 호환성, 전환 비용, 가역성과 검증 가능성

모든 축을 형식적으로 채우지 않는다. 추천안, 더 나은 이유, 얻는 것과 포기하는 것,
남은 위험과 재검토 조건을 명확히 한다. 사용자에게 보이는 동작, public API, data,
security, 운영 책임, 비용·공급자 종속, migration이나 사용자가 고정한 범위를 바꾸는
선택은 설계·계획 owner에 인계하고 개선 추천을 확정 결정으로 가장하지 않는다.

## 5. 전문 실행 경로

작업 실행 workflow가 반영 결과를 승인한 범위에서는 개선안을 제안으로만 남기지 않고
다음 specialist에 전달한다.

| 변경 표면·위험 | specialist |
| --- | --- |
| 저장소 Markdown 내용·구조·owner | `$documentation` |
| code, test, runtime 설정과 구현 | `$develop` |
| public API, DB, security, transaction, module·기반 구조 | 설계 workflow, 필요한 계획은 `$dev-plan` |
| build, CI/CD, infrastructure, 외부 연동과 운영 | 설계·도구·개발·문서·operations owner의 필요한 조합 |
| repository skill의 trigger·절차·resource·metadata | `$skill-creator`와 공통 skill validator |
| 기존 결과의 독립 감사 | 해당 `$verify-*` skill |

각 specialist의 최신 workflow를 그대로 사용하고 TDD, 문서 구조, 설계 결정, 도구 생애주기,
검증 phase를 이 skill에 복제하지 않는다. 여러 도메인이면 primary specialist가 결과를
소유하고 supporting specialist는 직접 영향 범위의 판단·검증만 제공한다.

## 6. 개선 결과 평가

개선 판단의 검증에서는 다음을 확인한다.

- 사용자 목적과 고정 제약이 유지됐다.
- 선택 개선이 현재 source와 직접 연결되고 현재안보다 나은 이유가 남아 있다.
- 허용된 artifact에 선택 개선이 실제 반영됐다.
- 관련 specialist의 test·검증·문서·SOT 결과가 기존 보장을 약화시키지 않았다.
- 범위 밖 발견이 개선 결과에 섞이지 않고 owner와 영향으로 분리됐다.

전문 검증 결과를 개선 판단에 연결하되 각 verifier의 고유 축과 판정을 다시 계산하지
않는다. 공통 후보 동결, 보완·closure와 전체 완료 판정은 작업 실행 workflow에 맡긴다.
채팅 보고는 [완료 및 작업 보고 워크플로우](../../../docs/workflow/140-completion-reporting-workflow.md)가
확정된 개선 결과와 실제 Git·외부 상태를 투영하게 한다.
