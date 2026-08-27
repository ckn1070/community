# Staging Swagger 관리자 접근 보호

## 상태

Done

## 달성한 결과

- staging application만 Swagger UI와 OpenAPI JSON·YAML을 제공합니다.
- 기존 `ROLE_ADMIN` 또는 `ROLE_SUPER_ADMIN` 계정이 Spring Security 기본 로그인 화면에서
  인증해야 문서에 접근할 수 있습니다.
- Swagger browser session과 `/api/**` Bearer JWT 인증 경계를 분리하고 production 문서는
  계속 비활성화합니다.
- 비밀번호 검증과 account 상태 판정을 token 발급에서 분리해 API 로그인과 Swagger Form
  Login이 같은 정책을 사용합니다.
- Swagger 로그인은 기존 관리자 account만 허용하고 refresh token 없이 로그인 상태와 감사
  기록만 갱신합니다.
- staging 전용 우선 filter chain은 Swagger·OpenAPI·login·logout 경로만 server session으로
  보호하며 `/api/**`는 기존 무상태 Bearer JWT 체인을 유지합니다.
- `application-staging.yaml`이 production의 문서 비활성을 staging에서만 덮고, Kustomize
  overlay는 application Deployment만 `production,staging`으로 실행합니다. Migration Job은
  `production`을 유지합니다.
- 환경별 확인 경로, 로그인 조건, Try it out의 별도 JWT 조건과 replica 확장 시 재검토 조건을
  영구 SOT에 반영했습니다.

## 확정 결정 색인

- [008 Staging Swagger 관리자 접근 보호 설계](008-staging-swagger-admin-access-design.md)

## 검증·전환·복구

- 인증 application service, provider, filter chain과 profile 설정 focused test가 통과했습니다.
- PostgreSQL 통합 테스트에서 interactive 로그인 성공이 refresh token을 저장하지 않고 실패
  상태, `last_login_at`과 감사 이벤트만 갱신함을 확인했습니다.
- `kubectl kustomize deploy/overlays/stg` 결과에서 application은 `production,staging`,
  Migration Job은 `production`임을 확인했습니다.
- `./gradlew check --no-daemon --rerun-tasks --console=plain`과 독립 개발 검증을 통과했습니다.
- 장애 시 staging overlay의 profile patch를 제거하면 기존 production-only 실행으로
  복구됩니다.

## 영구 SOT 승격

- 환경별 문서 노출과 접근 정책은 `docs/tech-stack/260-openapi.md`에 반영했습니다.
- 기존 account 인증 정책은 `docs/domain/110-account-auth-policy.md`, filter chain 경계는
  `docs/tech-stack/220-spring-security.md`에 반영했습니다.
- staging profile 적용 책임은 `deploy/README.md`와 GitOps application delivery SOT에
  반영했습니다.

## 관련 문서

- [110 Account 인증·인가 정책](../../../domain/110-account-auth-policy.md)
- [220 Spring Security](../../../tech-stack/220-spring-security.md)
- [260 OpenAPI 문서화 가이드](../../../tech-stack/260-openapi.md)
- [220 GitOps Application Delivery](../../../project/infrastructure/220-gitops-application-delivery.md)
