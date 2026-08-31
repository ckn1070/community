package com.ronams.carmunity.securityaudit.internal.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.ronams.carmunity.securityaudit.api.SecurityAuditAppendResult;
import com.ronams.carmunity.securityaudit.api.SecurityAuditEventType;
import com.ronams.carmunity.securityaudit.api.SecurityAuditRecord;
import com.ronams.carmunity.securityaudit.api.SecurityAuditResult;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** REQUIRED와 REQUIRES_NEW 감사 계약의 실패 의미가 섞이지 않는지 검증합니다. */
class SecurityAuditPersistenceAdapterTest {

  private static final SecurityAuditRecord RECORD =
      SecurityAuditRecord.of(
          null,
          SecurityAuditEventType.PASSWORD_LOGIN,
          SecurityAuditResult.FAILURE,
          "UNKNOWN_IDENTIFIER",
          "a".repeat(64),
          Instant.parse("2099-07-01T00:00:00Z"));

  @Test
  void requiredAppend는_저장실패를_호출자에게_전파한다() {
    SecurityAuditTransactionalWriter writer = mock(SecurityAuditTransactionalWriter.class);
    doThrow(new IllegalStateException("write failed")).when(writer).appendRequired(RECORD);
    SecurityAuditPersistenceAdapter adapter = new SecurityAuditPersistenceAdapter(writer);

    assertThatThrownBy(() -> adapter.append(RECORD)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void independentAppend는_저장실패를_failed결과로_분리한다() {
    SecurityAuditTransactionalWriter writer = mock(SecurityAuditTransactionalWriter.class);
    doThrow(new IllegalStateException("write failed")).when(writer).appendRequiresNew(RECORD);
    SecurityAuditPersistenceAdapter adapter = new SecurityAuditPersistenceAdapter(writer);

    assertThat(adapter.appendIndependent(RECORD)).isEqualTo(SecurityAuditAppendResult.FAILED);
  }
}
