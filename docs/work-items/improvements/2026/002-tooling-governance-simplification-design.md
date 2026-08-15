# 도구 거버넌스와 검증 체계 단순화 설계

이 문서는 저장소 도구를 도입·유지·제거할 때 적용한 장기 결정만 보존합니다. 현재 결과는
[작업 본문](002-tooling-governance-simplification.md), 공통 기준은
[도구 도입 워크플로우](../../../workflow/130-tool-adoption-workflow.md)가 소유합니다.

## 결정 1. custom hard gate는 마지막 수단입니다

도구의 예방 효과에서 구현·test·문서·갱신 비용, false block·pass와 새 실패 표면을 뺀
순효과가 보수적으로도 충분해야 합니다. 실제 반복 실패와 독립 machine oracle이 없거나
표준 build·test·CI 기능으로 충분하면 custom verifier를 만들지 않습니다.

## 결정 2. 실행 경계가 호출 경계입니다

개발자는 변경에 가까운 test와 최종 Gradle 검증을 직접 실행하고, GitHub Actions는
애플리케이션 품질 경계를 재현합니다. 원격 상태 조회는 그 상태가 실제 완료 조건인
시점에만 수행하며 profile runner나 script catalog로 서로 다른 책임을 다시 묶지 않습니다.

## 결정 3. caller와 함께 수명을 끝냅니다

script나 verifier의 caller·owner가 사라지면 이미 구현했다는 이유로 유지하지 않습니다.
executable, test, fixture, 설치 설정과 현재 설명을 같은 변경에서 제거하고 Git history를
복구 owner로 사용합니다.

## 결정 4. Sonar 완료 점검은 최소 read-only 책임만 소유합니다

Gradle scanner와 Sonar Quality Gate가 분석 처리와 품질 임계값을 소유합니다. 저장소의
완료 점검 script는 push한 exact SHA의 latest analysis identity, Gate와 Open issues만 읽고
GitHub CI 상태·polling·issue 상태 변경을 소유하지 않습니다.

## 결정 5. 보상 도구보다 선행 조건을 먼저 다시 엽니다

도구가 실행 platform·architecture·format·protocol·network path나 actor의 차이를 보상할
때에는 그 도구의 국소 기능만 심사하지 않습니다. 공통 시작·종료 상태를 기준으로 선행
조건을 바꿔 도구가 사라지는 표준 경로와 기존 조건에 도구를 추가하는 경로를 함께
비교합니다. 기본 template과 현재 구현은 선행 조건의 결정 권위가 아닙니다.

공식 source와 현재 요구가 한 경로를 유일하게 닫으면 Agent가 질문 없이 선택·검증하고
근거를 남깁니다. 둘 이상의 유효한 경로가 비용·성능·위험·외부 효과를 다르게 만들면
사용자 결정을 mutation 전에 한 번에 닫습니다. 제품별 금지 목록이나 새 verifier는 두지
않습니다.

## 재검토 조건

- 표준 도구가 같은 실패를 더 직접 판정합니다.
- 실제 caller가 사라지거나 실행 비용이 검출 가치보다 커집니다.
- false block·false pass 또는 verifier 자체 결함이 반복됩니다.
- 보상 계층을 추가했는데 그 필요성을 만든 선행 조건의 결정 근거를 역추적할 수 없습니다.
- 여러 저장소가 동일한 versioned interface를 실제로 공유해야 합니다.
