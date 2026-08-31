package com.ronams.carmunity.account.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** account 자격 증명이 바뀌어 기존 인증 session을 폐기해야 함을 알리는 동기 공개 사실입니다. */
public record CredentialChanged(UUID accountPublicId, Instant changedAt) {

  public CredentialChanged {
    Objects.requireNonNull(accountPublicId, "accountPublicId must not be null");
    Objects.requireNonNull(changedAt, "changedAt must not be null");
  }
}
