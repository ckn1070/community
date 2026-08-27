# Staging Swagger 관리자 접근 보호 설계

## 배경

production profile은 정적 resource와 springdoc endpoint를 닫습니다. staging도 같은 profile만
사용하므로 실제 배포 환경에서 OpenAPI 문서를 확인할 수 없습니다. 문서를 공개 인터넷에
익명으로 노출하지 않으면서 기존 account 권한 체계로 접근자를 제한해야 합니다.

## 선택

### Profile 합성

- application Deployment에는 `production,staging`을 순서대로 적용합니다.
- `application-staging.yaml`은 production의 운영 안전 설정을 유지한 채 정적 resource와
  springdoc만 다시 활성화합니다.
- Flyway Migration Job은 UI나 HTTP endpoint가 필요 없으므로 production profile만 유지합니다.

### 인증과 인가

- Swagger 경로에는 staging profile에서만 생성되는 우선순위가 높은 별도
  `SecurityFilterChain`을 적용합니다.
- Spring Security가 제공하는 기본 Form Login 화면과 서버 `HttpSession`을 사용합니다.
- 기존 account의 email·password를 검증하고 `ACTIVE`, full session,
  password-change-not-required 조건과 `ROLE_ADMIN` 또는 `ROLE_SUPER_ADMIN`을 모두 요구합니다.
- 비밀번호 검증은 기존 API 로그인과 같은 application service를 사용하되, Swagger 로그인은
  JWT와 refresh token을 발급하거나 저장하지 않습니다.
- 로그인 실패 상태와 감사 기록은 기존 비밀번호 인증 정책을 재사용하고, 성공은 token 없는
  대화형 로그인으로 별도 기록합니다.

### 보안 경계

- Swagger session cookie는 Swagger UI, OpenAPI JSON, 로그인·로그아웃 경로에서만
  소비합니다.
- `/api/**`는 기존 무상태 Bearer JWT 체인을 그대로 사용합니다. Swagger의 Try it out으로
  보호 API를 호출하려면 문서 로그인과 별개로 유효한 Bearer JWT를 입력해야 합니다.
- Swagger 체인은 cookie 기반이므로 CSRF 보호를 유지합니다.
- production profile에서는 springdoc endpoint와 Swagger 보안 체인을 모두 생성하지 않습니다.

## 영향과 재검토 조건

- 현재 staging application은 replica 1개이므로 in-memory `HttpSession`을 사용합니다.
- replica가 2개 이상이 되면 sticky session 또는 Spring Session 공유 저장소를 도입하기 전에
  이 결정을 재검토합니다.
- 외부 Identity Provider나 조직 SSO가 도입되면 account password Form Login보다 해당 인증을
  우선 검토합니다.

## 배제 대안

- Swagger 전용 공유 계정이나 Basic Auth는 기존 account 비활성화·권한 회수 정책과 분리된
  credential을 추가하므로 선택하지 않습니다.
- 기존 JSON 로그인 usecase를 그대로 호출하면 사용하지 않는 JWT·refresh token이 발급되고
  저장되므로 선택하지 않습니다.
- 일반 회원 로그인 후 Swagger URL만 role로 막는 방식은 불필요한 문서 session을 만들므로
  AuthenticationProvider 단계에서 관리자 접근 조건까지 검증합니다.

## 관련 문서

- [008 Staging Swagger 관리자 접근 보호 작업 항목](008-staging-swagger-admin-access.md)
- [110 Account 인증·인가 정책](../../../domain/110-account-auth-policy.md)
- [220 Spring Security](../../../tech-stack/220-spring-security.md)
- [260 OpenAPI 문서화 가이드](../../../tech-stack/260-openapi.md)
