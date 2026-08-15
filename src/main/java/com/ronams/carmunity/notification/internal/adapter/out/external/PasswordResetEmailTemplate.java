package com.ronams.carmunity.notification.internal.adapter.out.external;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/** 비밀번호 재설정용 transactional email subject/html/text 문자열을 조립합니다. */
class PasswordResetEmailTemplate {

  private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

  String subject() {
    return "Carmunity 비밀번호 재설정을 완료해 주세요";
  }

  String html(String resetUrl, Instant expiresAt) {
    String expiresAtText = ISO_FORMATTER.format(expiresAt);
    return """
        <div style="font-family:Arial,sans-serif;line-height:1.6;color:#172033">
          <h1 style="font-size:20px;color:#1d4ed8">비밀번호 재설정</h1>
          <p>아래 버튼을 눌러 Carmunity 비밀번호 재설정을 완료해 주세요.</p>
          <p>
            <a href="%s" style="display:inline-block;background:#2563eb;color:#ffffff;text-decoration:none;padding:10px 16px;border-radius:6px">
              비밀번호 재설정하기
            </a>
          </p>
          <p style="font-size:13px;color:#4b5563">이 링크는 %s까지 유효합니다.</p>
        </div>
        """
        .formatted(escapeHtml(resetUrl), expiresAtText);
  }

  String text(String resetUrl, Instant expiresAt) {
    Duration remaining = Duration.between(Instant.now(), expiresAt);
    long minutes = Math.max(0, remaining.toMinutes());
    return """
        Carmunity 비밀번호 재설정을 완료해 주세요.

        재설정 링크: %s
        만료 시각: %s
        남은 시간: 약 %d분
        """
        .formatted(resetUrl, ISO_FORMATTER.format(expiresAt), minutes);
  }

  private String escapeHtml(String value) {
    return value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;");
  }
}
