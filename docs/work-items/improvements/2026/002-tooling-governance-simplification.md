# 도구 거버넌스와 검증 체계 단순화

## 상태

Done

## 결과

- 저장소 도구를 생애주기 순효과 기준으로 재평가하고 과도한 자기검증·중복 호출 계층을 제거했습니다.
- 애플리케이션 개발에는 Gradle·Testcontainers·Spotless·JaCoCo·Sonar와 GitHub Actions의
  `Application quality` job만 유지합니다.
- push 후 exact SHA의 Sonar revision·Quality Gate·Open issues를 읽는
  `scripts/quality/verify-sonar-status.sh`는 CI나 hook에 연결하지 않고 완료 시점에만 사용합니다.
- Terraform·Kubernetes·GitOps 배포 source와 caller가 사라진 뒤에는 관련 verifier·fixture·
  toolchain도 함께 제거했습니다.
- 새로운 script나 hard gate는 실제 반복 실패, 독립 oracle, 표준 기능으로 대체할 수 없는
  이유와 제거 조건이 모두 있을 때만 다시 도입합니다.
- 도구가 Agent가 고른 실행 platform·architecture 같은 선행 조건을 보상한다면 그 조건을
  고정하지 않고, 도구가 사라지는 표준 경로와 도구를 더하는 경로를 같은 시작·종료 상태에서
  먼저 비교합니다.
- `f99d77c`에서 x86 runner를 먼저 고정하고 QEMU로 architecture 차이를 보상한 재발은
  제품 결과 `9b624c3`의 native ARM64 runner 전환과 공통 workflow 통제 보강으로 닫았습니다.
- 통제 후보는 설명·구현 regression, renamed transfer, 확정 경로 false-block과 Agent·사용자
  결정 경계를 사례별 독립 root entry에서 검증했고, 실제 격리 diff·test·commit을 포함한
  최종 판정에서 7개 사례 모두 통과했습니다.
- `tests/`·`spec` 경로와 `test-*` 이름은 도구 심사를 면제하지 않습니다. 독립 실행되어
  source·설정을 해석하고 non-zero로 진행을 차단하는 artifact는 writer 위임 전에 관찰
  효과 기준으로 도구 생애주기 처분을 받으며, 기존 framework 안의 product test는 그대로
  test workflow가 소유합니다.

## 확정 결정

1. 도구의 기본값은 비도입이며 근본 문제와 무도구·표준 기능 대안을 먼저 검토합니다.
2. custom hard gate에는 독립 oracle, 압도적인 순효과, 제한된 실패 경계와 제거 조건이 필요합니다.
3. 개발자 local 검증, GitHub CI와 push 후 원격 품질 판정을 서로 다른 실행 경계로 유지합니다.
4. 실제 caller가 사라지면 tool·test·fixture·문서와 설치 흔적을 같은 변경에서 제거합니다.
5. 보상 도구는 필요성을 만든 선행 조건까지 다시 열어 완전한 경로끼리 비교한 뒤에만
   도입합니다.

## 영구 SOT

- 도구 도입·존치·제거: [도구 도입 워크플로우](../../../workflow/130-tool-adoption-workflow.md)
- application 품질 기준: [코드 품질 도구](../../../tech-stack/310-code-quality-tools.md)
- push 후 Sonar 판정: [품질 완료 점검](../../../../scripts/quality/README.md)
- Git 실행 책임: [커밋 워크플로우](../../../workflow/300-commit-workflow.md)

## 관련 기록

- [결정 설계](002-tooling-governance-simplification-design.md)
- [완료 판정 누락 회고](002-tooling-governance-simplification-history.md)
