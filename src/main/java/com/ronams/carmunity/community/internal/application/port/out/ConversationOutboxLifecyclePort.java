package com.ronams.carmunity.community.internal.application.port.out;

import java.time.Instant;
import java.util.*;

/** 원본 조회·완료 적격 표시·승인 종료·유한 삭제는 Community만 수행합니다. */
public interface ConversationOutboxLifecyclePort {
  Set<UUID> nextCompletionBatch();

  void checked(Set<UUID> checked, Set<UUID> completed, Instant now);

  int deleteEligible(Instant cutoff);

  List<Original> originals(Set<UUID> ids);

  int terminate(
      Set<UUID> ids, Instant now, String operator, String reason, String impact, String evidence);

  record Original(UUID eventId, UUID postId, String payload, boolean terminal) {}
}
