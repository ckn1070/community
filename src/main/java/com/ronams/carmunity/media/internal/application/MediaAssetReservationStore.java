package com.ronams.carmunity.media.internal.application;

import com.ronams.carmunity.shared.error.ApiException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** S3 I/O와 분리된 짧은 transaction으로 upload reservation 생명주기를 소유합니다. */
@Component
@RequiredArgsConstructor
class MediaAssetReservationStore {
  private final JdbcClient jdbc;

  @Transactional
  Reservation reserve(
      UUID actor,
      UUID idempotencyKey,
      String requestHash,
      String mediaType,
      long byteSize,
      String checksum) {
    var existing =
        jdbc
            .sql(
                "SELECT public_id,status,request_hash,staging_object_key FROM media_asset WHERE owner_account_public_id=:actor AND idempotency_key=:key")
            .param("actor", actor)
            .param("key", idempotencyKey)
            .query()
            .listOfRows()
            .stream()
            .findFirst();
    if (existing.isPresent()) {
      Map<String, Object> row = existing.get();
      if (!requestHash.equals(row.get("request_hash"))) {
        throw new ApiException(MediaErrorCode.IDEMPOTENCY_KEY_REUSED);
      }
      return new Reservation(
          (UUID) row.get("public_id"),
          (String) row.get("staging_object_key"),
          "RESERVED".equals(row.get("status")));
    }

    UUID assetId = UUID.randomUUID();
    String stagingKey = "staging/" + UUID.randomUUID();
    jdbc.sql(
            "INSERT INTO media_asset(public_id,owner_account_public_id,idempotency_key,request_hash,status,staging_object_key,expected_media_type,original_byte_size,original_checksum_sha256,reservation_expires_at) VALUES(:id,:actor,:key,:hash,'RESERVED',:object,:type,:size,:checksum,:expires)")
        .param("id", assetId)
        .param("actor", actor)
        .param("key", idempotencyKey)
        .param("hash", requestHash)
        .param("object", stagingKey)
        .param("type", mediaType)
        .param("size", byteSize)
        .param("checksum", checksum)
        .param("expires", OffsetDateTime.now(ZoneOffset.UTC).plus(15, ChronoUnit.MINUTES))
        .update();
    return new Reservation(assetId, stagingKey, true);
  }

  @Transactional
  void uploaded(UUID assetId) {
    int changed =
        jdbc.sql(
                "UPDATE media_asset SET status='UPLOADED',updated_at=NOW(),version=version+1 WHERE public_id=:id AND status='RESERVED'")
            .param("id", assetId)
            .update();
    if (changed == 0) return;
    jdbc.sql(
            "INSERT INTO media_job(public_id,asset_id,type,status) SELECT :job,id,'VERIFY','READY' FROM media_asset WHERE public_id=:asset ON CONFLICT DO NOTHING")
        .param("job", UUID.randomUUID())
        .param("asset", assetId)
        .update();
  }

  record Reservation(UUID assetId, String stagingKey, boolean uploadRequired) {}
}
