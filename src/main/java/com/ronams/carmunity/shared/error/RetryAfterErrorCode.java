package com.ronams.carmunity.shared.error;

/** 일시 오류가 클라이언트에 공개할 delta-seconds 재시도 계약입니다. */
public interface RetryAfterErrorCode extends ErrorCode {

  int retryAfterSeconds();
}
