package com.ronams.carmunity.community.internal.application.service;

import com.ronams.carmunity.community.internal.application.port.out.ConversationOutboxLifecyclePort;
import com.ronams.carmunity.community.internal.application.port.out.ConversationOutboxLifecyclePort.Original;
import com.ronams.carmunity.notification.api.ConversationEventCompletion;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConversationOutboxLifecycleService {
  private final ConversationOutboxLifecyclePort outbox;
  private final ConversationEventCompletion completion;
  private final Clock clock;

  /** 조회 실패·잘못된 응답이면 transaction 전체를 취소하여 이전 적격 원본도 이번 회차에는 삭제하지 않습니다. */
  @Transactional(timeout = 5)
  public int cleanup() {
    Set<UUID> ids = outbox.nextCompletionBatch();
    Set<UUID> completed = completion.completed(ids);
    verifyCompletion(ids, completed);
    outbox.checked(ids, completed, clock.instant());
    return outbox.deleteEligible(clock.instant().minus(Duration.ofDays(7)));
  }

  /** 발행 이력과 무관하게 원본 전체에서 완료·terminal을 제외합니다. 알 수 없는 ID는 부분 실행하지 않습니다. */
  @Transactional(readOnly = true, timeout = 5)
  public List<Original> preview(Set<UUID> ids) {
    validateIds(ids);
    List<Original> originals = outbox.originals(ids);
    if (originals.size() != ids.size())
      throw new IllegalArgumentException("원본을 찾을 수 없는 event ID가 있습니다");
    Set<UUID> completed = completion.completed(ids);
    verifyCompletion(ids, completed);
    return originals.stream()
        .filter(e -> !e.terminal() && !completed.contains(e.eventId()))
        .toList();
  }

  /** 운영자가 확인한 원인·영향·복구 불가 증거를 원본에 보존하며 batch 변경이나 유효 lease가 있으면 전부 취소합니다. */
  @Transactional(timeout = 5)
  public void terminate(
      Set<UUID> ids, int expected, String operator, String reason, String impact, String evidence) {
    validateEvidence(operator);
    validateEvidence(reason);
    validateEvidence(impact);
    validateEvidence(evidence);
    List<Original> candidates = preview(ids);
    if (candidates.size() != expected || expected != ids.size())
      throw new IllegalStateException("승인한 대상 건수가 달라졌습니다");
    if (outbox.terminate(ids, clock.instant(), operator, reason, impact, evidence) != expected)
      throw new IllegalStateException("종료 대상이 변경됐거나 발행 중입니다");
  }

  private static void validateIds(Set<UUID> ids) {
    if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull))
      throw new IllegalArgumentException("event ID는 1~100개여야 합니다");
  }

  private static void verifyCompletion(Set<UUID> ids, Set<UUID> completed) {
    if (completed == null || !ids.containsAll(completed))
      throw new IllegalStateException("완료 조회 응답이 요청과 다릅니다");
  }

  public static void validateEvidence(String value) {
    if (value == null || value.isBlank() || value.length() > 1000)
      throw new IllegalArgumentException("운영 근거는 1~1000자여야 합니다");
  }
}
