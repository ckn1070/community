package com.ronams.carmunity.securityaudit.api;

/** 호출자의 transaction과 함께 성공하거나 rollback되어야 하는 보안 감사 기록 계약입니다. */
public interface SecurityAuditAppender {

  /** 보안 감사 사실을 현재 transaction에 참여시켜 저장합니다. */
  void append(SecurityAuditRecord record);
}
