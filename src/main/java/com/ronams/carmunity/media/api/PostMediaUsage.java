package com.ronams.carmunity.media.api;

import java.util.Set;
import java.util.UUID;

/** Community post transaction이 검증 완료 이미지를 claim·release하는 공개 계약입니다. */
public interface PostMediaUsage {
  void replace(UUID actorId, UUID postId, Set<UUID> mediaIds);

  void hide(UUID postId);

  void release(UUID postId);
}
