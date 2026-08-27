# 코드 품질 도구

이 문서는 Java 포맷, 테스트·coverage, 아키텍처 검증과 SonarQube Cloud 결과를 판단하는 기술 기준을 정의합니다.
실행 순서와 완료 보고 형식은 `workflow`, 로컬 설치와 환경변수는 `development` 문서가 소유합니다.

## 현재 도구

| 도구 | 역할 | 현재 기준 |
| --- | --- | --- |
| Gradle Wrapper | 프로젝트 Gradle version 실행 | `gradle-wrapper.properties`의 distribution을 사용 |
| google-java-format | Java 포맷 엔진 | Spotless를 통해 실행 |
| Spotless | Java 포맷 검사와 적용 | `./gradlew check`에 포함 |
| JUnit Platform/Jupiter | 단위·통합 테스트 실행 | Gradle `test` task. exact resolved version은 build가 소유합니다. |
| Testcontainers | 실제 PostgreSQL 기반 통합 테스트 | test scope에서 사용 |
| JaCoCo | coverage 측정 | XML과 HTML report 생성 |
| Spring Modulith | 애플리케이션 module 검증 | 순환 의존과 공개 경계 검사 |
| ArchUnit | 계층·package 규칙 검증 | domain/application/adapter 의존 방향 검사 |
| SonarQube Cloud | 정적 분석과 품질 신호 | Gradle scanner가 현재 분석의 Quality Gate를 기다려 실패를 전파 |

정확한 plugin과 library version은 `build.gradle`과 Gradle Wrapper가 단일 출처입니다.

## Gradle Wrapper와 최종 검증

실제 Gradle version은 system Gradle이나 GitHub Action이 아니라 저장소의 Wrapper가 소유합니다.
로컬과 CI는 같은 `./gradlew` entrypoint를 사용합니다.

빠른 확인에는 특정 test나 필요한 Gradle task를 사용할 수 있습니다.
완료 보고, work item `Done` 전환과 구현 commit의 최종 Gradle 검증에는 CI와 같은 두 명령을 직접 사용합니다.

```sh
docker info
JAVA_HOME=<프로젝트 JDK 경로> ./gradlew check --no-daemon --rerun-tasks --console=plain
```

`docker info`는 Docker client 존재가 아니라 daemon 연결을 확인하고, Gradle check는 실제 PostgreSQL 16 Testcontainers 통합 테스트를 실행합니다.
공통 Testcontainers 기반은 Docker를 사용할 수 없을 때 테스트를 skip하지 않고 실패시키므로 DB 검증 미실행 상태를 전체 성공으로 간주하지 않습니다.
공통 지원 클래스의 계약 테스트는 fail-closed 설정과 고정 image를 검사하고, ArchUnit은 이 지원 클래스 밖의 직접 Testcontainers 사용을 차단합니다.
최종 보고에는 Docker preflight와 Gradle `check`의 실제 성공·실패를 각각 남깁니다.
별도 wrapper나 local hook은 두 명령의 결과를 대신하지 않습니다.

## Java 포맷

- Java source와 test는 google-java-format 결과를 기준으로 합니다.
- 프로젝트 기준은 IDE plugin이 아니라 Gradle Spotless 설정입니다.
- 포맷 위반은 `./gradlew spotlessApply`로 수정한 뒤 diff를 다시 확인합니다.
- 기능 변경과 무관한 대량 포맷은 같은 변경에 섞지 않습니다.

## Test와 coverage

`./gradlew check`는 다음 품질 신호를 한 entrypoint에서 실행합니다.

- JUnit test
- Spring Modulith와 ArchUnit test
- Spotless 검사
- JaCoCo XML·HTML report

JaCoCo XML 기본 경로는 `build/reports/jacoco/test/jacocoTestReport.xml`입니다.
SonarQube Cloud는 이 report를 coverage 입력으로 사용합니다.
report가 없거나 경로가 어긋나면 분석 대상 code의 coverage가 0%로 보일 수 있으므로 분석 설정부터 확인합니다.

## 아키텍처 검증

Spring Modulith 기준:

- 애플리케이션 root의 직접 하위 업무 package를 module로 봅니다.
- module 간 순환 의존을 허용하지 않습니다.
- 다른 module의 `internal` package를 직접 참조하지 않습니다.
- 공개가 필요한 하위 package는 named interface로 명시합니다.
- `allowedDependencies`가 있으면 선언한 module 또는 named interface만 참조합니다.

ArchUnit 기준:

- `domain`은 application, adapter, Spring, JPA와 Servlet API에 의존하지 않습니다.
- `application`은 Web, persistence 구현, JPA와 Servlet API에 의존하지 않습니다.
- persistence adapter type은 다른 계층으로 노출하지 않습니다.
- 다른 업무 module의 `internal` package 의존을 금지합니다.
- domain 공개 type과 UseCase·Port naming은 architecture 문서의 경계 규칙과 일치해야 합니다.

검증이 현재 확정 구조와 충돌하면 test를 임시 우회하지 않습니다.
코드가 잘못된 것인지 규칙이 오래된 것인지 먼저 분류하고, 규칙 변경이면 관련 architecture 문서를 함께 수정합니다.

## SonarQube Cloud 분석 계약

Gradle scanner는 다음 범위를 분석합니다.

- production source: `src/main/java`, `src/main/resources/application.yaml`
- test source: `src/test/java`
- coverage: JaCoCo XML report

문서, build 산출물, IDE 설정과 migration SQL은 현재 기본 분석 범위에서 제외합니다.
분석 범위를 넓히려면 LOC, noise와 품질 기준 영향을 별도 결정으로 검토합니다.

필요한 설정:

| 값 | 저장 위치 | 용도 |
| --- | --- | --- |
| `SONAR_TOKEN` | GitHub secret 또는 local shell 환경변수 | 분석 인증 |
| `SONAR_ORGANIZATION` | GitHub variable 또는 local shell 환경변수 | organization key |
| `SONAR_PROJECT_KEY` | GitHub variable 또는 local shell 환경변수 | project key |
| `SONAR_HOST_URL` | workflow 기본값 또는 local 환경변수 | 기본값 `https://sonarcloud.io` |

Gradle scanner를 사용하므로 SonarQube Cloud의 Automatic Analysis는 비활성화합니다.
token과 실제 설정값은 저장소, 문서와 작업 문서에 남기지 않습니다.

## GitHub repository 품질 게이트

SonarQube Cloud organization은 현재 `car-community` repository를 소유한 GitHub owner와
bind된 organization을 사용합니다. Owner가 organization인지 personal account인지는 live
화면에서 확인하며, 다른 owner에 같은 repository의 project를 중복 생성하지 않습니다.
Organization/GitHub App binding이나 유료 plan 선택이 필요하면 live 상태를 추측하지 않고
별도 사용자 결정을 먼저 받습니다.

Project 품질 계약은 다음과 같습니다.

| 항목 | 현재 선택 | 선택 이유와 재검토 경계 |
| --- | --- | --- |
| Analysis method | GitHub Actions의 Gradle scanner | Build와 같은 source·test·JaCoCo 결과를 분석하고 Quality Gate 실패를 같은 CI에 전파합니다. Scanner를 제거할 때만 Automatic Analysis를 재검토합니다. |
| New Code Definition | `Number of days: 30` | 현재 `build.gradle`의 version은 `0.0.1-SNAPSHOT`으로 고정돼 있고 release version increment를 분석 계약으로 운영하지 않습니다. Continuous delivery에는 일수 기준이 적합하며 30일은 SonarQube Cloud 기본값입니다. Release/version 정책을 실제 도입하거나 delivery 주기 측정상 30일이 맞지 않을 때 재검토합니다. |
| Quality Gate | Built-in `Sonar way` | New code 중심의 Sonar 기본 권장 기준을 사용하고 임의 custom gate를 만들지 않습니다. 별도 품질 목표와 metric 근거가 합의될 때만 변경합니다. |

현재 merge 전 Sonar 품질 게이트 계약은 SonarQube Cloud Team·Enterprise plan과 SOT를
전제로 합니다. Free plan에서는 이 계약을 완료했다고 판정하지 않고 plan 변경 또는 별도
축소 계약을 먼저 결정합니다.

GitHub의 민감 값 생산자와 소비자는 분리합니다.

| Credential | 생산자·범위 | 소비자와 lifecycle |
| --- | --- | --- |
| Team·Enterprise SOT | Sonar organization admin이 `car-community` project와 `Execute analysis`만 허용해 발급. 이름은 `car-community-github-actions-analysis-<YYYYMMDD>-<NN>`, 설명은 `car-community GitHub Actions CI analysis`를 사용합니다. 같은 날 sequence는 `01`부터 올립니다. | GitHub `SONAR_TOKEN` secret을 통해 CI scanner만 사용합니다. |

SOT는 user lifecycle에 종속되지 않고 project scope를 제한할 수 있습니다. 발급 시 expiry를
명시적으로 선택하며, 만료일이 없는 SOT도 60일 동안 사용되지 않으면 제거될 수 있습니다.
CI 분석에는 `Execute analysis`만 필요하며 private project Web API 조회에 필요한
`Browse Project`를 CI secret 권한에 섞지 않습니다. 실제 token 값은 GitHub secret만
소유합니다.

현재 branch governance 계약은 다음과 같습니다.

| 경계 | 현재 선택 | 이유·재검토 경계 |
| --- | --- | --- |
| Target | Literal `main` | CI trigger와 ECR OIDC trust가 `main`을 사용합니다. Default branch를 바꿀 때 workflow·OIDC trust와 배포 source를 함께 바꿉니다. |
| 현재 GitHub 강제 | Branch protection·required check 비활성 | Private repository의 현재 plan과 단일 운영에서는 GitHub merge gate를 최초 구축 조건으로 두지 않습니다. |
| 최초 GitOps bootstrap | Target dry-run 뒤 exact `main` 직접 push 허용 | Argo CD Application 연결 전이라 cluster 자동 sync가 없고, 이미 local `main`에 누적된 변경을 형식적인 remote branch로 우회하지 않습니다. |
| 반복 source·GitOps 변경 | PR review 뒤 merge하는 운영 정책 | Argo CD 연결 뒤 merge가 배포 승인 경계가 되므로 source와 `deploy/**` diff·CI를 확인합니다. 현재 GitHub가 이 정책을 강제한다고 주장하지 않습니다. |
| Force push | 사용하지 않음 | 현재 GitHub가 차단하지 않더라도 공유된 `main` history를 교체하지 않습니다. 실제 강제가 필요해지면 ruleset과 recovery 절차를 함께 도입합니다. |
| 재검토 | 운영자·reviewer 증가, 지원 plan 도입, 자동 digest PR | Ruleset target·required check·approval·bypass와 Git write identity를 하나의 branch policy로 설계합니다. |

향후 GitHub required check를 도입할 때에는 same-repository PR에서 성공 check를 먼저 만든
뒤 ruleset에 등록합니다. Fork·Dependabot PR은 credential을 받지 않아 Sonar step을
건너뛰므로 최초 Sonar 연결이나 merge 전 Sonar gate의 성공 증거가 아닙니다. 이 PR들에서도
`Application quality` job의 build·test는 실행되지만 Sonar pre-merge 분석은 보장하지 않으며,
merge 뒤 `main` workflow에서 Sonar를 실행합니다. 이 한계를 허용하지 않으려면 workflow와
untrusted PR credential 정책을 별도로 설계합니다. Private repository에서 Rulesets를
지원하지 않는 plan이면 branch protection으로 자동 대체하지 않습니다.

최초 설정과 완료 전 복구는
[GitHub CI 품질 게이트 최초 설정](../operations/310-github-ci-quality-configuration-runbook.md),
완료 뒤 token의 만료·노출·비활성 제거·정기 교체는
[Sonar token 교체](../operations/530-sonar-token-rotation-runbook.md)가 각각 소유합니다.

## Sonar 결과 판정

`build.gradle`은 공식 `sonar.qualitygate.wait=true`와 300초 timeout을 설정합니다.
따라서 `./gradlew sonar`는 분석을 업로드한 뒤 SonarQube Cloud가 그 report를 처리하고
Quality Gate를 계산할 때까지 기다리며, gate가 실패하거나 제한 시간 안에 결과를
받지 못하면 Gradle task와 CI를 실패시킵니다.

새 project의 최초 main analysis는 Quality Gate가 `Not Computed/NONE`일 수 있고,
`sonar.qualitygate.wait=true`는 분석이 생성됐어도 scanner와 CI를 실패시킬 수 있습니다.
이 상태는 credential 성공으로 추측하지 않고 exact main revision의 analysis 생성을 먼저
확인한 뒤 동일 main run을 한 번 더 실행합니다. 두 번째 analysis는 `Passed`와 CI 성공을
모두 충족해야 하며, 계속 `NONE`이면 설정·처리 실패로 중단합니다. 최초 baseline의 이
한정된 재실행 경계는
[GitHub CI 품질 게이트 최초 설정](../operations/310-github-ci-quality-configuration-runbook.md)이
소유합니다.

scanner에는 현재 SHA를 명시합니다.

```sh
revision="$(git rev-parse HEAD)"
./gradlew sonar \
  -Dsonar.scm.revision="${revision}" \
  --no-daemon \
  --console=plain
```

판정 기준:

- CI scanner가 제출한 exact revision의 기본 `Sonar way` Quality Gate가 `Passed`여야 합니다.
- credential이 없는 fork·Dependabot pull request는 Sonar만 건너뛰고, same-repository
  pull request와 `main`은 설정 누락이나 Gate 실패를 CI 실패로 전파합니다.
- 코드 작업을 push한 뒤에는 해당 40자리 SHA의 required CI가 성공한 다음
  `scripts/quality/verify-sonar-status.sh --expected-revision <SHA>`를 실행합니다.
- 완료 점검은 조회 전후 최신 analysis가 같고 revision이 입력 SHA와 정확히 일치하며,
  Quality Gate가 `OK`이고 overall `open_issues`가 `0`일 때만 통과합니다.
- coverage, duplication과 Security Hotspot review 수치는 참고로 출력하지만 별도
  임계값을 구현하지 않습니다. 이 기준은 Quality Gate가 소유합니다.

SonarQube Cloud의 `open_issues`는 현재 Open 상태인 issue 수이며 Accepted·False Positive
상태와 구분됩니다. 따라서 완료 점검은 상태를 변경하지 않고 공식 metric을 그대로
읽습니다. 오탐이나 의도된 코드는 통과만을 위해 자동으로 Accepted·False Positive
처리하지 않으며 아래 issue 처리 기준에 따라 근거를 확인합니다.

조회 도구는 CI나 Git hook에 연결하지 않고 작업자의 push 후 완료 절차에서만 실행합니다.
분석 처리 대기와 Gate 실패 전파는 Gradle scanner가, issue·coverage·보안 기준은 Sonar가,
exact SHA와 Open issue 0건의 최종 확인은 읽기 전용 완료 점검이 소유합니다. API·인증·응답
오류, SHA 불일치, 조회 중 latest analysis 변경과 필수 metric 누락은 성공이 아니라
`NOT VERIFIED`입니다. 실행법과 exit 의미는
[품질 완료 점검 스크립트](../../scripts/quality/README.md)가 소유합니다.

## Sonar issue 처리

- 보안 취약점, Bug 가능성, 데이터 무결성 문제는 우선 해결합니다.
- Code Smell, 복잡도와 중복은 현재 범위와 유지보수성 이득을 기준으로 판단합니다.
- 오탐이나 의도된 구조면 rule id, 코드 근거와 재검토 조건을 남깁니다.
- `@SuppressWarnings`와 `NOSONAR`는 마지막 수단이며 rule, 한국어 예외 사유와 제거 조건을 코드 가까이에 기록하고 review에서 실제 필요성을 확인합니다.
- issue 수정 범위가 커지면 현재 기능에 숨겨 넣지 않고 별도 품질 작업으로 분리합니다.

Security Hotspot은 일반 issue와 별도 review 대상입니다.
`Safe` 또는 `Fixed` 상태로 바꾸려면 실제 인증 방식, threat model, 코드와 test 근거가 필요합니다.
AI 추천만으로 Sonar review 상태를 변경하지 않습니다.

## CI 기술 기준

Workflow trigger, step 순서와 image·GitOps handoff의 전체 설명은
[200 CI/CD pipeline](../project/infrastructure/200-ci-cd-pipeline.md)이 소유합니다. 이 절은
품질 도구의 CI 적용 기준만 소유합니다.

- `push`와 `pull_request`에서 재사용 없는 `./gradlew check`를 실행합니다.
- `Application quality`의 같은 runner에서 조건 없는 `docker info` 뒤 재사용 없는 Gradle check를 실행하고 어느 단계도 실패를 허용하지 않습니다.
- checkout, Java와 Gradle 준비 Action은 신뢰 가능한 소유자의 full commit SHA로 고정합니다.
- workflow token은 job이 필요한 최소 권한만 가집니다.
- Sonar 분석을 수행한 실행은 현재 Git SHA를 analysis revision으로 전달하고, Gradle scanner의 공식 대기로 해당 분석의 Quality Gate 실패를 같은 step에 전파합니다.
- push한 코드 작업은 exact SHA의 required CI 성공과 Sonar 완료 점검 통과 전에는 완료로 보고하지 않습니다.
- branch protection의 required check 이름은 실제 workflow job과 일치해야 합니다.

## 관련 문서

- **로컬 실행 절차**: [로컬 개발 환경](../development/100-local-development.md)
- **개발·완료 절차**: [개발 워크플로우](../workflow/200-development-workflow.md), [완료 점검 게이트](../workflow/100-work-execution-workflow.md)
- **구조 판정 기준**: [Spring Boot 모듈 구조](../architecture/210-spring-boot-module-structure.md)
- **test 기준**: [테스트 스택](300-testing-stack.md)
- **CI 구현 source**: [GitHub Actions CI](../../.github/workflows/ci.yml)
- **CI/CD 전체 흐름과 step**: [200 CI/CD pipeline](../project/infrastructure/200-ci-cd-pipeline.md)
- **GitHub·Sonar 최초 설정**: [GitHub CI 품질 게이트 최초 설정](../operations/310-github-ci-quality-configuration-runbook.md)
- **Sonar token 변경·복구**: [Sonar token 교체](../operations/530-sonar-token-rotation-runbook.md)

## 공식 참고

- [Gradle User Manual](https://docs.gradle.org/current/userguide/userguide.html)
- [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html)
- [Gradle Build Scan](https://scans.gradle.com/)
- [SonarScanner for Gradle](https://docs.sonarsource.com/sonarqube-cloud/advanced-setup/ci-based-analysis/sonarscanner-for-gradle/)
- [Quality Gate 분석 parameter](https://docs.sonarsource.com/sonarqube-cloud/analyzing-source-code/analysis-parameters/parameters-not-settable-in-ui#quality-gate)
- [SonarQube Cloud Quality Gate](https://docs.sonarsource.com/sonarqube-cloud/standards/managing-quality-gates/introduction-to-quality-gates)
- [SonarQube Cloud Web API](https://docs.sonarsource.com/sonarqube-cloud/advanced-setup/web-api)
- [SonarQube Cloud metric 정의](https://docs.sonarsource.com/sonarqube-cloud/managing-your-projects/metric-definitions)
- [SonarQube Cloud GitHub onboarding](https://docs.sonarsource.com/sonarqube-cloud/getting-started/github)
- [SonarQube Cloud New Code Definition](https://docs.sonarsource.com/sonarqube-cloud/standards/about-new-code)
- [SonarQube Cloud Pull Request Analysis](https://docs.sonarsource.com/sonarqube-cloud/improving/pull-request-analysis)
- [SonarQube Cloud Scoped Organization Tokens](https://docs.sonarsource.com/sonarqube-cloud/administering-sonarcloud/scoped-organization-tokens)
- [GitHub repository ruleset](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/creating-rulesets-for-a-repository)
