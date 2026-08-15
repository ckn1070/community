# 스크립트 엔지니어링 가이드

이 문서는 저장소가 소유하는 실행 스크립트의 책임 경계, 인터페이스, 안전성, 검증과
운영 생애주기를 정의합니다. 스크립트를 새로 도입할지와 계속 유지할지는
[도구 도입과 생애주기 워크플로우](130-tool-adoption-workflow.md)가 먼저 판정하고,
실제 운영 순서와 명령은 `docs/operations`의 해당 runbook이 소유합니다.

스크립트도 production code입니다. 반복 명령을 파일에 옮겼다는 이유만으로 자동화가
완성되지 않습니다. 호출자가 입력·결과·실패를 예측할 수 있고, 중단 뒤 안전하게
복구하거나 재실행할 수 있으며, 실제 target 환경에서 검증 가능한 실행 계약을 가져야
합니다.

## 적용 범위와 책임 경계

이 가이드는 다음 저장소 실행물에 적용합니다.

- 운영체제와 host를 준비하는 bootstrap·installer·migration script
- build, test, 품질 검증과 release를 보조하는 script
- 개발·운영 작업을 자동화하는 shell·Python·Java executable과 wrapper
- 여러 하위 실행 단계를 순서대로 호출하는 orchestrator·dispatcher
- hook, verifier와 장애 진단·복구 helper

문서별 책임은 다음처럼 나눕니다.

| 독자 질문 | canonical owner |
| --- | --- |
| 이 자동화를 도입·유지할 가치가 있는가 | `130-tool-adoption-workflow.md` |
| 도입한 스크립트를 어떻게 나누고 구현·검증하는가 | 이 문서 |
| 특정 작업에서 무엇을 어떤 순서로 실행하는가 | `docs/operations`의 해당 runbook |
| 특정 디렉터리에 어떤 스크립트가 있고 어떻게 호출하는가 | 해당 `scripts/<영역>/README.md` |
| 스크립트가 호출하는 외부 도구의 version과 기술 계약 | 해당 tech-stack 또는 책임 SOT |

도입 여부와 구현 기준이 동시에 바뀌면 `130`을 먼저 적용한 뒤 이 문서를 적용합니다.
기존 스크립트의 내부 구현만 바뀌고 도입 목적·caller·비용·보장 범위가 그대로라면 이
문서만 적용합니다.

## 목표와 우선순위

스크립트 설계는 다음 순서로 최적화합니다.

1. **정확성**: 의도한 target과 상태만 변경하고 실패를 성공으로 보고하지 않습니다.
2. **안전성과 복구 가능성**: 부분 실패, 재실행, 중단과 잘못된 입력의 피해를 제한합니다.
3. **관찰 가능성**: 실행자가 현재 단계, 결과와 다음 복구 행동을 알 수 있습니다.
4. **재현성과 이식성**: 선언한 환경에서 같은 입력이 같은 계약을 따릅니다.
5. **유지보수성**: 작성자가 아닌 사람이 책임·변경 영향과 test를 이해할 수 있습니다.
6. **운영 효율**: 반복 작업과 사람의 실수를 줄이되 자동화의 유지비를 통제합니다.
7. **편의와 코드 크기**: 위 조건을 해치지 않는 범위에서만 최적화합니다.

짧은 코드, 한 번에 실행되는 파일과 높은 자동화 비율은 그 자체로 목표가 아닙니다.
자동화가 오류를 더 빠르고 넓게 전파하거나 유지비가 수동 작업보다 크면 범위를 줄이거나
수동 checkpoint를 남깁니다.

## 복잡성 admission과 과설계 판정

Guard, helper, parser, planner, 상태 cache, test double과 문서 상세는 각각 독립된 복잡성
단위입니다. 구현하거나 존치하려면 다음 질문에 모두 답할 수 있어야 합니다.

1. 이 단위가 현재 script의 어떤 직접 결과 또는 mutation을 보호합니까?
2. 없을 때 발생하는 구체적인 손상·오판·복구 불가능 상태는 무엇입니까?
3. 같은 보장을 package manager, consumer parser, atomic filesystem primitive처럼 더
   authoritative한 기존 도구가 이미 제공하지 않습니까?
4. 가장 단순한 표현은 무엇이며, 추가 상태·분기·parser가 꼭 필요한 이유는 무엇입니까?
5. 관찰 가능한 결과나 실제 위험 경계로 검증할 수 있습니까?

답하지 못한 단위는 “안전해 보인다”, 미래에 재사용할 수 있다거나 test하기 편하다는 이유로
admission하지 않습니다. 검토 결과는 다음 네 가지를 섞지 않고 분류합니다.

| 분류 | 판정 기준 | 처분 |
| --- | --- | --- |
| 유지 | 현재 단계의 직접 결과를 만들거나, 구체적인 손상·덮어쓰기·불완전 상태를 막거나, 현재 단계 postcondition을 검증합니다. | 필요한 최소 표현과 동작 test를 유지합니다. |
| 단순화 | 보장은 필요하지만 표준 도구나 더 짧은 표현으로 같은 보장을 얻을 수 있습니다. | 별도 parser·planner·wrapper를 제거하고 authoritative primitive에 위임합니다. |
| 제거 | 이전 단계의 완료 상태를 누적 재검사하거나, 현재 caller·시점에 없는 위험을 미리 방어하거나, 결과와 무관한 내부 순서를 고정합니다. | 코드·test·문서에서 함께 제거합니다. 필요한 검사는 실제 consumer 단계로 이동합니다. |
| 결함 | 정상 상태를 차단하거나 실패를 성공으로 보거나 다른 target을 변경하는 등 계약이 틀립니다. | 과설계와 별도로 회귀 test를 먼저 두고 수정합니다. |

### 단계 경계

- Preflight는 **현재 단계의 mutation을 안전하게 시작할 수 있는지**와 지금 실패시키지 않으면
  복구가 어려운 조건만 확인합니다.
- 앞 단계가 남긴 모든 파일·service·hold·kernel 상태를 다음 단계마다 다시 검증하지
  않습니다. Runbook의 순서와 앞 단계의 postcondition이 handoff 계약입니다.
- 다음 단계가 값을 실제로 소비하는 순간에는 consumer가 요구하는 effective state만
  확인합니다. 이전 script의 marker, 구현 방식과 전체 완료 증거를 대신 확인하지 않습니다.
- Postcondition은 현재 script가 직접 만든 결과만 검증합니다. 나중 단계의 성공 가능성을
  미리 증명하려고 범위를 넓히지 않습니다.

### 표준 도구와 최소 표현

- Exact package version처럼 package manager가 직접 판정할 수 있는 조건은 요청에 그대로
  전달하고 exit status를 처리합니다. 같은 출력을 미리 parsing해 availability를 중복
  판정하지 않습니다.
- 무해한 반복을 피하려고 누락 항목 planner, marker와 cache를 만들지 않습니다. 반복 자체가
  손상을 만들 때만 실제 상태 기반 skip을 둡니다.
- 한 local scope의 임시 file 정리는 그 scope의 직접 `trap`을 사용합니다. 문자열 command를
  조립하는 공통 cleanup wrapper는 여러 caller의 안정된 별도 계약이 확인되기 전에는 두지
  않습니다.
- 줄 수는 복잡성의 경보이지 판정 기준이 아닙니다. 100줄을 넘으면 상태 모델·parser·fake와
  분기가 늘어난 원인을 다시 확인하되, 줄 수만으로 분리하거나 언어를 바꾸지 않습니다.

### Test와 전체 검토 주장

- Test는 최종 상태, 손상 방지, 실패 전파와 복구에 필요한 상대 순서만 고정합니다. 내부
  helper 전체 목록, 모든 action의 exact 순서와 “두 번째 실행에서 호출 0회”를 계약으로
  만들지 않습니다.
- “과한 설계가 더 없다”처럼 전체 부재를 주장하기 전에는 판정 기준을 먼저 고정하고, 대상
  script뿐 아니라 contract test, 가까운 README와 운영 runbook까지 같은 기준으로 전수
  분류합니다. 일부 파일이나 새로 발견한 예만 보고 전체 결론을 갱신하지 않습니다.
- 후속 질문에는 고정한 기준과 전체 inventory를 유지합니다. 새 authoritative source나 사용자
  요구가 기준을 바꾼 경우에만 무엇이 왜 바뀌었는지 먼저 밝히고 재분류합니다.

## 스크립트와 구현 언어 선택

Shell은 다른 프로그램을 연결하는 좁은 orchestration에 사용합니다. 다음 조건을 대부분
만족하면 Shell이 적합합니다.

- 주 작업이 package manager, system command와 기존 executable 호출입니다.
- 입력과 출력이 단순한 문자열·파일·exit status입니다.
- 분기, 자료구조, 동시성과 상태 전이가 단순합니다.
- 성능이 병목이 아니며 별도 library 생태계가 필요하지 않습니다.
- target OS와 사용할 shell을 명확히 제한할 수 있습니다.

다음 중 하나가 핵심 책임이면 Python·Java 같은 구조화된 언어나 이미 도입한 전용 도구를
우선 검토합니다.

- 복잡한 JSON·YAML·API payload와 schema를 지속적으로 변환합니다.
- 중첩 분기, 복잡한 자료구조, 병렬 실행이나 장시간 실행 상태를 관리합니다.
- 정교한 retry·transaction·rollback 또는 여러 backend adapter가 필요합니다.
- business rule이나 재사용 library를 소유합니다.
- Shell test double만으로 핵심 동작을 안정적으로 검증하기 어렵습니다.
- 코드 증가보다 제어 흐름과 변경 영향의 이해 비용이 이미 큽니다.

`100`줄은 재검토 신호일 수 있지만 자동 분리·재작성 기준은 아닙니다. 줄 수가 짧아도
위 복잡성이 있으면 다른 언어가 적합하고, 반복적인 선언이 길어도 책임과 제어 흐름이
단순하면 Shell을 유지할 수 있습니다. 새 runtime이나 도구가 필요해지면 그 도입은
`130-tool-adoption-workflow.md`로 돌아가 판정합니다.

## 책임과 파일 분할

### 기본 분할 단위

하나의 스크립트는 **하나의 관찰 가능한 결과와 실패 경계**를 소유합니다. 같은 파일에
두 단계는 다음 계약이 같을 때만 함께 둡니다.

- 목적과 최종 postcondition
- 필요한 권한과 target 환경
- 실패했을 때 재시작할 위치
- 멱등성·재실행 정책
- 검증 방법과 완료 증거
- 변경·upgrade 주기와 owner
- rollback 또는 forward recovery 방법

다음 중 하나라도 독립적이면 파일을 분리합니다.

- 앞 단계 성공을 보존한 채 뒤 단계만 재시도할 수 있습니다.
- reboot, 승인, credential 입력이나 사람이 판단하는 checkpoint가 있습니다.
- 비가역적 상태 변경이나 blast radius가 달라집니다.
- 지원 version과 release cadence가 다릅니다.
- 별도 test, rollback 또는 운영 owner가 필요합니다.

명령 한 줄마다 파일을 나누거나 모든 설치를 하나의 거대한 파일에 넣지 않습니다. 분할의
기준은 명령 수가 아니라 실패·재실행·검증 경계입니다.

### bootstrap 예시

단일 host에 Kubernetes를 준비하는 경우 다음은 합리적인 시작점입니다.

| 스크립트 | 관찰 가능한 책임 | 분리 이유 |
| --- | --- | --- |
| `100-prepare-host.sh` | OS package와 kernel·sysctl·swap 전제 준비 | reboot와 host 전제 검증 경계 |
| `200-install-containerd.sh` | container runtime 설치·설정·기동 | 독립 service와 version·재시도 경계 |
| `300-install-kubernetes-components.sh` | 호환되는 kubelet·kubeadm·kubectl 설치 | Kubernetes package version 계약 |
| `400-initialize-cluster.sh` | cluster control-plane 상태 최초 생성 | 비가역성에 가까운 상태 mutation 경계 |

CNI처럼 제품 선택과 cluster 상태 변경이 별도로 필요한 구성은 선택이 확정된 뒤 독립
단위로 둡니다. 이 표는 고정 파일 목록이 아니라 분할 원칙의 적용 예입니다.

### 공통 library와 wrapper

- 두 개 이상의 실제 caller가 같은 안정된 계약을 사용할 때만 공통 library를 둡니다.
- 공통 library는 source될 때 mutation하거나 shell option을 임의로 바꾸지 않습니다.
- wrapper는 하위 책임을 숨기지 않고 exact script path와 순서를 명시합니다.
- wrapper의 성공은 모든 필수 하위 postcondition이 성공했을 때만 반환합니다.
- 하위 단계의 독립 실행·test·제거 가능성을 유지합니다.
- 한 caller만 있는 짧은 코드에 미래 재사용을 예상해 framework를 만들지 않습니다.

## 디렉터리, 이름과 실행 순서

스크립트 경로는 기술 이름만이 아니라 **언제 누가 반복해서 사용하는지**를 먼저
드러냅니다. 파일을 배치하기 전에 caller·실행 시점·대상 상태·반복 여부를 기준으로 다음
생애주기 책임을 하나만 선택합니다.

| 경로 | 사용 계약 |
| --- | --- |
| `scripts/bootstrap/<기술 영역>/` | target의 최초 구축·재구축에서 원하는 초기 상태를 만드는 변경 |
| `scripts/operations/<기술 영역>/` | 구축 완료 뒤 운영자가 필요할 때 반복하는 변경·복구 작업 |
| `scripts/verify/<기술 영역>/` | 구축 결과나 운영 상태를 반복 확인하며 영구 목표 상태를 소유하지 않는 점검 |
| `scripts/quality/` | repository source의 local·CI 품질을 판정하는 검증 |

- 최상위 생애주기와 하위 기술 영역을 반대로 배치하지 않습니다. `scripts/ecr/`처럼 기술
  이름만 최상위에 두면 최초 구성과 반복 검증·운영을 경로에서 구분할 수 없기 때문입니다.
- 하나의 executable이 둘 이상의 생애주기 책임을 함께 가지면 편의상 한 경로에 두지 않고
  `책임과 파일 분할`의 실패·재실행·side effect 경계로 먼저 분리합니다.
- `bootstrap`은 순서상 앞에 있다는 뜻이 아닙니다. target의 최초 구축이나 같은 상태로의
  재구축에만 사용하는지로 판정합니다. 다른 bootstrap 단계 뒤에 실행돼도 이 계약이면
  bootstrap입니다.
- 기술 영역은 같은 생애주기 안에서 구분이 필요할 때만 하위 디렉터리로 둡니다. 서로 다른
  기술을 한 디렉터리에 섞거나, 생애주기가 다른 파일을 기술 이름만 같다는 이유로 합치지
  않습니다. `quality`처럼 최상위 생애주기 자체가 현재 책임을 충분히 구분하면 불필요한
  한 단계 하위 디렉터리를 만들지 않습니다.
- 파일명은 저장소 관례에 맞춰 lowercase kebab-case를 사용하고 동사와 결과가
  드러나게 합니다.
- 직접 실행하는 파일은 첫 줄의 정확한 shebang과 executable permission을 함께
  유지하고, source 전용 library와 구분합니다.
- 필수 순서가 있는 디렉터리만 `100`, `200`, `300`처럼 고정 폭 3자리 prefix를
  사용합니다.
- 번호 간격은 중간 단계를 추가할 여지를 주지만 번호가 dependency를 보장하지는
  않습니다.
- 선택 실행, 진단과 rollback helper는 실행 순서인 것처럼 번호를 붙이지 않습니다.
- orchestrator는 `*.sh` glob이나 디렉터리 정렬 결과에 의존하지 않고 승인된 exact
  path를 명시적으로 호출합니다.

번호는 사람과 runbook이 읽는 순서 계약입니다. 파일을 추가했다는 이유만으로 자동
실행되게 만들지 않습니다. 실행 dependency는 wrapper와 runbook에서 명시합니다.

## 실행 계약

각 스크립트는 구현 전에 다음 계약을 고정합니다.

```text
caller와 실행 시점
→ 지원 OS·architecture·shell과 사전 조건
→ 입력·credential·권한
→ 변경 대상과 side effect
→ 관찰 가능한 postcondition
→ 출력과 exit status
→ 부분 실패·재실행·복구 방법
```

상태를 변경하는 entrypoint는 다음 실행 phase가 log와 code에서 구분되게 합니다.

1. **preflight**: 입력, target, 권한, dependency와 현재 상태를 확인합니다.
2. **plan**: 실행할 변경과 skip할 상태를 확정하고 필요한 경우 사용자에게 보여 줍니다.
3. **mutate**: 확정된 target에 가장 작은 변경 단위를 적용합니다.
4. **verify**: command 종료가 아니라 실제 postcondition을 확인합니다.
5. **report**: 성공·부분 변경·실패, 재실행 가능 여부와 다음 행동을 반환합니다.

모든 phase를 함수나 별도 파일로 만들 필요는 없습니다. 다만 mutation 뒤에야 알 수 있는
필수 전제를 preflight에서 놓치거나, report가 verify보다 먼저 성공을 선언하지 않게
실행 경계를 유지합니다.

### 입력과 설정

- 필수 입력은 누락 시 mutation 전에 실패합니다.
- option은 `--help`에서 의미, 기본값, 단위와 예시를 확인할 수 있게 합니다.
- 같은 값에 CLI option, 환경변수와 설정 파일을 함께 허용하면 우선순위를 문서화합니다.
- positional argument는 순서가 자연스럽고 적은 필수값에만 사용합니다.
- 값의 형식뿐 아니라 허용 범위와 실제 target 존재 여부를 mutation 전에 확인합니다.
- 현재 작업 디렉터리를 암묵적으로 가정하지 않고 script 위치나 명시적 option으로 path를
  결정합니다.
- locale, timezone과 정렬 순서가 결과를 바꾸면 실행 계약에서 고정합니다.

기본 실행은 non-interactive로 설계합니다. 사람의 판단이 필요한 경우 runbook의 manual
checkpoint로 두거나, interactive wrapper와 non-interactive core를 분리합니다. prompt가
있는 script를 CI나 unattended automation에서 자동 사용하지 않습니다.

### 출력과 exit status

- `0`은 모든 필수 postcondition이 충족됐을 때만 반환합니다.
- 실패는 non-zero로 반환하고 실패 단계, target과 복구 행동을 `stderr`에 남깁니다.
- machine-readable 결과를 제공하면 `stdout`은 그 결과에 예약하고 진단 log와 섞지
  않습니다.
- caller가 구분해 처리할 필요가 있을 때만 추가 exit code를 정의하고 README에
  의미를 기록합니다.
- 명령이 끝났다는 사실을 상태가 올바르다는 증거로 사용하지 않고 postcondition을 직접
  확인합니다.
- child process 실패를 삼키거나 마지막 성공 명령으로 전체 실패를 덮지 않습니다.

### 지원 환경과 dependency

- OS, release, CPU architecture와 shell 차이가 동작에 영향을 주면 지원 범위를 명시하고
  확인되지 않은 환경에서는 fail closed합니다.
- 필수 executable과 version·권한·network 전제를 mutation 전에 preflight합니다.
- 호환성이 결합된 component는 version을 함께 고정하고 독립적으로 `latest`를 따라가지
  않습니다.
- platform별 분기가 커지면 한 파일의 조건문을 늘리기보다 platform별 adapter나 다른
  구현을 검토합니다.

## Shell 구현 기준

### interpreter와 shell option

- POSIX `sh`와 Bash 중 실제 사용할 언어를 먼저 선택하고 shebang과 문법을 일치시킵니다.
- Bash 기능을 사용하면서 `#!/bin/sh`로 선언하지 않습니다.
- privileged·고정 platform script는 보장된 absolute interpreter path를 사용합니다.
- 개발 환경을 오가는 script가 `#!/usr/bin/env bash`를 사용하면 `PATH`가 interpreter
  선택의 신뢰 경계임을 받아들이고 지원 환경에서 실제 Bash를 확인합니다.
- 실행 entrypoint인 Bash script는 기본적으로 `set -euo pipefail`을 사용합니다. `ERR`
  trap을 함수·subshell·command substitution까지 상속해야 할 때 `-E`를 추가합니다.
- `errexit`의 예외 문맥과 pipeline 동작을 이해하고 예상 가능한 실패는 `if`, `case`와
  명시적 status 처리로 표현합니다. shell option은 error handling 설계를 대신하지
  않습니다.
- source되는 library는 caller의 shell option을 임의로 변경하지 않습니다.

### 값과 command 처리

- 변수와 command substitution은 의도적인 word splitting·glob이 아니면 quote합니다.
- 여러 argument는 문자열 조립이나 `eval` 대신 array로 전달합니다.
- argument forwarding은 `"$@"`를 사용합니다.
- option으로 오인될 수 있는 path 앞에는 command가 지원하는 `--`를 사용합니다.
- format string은 상수로 두고 값은 `printf '%s\n' "${value}"`처럼 별도 argument로
  전달합니다.
- line 입력은 필요한 delimiter와 escaping을 정하고 `read -r`을 기본으로 사용합니다.
- `ls`나 사람이 읽는 표를 parsing하지 않고 machine-readable interface나 filesystem
  primitive를 사용합니다.
- command substitution 결과를 `local` 선언과 같은 문장에 넣어 실제 exit status를
  숨기지 않습니다.
- alias, `eval`과 동적으로 조립한 shell source는 사용하지 않습니다.

함수가 있는 script는 상수, 함수, `main` 순서로 두고 마지막에 `main "$@"`를 호출합니다.
함수 내부 변수는 `local`, 변경하지 않는 전역값은 `readonly`로 의도를 드러냅니다.

## 상태 변경의 안전성

### 권한 경계

- 전체 책임이 root 권한을 필요로 하면 시작 시 effective user를 확인하고 즉시 실패합니다.
- script 내부 여러 명령에 `sudo`를 흩뿌리지 않습니다. 권한 상승의 주체는 caller와
  runbook이 소유합니다.
- 일부 단계만 높은 권한이 필요하면 별도 script로 분리해 최소 권한 경계를 유지합니다.
- 실행 사용자의 `PATH`, umask와 credential source가 결과를 바꾸면 명시적으로 검증합니다.

### 멱등성과 재실행

반복 실행 가능한 운영 script는 **원하는 상태로 수렴**하도록 설계합니다. 이때 목표는
모든 command의 반복을 없애는 형식적 멱등성이 아니라, 정상 반복과 부분 실패 뒤의 재실행이
상태를 훼손하거나 새로운 문제를 만들지 않는 **재실행 안전성**입니다.

- 이미 충족된 상태는 실제 authoritative state로 확인한 뒤 안전하게 건너뜁니다.
- 첫 실행이 중간에 실패해도 다음 실행이 완료되지 않은 단계부터 수렴할 수 있게 합니다.
- 재실행이 영구 설정·resource를 중복 생성하거나, 완성된 backup·다른 owner의 상태를
  덮어쓰거나, 불완전한 중간 결과를 정상 상태로 오인하게 해서는 안 됩니다.
- 단순 marker file보다 package, service, config와 API의 실제 상태를 우선 확인합니다.
- 선언된 실행 단계에서 결과를 훼손하지 않는 package index 갱신, 이미 충족된 package
  command나 초기 bootstrap 중 service restart는 반복될 수 있습니다. 같은 restart라도
  live workload를 중단하는 운영 단계라면 무해한 반복으로 취급하지 않습니다.
- 무해한 반복을 생략하기 위해 marker, cache, change detector, rollback framework를
  추가하면 새 실패 지점과 유지보수 비용이 생깁니다. 반복 자체에 문제가 없으면 이런
  장치를 만들지 않습니다.
- 반복 실행이 불가능한 최초 초기화·migration은 그 사실을 계약에 표시하고 기존 상태를
  mutation 전에 차단합니다.
- `--force`는 검증을 무시하는 범용 탈출구로 쓰지 않고 허용하는 정확한 상태 전이를
  문서화합니다.

모든 script를 억지로 멱등하게 만들지는 않습니다. 비멱등 작업은 one-shot 경계,
중복 실행 차단, 성공 판정과 복구 방법을 더 엄격하게 둡니다.

재실행 contract test는 두 번째 실행에서 command가 한 번도 호출되지 않았는지가 아니라,
최종 postcondition이 유지되고 중복·손상·잘못된 덮어쓰기와 부분 실패 부산물이 없는지를
검증합니다. 특정 command를 건너뛰는 것이 안전성의 필수 조건일 때만 호출 횟수나 순서를
직접 검증합니다.

### 파일과 동시성

- 설정 파일은 가능한 경우 같은 filesystem의 임시 파일에 쓰고 syntax·내용·권한을
  검증한 뒤 atomic rename으로 교체합니다.
- 구조화된 설정은 지원 version의 공식 최소 설정과 default·merge 의미로 필요한 최종
  상태만 표현할 수 있는지 먼저 확인합니다. 가능하면 그 최소 template을 사용하고 실제
  consumer의 parser와 postcondition으로 검증합니다.
- 생성된 전체 기본 설정을 parsing·변환하는 경로는 기존의 비기본값을 보존하거나 전체
  snapshot을 명시적으로 소유해야 하는 요구가 있을 때만 사용합니다. 별도 parser가 더
  안전해 보인다는 이유만으로 최소 표현과 같은 결과를 내는 변환 계층을 추가하지 않으며,
  test는 parser의 중간 구조가 아니라 필요한 최종 상태와 실패 경계를 고정합니다.
- 임시 파일과 디렉터리는 안전한 생성 primitive를 사용하고 `trap`으로 정리합니다.
- 원본 backup을 만들면 복구 명령, 보존 기간과 민감정보 권한을 함께 정의합니다.
- 같은 target에 동시 실행될 가능성이 있으면 lock 또는 외부 serialization owner를
  정하고 lock 실패를 명시적으로 보고합니다.
- signal이나 비정상 종료가 남기는 lock·temporary state와 child process를 처리합니다.

### destructive operation과 복구

- 삭제·덮어쓰기 전 exact target을 canonical path와 allow-list로 확인합니다.
- 빈 변수, unresolved environment variable, broad glob과 상위 directory가 destructive
  target이 되지 않도록 mutation 직전에 다시 검증합니다.
- blast radius가 큰 작업은 `--dry-run` 또는 별도 계획 단계를 제공할 가치를 검토합니다.
- rollback이 실제로 가능한지, forward recovery만 가능한지, 비가역적인지를 실행 전에
  구분합니다.
- rollback script는 원래 작업의 존재만으로 만들지 않고 실제 복구 계약과 test가 있을
  때만 제공합니다.
- partial failure 뒤 이미 성공한 단계를 무조건 되돌리지 않고 target system이 지원하는
  일관된 복구 전략을 사용합니다.

### retry와 timeout

- 외부 호출과 장시간 command에는 정상 최대 시간을 근거로 timeout을 정합니다.
- retry는 일시적 network·rate limit·eventual consistency처럼 재시도로 회복 가능한
  실패에만 적용합니다.
- retry 횟수와 총 시간을 제한하고 필요하면 exponential backoff와 jitter를 사용합니다.
- 인증 실패, 잘못된 입력, incompatibility와 validation failure를 재시도하지 않습니다.
- non-idempotent operation은 공급자가 idempotency key나 안전한 상태 확인을 제공하지
  않으면 자동 재시도하지 않습니다.

## Secret과 공급망

- password, token, private key와 connection string을 source, command argument, process
  list, trace log와 shell history에 남기지 않습니다.
- secret은 승인된 secret manager, 제한된 permission의 file, file descriptor나 안전한
  표준 입력처럼 노출 범위가 좁은 경로로 전달합니다.
- `set -x`는 secret을 처리하는 production script에서 기본적으로 사용하지 않습니다.
- error message, environment dump와 child command output에서 secret을 redaction합니다.
- 임시 secret file은 최소 권한으로 만들고 종료·signal에서 삭제합니다.

외부 executable·package·script를 받을 때는 다음 순서를 따릅니다.

1. 공급자의 공식 HTTPS 배포 경로와 지원 platform을 확인합니다.
2. 가능하면 서명된 package repository나 immutable image digest를 사용합니다.
3. 직접 download하면 version을 고정하고 공급자가 게시한 signature·checksum·provenance를
   독립 경로에서 검증합니다.
4. 임시 위치에서 검증을 끝낸 뒤 설치·실행합니다.
5. update owner와 취약점·지원 종료 시 교체 조건을 기록합니다.

`curl | sh`, unverified archive 실행과 mutable `latest` 의존을 reproducible bootstrap의
기본 경로로 사용하지 않습니다. checksum 값을 download artifact와 같은 신뢰되지 않은
응답에서 함께 받아 비교하는 것은 독립 검증이 아닙니다.

## 관찰 가능성과 운영

- 배포되거나 운영 상태를 변경해 실행 artifact 추적이 필요한 script는 version 또는
  Git revision, target, mode와 핵심 non-secret 입력을 식별할 수 있게 합니다.
- 각 log는 단계, 대상, 결과와 실패 원인을 구분할 수 있어야 합니다.
- 시간이 장애 분석에 필요하면 일관된 timezone과 machine-parseable timestamp를
  사용합니다.
- progress log와 최종 postcondition 결과를 구분합니다.
- 성공·skip·warning·failure가 같은 표현으로 섞이지 않게 합니다.
- 실행 시간이 길면 현재 진행과 timeout 여부를 판단할 최소 heartbeat를 제공합니다.
- metric이나 audit event가 필요하면 shell text parsing을 늘리기보다 정식 관측 경계를
  가진 도구나 application으로 승격할지 검토합니다.

자동화가 실패하면 message만으로 운영자가 최소한 다음을 알 수 있어야 합니다.

```text
어느 단계와 target이 실패했는가
무엇이 이미 변경됐는가
재실행해도 안전한가
다음 확인·복구 명령은 무엇인가
어떤 log·artifact를 보존해야 하는가
```

## 문서화 계약

공통 규칙은 이 문서, 디렉터리 공통 사실은 `scripts/<영역>/README.md`, 특정 운영 절차는
runbook이 각각 한 번만 소유합니다. script header는 이름만으로 복원하기 어려운
script-specific 계약만 설명하고 README와 runbook을 복사하지 않습니다.

새 스크립트나 책임이 큰 변경에는 가까운 README 또는 책임 문서에서 다음 항목을 확인할
수 있어야 합니다.

```md
### <script 이름>

- 목적과 비목표:
- caller와 실행 시점:
- 지원 OS·architecture·shell:
- 필요한 권한·dependency·network:
- 입력과 secret 전달 경로:
- side effect와 postcondition:
- 멱등성·재실행·부분 실패 정책:
- 출력과 exit status:
- 검증 방법:
- rollback 또는 forward recovery:
- owner·version 갱신·제거 조건:
```

runbook은 각 단계를 `수동 필수`, `스크립트 실행`, `사람의 확인`으로 구분하고 exact
script path, 입력, 기대 postcondition과 실패 시 복귀점을 기록합니다. 스크립트가 있다는
사실만으로 운영 절차가 자동 실행된다고 표현하지 않습니다.

주석은 [코드와 설정 주석 작성 가이드](220-code-commenting-guideline.md)에 따라 코드만으로
복원하기 어려운 정책, 위험, 이유와 변경 조건을 설명합니다. 명령을 자연어로 번역하거나
모든 줄에 주석을 붙이지 않습니다.

## 검증 전략

검증은 실제 위험 경계에 가까워지는 순서로 구성합니다.

| 계층 | 검증 대상 | 성공이 의미하지 않는 것 |
| --- | --- | --- |
| syntax | interpreter가 파일을 parse함 | 외부 command와 상태 변경의 정확성 |
| static analysis | quoting, 확장, 취약한 pattern 후보 | runtime 환경과 business postcondition |
| unit·contract | argument parsing, 순수 함수, command 조합 | 실제 OS·cloud·service 동작 |
| integration | 임시 VM·container에서 package·file·service 결과 | production credential·network·quota |
| live·smoke | 실제 target의 좁은 postcondition | 모든 환경과 장애 시나리오의 안전성 |

### Shell 기본 검증

ShellCheck의 local 완료 gate 도입 경계는 다음과 같습니다.

| 판단 축 | 현재 결정과 이유 |
| --- | --- |
| 근본 문제·무도구 대안 | `bash -n`은 parse 가능 여부만 확인하고 contract test는 작성한 사례만 재현하므로 expansion, quoting과 pipeline의 정적 결함 후보가 남습니다. 사람 review만으로 반복 확인하지 않습니다. |
| 가장 단순한 도구·oracle | Shell 전용 표준 analyzer인 ShellCheck의 독립 rule을 사용하며 같은 검사를 위한 custom parser나 wrapper를 만들지 않습니다. |
| 배포·version·무결성 | 현재 지원하는 local executor는 macOS 26 Tahoe ARM64와 Homebrew입니다. `homebrew/core`의 `shellcheck` formula로 설치한 `0.11.0`을 pin합니다. Upstream `v0.11.0` source SHA-256은 `8b07554f92e4fbfc33f1539a1f475f21c6503ceae8f806efcc518b1f529f7102`, Homebrew `arm64_tahoe` bottle SHA-256은 `102f7f385855df8eabf5c9017b8d729a02a5ccca810aa23e3ae700a46226ab70`입니다. Formula가 이 version을 제공하지 않으면 최신 version으로 대신 통과시키지 않습니다. 다른 OS·CPU executor는 공식 배포물과 checksum을 `130`에서 검증해 이 지원 범위에 추가하기 전까지 local 완료를 판정하지 않습니다. |
| 권한·secret·trust boundary | 설치는 root가 아닌 local 사용자로 `brew install shellcheck`를 실행하며 Homebrew formula metadata, GitHub upstream source와 GHCR bottle을 신뢰합니다. 검증 실행은 network와 secret 없이 repository의 명시한 Shell path를 읽고 진단만 출력하며 source를 수정하지 않습니다. |
| 실행자·시점·실패 경계 | 저장소 Shell을 변경한 Agent가 최종 후보에서 한 번 실행합니다. 진단이나 실행 실패는 static analysis와 local 완료만 차단합니다. |
| 보장·오판 경계 | 정적 결함 후보만 탐지하며 runtime·integration·live postcondition은 증명하지 않습니다. 적용할 수 없는 진단은 좁은 suppression과 사람이 검토할 이유를 요구합니다. |
| 실제 caller 검증 | 최종 후보에서 변경·신규 Shell과 그 실행 경로의 entrypoint·test·source dependency를 exact path로 확정해 정상 통과, rule 진단 실패와 source 상대 경로 해석 경계를 확인합니다. 특정 시점의 file 목록을 영구 baseline으로 두지 않으며 이 검증은 runtime·live postcondition을 대신하지 않습니다. |
| owner·갱신·강등·제거 | 이 문서가 local gate 계약을 소유합니다. Upgrade는 release note, formula source·bottle checksum과 위 실제 caller의 정상·진단·source 경계를 다시 검증한 뒤 version과 hash를 함께 바꾸고 다시 pin합니다. 지원 Shell·platform, false block 빈도나 더 권위 있는 표준 도구가 달라지면 `130`에서 존치와 배치를 다시 판정합니다. Gate를 대체하거나 제거하기로 결정한 뒤에만 `brew unpin shellcheck`, `brew uninstall shellcheck`로 local 설치를 제거합니다. |

- 최종 후보의 Shell 검증 대상은 결과 단위에서 변경·신규인 모든 Shell 파일과 그
  실행 경로를 구성하는 entrypoint, test, source dependency입니다. 확장자나 executable
  bit만으로 대상을 추측하지 않고 shebang, source 관계와 실제 caller에서 정확한 path
  집합을 확정합니다.
- 대상 전체를 정확한 경로로 지정해 선언한 interpreter의 syntax 검사를 실행합니다. 현재
  Bash 대상은 최소 `bash -n`으로 확인합니다.
- 같은 대상 전체를 source까지 따라가는 `shellcheck -x -P SCRIPTDIR`로 검사하는 것은
  저장소 Shell 변경의 local 완료 gate입니다. `SCRIPTDIR`은 source annotation의 상대
  경로를 각 script 위치에서 해석하게 합니다. 최종 source 수정 뒤 실행하며, 검사 뒤
  후보가 바뀌면 해당 syntax 검사와 ShellCheck를 다시 실행합니다.
- ShellCheck가 없거나 실행·진단이 실패하면 static analysis를 통과했다고 판정하지 않고
  local 완료로 판정하지 않습니다. 도구를 사용할 수 없는 이유와 재개 조건을 검증
  공백으로 보고합니다.
- ShellCheck 진단은 수정하는 것이 기본입니다. 적용할 수 없는 진단만 rule, 적용하지
  못하는 이유와 제거 조건을 코드 가까이에 좁게 suppression하며, 파일·rule 전체를
  근거 없이 제외하지 않습니다.
- 이 gate는 작업 Agent의 local 검증 절차입니다. ShellCheck를 저장소 CI, 개발자 hook이나
  애플리케이션 설치 dependency로 추가하지 않으며, 그 범위로 확대하거나 위 version·
  배포 계약을 바꾸려면 `130`의 도입 판정을 별도로 통과합니다. Local 실행에서는 실제
  ShellCheck version과 pin 상태를 확인하고 완료 보고에 남깁니다.
- formatter를 도입하면 사람이 손으로 같은 style을 반복 교정하지 않도록 standard
  entrypoint를 제공하되 formatter 성공을 정확성 검증으로 표현하지 않습니다.

### 동작 검증

- command 호출은 임시 `PATH`의 fake·stub executable로 argument, 순서, input과 실패
  propagation을 검증할 수 있게 설계합니다.
- 외부 command의 exit·output, filesystem entry와 설정 후보를 분류하는 guard·classifier는
  실제 downstream consumer가 사용하는 선택 규칙을 공식 source나 동등한 지원 target에서
  먼저 복원합니다. Test double이 그 후보 집합이나 status 의미를 새로 정의하게 두지
  않습니다.
- Guard·classifier 자체를 stub으로 교체한 뒤 이미 선택된 값의 허용·거부만 확인하는 test는
  그 guard의 contract test가 아닙니다. 실제 후보 발견·분류를 실행하고, fake는 그보다 바깥의
  transport나 고정된 외부 interface만 대체합니다.
- filesystem test는 임시 directory를 사용하고 실제 사용자·system directory를
  변경하지 않습니다.
- 정상 실행뿐 아니라 잘못된 OS·architecture·권한·입력, dependency 누락, child 실패,
  timeout과 signal 중단을 검증합니다.
- 반복 가능한 script는 두 번째 실행과 첫 실행의 중간 실패 뒤 재실행을 검증합니다.
- destructive script는 dry-run·target guard와 잘못된 target 거부를 우선 검증합니다.
- package·service·kernel 동작은 실제 지원 OS의 disposable VM이나 동등한 환경에서
  integration test합니다. container가 kernel·systemd 계약을 대신할 수 있는지는
  별도로 판정합니다.
- cloud live 검증은 명시적 credential·비용·cleanup 경계에서 수행하고 일반 unit test와
  같은 성공으로 표현하지 않습니다.

test는 구현 line이 아니라 관찰 가능한 계약을 고정합니다. script 내부 함수가 지나치게
많은 fake와 호출 순서 검증을 요구하면 책임 분리나 구현 언어 선택을 다시 검토합니다.

## 변경과 운영 생애주기

### 변경 전

1. 실제 caller, runbook, wrapper와 downstream consumer를 찾습니다.
2. 기존 입력·출력·exit status·side effect와 재실행 계약을 복원합니다.
3. Script·test·README·runbook의 각 guard·helper·parser·planner·상세를 복잡성 admission으로
   분류합니다.
4. 변경이 구현만인지 도입 목적·비용·보장 범위까지 바꾸는지 판정합니다.
5. target platform, external version과 공식 계약을 확인합니다. 외부 interface에 의존하는
   각 guard·classifier·parser는 `실제 consumer 또는 공식 계약 → 적용한 source·동등한 target
   oracle → 검증 계층 → 아직 live에서만 확인 가능한 값`을 작업 evidence로 연결합니다.
   이 evidence는 현재 결과를 검증하기 위한 것이며 별도 영구 registry나 제품별 checklist를
   만들기 위한 것이 아닙니다.
6. 실제 consumer의 후보 선택이나 exit·output 의미를 test double이 대신 정의하지 않는지
   확인합니다.
7. 기존 dirty 변경과 이번 write set을 분리합니다.

### 변경 중

1. 가장 작은 관찰 책임과 실패 경계부터 변경합니다.
2. 정상·실패·경계 test를 함께 변경합니다.
3. script, local README와 runbook의 계약을 한 owner 원칙으로 동기화합니다.
4. backward compatibility가 필요하면 실제 caller와 종료 조건을 기록합니다.
5. 임시 우회는 발동 조건, 위험, owner와 제거 조건 없이 남기지 않습니다.

### 실제 target 불일치 뒤 복구 라운드

지원 target에서 처음으로 script의 외부 command, parser, filesystem 선택 또는 service 상태
가정이 실제 동작과 다르다고 확인되면 그 오류 한 줄이나 해당 script만 독립 수정하지
않습니다.

1. 현재 사용자에게 반환할 상위 결과 또는 runbook handoff의 pending mutation을 멈춥니다.
   그 dependency closure에서 같은 불충분한 source·test oracle로 target compatibility를
   판정한 관련 외부 interface와 local 완료 판정을, 이미 실행됐는지와 관계없이
   `Invalidated`로 돌립니다.
2. 해당 dependency closure의 남은 preflight·mutate·postcondition 경로와 무효화한 외부
   interface를 끝까지 열거하고, 실제 downstream consumer source·공식 계약·지원 target
   integration으로 Agent가 판정할 수 있는 항목을 먼저 모두 닫습니다.
3. Source와 재현 가능한 검증으로 닫히지 않고 실제 host에만 존재하는 값이 남을 때만 사용자
   입력으로 분류합니다. 같은 복구 결과를 막는 live-only 입력 전체를 한 번에 모아, 상태를
   바꾸지 않는 하나의 진단 묶음과 각 명령의 목적·기대 출력·secret redaction 경계를 함께
   요청합니다.
4. 진단을 위해 package 설치, resource apply, service 설정 변경이나 script가 자동화하려는
   작업의 수동 재현을 사용자에게 넘기지 않습니다.
5. 필요한 live evidence를 받으면 한 후보에서 영향받은 script·test·README·runbook과 관련
   외부 interface 전체를 다시 검증합니다. 같은 복구 라운드에서 또 다른 관련 가정이
   실패하면 새 오류만 덧대지 않고 source·oracle·질문 수집 전략 전체를 다시 엽니다.

이 경계는 최초 live qualification과 운영 중 회귀 모두에 적용합니다. 범위는 현재 반환할
결과의 dependency closure와 실패한 검증 전략을 실제로 공유한 interface까지이며, 전체
저장소나 target의 무관한 상태로 확대하지 않습니다. 반대로 모든 외부 계약이 source와 지원
target 검증으로 이미 닫혔거나 외부 interface가 없는 script에는 별도 진단 묶음이나 환경
inventory를 요구하지 않습니다.

### 완료와 배포

1. 최종 Shell 후보 전체에 선언한 interpreter의 syntax 검사와 ShellCheck를 실행합니다.
2. contract, integration과 live 검증 중 적용되는 범위를 실행합니다.
3. 실행하지 못한 계층은 성공으로 추정하지 않고 남은 위험을 구분합니다.
4. 최소 권한, secret redaction, partial failure와 recovery를 다시 확인합니다.
5. 실제 caller가 새 interface를 사용하고 이전 경로가 제거됐는지 확인합니다.
6. runbook의 exact path·순서·postcondition과 현재 구현을 대조합니다.
7. 완료 보고에는 실제 ShellCheck version, 검사한 exact path 또는 모호하지 않은 path
   집합, 통과·실패 결과와 남은 suppression의 rule·이유·제거 조건을 포함합니다.
8. 전체 설계 검토를 보고한다면 고정 기준으로 script·test·README·runbook 전부를 다시
   분류하고 미검토 범위를 남기지 않습니다.

### 존치와 제거

caller, target platform, 공식 배포 경로 또는 자동화의 순효과가 달라지면 `130`으로 돌아가
존치를 재평가합니다. 제거할 때 executable만 지우지 않고 caller, test, fixture, README,
runbook, 권한, secret과 설치 흔적을 함께 정리합니다.

## 피해야 할 패턴

- 명령 한 줄마다 파일을 나눠 실행 흐름을 찾기 어렵게 만드는 구조
- 여러 독립 상태 변경을 하나의 거대한 `setup.sh`에 숨기는 구조
- glob과 파일명 정렬을 암묵적인 실행 engine으로 사용하는 dispatcher
- 현재 directory, 개발자 개인 환경과 interactive shell profile에 의존하는 실행
- `sudo`, secret, retry와 error suppression을 여러 함수에 흩뿌리는 구현
- `curl | sh`, mutable `latest`, 검증하지 않은 binary·archive 실행
- `set +e`, `|| true`와 broad exception으로 필수 실패를 성공 처리하는 구현
- 임의 marker file만 보고 실제 target 상태를 확인하지 않는 skip
- 무제한 retry, 고정 sleep과 비멱등 operation의 자동 재호출
- 사람이 읽는 출력 parsing, 문자열 command 조립과 `eval`
- 성공 message를 출력한 뒤 postcondition을 확인하지 않는 구현
- static test 성공을 실제 host·cloud 배포 성공으로 확대하는 보고
- 실제 caller가 하나뿐인데 공통 framework·plugin system부터 만드는 설계

## 설계·검토 체크리스트

### 책임과 구조

- script가 필요한 이유와 무도구·기존 기능 대안은 `130`에서 확인됐습니다.
- 각 guard·helper·parser·planner가 직접 결과, 구체적 손상 또는 현재 postcondition 중 하나로
  admission됐고 표준 도구보다 약한 중복 판정이 없습니다.
- 하나의 관찰 가능한 결과와 실패·재실행 경계를 소유합니다.
- 파일 분할이 명령 수가 아니라 권한, postcondition, retry와 복구 경계를 따릅니다.
- 번호가 있다면 실제 필수 순서이며 orchestrator가 exact path를 호출합니다.
- Shell을 유지할 복잡도이고 다른 언어나 전용 도구로 승격할 신호가 없습니다.

### 인터페이스와 안전성

- caller, 지원 환경, 입력, 권한, side effect와 postcondition이 명확합니다.
- Preflight가 현재 단계의 mutation 안전 조건만 확인하고 앞 단계 전체를 누적 재검사하지
  않습니다.
- secret이 source, argument, process list와 log에 노출되지 않습니다.
- partial failure, 중단, 재실행과 rollback·forward recovery가 구분됩니다.
- destructive target, timeout, retry와 동시 실행 경계가 제한됩니다.

### 검증과 운영

- syntax와 적용 가능한 static·contract·integration·live 검증이 구분됩니다.
- 정상, 실패, 경계, 두 번째 실행과 중간 실패 후 재실행을 검토했습니다.
- log와 exit status가 실패 지점과 다음 행동을 알려줍니다.
- README와 runbook이 중복 원장 없이 실제 interface를 가리킵니다.
- owner, version 갱신, 지원 종료와 제거 조건이 있습니다.

## 공식 근거와 해석 범위

이 가이드는 다음 공식 자료의 공통 원칙을 저장소 상황에 맞게 종합했습니다. 각 자료의
조직·package·shell 고유 규칙을 그대로 보편 규칙으로 승격하지 않고, 현재 target과
caller에 적용되는 범위만 사용합니다.

- [Google Shell Style Guide](https://google.github.io/styleguide/shellguide.html): Shell을
  좁은 utility·wrapper에 제한하는 기준, quoting, 함수·변수와 static analysis 관행
- [GNU Bash Reference Manual](https://www.gnu.org/s/bash/manual/bash.html):
  `errexit`, `pipefail`, pipeline, trap과 exit status의 실제 동작
- [POSIX Shell Command Language](https://pubs.opengroup.org/onlinepubs/9799919799/utilities/V3_chap02.html):
  portable `sh`의 문법과 실행 계약
- [ShellCheck Wiki](https://www.shellcheck.net/wiki/): Shell expansion, quoting와 취약한
  command pattern의 static 진단 근거
- [ShellCheck v0.11.0](https://github.com/koalaman/shellcheck/releases/tag/v0.11.0):
  현재 local gate version의 upstream release
- [Homebrew shellcheck formula](https://formulae.brew.sh/formula/shellcheck): macOS 26
  Tahoe ARM64 local 설치·배포 metadata
- [Debian Policy: Maintainer Scripts](https://www.debian.org/doc/debian-policy/ch-maintainerscripts.html):
  중단·부분 실패 뒤 복구를 위한 멱등성과 exit status 원칙
- [Google SRE: The Evolution of Automation at Google](https://sre.google/sre-book/automation-at-google/):
  자동화의 일관성·효율과 함께 오류 증폭·유지비를 평가하는 관점
- [Google SRE Workbook: Eliminating Toil](https://sre.google/workbook/eliminating-toil/):
  반복 작업 자동화의 편익과 개발·운영 유지비를 함께 비교하는 관점
- [OWASP Secrets Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html):
  secret 저장·전달·logging·rotation의 최소 노출 원칙
- [SLSA Requirements](https://slsa.dev/spec/v1.2/requirements): source와 build artifact의
  provenance·무결성 검증 관점

## 관련 문서

- **도입·존치·제거 판단**: [도구 도입과 생애주기 워크플로우](130-tool-adoption-workflow.md)
- **공통 개발 절차**: [개발 작업 워크플로우](200-development-workflow.md)
- **주석 기준**: [코드와 설정 주석 작성 가이드](220-code-commenting-guideline.md)
- **test 구조 기준**: [테스트 작성 가이드](230-test-writing-guide.md)
- **문서 owner와 작성 절차**: [문서화 워크플로우](150-documentation-workflow.md)
- **Git 완료 경로**: [커밋 워크플로우](300-commit-workflow.md)
