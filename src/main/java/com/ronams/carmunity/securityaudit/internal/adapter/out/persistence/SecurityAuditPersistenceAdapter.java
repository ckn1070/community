package com.ronams.carmunity.securityaudit.internal.adapter.out.persistence;

import com.ronams.carmunity.securityaudit.api.IndependentSecurityAuditAppender;
import com.ronams.carmunity.securityaudit.api.SecurityAuditAppendResult;
import com.ronams.carmunity.securityaudit.api.SecurityAuditAppender;
import com.ronams.carmunity.securityaudit.api.SecurityAuditRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 호출 transaction 참여와 독립 기록 계약을 각각 올바른 transaction 경계로 연결합니다. */
@Slf4j
@RequiredArgsConstructor
@Component
class SecurityAuditPersistenceAdapter
    implements SecurityAuditAppender, IndependentSecurityAuditAppender {

  private final SecurityAuditTransactionalWriter writer;

  @Override
  public void append(SecurityAuditRecord record) {
    writer.appendRequired(record);
  }

  @Override
  public SecurityAuditAppendResult appendIndependent(SecurityAuditRecord record) {
    try {
      writer.appendRequiresNew(record);
      return SecurityAuditAppendResult.APPENDED;
    } catch (RuntimeException exception) {
      log.error(
          "security audit append failed: eventType={}, accountPublicId={}",
          record.eventType(),
          record.accountPublicId(),
          exception);
      return SecurityAuditAppendResult.FAILED;
    }
  }
}
