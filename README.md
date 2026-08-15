# Carmunity API

Carmunity는 자동차 정보와 커뮤니티 기능을 목표로 하는 Spring Boot 기반 백엔드 프로젝트입니다.
현재 저장소에는 account 인증 주체, 비밀번호 로그인, JWT access token과 opaque refresh token,
이메일 인증, 비밀번호 변경·재설정, 관리자 bootstrap·초대 기능이 구현되어 있습니다.

## 공개 범위

이 저장소는 원본 저장소의 현재 구현과 문서를 공개 용도로 옮긴 사본입니다. 실제 운영 환경의
credential, endpoint, account·application 식별자와 자동 image 게시 권한은 예시값 또는
비활성 설정으로 대체했습니다. 코드, 테스트, 문서, 작업 기록과 AI 작업 규칙은 가능한 범위에서
동일하게 유지합니다.

## 현재 구성

- Java 21, Spring Boot 4
- Spring Security Resource Server
- Spring Data JPA, Flyway, PostgreSQL
- Spring Modulith와 ArchUnit 모듈 경계 검증
- springdoc-openapi, Resend 이메일 발송
- Gradle, Spotless, JaCoCo, SonarQube Cloud

완료된 application 기준선은 [현재 기술 스택](docs/tech-stack/100-current-stack.md), 정확한
의존성과 version은 `build.gradle`에서 확인합니다. 저장소에는 Terraform은 없지만 application
Kubernetes manifest와 Argo CD GitOps bootstrap 자산은 있습니다. AWS 환경은 수동
절차로 구축하며,
Ubuntu host부터 단일 node Kubernetes cluster network까지 재현하도록 순차 bootstrap
script를 구현했습니다. 성공한 main CI revision 중 `deploy/**` 전용 변경을 제외해 private
ECR ARM64 image로 게시하고
kubelet이 EC2 role로 exact digest를 pull하는 공급 workflow·script도 구현했습니다. 실제
AWS resource, GitHub publish, EC2 실행·postcondition 완료는 local 정적·contract 검증과
별도입니다. Argo CD Core 설치와 현재 private repository의 `deploy/overlays/stg` 연결,
runtime·migrator DB identity를 분리한 단일 PreSync Migration Job과 Deployment·Service manifest를
구현했습니다. 실제 RDS·Kubernetes prerequisite 적용, GitHub App 연결과 최초 sync는 아직
완료되지 않았습니다.

## 주요 기능

- 일반 회원 가입과 이메일 인증
- 비밀번호 로그인, refresh token rotation과 reuse detection, 로그아웃
- 비밀번호 변경과 비밀번호 재설정
- 최초 최고 관리자 bootstrap
- 관리자 초대 생성, 수락, 회수
- 인증·인가 감사 이벤트
- RFC 9457 기반 API 오류 응답과 request id logging

API 경로와 인가 조건은 [Account 인증/인가 정책](docs/domain/110-account-auth-policy.md)을
확인합니다.

## 로컬 실행

JDK 21과 PostgreSQL이 필요하며, 전체 Gradle 검증에는 PostgreSQL Testcontainers를 실행할
Docker daemon이 필요합니다. 루트 `.env.example`을 기준으로 로컬 `.env`에 실제 값을
채우되 비밀값과 운영값은 저장소에 기록하지 않습니다.

```sh
./gradlew bootRun
```

기본 HTTP port는 `9097`입니다. 환경변수와 검증 절차는
[로컬 개발 환경](docs/development/100-local-development.md)을 확인합니다.

## 검증과 CI

```sh
docker info
./gradlew check --no-daemon --rerun-tasks --console=plain
```

GitHub Actions의 `CI` workflow는 pull request와 `main` push에서 같은 application 품질
경계를 검증하고, SonarQube Cloud 분석은 repository variable로 명시적으로 활성화한 경우에만
실행합니다. push 후
exact SHA의 최종 Sonar 판정은 [품질 완료 점검](scripts/quality/README.md)을 사용합니다.
별도 `Publish image` workflow는 repository variable로 명시적으로 활성화한 경우에만 성공한
`main` CI SHA 중 `deploy/**` 전용 변경을 제외하고 GitHub OIDC로 private ECR에 게시합니다. 구성 원리는
[Container image delivery](docs/project/infrastructure/200-container-image-delivery.md),
반복 게시 절차는
[GitHub ECR image publishing runbook](docs/operations/400-github-ecr-image-publishing-runbook.md)을
확인합니다.

## 문서 진입점

- 전체 문서 지도: [docs/000-index.md](docs/000-index.md)
- 현재 프로젝트 기술 상태: [docs/project/000-index.md](docs/project/000-index.md)
- 도메인 정책: [docs/domain/000-index.md](docs/domain/000-index.md)
- 개발·설계 workflow: [docs/workflow/000-index.md](docs/workflow/000-index.md)
- 작업 상태: [docs/work-items/000-index.md](docs/work-items/000-index.md)
- Infrastructure 개념: [Infrastructure system](docs/project/300-infrastructure-system.md)
- 운영 절차 지도: [Operations](docs/operations/000-index.md)
- 수동 AWS 최초 구축: [AWS initial provisioning runbook](docs/operations/100-aws-initial-provisioning-runbook.md)
- 단일 node Kubernetes 구축: [Kubernetes bootstrap runbook](docs/operations/200-kubernetes-bootstrap-runbook.md)
- Repository·delivery platform 최초 구축: [Repository·delivery platform bootstrap runbook](docs/operations/300-repository-and-delivery-platform-bootstrap-runbook.md)
- 반복 image 게시: [GitHub ECR image publishing runbook](docs/operations/400-github-ecr-image-publishing-runbook.md)
- 반복 GitOps 배포·rollback: [GitOps deployment and rollback runbook](docs/operations/420-gitops-deployment-and-rollback-runbook.md)

작업자는 [AGENTS.md](AGENTS.md)를 먼저 읽고 필요한 상세 문서로 이동합니다.
