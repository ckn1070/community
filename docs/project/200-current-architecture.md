# 현재 프로젝트 아키텍처

이 문서는 Carmunity backend에 실제 적용된 아키텍처 결정과 재검토 조건을 소유합니다.

## 결정

```text
DDD 기반 업무 경계
+ 단일 Spring Boot 배포
+ Modular Monolith
+ 모듈 내부 Hexagonal Architecture
+ 단순 조회의 Read Model/Projection 허용
+ Spring Modulith와 ArchUnit 경계 검증
```

## 배경

- 자동차 정보, 커뮤니티, account/auth는 서로 다른 업무 언어와 변경 이유를 가질 수 있습니다.
- 현재 규모에서 마이크로서비스는 배포, 데이터 일관성, 로컬 개발 비용이 이득보다 큽니다.
- 인증, 권한, token, 외부 이메일, persistence는 기술 의존성과 업무 정책을 분리할 필요가 있습니다.
- 단순 조회까지 모든 계층을 강제하면 매핑과 boilerplate가 업무 규칙보다 커질 수 있습니다.

## 검토한 선택지

### 계층 중심 단일 package

- 초기 코드는 단순하지만 업무 경계가 기술 계층에 묻힙니다.
- account와 auth 내부 구현의 직접 참조를 막기 어렵습니다.

### Modular Monolith와 module 내부 Hexagonal Architecture

- 단일 배포를 유지하면서 업무 module과 기술 adapter 경계를 분리합니다.
- 상태 변경과 외부 의존성이 있는 유스케이스를 순수하게 test할 수 있습니다.

### Microservices

- 장애와 배포 단위를 분리할 수 있지만 현재 제품·운영 규모에 비해 network, 배포, 관측성, transaction 비용이 큽니다.

## 선택 근거

- 단일 배포와 PostgreSQL transaction의 단순성을 유지합니다.
- 업무 module이 다른 module의 `internal` 구현을 직접 참조하지 않게 합니다.
- application use case는 Web, JPA, Security, Resend 구현과 Port로 분리합니다.
- Spring Modulith `ApplicationModules.verify()`와 ArchUnit test로 module·계층 경계를 자동 검증합니다.
- 조회 전용 흐름은 규칙과 변경 모델을 오염시키지 않는 범위에서 projection을 허용합니다.

## 영향

- 실제 Java 최상위 package는 `account`, `auth`, `notification`, `shared`, `config`입니다.
- module 간 협력은 공개 API 또는 named interface를 사용합니다.
- state change, 권한, transaction, 외부 연동이 있는 흐름은 UseCase, Port, Adapter를 명확히 둡니다.
- Spring Modulith은 module boundary 검증에 사용하며 독립 배포나 messaging을 자동 도입하지 않습니다.

## 배제한 대안

- 계층 중심 전역 package는 업무 응집도와 module encapsulation을 약화해 기본 구조로 사용하지 않습니다.
- Microservices는 현재 운영 비용이 과하므로 도입하지 않습니다.
- 모든 조회에 Domain 변환과 Port를 강제하는 구조는 실익보다 boilerplate가 커질 수 있어 사용하지 않습니다.

## 재검토 조건

- module 간 공개 계약만으로 해결하기 어려운 직접 참조가 반복됩니다.
- 특정 module이 독립 배포와 장애 격리를 요구합니다.
- 단일 DB transaction이 확장과 운영의 병목이 됩니다.
- 비동기 처리나 검색이 명확한 제품 요구와 측정된 부하를 가집니다.
- projection이 변경 모델에 재사용돼 업무 규칙을 우회합니다.
- Spring Modulith 또는 ArchUnit 검증이 실제 구조와 지속적으로 어긋납니다.

## 관련 문서

- **일반 판단 기준**: [아키텍처 판단 원칙](../architecture/100-architecture-principles.md), [헥사고날 아키텍처](../architecture/200-hexagonal-architecture.md)
- **현재 구현 배치**: [모듈 지도](210-module-map.md)
- **구조 검증 도구**: [코드 품질 도구](../tech-stack/310-code-quality-tools.md)
