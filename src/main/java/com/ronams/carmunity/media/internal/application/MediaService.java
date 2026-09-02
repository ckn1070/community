package com.ronams.carmunity.media.internal.application;

import com.ronams.carmunity.shared.error.ApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "carmunity.community", name = "enabled", havingValue = "true")
public class MediaService {
  private static final int MAX_BYTES = 10 * 1024 * 1024;
  private static final String GIF_MEDIA_TYPE = "image/gif";
  private static final String JPEG_MEDIA_TYPE = "image/jpeg";
  private static final String PNG_MEDIA_TYPE = "image/png";
  private static final Set<String> SUPPORTED_MEDIA_TYPES =
      Set.of(JPEG_MEDIA_TYPE, PNG_MEDIA_TYPE, GIF_MEDIA_TYPE);
  private final JdbcClient jdbc;
  private final MediaObjectStorage storage;
  private final MediaAssetReservationStore reservations;

  public Map<String, Object> upload(
      UUID actor, UUID key, Path file, long byteSize, String declaredType) {
    if (byteSize == 0 || byteSize > MAX_BYTES)
      throw new ApiException(MediaErrorCode.IMAGE_TOO_LARGE);
    validateSignature(file, declaredType);
    String checksum = sha(file);
    String requestHash = sha(declaredType + ":" + checksum);
    MediaAssetReservationStore.Reservation reservation =
        reservations.reserve(actor, key, requestHash, declaredType, byteSize, checksum);
    if (reservation.uploadRequired()) {
      storage.put(reservation.stagingKey(), file, byteSize, declaredType);
      reservations.uploaded(reservation.assetId());
    }
    return status(actor, reservation.assetId());
  }

  public Map<String, Object> status(UUID actor, UUID id) {
    Map<String, Object> row =
        jdbc
            .sql(
                "SELECT public_id AS id,status,detected_media_type,width,height,created_at,version FROM media_asset WHERE public_id=:id AND owner_account_public_id=:actor")
            .param("id", id)
            .param("actor", actor)
            .query()
            .listOfRows()
            .stream()
            .findFirst()
            .orElseThrow(() -> new ApiException(MediaErrorCode.MEDIA_NOT_FOUND));
    return camelCase(row);
  }

  public MediaContent content(UUID id) {
    Map<String, Object> row =
        jdbc
            .sql(
                "SELECT a.delivery_object_key,a.detected_media_type FROM media_asset a JOIN media_usage u ON u.asset_id=a.id WHERE a.public_id=:id AND a.status='READY' AND u.status='ACTIVE'")
            .param("id", id)
            .query()
            .listOfRows()
            .stream()
            .findFirst()
            .orElseThrow(() -> new ApiException(MediaErrorCode.MEDIA_NOT_FOUND));
    byte[] bytes = storage.get((String) row.get("delivery_object_key"));
    if (bytes.length == 0) throw new ApiException(MediaErrorCode.MEDIA_NOT_FOUND);
    return new MediaContent(bytes, (String) row.get("detected_media_type"));
  }

  private void validateSignature(Path file, String type) {
    if (type == null || !SUPPORTED_MEDIA_TYPES.contains(type)) {
      throw new ApiException(MediaErrorCode.INVALID_IMAGE);
    }
    try {
      try (var input = ImageIO.createImageInputStream(file.toFile())) {
        var readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) {
          throw new ApiException(MediaErrorCode.INVALID_IMAGE);
        }
        var reader = readers.next();
        reader.setInput(input, true, true);
        String detected = mediaType(reader.getFormatName().toLowerCase(Locale.ROOT));
        if (!type.equals(detected)
            || (GIF_MEDIA_TYPE.equals(detected) && reader.getNumImages(true) != 1)) {
          throw new ApiException(MediaErrorCode.INVALID_IMAGE);
        }
        var image = reader.read(0);
        reader.dispose();
        if (image == null || (long) image.getWidth() * image.getHeight() > 24_000_000L) {
          throw new ApiException(MediaErrorCode.INVALID_IMAGE);
        }
      }
    } catch (java.io.IOException e) {
      throw new ApiException(MediaErrorCode.INVALID_IMAGE, e);
    }
  }

  private String mediaType(String format) {
    if ("jpeg".equals(format) || "jpg".equals(format)) {
      return JPEG_MEDIA_TYPE;
    }
    if ("png".equals(format)) {
      return PNG_MEDIA_TYPE;
    }
    if ("gif".equals(format)) {
      return GIF_MEDIA_TYPE;
    }
    return "";
  }

  private String sha(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private String sha(Path file) {
    try (var input = Files.newInputStream(file)) {
      var digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[8192];
      for (int read; (read = input.read(buffer)) >= 0; ) {
        if (read > 0) digest.update(buffer, 0, read);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (IOException | NoSuchAlgorithmException exception) {
      throw new IllegalStateException("업로드 이미지 checksum을 계산하지 못했습니다.", exception);
    }
  }

  private String sha(String value) {
    return sha(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private Map<String, Object> camelCase(Map<String, Object> row) {
    Map<String, Object> result = new java.util.LinkedHashMap<>();
    row.forEach(
        (key, value) -> {
          String[] parts = key.split("_");
          StringBuilder name = new StringBuilder(parts[0]);
          for (int index = 1; index < parts.length; index++) {
            name.append(Character.toUpperCase(parts[index].charAt(0)))
                .append(parts[index].substring(1));
          }
          result.put(name.toString(), value);
        });
    return result;
  }

  public record MediaContent(byte[] bytes, String mediaType) {
    @Override
    public boolean equals(Object candidate) {
      return candidate instanceof MediaContent other
          && java.util.Arrays.equals(bytes, other.bytes)
          && java.util.Objects.equals(mediaType, other.mediaType);
    }

    @Override
    public int hashCode() {
      return 31 * java.util.Arrays.hashCode(bytes) + java.util.Objects.hashCode(mediaType);
    }

    @Override
    public String toString() {
      return "MediaContent[bytes=" + bytes.length + " bytes, mediaType=" + mediaType + "]";
    }
  }
}
