package com.ronams.carmunity.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Entity의 생성/수정 시각을 애플리케이션에서 관리하게 합니다.
 *
 * <p>DB default는 insert fallback으로 유지하고, 일반 애플리케이션 저장 경로에서는 Spring Data JPA Auditing이 timestamp 책임을
 * 집니다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {}
