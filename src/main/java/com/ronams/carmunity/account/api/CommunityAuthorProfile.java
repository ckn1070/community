package com.ronams.carmunity.account.api;

/** Community 공개 응답에 허용되는 최소 작성자 표시 정보입니다. */
public record CommunityAuthorProfile(String displayName, boolean displayable) {}
