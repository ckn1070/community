package com.ronams.carmunity.auth.internal.adapter.out.persistence;

import com.ronams.carmunity.auth.internal.application.model.RefreshTokenRecord;
import com.ronams.carmunity.auth.internal.application.model.RefreshTokenRejectionReason;
import com.ronams.carmunity.auth.internal.application.port.out.RefreshTokenLifecycleStorePort;
import com.ronams.carmunity.auth.internal.application.result.IssuedRefreshToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * refresh token lifecycle 저장소의 JPA adapter입니다.
 *
 * <p>rotation 대상 token과 family 활성 token은 pessimistic lock으로 조회해 동시 refresh가 같은 token을 중복 rotation하지
 * 못하게 합니다.
 */
@RequiredArgsConstructor
@Component
class RefreshTokenPersistenceAdapter implements RefreshTokenLifecycleStorePort {

  private final AuthRefreshTokenJpaRepository refreshTokenRepository;
  private final AuthTokenEventJpaRepository authEventRepository;

  @Override
  @Transactional
  public Optional<RefreshTokenRecord> findByHashForUpdate(String tokenHash) {
    return refreshTokenRepository
        .findByTokenHashForUpdate(tokenHash)
        .flatMap(
            token ->
                refreshTokenRepository.findAccountPublicId(token.accountId()).map(token::toRecord));
  }

  /** 새 refresh token 저장, 기존 token 폐기, 성공 감사 이벤트를 같은 transaction에서 기록합니다. */
  @Override
  @Transactional
  public void rotate(
      RefreshTokenRecord currentToken, IssuedRefreshToken replacementToken, Instant rotatedAt) {
    AuthRefreshTokenJpaEntity currentEntity = findTokenForMutation(currentToken.id());
    AuthRefreshTokenJpaEntity replacementEntity =
        refreshTokenRepository.saveAndFlush(
            AuthRefreshTokenJpaEntity.issue(
                currentToken.accountId(), currentToken.familyId(), replacementToken));
    currentEntity.rotateTo(replacementEntity.id(), rotatedAt);
    authEventRepository.save(
        AuthTokenEventJpaEntity.tokenRefreshSuccess(currentToken.accountId(), rotatedAt));
  }

  /** 단일 refresh token을 LOGOUT으로 폐기하고 성공 감사 이벤트를 같은 transaction에서 기록합니다. */
  @Override
  @Transactional
  public void revokeForLogout(RefreshTokenRecord currentToken, Instant loggedOutAt) {
    AuthRefreshTokenJpaEntity currentEntity = findTokenForMutation(currentToken.id());
    currentEntity.revokeForLogout(loggedOutAt);
    authEventRepository.save(
        AuthTokenEventJpaEntity.logoutSuccess(currentToken.accountId(), loggedOutAt));
  }

  /** 재사용이 의심된 family의 아직 활성 상태인 token을 모두 폐기하고 감사 이벤트를 남깁니다. */
  @Override
  @Transactional
  public void revokeFamilyForReuse(RefreshTokenRecord reusedToken, Instant detectedAt) {
    revokeActiveFamilyTokens(
        reusedToken.familyId(), AuthRefreshTokenJpaEntity.REUSE_DETECTED_REASON, detectedAt);
    authEventRepository.save(
        AuthTokenEventJpaEntity.tokenReuseDetected(reusedToken.accountId(), detectedAt));
  }

  /**
   * account 상태가 token 발급을 허용하지 않는 경우 같은 family를 폐기합니다.
   *
   * <p>현재 schema에는 SUSPENDED/WITHDRAWN을 구분하는 refresh token 폐기 사유가 없으므로 기존 ACCOUNT_SUSPENDED 사유로
   * family를 폐기하고, 구체 실패 원인은 auth_event.reason_code에 남깁니다.
   */
  @Override
  @Transactional
  public void revokeFamilyForRejectedAccount(
      RefreshTokenRecord currentToken, RefreshTokenRejectionReason reason, Instant rejectedAt) {
    revokeActiveFamilyTokens(
        currentToken.familyId(), AuthRefreshTokenJpaEntity.ACCOUNT_SUSPENDED_REASON, rejectedAt);
    authEventRepository.save(
        AuthTokenEventJpaEntity.tokenRefreshFailureForRejectedAccount(
            currentToken.accountId(), reason, rejectedAt));
  }

  private AuthRefreshTokenJpaEntity findTokenForMutation(Long tokenId) {
    return refreshTokenRepository
        .findById(tokenId)
        .orElseThrow(() -> new IllegalStateException("refresh token row is missing"));
  }

  private void revokeActiveFamilyTokens(UUID familyId, String reason, Instant revokedAt) {
    List<AuthRefreshTokenJpaEntity> activeFamilyTokens =
        refreshTokenRepository.findActiveFamilyTokensForUpdate(familyId);
    activeFamilyTokens.forEach(token -> token.revoke(reason, revokedAt));
  }
}
