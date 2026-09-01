package com.ronams.carmunity.media.internal.application;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "carmunity.community", name = "enabled", havingValue = "true")
public class MediaVerificationWorker {
  private final JdbcClient jdbc;
  private final MediaObjectStorage storage;
  private final TransactionTemplate transactions;

  @Scheduled(fixedDelayString = "${carmunity.community.media.worker-delay:PT5S}")
  public void run() {
    jdbc
        .sql(
            "SELECT j.id,j.public_id,a.id AS asset_id,a.public_id AS asset_public_id,a.staging_object_key,a.expected_media_type,a.original_byte_size,a.original_checksum_sha256 FROM media_job j JOIN media_asset a ON a.id=j.asset_id WHERE j.type='VERIFY' AND ((j.status IN ('READY','RETRY') AND (j.next_attempt_at IS NULL OR j.next_attempt_at<=NOW())) OR (j.status='RUNNING' AND j.lease_until<NOW())) ORDER BY j.created_at,j.id LIMIT 1")
        .query()
        .listOfRows()
        .stream()
        .findFirst()
        .ifPresent(this::verify);
  }

  private void verify(Map<String, Object> job) {
    Long jobId = (Long) job.get("id");
    String lease = UUID.randomUUID().toString();
    Boolean claimed =
        transactions.execute(
            ignored -> {
              int jobChanged =
                  jdbc.sql(
                          "UPDATE media_job SET status='RUNNING',lease_owner=:lease,lease_until=NOW()+INTERVAL '2 minutes',attempt_count=attempt_count+1,updated_at=NOW(),version=version+1 WHERE id=:id AND ((status IN ('READY','RETRY') AND (next_attempt_at IS NULL OR next_attempt_at<=NOW())) OR (status='RUNNING' AND lease_until<NOW()))")
                      .param("lease", lease)
                      .param("id", jobId)
                      .update();
              if (jobChanged == 0) return false;
              int assetChanged =
                  jdbc.sql(
                          "UPDATE media_asset SET status='VERIFYING',updated_at=NOW(),version=version+1 WHERE id=:id AND status IN ('UPLOADED','VERIFYING')")
                      .param("id", job.get("asset_id"))
                      .update();
              if (assetChanged != 1) throw new IllegalStateException("VERIFY asset 상태가 올바르지 않습니다.");
              return true;
            });
    if (!Boolean.TRUE.equals(claimed)) return;
    try {
      byte[] original = storage.get((String) job.get("staging_object_key"));
      if (original.length != ((Number) job.get("original_byte_size")).longValue()
          || !sha(original).equals(job.get("original_checksum_sha256"))) {
        throw new IllegalArgumentException("STAGING_INTEGRITY_MISMATCH");
      }
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(original));
      if (image == null || (long) image.getWidth() * image.getHeight() > 24_000_000L)
        throw new IllegalArgumentException("INVALID_IMAGE");
      String format = "image/jpeg".equals(job.get("expected_media_type")) ? "jpg" : "png";
      String mediaType = "jpg".equals(format) ? "image/jpeg" : "image/png";
      var output = new ByteArrayOutputStream();
      ImageIO.write(image, format, output);
      byte[] delivery = output.toByteArray();
      String key = "delivery/" + job.get("asset_public_id") + "/image." + format;
      storage.put(key, delivery, mediaType);
      transactions.executeWithoutResult(
          ignored -> complete(job, jobId, lease, key, mediaType, delivery, image));
    } catch (IllegalArgumentException exception) {
      reject(job, jobId, lease);
    } catch (Exception exception) {
      jdbc.sql(
              "UPDATE media_job SET status=CASE WHEN attempt_count>=8 THEN 'DEAD' ELSE 'RETRY' END,lease_owner=NULL,lease_until=NULL,next_attempt_at=NOW()+(LEAST(1800,30*POWER(2,GREATEST(attempt_count-1,0)))::TEXT||' seconds')::INTERVAL,last_error_code='VERIFY_FAILED',updated_at=NOW(),version=version+1 WHERE id=:id AND lease_owner=:lease")
          .param("id", jobId)
          .param("lease", lease)
          .update();
    }
  }

  private void reject(Map<String, Object> job, Long jobId, String lease) {
    transactions.executeWithoutResult(ignored -> persistRejection(job, jobId, lease));
  }

  private void persistRejection(Map<String, Object> job, Long jobId, String lease) {
    jdbc.sql(
            "UPDATE media_asset SET status='REJECTED',updated_at=NOW(),version=version+1 WHERE id=:id AND status='VERIFYING'")
        .param("id", job.get("asset_id"))
        .update();
    jdbc.sql(
            "UPDATE media_job SET status='DONE',lease_owner=NULL,lease_until=NULL,last_error_code='INVALID_IMAGE',updated_at=NOW(),version=version+1 WHERE id=:id AND lease_owner=:lease")
        .param("id", jobId)
        .param("lease", lease)
        .update();
    jdbc.sql(
            "INSERT INTO media_job(public_id,asset_id,type,status) VALUES(:id,:asset,'DELETE_STAGING','READY') ON CONFLICT DO NOTHING")
        .param("id", UUID.randomUUID())
        .param("asset", job.get("asset_id"))
        .update();
  }

  private void complete(
      Map<String, Object> job,
      Long jobId,
      String lease,
      String key,
      String mediaType,
      byte[] delivery,
      BufferedImage image) {
    int assetChanged =
        jdbc.sql(
                "UPDATE media_asset SET status='READY',delivery_object_key=:key,detected_media_type=:type,delivery_byte_size=:size,width=:width,height=:height,delivery_checksum_sha256=:hash,updated_at=NOW(),version=version+1 WHERE id=:id AND status='VERIFYING'")
            .param("key", key)
            .param("type", mediaType)
            .param("size", delivery.length)
            .param("width", image.getWidth())
            .param("height", image.getHeight())
            .param("hash", sha(delivery))
            .param("id", job.get("asset_id"))
            .update();
    int jobChanged =
        jdbc.sql(
                "UPDATE media_job SET status='DONE',lease_owner=NULL,lease_until=NULL,updated_at=NOW(),version=version+1 WHERE id=:id AND lease_owner=:lease AND status='RUNNING'")
            .param("id", jobId)
            .param("lease", lease)
            .update();
    if (assetChanged != 1 || jobChanged != 1) {
      throw new IllegalStateException("VERIFY 완료 상태가 충돌했습니다.");
    }
    jdbc.sql(
            "INSERT INTO media_job(public_id,asset_id,type,status) VALUES(:id,:asset,'DELETE_STAGING','READY') ON CONFLICT DO NOTHING")
        .param("id", UUID.randomUUID())
        .param("asset", job.get("asset_id"))
        .update();
  }

  private String sha(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
