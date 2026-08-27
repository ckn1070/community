# Sonar token 교체 runbook

최초 GitHub CI 품질 게이트 설정이 완료된 뒤 SonarQube Cloud 분석 SOT의 만료 예정,
60일 비활성 제거, 노출 또는 정기 교체에 대응하는 절차입니다. 최초
project·secret·ruleset 구성은
[GitHub CI 품질 게이트 최초 설정](310-github-ci-quality-configuration-runbook.md)이
소유합니다.

## 실행 계약

| 항목 | 계약 |
| --- | --- |
| 언제 | Sonar SOT의 만료 예정·정기 교체·노출·무효화에 대응할 때 사용합니다. 최초 project 설정에는 사용하지 않습니다. |
| 누가 | Sonar scoped token과 GitHub Actions secret을 관리하고 CI를 재실행할 권한이 있는 운영자가 실행합니다. |
| 어디서 | SonarQube Cloud organization Console과 GitHub `ckn1070/car-community → Settings/Actions`입니다. |
| 무엇을 | 새 scoped token 발급, `SONAR_TOKEN` 교체, latest `main` CI 재실행, 이전 token revoke를 수동 수행합니다. Repository script는 없습니다. |
| 왜 | 새 token이 exact revision 분석에 실제 사용됐다는 증거를 확보한 뒤 이전 credential을 폐기하기 위해서입니다. |
| 완료·재개 | `Application quality`와 같은 revision의 `Sonar way` 통과 뒤 이전 token 상태를 확인합니다. 실패하면 계획 교체 또는 사고 복구의 해당 번호부터 재개합니다. |

## 시작 전 확인

- GitHub의 `SONAR_ORGANIZATION`, `SONAR_PROJECT_KEY`와 Sonar project binding은 그대로
  유지하며 token만 교체하는 작업인지 확인합니다.
- GitHub repository secret과 Sonar token을 관리할 권한이 있습니다.
- 30일 이내의 `main` CI run을 **Re-run all jobs**할 권한이 있습니다.
- 현재 SOT 이름·expiry와 교체 사유가 확인됩니다.

Organization·project binding, New Code Definition, Quality Gate 또는 ruleset까지 바꾸려는
작업은 token rotation이 아니므로 여기서 중단하고 별도 변경 범위를 결정합니다.

## 계획 교체

**실행 위치:** `2`·`7`은 SonarQube Cloud Console, `3`은 조직이 승인한 credential
inventory, `4`·`5`는 GitHub Web UI·Actions, `6`은 GitHub Actions와 SonarQube Cloud Console

만료 예정·정기 교체처럼 기존 token이 노출되지 않은 경우에 사용합니다.

1. 기존 token은 새 token 검증이 끝날 때까지 revoke하지 않습니다.
2. Sonar organization **Administration → Scoped Organization Tokens**에서 새 SOT를
   발급합니다.
   - Name: `car-community-github-actions-analysis-<YYYYMMDD>-<NN>`에서 실제 발급일과 같은 날 `01`부터 시작하는 sequence
   - Description: `car-community GitHub Actions CI analysis`
   - Expires in: 운영 담당자가 관리할 expiry
   - Project: Custom selection의 `car-community` 하나
   - Permission: `Execute analysis`만
3. 새 SOT의 expiry와 담당자를 운영 기록에 남기되 token 값은 남기지 않습니다.
4. GitHub repository **Settings → Secrets and variables → Actions → Secrets**에서
   `SONAR_TOKEN` 값을 새 token으로 교체합니다.
5. GitHub **Actions → CI**의 30일 이내 최신 `main` run에서 **Re-run all jobs**를
   실행합니다. Re-run은 같은 `GITHUB_SHA`·`GITHUB_REF`를 유지하고 repository secret은
   queue 시점에 다시 읽습니다.
6. GitHub check가 성공하고 Sonar main analysis revision이 해당 workflow run의
   `GITHUB_SHA`와 정확히 같으며 `Sonar way` Quality Gate가 Passed인지 확인합니다.
7. 새 token 사용 증거를 확인한 뒤에만 이전 token을 SonarQube Cloud에서 revoke합니다.

30일 이내 main run이 없거나 re-run할 수 없으면 direct push나 빈 commit으로 우회하지
않습니다. Manual trigger 도입 또는 안전한 실제 변경 방식을 별도 결정하며, 그동안 기존
token을 revoke하지 않습니다.

## 노출 사고·이미 무효인 token 복구

**실행 위치:** SonarQube Cloud Console과 GitHub Web UI·Actions

노출된 token은 검증을 기다리지 않고 즉시 revoke합니다. 이미 만료·60일 비활성 제거·
revoke된 token도 존재하지 않는 이전 token의 유지를 전제로 하지 않습니다.

1. 노출되었거나 무효인 기존 SOT를 즉시 revoke하고 GitHub incident 범위에서 log·artifact
   노출 여부를 확인합니다.
2. 계획 교체 2~6단계와 같은 이름 pattern·설명·project scope·permission으로 새 SOT를
   발급하고 GitHub secret을 교체한 뒤 latest `main` run을 재실행합니다.
3. Exact revision과 `Sonar way` Quality Gate가 성공하면 복구가 완료됩니다.

안전한 재실행 trigger가 없으면 기존 token은 revoked 상태로 유지하고 CI는 실패하도록
둡니다. 노출 token을 다시 활성화하거나 다음 실제 PR까지 유효하게 남겨두지 않습니다.

## 실패·복구

**실행 위치:** Token 발급·상태·revoke와 Quality Gate 확인은 SonarQube Cloud Console,
repository secret 교체와 workflow 재실행은 GitHub Web UI·Actions에서 수행합니다.

| 실패 | 처리 |
| --- | --- |
| 새 token을 GitHub에 저장하기 전에 유실 | 해당 token을 revoke하고 새 token을 다시 발급합니다. |
| `Application quality` 인증 실패 | Project scope, `Execute analysis`, expiry와 GitHub secret 이름을 확인하고 새 token 또는 권한을 바로잡은 뒤 latest `main` run을 다시 실행합니다. |
| 새 token 검증 전 기존 token을 실수로 revoke | 노출 사고·무효 token 복구 절차로 전환합니다. 이전 token을 되살릴 수 있다고 가정하지 않습니다. |
| GitHub check는 성공했지만 Sonar revision이 다름 | 이전 결과를 사용하지 않고 새 workflow 실행의 exact revision이 보일 때까지 5~6단계를 반복합니다. |
| 30일 이내 `main` run이 없음 | Direct push하지 말고 manual trigger 또는 안전한 실제 변경을 별도 결정합니다. 노출 사고면 old token은 revoked 상태를 유지합니다. |

## 완료 확인

- GitHub `SONAR_TOKEN` secret은 새 token을 사용합니다.
- 재실행한 `main`의 `Application quality`와 같은 exact revision의 `Sonar way` Quality Gate가
  성공했습니다.
- 기존 token은 새 token 검증 뒤 revoke됐거나, 이미 제거돼 존재하지 않습니다.
- Secret 값은 Git, 문서, log와 작업 보고에 노출되지 않았습니다.

## 관련 문서

- **Token·품질 기준**: [코드 품질 도구](../tech-stack/310-code-quality-tools.md)
- **최초 설정**: [GitHub CI 품질 게이트 최초 설정](310-github-ci-quality-configuration-runbook.md)
- **CI 구현 source**: [GitHub Actions CI](../../.github/workflows/ci.yml)

## 공식 참고

- [SonarQube Cloud Scoped Organization Tokens](https://docs.sonarsource.com/sonarqube-cloud/administering-sonarcloud/scoped-organization-tokens)
- [GitHub Actions secret](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)
- [GitHub Actions workflow 재실행](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/re-run-workflows-and-jobs)
- [GitHub Actions secret 읽기 시점](https://docs.github.com/en/enterprise-cloud@latest/actions/reference/security/secrets#when-github-actions-reads-secrets)
