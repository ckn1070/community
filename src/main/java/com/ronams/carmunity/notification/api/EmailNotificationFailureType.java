package com.ronams.carmunity.notification.api;

/** provider 세부사항을 숨긴 notification 발송 실패 분류입니다. */
public enum EmailNotificationFailureType {
  CONFIGURATION,
  TEMPORARY_PROVIDER_FAILURE,
  PERMANENT_PROVIDER_FAILURE
}
