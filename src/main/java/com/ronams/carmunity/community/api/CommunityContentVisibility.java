package com.ronams.carmunity.community.api;

import java.util.Map;
import java.util.UUID;

/** Moderation이 Community 저장소를 우회하지 않고 콘텐츠를 숨기는 공개 계약입니다. */
public interface CommunityContentVisibility {
  void requireVisible(UUID contentId, String kind);

  Map<String, Object> moderationSnapshot(UUID contentId, String kind);

  void hide(UUID contentId);
}
