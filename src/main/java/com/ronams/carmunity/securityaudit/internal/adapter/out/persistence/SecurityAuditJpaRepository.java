package com.ronams.carmunity.securityaudit.internal.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SecurityAuditJpaRepository extends JpaRepository<SecurityAuditJpaEntity, Long> {}
