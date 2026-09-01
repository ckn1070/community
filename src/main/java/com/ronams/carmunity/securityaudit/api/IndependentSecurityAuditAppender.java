package com.ronams.carmunity.securityaudit.api;

/** 사용자 요청 transaction과 독립적으로 보존해야 하는 보안 감사 기록 계약입니다. */
public interface IndependentSecurityAuditAppender {

  /** 별도 transaction으로 기록하고 저장 불가를 구조화된 결과로 반환합니다. */
  SecurityAuditAppendResult appendIndependent(SecurityAuditRecord auditRecord);
}
