# 문서 정보 구조와 문서화 절차 개편 History

## 사건과 영향

문서 번호·topology 검증 계약이 적용된 뒤 `docs/operations`에 새 runbook과 책임 group을
추가하는 작업에서 기존 `200` path를 현재 규칙으로 다시 판정하지 않고 다음 group을
`300`으로 만들었습니다. 현재 책임과 맞지 않는 기존 filename도 새 구조의 기준처럼
유지했습니다. 그 결과 첫 활성 group이 `100`이 아닌 구조와 폐기된 책임명이 router·link와
서로 일치하는 상태로 확대됐습니다.

## 확인된 통제 timeline

- `f231dad`에서 actual sibling·direct router 대조와 번호 group 검증 계약이 도입됐습니다.
- 이후 `cf8ce42`에서 기존 `100` group이 삭제되고 AWS runbook이
  `210-cicd-aws-runbook.md`에서 `200-cicd-aws-runbook.md`로 이동했습니다.
- 현재 사건 artifact는 이 상태를 다시 계산하지 않은 채 Kubernetes 책임을 `300` group에
  추가하고, 현재 책임과 맞지 않는 `cicd` filename을 유지했습니다.
- 사건 당시 기존 통제가 실제 path 결정 전에 호출됐는지, 구조 proposal·fresh final이
  수행됐는지는 durable source에서 확인할 수 없습니다. 따라서 이를 호출 후 동일 불변식이
  다시 실패한 사건으로 확정하지 않습니다.

## 최초 단절과 시스템 수준 원인

가장 이른 관찰 가능한 단절은 `000-index.md`와 첫 활성 문서
`200-cicd-aws-runbook.md`가 있는 편집 전 directory topology에서 새 path를 다시 산출하지
않고 `300-kubernetes-bootstrap-runbook.md`를 선택한 것입니다.

기존 번호 계약은 direct router를 canonical owner로 두면서 "아직 쓰지 않은 가장 작은
hundred block"을 배정하도록 했습니다. 이 표현은 삭제된 과거 `100`을 현재의 빈 선행
block으로 판정하는지 명확히 고정하지 않았고, router와 sibling이 같은 stale numbering을
공유할 때 둘의 일치가 잘못된 topology를 배제하지 못했습니다. Filename을 현재 canonical
책임에 대조하는 계약도 없었습니다.

따라서 관련 기존 통제는 호출 후 재실패로 무효화하지 않고 같은 owner에서 강화합니다.
Invocation 여부를 추정하는 대신, 새 numbered document의 path를 만들기 전에 pre-change
inventory에서 번호와 현재 책임명을 산출하도록 입력과 oracle을 분리합니다.

## 변경한 현재 통제

- Existing router·filename을 독립 oracle이 아닌 source 후보로 분류합니다.
- 새 numbered document는 편집 전 topology에서 path를 산출하고, 새 group map entry·owner
  분리·재번호·rename이 필요하면 구조 변경으로 분류합니다.
- 활성 책임 group은 pre-change topology에서 `100`부터 다시 계산합니다.
- Filename을 현재 canonical 책임과 독자 질문에 대조하고 stale 이름을 새 path에
  전파하지 않습니다.
- 구조 수정 권한이나 move·link 범위가 없으면 잘못된 기존 path를 전제로 새 문서를
  만들지 않습니다.

## 남은 위험과 재검토 조건

자연어 topology 판단은 filename 의미와 독자 책임을 자동 판정하지 않습니다. Fresh
검토가 구조 변경으로 분류되지 않거나 작성자가 pre-change inventory 대신 candidate
router만 전달하면 같은 계열이 재발할 수 있습니다. 다른 directory·제품명으로 바꾼
사례에서 regression·transfer·false-block 검증이 실패하면 문서화 workflow의 분류
interface와 oracle 배치를 다시 조사합니다.

## Application bootstrap lifecycle이 downstream GitOps owner에 매몰된 사건

### 사건과 사용자 영향

Application delivery feature에서 `100-provision-database-access.sh`와
`200-apply-application-prerequisites.sh`가 새로 생겼습니다. 두 script는 repository의
다른 script가 호출하지 않고, Kubernetes bootstrap은 archive에 포함해 실행 권한만
설정합니다. GitOps 연결 script도 선행 resource가 없으면 실패할 뿐 생성하지
않으므로, 운영자가 `100 → 200 → GitOps 연결`을 직접 시작해야 합니다.

그러나 candidate `f644958`은 두 직접 실행 절차를 기존
`350-private-gitops-repository-connection-runbook.md`에 추가했고, operations router에는
application bootstrap 사람 목표가 없었으며 application README에서 canonical runbook으로
가는 link도 없었습니다. 기존 private GitOps path의 inbound link 12개는 모두
일치했지만, 운영자가 bootstrap 목표로 해당 절차를 찾을 수 없는 구조였습니다.

### 최초 단절과 수직 원인

최초 단절은 새 manual lifecycle이 생겼을 때 사람 목표·caller·credential·
postcondition·재개 경계를 먼저 복원하지 않고, downstream GitOps가 prerequisite를
소비한다는 dependency로 canonical Procedure owner를 정한 것입니다. Script tree의
`bootstrap/application` identity가 Procedure→router까지 이어지지 않았습니다.

기존 문서화 workflow는 pre-change topology·사람 목표·owner matrix와 중대한 구조의
proposal/final을 요구했습니다. 즉 제품별 규칙이 없어서 생긴 사건이 아닙니다.
다만 executable lifecycle identity와 Procedure owner 선택이 terminal oracle에 직접 결속되지
않았고, generic 개발 검증이 중대한 `verify-documentation final`을 대체할 수 없다는
경계도 문구 사이에 모호했습니다. 그 결과 잘못된 owner가 title·router·link에서
서로 일치하자 static link·diff 검사가 결함을 보지 못했습니다.

### 통제 처분과 행동 검증

- 사람이 직접 시작하는 executable은 archive 포함이나 downstream dependency로
  owner를 정하지 않고 caller·trigger·credential·postcondition·재개 경계를 복원합니다.
- 독립 lifecycle이면 Procedure→router를 같은 사람 목표로 이어 운영 진입점을
  만듭니다. README는 interface와 canonical Procedure link만 소유합니다.
- 중대한 owner·topology·coverage 변경은 generic `verify-dev`·final audit의 문서 축으로
  대체하지 않고 `verify-documentation final`의 독립 입력·전체 감사·verdict를 요구합니다.
- 새 product·script 이름별 규칙, executable, schema, hook은 추가하지 않습니다.

사전 동결한 fresh 행동 검증은 application bootstrap, archive-only, `100` 성공 후
`200` 실패·재개, 이름을 바꾼 lifecycle, title-only·README-only 오탐 사례와 정상
상위 delivery dependency·routine 국소 수정을 포함합니다. 이 candidate가 모든 사례와
중대한 specialist final 대체 금지를 모두 통과했습니다. Fresh runner는 history와
기대 답을 받지 않고 A–H 각 사례의 skill·phase, canonical Procedure owner·router,
완료 gate를 source에서 복원했으며 실행 전후 candidate hash도 일치했습니다.

이 통제는 운영 진입점·owner 선택과 전문 final gate만 소유합니다. 잘못된 feature
runbook의 rename·router·inbound link·README 복구와 feature commit 재작성·main 병합은
무효화된 application delivery 작업의 별도 복귀점입니다.

## Application delivery runbook이 실행 문서와 shell program을 합친 사건

### 사건과 영향

최초 Application delivery Procedure는 독립 준비 행동과 source-backed dependency를 구분하고,
각 사람 행동에서 actor·host·directory·entrypoint·입력·결과·재실행 지점을 바로 보여야
했습니다. 실제 `350-application-delivery-bootstrap-runbook.md`는 local 값 검증·secret
전달·GitHub App·GitOps 연결·qualification을 한 문서에 모으고, 반복 검증과 전달 logic을
Markdown shell block으로 구현했습니다. 일부 worker script는 존재했지만 전체 orchestration의
operator entrypoint가 없어 사용자가 script의 실행 위치와 재개 지점을 다시 조립해야 했습니다.

### 시스템 원인과 기존 방어 실패

최초 단절은 `application delivery source → 사람 행동별 실행 계약`입니다. 기존 문서 유형·
owner·router와 specialist final은 있었지만, Procedure writer 입력과 final oracle이 각 행동의
exact entrypoint를 역복원하는 독립 결과를 요구하지 않았습니다. 그 결과 repository entrypoint가
없는 reusable orchestration을 구현 공백으로 반환하지 못하고 Markdown이 대신 소유할 수
있었습니다.

### 현재 통제와 남은 위험

Procedure 작성 전 actor·host·directory·exact entrypoint·입력·결과·재실행 지점을 복원하고,
후보에서 같은 계약을 역복원합니다. 반복 shell logic이 Markdown에만 있으면 구현 owner로
미충족 dependency를 반환하되, 현재 사용자 원자에 구현이 없으면 구현 phase를 호출하지 않고
별도 결정 경계를 유지합니다. 표준 단일 명령과 사람의 Console 판단에는 wrapper를 강제하지
않습니다. 독립 행동 검증 `RET-350-REG`은 잘못된 과거 후보의 specialist final·Git 인계를
거부했고, backup transfer와 단일 명령·Console false-block도 통과했습니다.

사건 artifact `350`의 실제 분리·launcher 구현·재작성은 아직 원래 feature operation map의
복귀점입니다. 이 검증은 아래 사건 2의 verdict를 대신하지 않습니다.

## 존재하던 Procedure 기준의 writer·final 소비 효과가 관찰되지 않은 사건

### 사건과 영향

사건 당시에는 한 runbook의 사람 목표, actor·실행 위치, 자동화 내부 구현 분리, script의 exact
path·입력·postcondition·복귀점을 요구하는 canonical 기준이 이미 있었습니다. 결과 artifact는
그 기준을 위반했고 기준 준수 evidence 없이 Git handoff·commit에 도달했습니다. Durable
source만으로 writer가 기준을 읽지 않았는지, 잘못 적용했는지, specialist final이 호출되지
않았는지 또는 잘못 판정했는지는 구분할 수 없어 모두 `미증명`입니다.

### 시스템 원인과 기존 방어 실패

기존 `R/C/P` handoff와 specialist fail-close는 유지할 유효한 통제입니다. 공백은 specialist
결과가 Procedure의 사람 행동별 exact entrypoint 계약과 후보의 역복원 결과를 독립 admission
결과로 식별하지 않은 데 있었습니다. 따라서 기존 interface가 실제 호출됐는지와 별개로,
source·link·section 일치와 사람의 실행 가능성을 서로 다른 결과로 전달하는 보호 범위를
증명할 수 없었습니다.

### 현재 통제와 남은 위험

기존 interface와 fail-close는 유지하고 Procedure action contract의 입력·역복원 결과만
구체화했습니다. 독립 행동 검증 `RET-CONTROL-REG`은 historical control identity와 동결 후보를
소비한 fresh final이 기준 위반 후보를 거부해 사건 1과 별도로 `PASS`했습니다. Backup transfer와
단일 명령·Console 사례도 각각 전이와 과잉 차단 경계를 통과했습니다.

이 verdict는 Procedure-specific admission을 닫지만 과거 writer·final invocation이나 `350`
복구를 증명하지 않습니다. 사건 artifact의 별도 복구가 끝나기 전 work item은 `In Progress`를
유지합니다.

## 반복 진단 문서군을 대표 결과 없이 전면 확장한 사건

### 기대·실제·영향과 최초 단절

사용자가 요구한 결과는 하나의 간략한 진단 outline과, 각 영역에서 상단 확장 조회 뒤 하단의
command family·option·출력 field·정상·이상·다음 탐색을 같은 계층으로 설명하는 상세 문서군이었습니다.
실제 작성은 이 공통 설명 contract를 한 상세 문서에서 먼저 증명하지 않고 outline과 모든
sibling으로 확장됐습니다. 상세 문서마다 heading level과 option 설명 방식이 달랐고, 상단 조회
결과를 하단에서 해석할 수 없는 공백과 민감 출력 경계가 final 감사에서 순차적으로 발견됐습니다.

최초 단절은 `사용자 품질 차원 → 반복 문서 authoring contract → 첫 대표 문서` 전이입니다.
Owner·topology proposal 통과를 문장 구조도 검증된 것처럼 소비했고, 전체 command·field inventory와
대표 문서의 직접 closure oracle 없이 두 번째 문서 mutation으로 확장했습니다. 그 결과 final
reviewer가 동결 후보를 판정하는 대신 공통 contract를 한 finding씩 공급하는 authoring loop가 됐고,
사용자는 이미 설명한 구조를 반복해서 다시 요구해야 했습니다.

### 수직 원인과 기존 방어의 실제 효과

1. 공통 heading·coverage 오류가 늦게 발견된 이유는 전체 sibling을 먼저 작성했기 때문입니다.
2. 전체 sibling을 먼저 쓴 이유는 proposal의 owner·topology verdict를 대표 content proof로
   확대했기 때문입니다.
3. 그 확대가 통과한 이유는 문서화 workflow가 반복 문서의 authoring contract와 대표 consumer를
   writer admission 결과로 요구하지 않았기 때문입니다.
4. Final이 반복된 이유는 첫 공통 finding에서 contract와 전체 후보를 무효화하지 않고 각 finding을
   현재 후보의 국소 결함으로 처리했기 때문입니다.
5. 따라서 시스템 root는 설명력이 부족한 개별 문장이나 reviewer 수가 아니라, proposal에서
   writer로 넘어갈 때 공유 품질 contract·전체 inventory·대표 proof identity가 사라지는 interface
   공백입니다.

Fresh final은 부정확한 후보가 commit되는 것을 최종적으로 막았으므로 유지할 통제입니다. 그러나
작성 전에 누락을 줄이겠다는 문서화 절차의 효과와 빠른 first-pass authoring은 증명하지 못했습니다.
Final finding을 더 늘리거나 진단 명령별 template를 추가해도 이 root를 제거하지 않습니다.

### 통제 처분과 반사실

- 의미·구조 proposal과 중대한 final은 각각 작성 가능성과 동결 후보 closure owner로 유지합니다.
- 문서화 workflow의 작성 진입은 같은 owner에서 강화합니다. 반복 문서군은 공통 독자 질문·설명
  level·heading hierarchy, 전체 문서·source inventory와 대표 문서 oracle을 고정하고, 전체 저상세
  골격과 상세 대표 문서 하나를 닫은 뒤에만 확장합니다.
- Final에서 공통 contract 결함이 처음 발견되면 finding별 patch를 금지하고 authoring contract와
  그 contract를 소비한 후보를 무효화합니다.
- 새 executable·custom schema·제품별 command 규칙은 추가하지 않습니다.

Root 제거 반사실에서 저상세 diagnostics topology를 먼저 대조하고 한 상세 문서의 상단 producer
전체를 동일 hierarchy로 설명한 뒤 source-first closure를 수행했다면, heading·출력·민감정보 계약은
나머지 sibling mutation 전에 드러났습니다. Terraform 환경별 runbook이나 API resource reference처럼
명사를 바꿔도 같은 전이가 적용됩니다. 반대로 독립 문서 한 개의 국소 수정은 이 gate로 막지 않습니다.

### 독립 행동 검증과 증거 경계

독립 evaluator는 통제 후보를 보기 전에 원사건, 장애 playbook transfer와 실제 다중 consumer
false-block, writer→reviewer→expansion 전이 기준을 동결했습니다. Fresh writer는 별도
fixture에서 전체 저상세 outline과 민감 field를 가진 상세 대표 문서 하나만 작성하고 멈췄습니다.
Evaluator가 raw source와 동일 candidate hash를 직접 대조해 확장을 허용한 뒤에만 같은 writer의
새 invocation이 나머지 sibling을 작성했고, 대표 candidate는 바뀌지 않았습니다.

False-block은 서로 다른 독자 질문·source·oracle을 가진 두 기존 문서를 같은 작업에서 각각
source와 대조해 수정하되, 첫 문서 closure를 두 번째 mutation의 admission으로 쓰지 않는 사례로
`PASS`했습니다. 문서 사건의 regression·transfer와 writer fixture invocation도 각각
`PASS`했습니다. 이 verdict는 현재 authoring contract의 모델링 효과와 해당 fixture 전이만 증명합니다. 실제 진단 문서
작성의 과거 순서를 소급해 바꾸거나 미래 문서 writer·다른 branch에서의 invocation을 완료로
승격하지 않으며, work item은 기존 Application runbook 복귀점 때문에 `In Progress`를 유지합니다.

## Scenario의 live delta를 재사용 단계로 누적한 사건

### 두 사건과 공통 불변식

`748a0bfe`는 기존 EC2 archive 전달 완료 항목을 유지한 채, 새 Super Admin script가 아직
전달되지 않았다는 상태를 `확장 EC2 실행 파일 재전달`이라는 두 번째 실행 항목으로
추가했습니다. 후속 ingress 작업도 같은 항목에 Ingress script를 더해 이 구조를 다시
확장했습니다. 두 사건의 공통 불변식은 재사용 가능한 Scenario를 빈 환경에서 실행할 때 같은
actor·대상·상태 전이·완료 조건을 한 번만 수행하고, 기존 환경의 revision delta는 현재 live
상태에서만 추적하는 것입니다.

### 이전 방어 생애주기의 실패

Scenario 유형은 사건 전에 이미 재사용 흐름과 live 상태를 명확히 나누고 과거 snapshot을
누적하지 말라고 규정했습니다. `748a0bfe` 후보는 문서 전문 final까지 통과했지만, writer와
reviewer는 `기존 전달`과 `확장 재전달`이라는 서로 다른 제목을 독립 작업처럼 소비했습니다.
각 항목의 actor·대상·상태 전이·완료 조건을 정규화하거나 빈 환경에서 전체 순서를 재생하지
않아, 내부 문장과 link가 모두 맞는 후보를 재사용 recipe로도 맞다고 판정했습니다.

다섯 번의 인과를 닫으면 직접 오류는 같은 전달을 두 checklist 항목으로 만든 것, 허용 원인은
현재 미전달 상태를 새 영구 행동으로 분류한 것, interface 공백은 statement를 재사용 흐름과
live 상태 중 하나로 배치하는 전이 대조 부재, oracle 실패는 제목·시점 차이를 독립 작업으로
인정한 것입니다. 2차 root는 Scenario의 두 책임을 선언했지만 같은 상태 전이를 식별하는
writer·final 입력과 replay oracle로 연결하지 않은 통제 생애주기 단절입니다.

### 통제 처분과 남은 위험

Scenario가 재사용 흐름과 live 상태를 함께 소유하는 구조는 유지합니다. 문서 정보 구조와
문서화 final을 강화해 실행 항목과 복합 항목의 하위 행동을 actor·artifact·effective target
state·상태 전이·완료 조건으로 대조하고, 빈 환경 replay에서 중복 전이를 거부합니다. 제품별
재전달 금지나 새 verifier는 추가하지 않습니다. 명사를
certificate rotation이나 configuration bundle로 바꿔도 같은 revision delta는 현재 상태에만
남고, 실제로 다른 target state를 만드는 독립 전이는 별도 단계로 유지해야 합니다.
