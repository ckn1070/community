package com.ronams.carmunity.media.api;

/** Community capability가 private storage 의존성 준비 여부를 fail-close 판단하는 공개 계약입니다. */
public interface MediaAvailability {
  boolean isReady();
}
