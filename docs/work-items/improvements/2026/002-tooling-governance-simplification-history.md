# 도구 체계 단순화 회고

## 사건과 영향

도구와 script 파일을 제거한 뒤 비어 있는 하위 디렉터리와 삭제한 Git hook의 설치
사본이 실제 filesystem에 남아 있었지만, tracked file·caller·문서·Git 상태 검증만
통과한 상태를 전체 구조 정리 완료로 판정했습니다. 그 결과 목표 디렉터리 구조와
실제 local workspace가 다른데도 work item을 `Done`으로 닫고 완료 보고를 했으며,
첫 push는 삭제된 verifier를 호출하는 기존 `pre-push` hook에서 중단됐습니다.

## 시스템 원인

구조 개편 요구를 Git이 추적하는 artifact와 호출 관계 중심의 수용 기준으로
축소했고, 목표 tree와 local 설치 상태는 설명 자료로만 두어 실제 filesystem 관찰
조건으로 연결하지 않았습니다. 완료 단계에서도 Git clean과 삭제 경로 0건을 물리
디렉터리와 설치 흔적 정리까지 확장해 해석했습니다. Git은 빈 디렉터리와 `.git/hooks`
설치 사본을 추적하지 않으므로 이 증거 집합만으로는 목표 workspace 상태를 판정할 수
없습니다.

## 기존 방어가 작동하지 않은 이유

stale path 검색, 문서 링크, shell·CI 검증과 commit object 확인은 모두 tracked
artifact의 정합성을 검사했습니다. 도구 도입 워크플로우에는 제거 시 설치 흔적을
정리하는 규칙이 있었지만 이번 작업의 관찰 가능한 수용 기준과 전환 절차로 가져오지
않았습니다. 따라서 실제 디렉터리와 local hook 상태를 읽는 단계가 요구사항 확정,
삭제 직후 실행과 완료 판정 어디에도 결속되지 않아 기존 방어가 성공해도 누락을
관찰할 수 없었습니다.

## 조치

[작업 실행 워크플로우](../../../workflow/100-work-execution-workflow.md)에 구조 변경 전
목표 물리 topology와 빈 디렉터리 0개를 수용 기준으로 확정하고, 삭제 직후 표준
`find`·`rmdir`로 실제 filesystem을 정리하며, commit·push·완료 전에 목표 tree를
다시 대조하도록 연결했습니다. 기존 local hook은 삭제 전 tracked hook과 Git blob
hash가 정확히 일치함을 확인한 뒤 제거했고, 작업 설계에는 제거한 도구의 local 설치
흔적 0개를 완료 조건으로 연결했습니다. 별도 verifier나 catalog는 추가하지
않았습니다.

## 남은 위험과 재검토 조건

빈 디렉터리와 `.git/hooks` 설치 사본은 clone에 전달되지 않는 local 상태이므로 중앙
CI가 대신 판정할 수 없습니다. 향후 구조 변경에서도 실제 filesystem 대조가
생략되거나 의도적 상태와 정리 누락을 구분하지 못하는 사례가 반복되면, 먼저 작업
템플릿의 구조·설치 흔적 수용 기준 연결을 재검토합니다. custom hard gate는 독립
oracle과 생애주기 순효과가 별도로 입증될 때만 검토합니다.

## 후속 사건: Sonar custom verifier 존치 오판

### 사건과 영향

도구 체계를 단순화하면서도 Sonar Python 판정기는 “독립된 원격 상태를 읽는다”는 이유로
파일 전체를 유지했습니다. 그 결과 공식 scanner와 Quality Gate가 소유할 수 있는 분석
대기·실패 전파·issue 조건을 965줄 production code와 664줄 fixture test로 계속
재구현했고, CI·README·기술 기준이 이 custom 계약에 결합됐습니다.

### 시스템 원인

존치 평가 단위를 executable 전체로 잡고 독립 oracle 존재를 충분조건처럼 취급했습니다.
분석 대기, gate 결과, issue 기준, 상세 보고와 추가 race fence를 각각 분리하지 않아
SonarQube Cloud의 공식 `sonar.qualitygate.wait`와 Quality Gate 조건을 책임별 대안으로
비교하는 단계가 생략됐습니다. 작성한 test가 구현의 동작을 검증한다는 사실도 도구
자체의 필요성과 순효과를 뒷받침하는 근거로 잘못 확대했습니다.

### 기존 방어가 작동하지 않은 이유

당시 도구 도입 워크플로우에도 표준 기능 우선과 전체 생애주기 비용 기준은 있었지만,
평가 문서는 Sonar verifier를 한 행으로만 다뤘습니다. 이후
[검증 책임 단위 존치 심사](../../../workflow/130-tool-adoption-workflow.md#평가-단위)가
복합 executable의 모든 blocking 책임을 개별 결정으로 역추적하도록 강화됐지만,
그 통제를 이미 예외로 남긴 Sonar 판정기에 다시 적용하지 않아 모순이 지속됐습니다.

### 조치

기존 책임 단위 심사를 Sonar caller와 source 전체에 다시 적용해 Python 판정기, fixture
test, 별도 CI step과 문서를 제거했습니다. 분석 처리 대기와 pipeline 실패는 Gradle
scanner의 공식 Quality Gate 대기로, new code의 `Number of issues`와 overall code의
`Open issues` 조건은 SonarQube Cloud custom gate로 이전했습니다. new code
`Number of issues`의 Accepted·False Positive 반영 의미는 기존 `resolved=false`와
동등하다고 단정하지 않고 live 확인 책임으로 남겼습니다. 별도 verifier나 새 custom gate
code는 추가하지 않았습니다.

### 남은 위험과 재검토 조건

Quality Gate 정의와 project 연결은 repository 밖의 live 상태입니다. 외부 설정을 실제로
확인하기 전에는 issue 0건 정책의 운영 전환을 완료로 판정할 수 없습니다. 작업자는
push한 exact SHA의 `Application quality` CI가 끝난 뒤 read-only Sonar 완료 점검으로
revision·Gate·Open issues를 대조해야 합니다. 공식
scanner 대기에서 실제 분석 결속 결함이 재현되면 공식 integration을 먼저 재검토하고,
표준 기능으로 해결할 수 없다는 증거와 순효과가 모두 입증될 때만 custom code를
후보로 엽니다.

### 후속 제약 확인과 처분

실제 account의 현재 plan에서는 custom Quality Gate를 만들 수 없어 위 조치의 issue
조건 이전을 적용할 수 없었습니다. plan 제약을 확인하지 않은 채 custom gate를 현재
해법으로 기록한 판단은 폐기했습니다. CI는 공식 scanner 대기와 기본 `Sonar way`를
유지하고, 작업자가 dashboard를 매번 열어야 하는 남은 비용은 push 후 완료 시점의
작은 읽기 전용 shell 점검으로 닫았습니다.

새 점검은 대형 판정기를 복원하지 않습니다. expected SHA, 조회 전후 동일 analysis,
Quality Gate와 공식 `open_issues` metric만 판정하며 polling·issue 상세·자체 품질
임계값·GitHub 호출·Sonar 상태 변경을 소유하지 않습니다. 이 경계가 커지거나 표준
기능과 중복되면 [도구 도입 워크플로우](../../../workflow/130-tool-adoption-workflow.md)에
따라 다시 축소하거나 제거합니다.

## 후속 재개: runner 선행 조건 뒤에서 시작한 도구 심사

`f99d77c`의 image 게시 구현은 runner 선택의 권위와 비교 기록 없이 x86 runner를 선행
조건으로 두고 QEMU의 기능만 심사했습니다. 이는 이 work item이 완료로 선언한 `원인 제거·
표준 기능 우선·도구 기본 비도입` 범위를 직접 위반하므로 기존 `Done`을 무효화합니다.

도구 평가 단위는 이제 보상 계층의 기능에 그치지 않고 그 계층이 필요해진 선행 조건까지
포함합니다. 공통 시작·종료 상태에서 선행 조건을 바꿔 도구가 사라지는 경로와 도구를 더하는
경로를 비교하고, 선택 권위·oracle·생애주기 비용을 같은 decision record에 남깁니다.
두 사건의 전체 lifecycle과 reporting 인계 실패는
[작업 실행 회고](003-work-execution-traceability-history.md#native-경로를-닫지-않고-emulator를-도입한-구현-재발)가
소유합니다.

제품 artifact는 별도 결과 `9b624c3`에서 native ARM64 runner로 복구하고 QEMU를 제거했습니다.
이 사실은 원래 선택의 권위를 소급해 만들지 않습니다. 공통 통제는 설명·구현 regression,
renamed transfer, 확정 경로 false-block과 Agent·사용자 결정 경계를 사례별 독립 root
entry에서 검증했고 실제 격리 diff·test·commit을 포함한 최종 7개 사례가 통과했습니다.
제품 artifact와 공통 통제의 Git 결과가 모두 닫혀 이 work item의 `Done`을 복구합니다.

## 후속 재발: DB TLS 선택과 manifest verifier를 연결한 오판

### 서로 다른 두 사건과 영향

사용자가 선택한 `verify full`은 application의 PostgreSQL JDBC URL에 적용하는
`sslmode=verify-full`이었습니다. 서버 인증서의 신뢰 체인과 hostname을 검증하는 DB TLS
결정이며, manifest 검증 방식이나 저장소 도구 선택과는 인과가 없습니다. 그런데 후속 설명은
현재 대화의 manifest 검증 주제를 이 짧은 표현의 원래 결정 차원으로 바꾼 뒤,
`verify full → 전체 검증 → 표준 도구`라는 관계를 사후에 만들었습니다.

별도로 `570215d`는 `deploy/tests/test-staging-manifests.sh`를 executable로 추가했습니다.
이 파일은 334줄의 `grep`·`awk` 문자열 판정과 자체 변형 fixture로 manifest를 해석하고
non-zero로 진행을 차단했습니다. Kubernetes·Argo CD가 소비하는 파일도 아니고 실제 caller도
없었지만, `tests/` 경로와 `test-*` 이름 때문에 일반 product test로 취급됐습니다. 그 결과
기존 `kubectl kustomize`와 source review로 닫히는 책임, schema·server dry-run처럼 표준
consumer가 소유할 책임과 project-specific 정책을 나누지 않고 하나의 custom verifier로
복제했습니다.

### 최초 단절과 기존 기준 미호출

DB TLS 설명의 최초 단절은 짧은 선택 표현이 속한 원문 원자·결정 차원과 canonical consumer를
복원하지 않은 것입니다. Manifest verifier의 최초 단절은 예정 artifact를 관찰 효과가 아니라
path·이름으로 분류한 것입니다. 이후 review는 도구의 필요성과 admission을 다시 열지 않고,
이미 만든 verifier의 문자열 coverage와 self-test를 강화해 비용을 키웠습니다.

사건 당시에도 도구 도입 workflow에는 repository script·verifier, 표준 기능 우선, 독립
oracle, 실제 caller, false pass·block, owner·제거 조건과 생애주기 순효과 기준이 있었습니다.
따라서 기준이 없었던 사건이 아닙니다. Writer 인계 전에 artifact의 observable behavior와
전문 owner 처분을 운반하지 않아 기존 기준이 호출되지 않은 invocation failure입니다.

문제의 verifier는 반복 실패 증거, 표준 기능으로 대체할 수 없는 책임, 독립 oracle, caller,
제한된 실패 경계, owner와 제거 조건을 모두 충족하지 못했습니다. 같은 transition에서 만든
manifest와 assertion이 서로를 검증했고, YAML의 공백·순서·동등 표현에는 false block을 만들면서
실제 API admission·Argo 실행·Secret 격리 완료는 증명하지 못했습니다. 기존 도구 기준으로는
도입 불가가 정확한 판정입니다.

### 통제 처분과 행동 검증

결정 완료 상태를 투영하는 `report-task`는 짧은 선택값을 직접 선행 원문과 canonical
consumer의 유일한 설정 key에 다시 결속하고, 이미 닫힌 결정을 이름이 짧다는 이유로 다시
묻지 않도록 보강했습니다. `develop`의 writer 계약에는 예정 artifact의 실행 방식·blocking
효과·전문 owner 처분을 추가해 `test`·`spec` 이름으로 도구 workflow를 우회하지 못하게
했습니다. 일반 JUnit product test와 기존 표준 도구의 읽기 전용 사용은 별도 도구 심사로
확대하지 않습니다. 새 verifier·hook·wrapper는 만들지 않았습니다.

작성자와 분리된 fresh 실행자·evaluator가 원래 DB TLS 결정, 실제 `bfff9b6..570215d` 전이,
renamed option·executable transfer, 정상 기술 질문, 기존 `kubectl kustomize` 사용과 기존
Gradle/JUnit test를 같은 동결 후보에서 검증했습니다. Regression 2건, transfer 2건,
false-block 3건과 실제 routing 2건이 모두 통과했습니다. 이 결과는 source 해석과 도구
admission 통제만 닫습니다. 문제 feature artifact의 제거·대체와 원래 application delivery
구현은 이 회고의 control-only 결과에 포함하지 않습니다.
