# GitHub CI 품질 게이트 최초 설정 runbook

현재 source repository를 SonarQube Cloud project와 연결하고 GitHub Actions의
`Application quality`로 `main` exact revision을 처음 검증하는 절차입니다. 품질 기준과
branch governance 선택 이유는 [코드 품질 도구](../tech-stack/310-code-quality-tools.md)가
소유합니다. 이 문서는 GitHub·SonarQube Cloud 화면에서 수행할 입력, 순서, 성공과 최초
완료 전 복구만 소유합니다.

이 절차는 GitHub나 SonarQube Cloud 설정을 자동 변경하지 않습니다. 실제 설정값과 token을
문서, Git, issue, PR, shell history와 작업 보고에 남기지 않습니다.

## 실행 계약

| 항목 | 계약 |
| --- | --- |
| 언제 | Repository에 Sonar 기반 `Application quality` 검증이 아직 없을 때 최초 한 번 실행합니다. 완료 뒤 token 교체에는 `530`을 사용합니다. |
| 누가 | GitHub repository 설정과 SonarQube Cloud organization·project·token을 관리할 권한이 있는 운영자가 실행합니다. |
| 어디서 | SonarQube Cloud Console과 GitHub `example-org/car-community`의 Settings·Actions 화면입니다. |
| 선택 hardening 위치 | GitHub `example-org/car-community`의 Rulesets 화면입니다. |
| 무엇을 | Sonar project·scoped token·GitHub secret/variable과 `main` baseline을 아래 순서로 수동 설정합니다. Repository script는 없습니다. |
| 왜 | 실제 게시·배포 입력인 `main` exact revision의 품질 결과를 신뢰할 수 있어야 하기 때문입니다. |
| 완료·재개 | `main`의 `Application quality`, exact Sonar revision·Quality Gate를 확인합니다. 실패한 필수 번호 단계부터 재개합니다. |

## 완료 결과

- 현재 GitHub owner와 연결된 SonarQube Cloud organization에 이 repository의
  project가 하나만 존재합니다.
- Project의 New Code Definition은 `Number of days: 30`, Quality Gate는 built-in
  `Sonar way`, 분석 방식은 GitHub Actions CI입니다.
- GitHub repository에는 `SONAR_TOKEN` secret과 `SONAR_ORGANIZATION`,
  `SONAR_PROJECT_KEY` variable이 있습니다.
- `main`에서 `Application quality`와 제출한 exact revision의 Quality Gate가 성공합니다.
- 현재 private repository의 `main` branch protection과 required check 강제는 비활성입니다.
  단일 운영자의 최초 bootstrap은 이 상태를 허용합니다.
- Argo CD 연결 이후 application source와 `deploy/**` 변경은 PR을 운영 승인 경계로
  사용하지만, 현재 GitHub plan이 merge를 강제한다고 주장하지 않습니다.

## 입력과 시작 전 확인

| 입력·권한 | 확인할 내용 |
| --- | --- |
| Source repository | 현재 repository가 `car-community`이고 default branch가 literal `main`인지 확인합니다. `.github/workflows/ci.yml`과 ECR OIDC trust가 `main`을 사용하므로 다르면 중단합니다. |
| GitHub 권한 | Repository Actions secret·variable을 관리할 수 있어야 합니다. Optional ruleset hardening을 수행할 때만 Rulesets 지원 plan과 관리 권한을 추가로 확인합니다. |
| Sonar organization | `car-community`를 소유한 GitHub owner를 SonarQube Cloud에 import·bind한 organization과 key가 필요합니다. Owner가 organization인지 personal account인지 live 화면에서 확인합니다. |
| Sonar 권한 | Project import·quality setting을 확인할 organization/project 관리자와 token을 발급할 권한이 필요합니다. |
| Workflow source | Job 표시 이름이 exact `Application quality`이고 same-repository PR과 `main`에서 Sonar step을 실행하는지 `.github/workflows/ci.yml`에서 확인합니다. |

SonarQube Cloud organization이나 GitHub App binding이 아직 없다면 다른 owner를 임의로
사용하지 않습니다. Repository owner가 공식 onboarding으로 binding과 현재 plan을 먼저
확인합니다. Merge 전 PR Sonar analysis가 필요하면 Team·Enterprise plan을 전제로 하므로,
Free에서는 해당 optional gate를 완료로 주장하지 않습니다. 유료 plan 선택·구매는 이
runbook의 권한이 아닙니다.

Private repository에서 **Settings → Rules → Rulesets**를 사용할 수 없어도 `main` CI·Sonar
baseline 설정은 진행할 수 있습니다. Branch protection으로 임의 대체하지 않으며,
ruleset 강제가 실제로 필요해지는 시점에 plan 변경 또는 별도 보호 방식을 결정합니다.

## 1. SonarQube Cloud project 구성

**실행 위치:** SonarQube Cloud Console

1. SonarQube Cloud에서 현재 GitHub owner와 연결된 organization을 선택합니다.
2. **Analyze new project**에서 현재 `car-community` repository를 import합니다. 이미 같은
   repository에 연결된 project가 있으면 새 project를 만들지 않고 organization key와
   project key가 현재 binding과 일치하는지 확인합니다.
3. New Code Definition은 **Number of days**, 값은 **30**을 선택합니다.
4. Project의 Quality Gate가 built-in **Sonar way**인지 확인합니다. 이 최초 설정에서 custom
   Quality Gate를 만들지 않습니다.
5. Project **Administration → Analysis Method**에서 **Automatic Analysis**를 끄고 CI-based
   analysis를 사용합니다. Automatic Analysis와 Gradle scanner를 동시에 사용하지
   않습니다.

Organization binding, project key, New Code Definition, Quality Gate 가운데 하나라도
확정하지 못하면 GitHub 값을 먼저 넣지 않습니다.

## 2. 분석 token 발급

**실행 위치:** SonarQube Cloud Console

SonarQube Cloud plan이 Team·Enterprise인지 실제 화면에서 확인한 뒤 organization
**Administration → Scoped Organization Tokens → Create token**에서 다음 값을 사용합니다.

| 항목 | 입력 |
| --- | --- |
| Name | `car-community-github-actions-analysis-<YYYYMMDD>-<NN>`. 실제 발급일과 같은 날 `01`부터 시작하는 sequence를 사용 |
| Description | `car-community GitHub Actions CI analysis` |
| Expires in | 운영 담당자가 관리할 수 있는 expiry를 명시적으로 선택. No expiration을 선택했다면 60일 비활성 제거 경계를 운영 기록에 남김 |
| Projects this token can access | Custom selection에서 `car-community` 하나만 선택 |
| Permission | `Execute analysis`만 사용 |

발급 화면에서 token 값을 한 번만 복사해 곧바로 다음 단계의 GitHub secret에 저장합니다.
CI scanner에는 `Execute Analysis`만 필요합니다. Private project Web API 조회에 쓰이는
`Browse Project`를 CI secret에 추가하지 않습니다.

## 3. GitHub secret과 variable 저장

**실행 위치:** GitHub Web UI의 `example-org/car-community` repository

GitHub repository **Settings → Secrets and variables → Actions**에서 다음을 저장합니다.

| 종류 | 이름 | 값의 source |
| --- | --- | --- |
| Repository secret | `SONAR_TOKEN` | 직전 단계에서 발급한 token |
| Repository variable | `SONAR_ORGANIZATION` | 현재 repository와 bind된 Sonar organization key |
| Repository variable | `SONAR_PROJECT_KEY` | Import한 `car-community` Sonar project key |

Token을 variable로 저장하지 않습니다. Variable은 민감하지 않은 identifier만 소유합니다.
저장 뒤 GitHub 화면에서 이름의 존재만 확인하고 값을 출력하거나 복사해 검증하지 않습니다.

## 4. CI 기반 `main` baseline 생성

**실행 위치:** GitHub Actions와 SonarQube Cloud Console

PR analysis를 나중에 사용하더라도 최신 target branch 분석이 필요하므로 먼저 `main`
baseline을 만듭니다.

1. GitHub **Actions → CI**에서 30일 이내의 가장 최근 `main` workflow run을 엽니다.
2. **Re-run all jobs**를 선택합니다. Re-run은 원래 run의 `GITHUB_SHA`와 `GITHUB_REF`를
   유지하고 repository secret은 새 run이 queue될 때 읽으므로 방금 설정한 값을 사용합니다.
   각 재실행은 새 `run_attempt`를 사용하며 image 게시 분류 artifact도 SHA·attempt로 이름을
   나누고 30일 보존하므로 이전 attempt와 충돌하지 않습니다.
3. SonarQube Cloud의 main analysis revision이 그 run의 40자리 `GITHUB_SHA`와 같은지
   확인합니다.
4. 첫 main analysis는 Quality Gate가 `Not Computed/NONE`일 수 있습니다.
   `sonar.qualitygate.wait=true`는 이 상태를 scanner·`Application quality` 실패로
   전파할 수 있습니다. Exact analysis가 생성됐다면 일반 credential 실패로 오판하지 않고
   같은 GitHub run에서 **Re-run all jobs**를 한 번 더 실행합니다.
5. 두 번째 실행의 `Application quality`가 성공하고 같은 exact revision의 `Sonar way`
   Quality Gate가 `Passed`인지 확인하면 현재 필수 CI 설정은 완료입니다. 첫 실행에서 이미
   두 조건이 모두 충족됐다면 추가 재실행은 필요하지 않습니다.

두 번째 실행도 `Not Computed/NONE`이거나 실패하면 정상 최초 분석으로 간주하지 않고
New Code Definition, Quality Gate와 analysis 처리 상태를 확인한 뒤 중단합니다.

30일 이내 main run이 없거나 re-run 권한이 없으면 CI 실행만 만들기 위한 빈 commit을
push하지 않습니다. 실제로 검증된 bootstrap 변경을 `main`에 반영해 새 run을 만들거나,
별도 manual trigger 도입을 결정할 때까지 이 baseline 확인을 보류합니다.

## 5. Optional: same-repository PR check 확인

**실행 위치:** GitHub Web UI·Actions와 SonarQube Cloud Console

반복 변경을 PR로 운영하기 전에 same-repository PR의 품질 결과도 확인하려면 이 절을
실행합니다. 현재 최초 bootstrap의 필수 완료 조건은 아니며, GitHub required check를
나중에 고르려면 해당 repository에서 최근 성공한 check와 source가 먼저 필요합니다.

1. Same-repository branch에서 실제 필요한 변경으로 pull request를 만듭니다. Fork나
   Dependabot pull request는 credential이 없어 Sonar step을 건너뛰므로 증거로 사용하지
   않습니다. 빈 commit만을 만들지 않습니다.
2. PR의 **Checks**에서 exact `Application quality` job이 성공했는지 확인합니다.
3. SonarQube Cloud project에서 그 실행이 제출한 40자리 revision이 해당 workflow run의
   `GITHUB_SHA`와 같고 `Sonar way` Quality Gate가 Passed인지 확인합니다. `pull_request`
   event의 `GITHUB_SHA`는 PR head 자체가 아니라 GitHub가 만든 test merge commit일 수
   있으므로 Actions run의 실제 SHA와 대조합니다.

Check가 실패하면 PR을 merge하거나 optional ruleset으로 넘어가지 않습니다. Missing secret·variable이면 2~3단계를,
target baseline이 없으면 4단계를, project·revision·Quality Gate가 다르면 1단계를 수정하고 같은 PR의 새 실행에서 다시
확인합니다.

## 6. Optional: `main` ruleset hardening

**실행 위치:** GitHub Web UI의 `example-org/car-community → Settings → Rules → Rulesets`

독립 reviewer·GitHub 강제나 지원 plan이 실제로 필요해져 별도 결정한 경우에만 GitHub
repository **Settings → Rules → Rulesets → New branch ruleset**에서 다음 계약을
설정합니다. 현재 single-operator private repository의 최초 bootstrap에는 적용하지
않습니다.

| 항목 | 설정 |
| --- | --- |
| Ruleset name | `main-application-quality` |
| Enforcement status | `Active` |
| Target branch | Include default branch가 아니라 literal branch name `main` |
| Bypass list | 비움 |
| Restrict deletions | 활성화 |
| Require a pull request before merging | 활성화 |
| Required approvals | `0` |
| Require status checks to pass | 활성화 |
| Required check | Exact `Application quality`, expected source `GitHub Actions` |
| Require branches to be up to date before merging | 활성화 |
| Block force pushes | 활성화 |

`Required approvals: 0`은 review 승인을 우회한다는 뜻이 아니라 현재 독립 reviewer 정책이
아직 없어서 PR 이력과 required CI만 강제한다는 뜻입니다. 다른 signing, linear history,
merge queue rule을 이 절차에서 임의로 켜지 않습니다.

저장 뒤 ruleset 상태가 `Active`, target이 literal `main`, required check 이름과 source가
정확한지 다시 엽니다. 잘못된 target으로 저장했다면 PR을 merge하지 말고 ruleset을 먼저
수정합니다.

## 7. Optional: 보호된 merge와 `main` 검증

**실행 위치:** GitHub Web UI·Actions와 SonarQube Cloud Console

1. Optional verification PR에서 ruleset의 required check가 충족됐는지 확인하고 정상
   merge합니다.
2. `main` push로 시작한 `CI` workflow의 `Application quality`가 성공했는지 확인합니다.
3. SonarQube Cloud의 latest analysis revision이 merge 결과의 40자리 `main` SHA와 정확히
   같고 `Sonar way` Quality Gate가 Passed인지 확인합니다.
4. Ruleset을 활성화한 뒤에는 direct push나 force push로 이 gate를 우회하지 않습니다.

## 실패·재개

**실행 위치:** Token·project·Quality Gate 확인과 수정은 SonarQube Cloud Console,
repository secret·ruleset·workflow·run 재실행은 GitHub Web UI·Actions에서 수행합니다.

| 실패 | 재개 지점 |
| --- | --- |
| Owner binding이 없음 | 다른 owner를 쓰지 말고 binding을 완료한 뒤 1단계부터 재개합니다. |
| Plan이 PR analysis·Rulesets를 지원하지 않음 | 필수 `main` baseline은 유지하고 optional 5~7단계를 완료로 주장하지 않습니다. 강제가 필요해질 때 plan 또는 보호 방식을 별도 결정합니다. |
| Token 발급 뒤 GitHub 저장 전 유실 | 기존 token 값을 추측하지 말고 새 token을 발급해 2단계부터 재개합니다. |
| 최초 완료 전 token 오류·노출 | 노출 token을 즉시 revoke하고 새 SOT로 secret을 교체한 뒤 4단계 main baseline부터 다시 증명합니다. |
| Main baseline을 만들 실행이 없음 | 빈 commit을 만들지 말고 검증된 실제 bootstrap 변경의 `main` 반영 또는 manual trigger 도입을 결정할 때까지 보류합니다. |
| 첫 main analysis가 exact하지만 `Not Computed/NONE`으로 CI 실패 | 같은 main run을 한 번 더 전체 재실행합니다. 두 번째에도 Gate가 계산되지 않으면 중단합니다. |
| `Application quality`가 check 목록에 없음 | Optional ruleset을 구성 중이라면 same-repository PR인지, job이 실제 실행됐는지 확인하고 성공 check를 만든 뒤 6단계를 재개합니다. |
| Ruleset이 잘못 활성화됨 | Merge하지 말고 target·check source·rule을 수정한 뒤 6단계 확인을 반복합니다. |
| PR은 성공했지만 `main` revision이 다름 | 이전 분석을 성공으로 쓰지 않고 `main` workflow와 Sonar 처리 상태를 확인해 7단계를 반복합니다. |

최초 완료 뒤 token의 만료·비활성 제거·노출·정기 교체는 이 문서를 재실행하지 않고
[Sonar token 교체](530-sonar-token-rotation-runbook.md)를 사용합니다.

## 관련 문서

- **품질 기준과 선택 이유**: [코드 품질 도구](../tech-stack/310-code-quality-tools.md)
- **CI 구현 source**: [GitHub Actions CI](../../.github/workflows/ci.yml)
- **Local Sonar 재현·조회**: [로컬 개발 환경](../development/100-local-development.md)
- **다음 최초 설정**: [GitHub ECR 게시 설정](320-github-ecr-publishing-configuration-runbook.md)

## 공식 참고

- [SonarQube Cloud GitHub onboarding](https://docs.sonarsource.com/sonarqube-cloud/getting-started/github)
- [SonarQube Cloud New Code Definition](https://docs.sonarsource.com/sonarqube-cloud/standards/about-new-code)
- [SonarQube Cloud Quality Gates](https://docs.sonarsource.com/sonarqube-cloud/standards/quality-gates)
- [SonarQube Cloud Automatic Analysis](https://docs.sonarsource.com/sonarqube-cloud/advanced-setup/automatic-analysis)
- [SonarQube Cloud Scoped Organization Tokens](https://docs.sonarsource.com/sonarqube-cloud/administering-sonarcloud/scoped-organization-tokens)
- [SonarQube Cloud Pull Request Analysis](https://docs.sonarsource.com/sonarqube-cloud/improving/pull-request-analysis)
- [GitHub Actions workflow 재실행](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/re-run-workflows-and-jobs)
- [GitHub Actions secret 읽기 시점](https://docs.github.com/en/enterprise-cloud@latest/actions/reference/security/secrets#when-github-actions-reads-secrets)
- [GitHub repository ruleset 생성](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/creating-rulesets-for-a-repository)
- [GitHub ruleset rule](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/available-rules-for-rulesets)
- [GitHub Actions secret](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)
- [GitHub Actions variable](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-variables)
