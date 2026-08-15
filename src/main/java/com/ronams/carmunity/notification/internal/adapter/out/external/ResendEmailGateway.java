package com.ronams.carmunity.notification.internal.adapter.out.external;

import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;

/** Resend SDK 호출을 감싸는 내부 gateway입니다. 테스트는 이 interface의 대역으로 provider 응답을 통제합니다. */
interface ResendEmailGateway {

  CreateEmailResponse send(CreateEmailOptions options) throws ResendException;
}
