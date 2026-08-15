package com.ronams.carmunity.notification.internal.adapter.out.external;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;

/** Resend Java SDK를 호출하는 내부 gateway adapter입니다. */
class ResendEmailGatewayAdapter implements ResendEmailGateway {

  private final Resend resend;

  ResendEmailGatewayAdapter(Resend resend) {
    this.resend = resend;
  }

  @Override
  public CreateEmailResponse send(CreateEmailOptions options) throws ResendException {
    return resend.emails().send(options);
  }
}
