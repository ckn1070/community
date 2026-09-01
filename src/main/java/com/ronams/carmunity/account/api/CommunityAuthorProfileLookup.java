package com.ronams.carmunity.account.api;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Community가 Account table을 직접 읽지 않고 작성자 표시와 쓰기 가능 상태를 batch 조회합니다. */
public interface CommunityAuthorProfileLookup {
  Map<UUID, CommunityAuthorProfile> findAll(Set<UUID> accountIds);
}
