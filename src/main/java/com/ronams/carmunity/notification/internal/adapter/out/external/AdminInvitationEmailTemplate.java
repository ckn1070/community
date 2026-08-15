package com.ronams.carmunity.notification.internal.adapter.out.external;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

/** 관리자 초대용 transactional email subject/html/text 문자열을 조립합니다. */
class AdminInvitationEmailTemplate {

  private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

  String subject() {
    return "Carmunity 관리자 초대를 수락해 주세요";
  }

  String html(String invitationUrl, Instant expiresAt) {
    return """
        <div style="font-family:Arial,sans-serif;line-height:1.6;color:#172033">
          <h1 style="font-size:20px;color:#1d4ed8">관리자 초대</h1>
          <p>Carmunity 관리자 계정 생성을 완료하려면 아래 버튼을 눌러 초대를 수락해 주세요.</p>
          <p>
            <a href="%s" style="display:inline-block;background:#2563eb;color:#ffffff;text-decoration:none;padding:10px 16px;border-radius:6px">
              초대 수락하기
            </a>
          </p>
          <p style="font-size:13px;color:#4b5563">이 링크는 %s까지 유효합니다.</p>
        </div>
        """
        .formatted(escapeHtml(invitationUrl), ISO_FORMATTER.format(expiresAt));
  }

  String text(String invitationUrl, Instant expiresAt) {
    return """
        Carmunity 관리자 초대를 수락해 주세요.

        초대 수락 링크: %s
        만료 시각: %s
        """
        .formatted(invitationUrl, ISO_FORMATTER.format(expiresAt));
  }

  private String escapeHtml(String value) {
    return value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;");
  }
}
