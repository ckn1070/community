package com.ronams.carmunity.securityaudit.internal.adapter.out.persistence;

import com.ronams.carmunity.securityaudit.api.SecurityAuditRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 감사 저장의 실제 transaction 경계를 소유합니다. */
@RequiredArgsConstructor
@Component
class SecurityAuditTransactionalWriter {

  private final SecurityAuditJpaRepository repository;

  @Transactional
  void appendRequired(SecurityAuditRecord record) {
    repository.save(SecurityAuditJpaEntity.from(record));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  void appendRequiresNew(SecurityAuditRecord record) {
    repository.save(SecurityAuditJpaEntity.from(record));
  }
}
