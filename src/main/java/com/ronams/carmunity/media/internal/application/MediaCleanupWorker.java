package com.ronams.carmunity.media.internal.application;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "carmunity.community", name = "enabled", havingValue = "true")
public class MediaCleanupWorker {
  private final JdbcClient jdbc;
  private final MediaObjectStorage storage;
  private final TransactionTemplate transactions;

  @Scheduled(fixedDelayString = "${carmunity.community.media.cleanup-delay:PT5S}")
  public void run() {
    jdbc
        .sql(
            "SELECT j.id,j.type,a.id AS asset_id,a.staging_object_key,a.delivery_object_key FROM media_job j JOIN media_asset a ON a.id=j.asset_id WHERE j.type IN ('DELETE_STAGING','DELETE_DELIVERY') AND ((j.status IN ('READY','RETRY') AND (j.next_attempt_at IS NULL OR j.next_attempt_at<=NOW())) OR (j.status='RUNNING' AND j.lease_until<NOW())) ORDER BY j.created_at,j.id LIMIT 1")
        .query()
        .listOfRows()
        .stream()
        .findFirst()
        .ifPresent(this::execute);
  }

  @Scheduled(fixedDelayString = "${carmunity.community.media.reconcile-delay:PT6H}")
  public void reconcile() {
    for (Map<String, Object> asset :
        jdbc.sql("SELECT id,delivery_object_key FROM media_asset WHERE status='READY'")
            .query()
            .listOfRows()) {
      String key = (String) asset.get("delivery_object_key");
      if (key == null || !storage.exists(key)) {
        jdbc.sql(
                "INSERT INTO media_job(public_id,asset_id,type,status,last_error_code) SELECT :id,:asset,'RECONCILE','DEAD','MISSING_DELIVERY_OBJECT' WHERE NOT EXISTS (SELECT 1 FROM media_job WHERE asset_id=:asset AND type='RECONCILE' AND status='DEAD' AND created_at>NOW()-INTERVAL '6 hours')")
            .param("id", UUID.randomUUID())
            .param("asset", asset.get("id"))
            .update();
      }
    }
  }

  private void execute(Map<String, Object> job) {
    String lease = UUID.randomUUID().toString();
    int claimed = claim(job, lease);
    if (claimed == 0) return;
    try {
      boolean delivery = "DELETE_DELIVERY".equals(job.get("type"));
      String key = (String) job.get(delivery ? "delivery_object_key" : "staging_object_key");
      if (key != null) storage.delete(key);
      transactions.executeWithoutResult(ignored -> complete(job, lease, delivery));
    } catch (RuntimeException exception) {
      jdbc.sql(
              "UPDATE media_job SET status=CASE WHEN attempt_count>=8 THEN 'DEAD' ELSE 'RETRY' END,lease_owner=NULL,lease_until=NULL,next_attempt_at=NOW()+(LEAST(1800,30*POWER(2,GREATEST(attempt_count-1,0)))::TEXT||' seconds')::INTERVAL,last_error_code='DELETE_FAILED',updated_at=NOW(),version=version+1 WHERE id=:id AND lease_owner=:lease")
          .param("id", job.get("id"))
          .param("lease", lease)
          .update();
    }
  }

  private int claim(Map<String, Object> job, String lease) {
    Integer result =
        transactions.execute(
            ignored -> {
              int changed =
                  jdbc.sql(
                          "UPDATE media_job SET status='RUNNING',lease_owner=:lease,lease_until=NOW()+INTERVAL '2 minutes',attempt_count=attempt_count+1,updated_at=NOW(),version=version+1 WHERE id=:id AND ((status IN ('READY','RETRY') AND (next_attempt_at IS NULL OR next_attempt_at<=NOW())) OR (status='RUNNING' AND lease_until<NOW()))")
                      .param("lease", lease)
                      .param("id", job.get("id"))
                      .update();
              if (changed == 0) return 0;
              if ("DELETE_DELIVERY".equals(job.get("type"))) {
                int assetChanged =
                    jdbc.sql(
                            "UPDATE media_asset a SET status='DELETE_PENDING',updated_at=NOW(),version=version+1 WHERE a.id=:id AND a.status IN ('READY','DELETE_PENDING') AND NOT EXISTS(SELECT 1 FROM media_usage u WHERE u.asset_id=a.id AND u.status<>'RELEASED')")
                        .param("id", job.get("asset_id"))
                        .update();
                if (assetChanged != 1) throw new IllegalStateException("삭제할 수 없는 media asset입니다.");
              }
              return changed;
            });
    return result == null ? 0 : result;
  }

  private void complete(Map<String, Object> job, String lease, boolean delivery) {
    if (delivery) {
      int assetChanged =
          jdbc.sql(
                  "UPDATE media_asset SET status='DELETED',updated_at=NOW(),version=version+1 WHERE id=:id AND status='DELETE_PENDING'")
              .param("id", job.get("asset_id"))
              .update();
      if (assetChanged != 1) throw new IllegalStateException("media asset 삭제 완료 상태가 충돌했습니다.");
    }
    int jobChanged =
        jdbc.sql(
                "UPDATE media_job SET status='DONE',lease_owner=NULL,lease_until=NULL,updated_at=NOW(),version=version+1 WHERE id=:id AND lease_owner=:lease AND status='RUNNING'")
            .param("id", job.get("id"))
            .param("lease", lease)
            .update();
    if (jobChanged != 1) throw new IllegalStateException("media cleanup lease가 만료되었습니다.");
  }
}
