# 문서 정보 구조와 문서화 절차 개편

## 상태

In Progress

## 결과

- 전체 판정: 문서를 목적별 artifact로 분류하고, 의미·구조·결과를 서로 다른 시점에 검토하는 문서화 체계를 적용했습니다.
- 핵심 변경: Router, Reference/SOT, Decision/Design, Plan, Procedure, Result/Status, History의 책임과 방향 관계를 확정했습니다.
- 절차 변경: 작성 전에 내용 누락과 불필요한 범위를 검토하고, 작성 전에 구조를 검토하며, 작성 뒤 실제 source·문서 관계·현재성을 다시 검토합니다.
- 형식 변경: 계획, 진행 상태, 완료 결과, 개념 설명, 절차와 검증 보고를 하나의 공통 양식에 맞추지 않고 목적별 구조와 보고 경로를 사용합니다.
- 실제 정리: 과거 완료 work item의 중복 기록을 제거하고, 진행 중 작업 문서를 현재 상태와 다음 책임 중심으로 압축했으며 account/auth의 정책·데이터 구조 소유권을 분리했습니다.
- Skill 변경: `documentation`과 `report-task`를 영구 workflow의 얇은 실행 계층으로 바꾸고,
  중대한 owner·topology·coverage 변경을 독립 검토하는 `verify-documentation`을
  추가했습니다. 개발 결과에 포함된 중대한 문서도 같은 전문 final을 적용합니다.
- 재발 방어 보강: 기존 router·filename을 정답으로 재사용하지 않고 새 numbered
  document의 path를 pre-change directory topology에서 산출합니다. 그 결과 새 group·owner
  분리·재번호·rename이 필요할 때 구조 변경으로 분류하는 계약을 적용했습니다.
- 운영 lifecycle 보강: 사람이 직접 시작하는 executable은 caller·입력·결과·재개
  경계를 복원해 Procedure→router로 이어집니다. Archive 포함이나 downstream consumer의
  선행 조건은 실행 완료 또는 canonical owner 근거로 사용하지 않습니다.
- 전문 final 보강: 중대한 owner·topology·coverage 후보는 계획·개발의 generic
  검증이 아니라 독립 입력·전체 감사 축·verdict를 갖춘 `verify-documentation final`을
  통과해야 합니다.
- 행동 검증: 실제 사건, 이름을 바꾼 transfer와 정상 국소 편집 시나리오를 통과했고,
  archive-only·partial retry를 포함한 A–H 사례도 통과했습니다. 중대한 문서 final이
  찾은 기존 Result·Design 충돌을 보완한 후 최종 재감사를 통과했습니다.
- 재개 사유: 위 행동 검증은 Procedure owner·router·partial retry를 보호했지만, 같은
  Application bootstrap의 후속 runbook에서 사람의 실행 계약 대신 재사용 shell logic을
  Markdown에 누적한 회귀를 막지 못했습니다. Procedure 작성 전 실행 계약과 작성 후
  역복원 gate를 보강했습니다.
- Procedure 실행 계약 행동 검증: `RET-350-REG`과 `RET-CONTROL-REG`을 서로 다른 사건으로
  판정하고, backup transfer·표준 단일 명령·사람 Console false-block을 포함한 다섯 사례를
  모두 `PASS`했습니다. 잘못된 후보는 Git 인계를 거부하고 정상 단일 명령·Console 단계는
  wrapper 없이 허용했습니다.
- 반복 문서군 authoring 보강: 전체 저상세 골격과 상세 대표 문서 하나가 공유 heading·
  설명 level·producer coverage contract를 실제로 소비하고 source-first closure를 통과한
  뒤에만 sibling을 확장합니다. Final이 공통 contract 결함을 처음 발견하면 점진 patch가
  아니라 authoring contract와 후보를 무효화합니다.
- 반복 문서 행동 검증: 독립 evaluator가 동결한 원사건·renamed transfer와 독립
  source·oracle을 가진 실제 다중 consumer false-block을 통과했습니다. 별도 producer가
  outline과 상세 대표 문서를 만든 뒤 reviewer
  verdict를 실제 소비한 expansion actor만 나머지 sibling을 작성했습니다. 이 증거는 해당
  fixture 전이만 소유하며 미래 문서 writer의 호출 완료를 주장하지 않습니다.
- Operations Scenario 보강: 실행 항목과 복합 항목의 하위 행동을 원자화해 같은 artifact의
  같은 target state 전이를 revision·시점·임시 경로 이름만 바꿔 중복하지 않고, 기존 환경에
  다시 적용할 live delta는 현재 상태·다음 행동에만 두도록 작성·final oracle을 강화했습니다.
- 도구 판정: 새 executable, schema, catalog, hook 또는 CI gate는 추가하지 않았습니다. 자연어 의미와 구조는 고정 규칙 검증보다 source-first review가 적합합니다.
- 미완료 작업: 원래 feature operation map에서 잘못된 Application delivery runbook을 실제
  복구한 뒤 specialist final로
  acceptance oracle을 확인해야 합니다. 저장소 전체 문서군 이관은 별도 후속 범위이며
  [005 문서 시스템과 실제 문서군 전면 이관](005-documentation-system-and-corpus-migration.md)이 소유합니다.

## 확정 범위

- 문서 종류별 소유권·보존 등급·기본 구조와 관계 방향
- 문서 작성 전 의미·구조 검토와 작성 후 결과 검토
- work item의 Plan → Status → Result 전환과 완료 시 압축
- 대화 보고와 영구 문서의 분리
- 문서·보고 관련 workflow, skill, template와 router
- 기존 문서 중 중복·과거 실행 상태가 명확한 표면의 실제 정리

Java 애플리케이션 동작, API, DB schema, 배포 자산과 운영 정책은 변경하지 않았습니다.

## 영구 SOT

- 문서 종류·소유권·구조: [문서 정보 구조 가이드](../../../100-documentation-guide.md)
- 작성·개편 절차와 세 검토 관문: [문서화 워크플로우](../../../workflow/150-documentation-workflow.md)
- 작업 문서 상태 전환: [작업 문서 생명주기](../../100-work-item-lifecycle.md)
- 대화 보고 라우팅: [완료·상태 보고 워크플로우](../../../workflow/140-completion-reporting-workflow.md)
- 결정과 배제 대안: [상세 설계](004-documentation-information-architecture-design.md)
- 재발 사건과 방어 lifecycle: [History](004-documentation-information-architecture-history.md)

## 재검토 조건

- 다른 artifact 종류가 반복적으로 현재 분류에 들어맞지 않을 때
- 독립 문서 검토의 비용이 발견 결함보다 지속적으로 클 때
- 같은 고영향 문서 결함이 반복되어 현재 수동 검토 경계로 막지 못할 때
- 자동화가 읽을 수 있는 독립된 authoritative source가 생겨 제한적인 기계 검증의 순효과를 입증할 수 있을 때
