package com.ronams.carmunity.media.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ronams.carmunity.media.api.MediaAvailability;
import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.support.PostgreSqlIntegrationTestSupport;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    properties = {
      "carmunity.community.enabled=true",
      "carmunity.community.cursor-hmac-secret=integration-cursor-secret-that-is-at-least-32-bytes",
      "carmunity.community.media.bucket=test-bucket",
      "carmunity.community.media.role-arn=arn:aws:iam::000000000000:role/test"
    })
class MediaUploadIntegrationTest extends PostgreSqlIntegrationTestSupport {
  @Autowired MediaService service;
  @Autowired MediaVerificationWorker verificationWorker;
  @Autowired JdbcClient jdbc;
  @MockitoBean MediaObjectStorage storage;
  @MockitoBean MediaAvailability availability;

  @Test
  void sameRequestReturnsOneReservationAndDifferentPayloadConflicts() throws Exception {
    UUID actor = account();
    UUID requestId = UUID.randomUUID();
    Path first = image(0x112233);
    Path second = image(0x445566);
    try {
      Object firstId =
          service.upload(actor, requestId, first, Files.size(first), "image/png").get("id");
      Object replayId =
          service.upload(actor, requestId, first, Files.size(first), "image/png").get("id");

      assertThat(replayId).isEqualTo(firstId);
      assertThat(
              jdbc.sql("SELECT COUNT(*) FROM media_asset WHERE owner_account_public_id=:actor")
                  .param("actor", actor)
                  .query(Long.class)
                  .single())
          .isEqualTo(1);
      assertThat(
              jdbc.sql(
                      "SELECT COUNT(*) FROM media_job j JOIN media_asset a ON a.id=j.asset_id WHERE a.owner_account_public_id=:actor")
                  .param("actor", actor)
                  .query(Long.class)
                  .single())
          .isEqualTo(1);
      assertThatThrownBy(
              () -> service.upload(actor, requestId, second, Files.size(second), "image/png"))
          .isInstanceOf(ApiException.class)
          .extracting(exception -> ((ApiException) exception).errorCode().code())
          .isEqualTo("IDEMPOTENCY_KEY_REUSED");
    } finally {
      Files.deleteIfExists(first);
      Files.deleteIfExists(second);
    }
  }

  @Test
  void verificationNormalizesUploadedImageAndMarksAssetReady() throws Exception {
    UUID actor = account();
    Path source = image(0x123456);
    try {
      UUID assetId =
          (UUID)
              service
                  .upload(actor, UUID.randomUUID(), source, Files.size(source), "image/png")
                  .get("id");
      String stagingKey =
          jdbc.sql("SELECT staging_object_key FROM media_asset WHERE public_id=:id")
              .param("id", assetId)
              .query(String.class)
              .single();
      when(storage.get(stagingKey)).thenReturn(Files.readAllBytes(source));

      verificationWorker.run();

      assertThat(service.status(actor, assetId))
          .containsEntry("status", "READY")
          .containsEntry("detectedMediaType", "image/png")
          .containsEntry("width", 2)
          .containsEntry("height", 2);
      verify(storage)
          .put(eq("delivery/" + assetId + "/image.png"), any(byte[].class), eq("image/png"));
    } finally {
      Files.deleteIfExists(source);
    }
  }

  @Test
  void webpIsRejectedBeforeReservation() throws Exception {
    UUID actor = account();
    Path source = image(0x123456);
    try {
      assertThatThrownBy(
              () ->
                  service.upload(
                      actor, UUID.randomUUID(), source, Files.size(source), "image/webp"))
          .isInstanceOf(ApiException.class)
          .extracting(exception -> ((ApiException) exception).errorCode().code())
          .isEqualTo("INVALID_IMAGE");
      assertThat(
              jdbc.sql("SELECT COUNT(*) FROM media_asset WHERE owner_account_public_id=:actor")
                  .param("actor", actor)
                  .query(Long.class)
                  .single())
          .isZero();
    } finally {
      Files.deleteIfExists(source);
    }
  }

  private UUID account() {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            "INSERT INTO account(public_id,email,email_normalized,status,email_verified_at) VALUES(:id,:email,:email,'ACTIVE',NOW())")
        .param("id", id)
        .param("email", id + "@example.com")
        .update();
    return id;
  }

  private Path image(int rgb) throws Exception {
    Path file = Files.createTempFile("media-upload-test-", ".png");
    var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
    image.setRGB(0, 0, rgb);
    ImageIO.write(image, "png", file.toFile());
    return file;
  }
}
