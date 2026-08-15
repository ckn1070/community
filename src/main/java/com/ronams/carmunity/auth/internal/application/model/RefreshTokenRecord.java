package com.ronams.carmunity.auth.internal.application.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * application이 refresh token lifecycle 판단에 사용하는 저장 token snapshot입니다.
 *
 * <p>내부 DB id는 persistence adapter가 후속 update/delete port 호출에서 같은 row를 지정할 때 쓰는 opaque 값이며, API
 * 응답이나 token claim에 노출하지 않습니다.
 *
 * @param id refresh_token row 식별자입니다.
 * @param accountId auth_event 기록과 새 refresh token 저장에 필요한 내부 account row 식별자입니다.
 * @param accountPublicId access token subject 조회에 사용할 외부 account 식별자입니다.
 * @param familyId 재사용 탐지 시 함께 폐기할 token family 식별자입니다.
 * @param issuedAt token 발급 시각입니다.
 * @param expiresAt token 만료 시각입니다.
 * @param revokedAt 폐기 시각입니다.
 * @param revokedReason 폐기 사유입니다.
 * @param replacedByTokenId rotation으로 교체된 새 token row 식별자입니다.
 */
public record RefreshTokenRecord(
    Long id,
    Long accountId,
    UUID accountPublicId,
    UUID familyId,
    Instant issuedAt,
    Instant expiresAt,
    Instant revokedAt,
    String revokedReason,
    Long replacedByTokenId) {

  public RefreshTokenRecord {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(accountId, "accountId must not be null");
    Objects.requireNonNull(accountPublicId, "accountPublicId must not be null");
    Objects.requireNonNull(familyId, "familyId must not be null");
    Objects.requireNonNull(issuedAt, "issuedAt must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
  }

  /**
   * refresh token이 만료 시각에 도달했는지 판단합니다.
   *
   * @param now 유스케이스가 고정한 현재 시각입니다.
   * @return 현재 시각이 만료 시각 이상이면 true입니다.
   */
  public boolean isExpiredAt(Instant now) {
    return !expiresAt.isAfter(now);
  }

  /**
   * 이미 폐기되었거나 교체된 token을 다시 제출한 재사용 후보인지 판단합니다.
   *
   * @return 폐기 시각이나 replacement 연결이 있으면 true입니다.
   */
  public boolean isReuseCandidate() {
    return revokedAt != null || replacedByTokenId != null;
  }
}
