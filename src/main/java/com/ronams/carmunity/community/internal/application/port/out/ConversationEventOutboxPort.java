package com.ronams.carmunity.community.internal.application.port.out;

import com.ronams.carmunity.shared.integration.CommunityConversationCreatedV1;

/** 호출자의 댓글 transaction에 참여해 전달 원본을 저장하며 실패를 호출자에게 전파합니다. */
public interface ConversationEventOutboxPort {
  void append(CommunityConversationCreatedV1 event);
}
