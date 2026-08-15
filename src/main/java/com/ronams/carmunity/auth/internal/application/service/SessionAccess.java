package com.ronams.carmunity.auth.internal.application.service;

/** 발급된 인증 session이 일반 보호 API에 접근할 수 있는 범위입니다. */
public enum SessionAccess {
  FULL,
  LIMITED
}
