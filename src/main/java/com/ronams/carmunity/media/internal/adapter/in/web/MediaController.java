package com.ronams.carmunity.media.internal.adapter.in.web;

import com.ronams.carmunity.media.internal.application.MediaDeliveryBudget;
import com.ronams.carmunity.media.internal.application.MediaErrorCode;
import com.ronams.carmunity.media.internal.application.MediaService;
import com.ronams.carmunity.shared.error.ApiException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/media/images")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "carmunity.community", name = "enabled", havingValue = "true")
public class MediaController {
  private final MediaService service;
  private final MediaDeliveryBudget deliveryBudget;

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Map<String, Object>> upload(
      @RequestHeader("Idempotency-Key") UUID key,
      @RequestPart("file") MultipartFile file,
      Principal principal)
      throws java.io.IOException {
    Path temporaryDirectory =
        Files.createTempDirectory(
            "community-upload-",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
    try {
      Path temporary =
          Files.createFile(
              temporaryDirectory.resolve("payload.bin"),
              PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
      try {
        try (var input = file.getInputStream()) {
          Files.copy(input, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return ResponseEntity.accepted()
            .body(
                service.upload(
                    actor(principal),
                    key,
                    temporary,
                    Files.size(temporary),
                    file.getContentType()));
      } finally {
        Files.deleteIfExists(temporary);
      }
    } finally {
      Files.deleteIfExists(temporaryDirectory);
    }
  }

  @GetMapping("/{mediaId}")
  public Map<String, Object> status(@PathVariable UUID mediaId, Principal principal) {
    return service.status(actor(principal), mediaId);
  }

  @GetMapping("/{mediaId}/content")
  public ResponseEntity<byte[]> content(@PathVariable UUID mediaId) {
    MediaService.MediaContent content = service.content(mediaId);
    if (!deliveryBudget.allow(content.bytes().length))
      throw new ApiException(MediaErrorCode.DELIVERY_LIMITED);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(content.mediaType()))
        .cacheControl(org.springframework.http.CacheControl.noStore())
        .header("Content-Length", Integer.toString(content.bytes().length))
        .body(content.bytes());
  }

  private UUID actor(Principal principal) {
    return UUID.fromString(principal.getName());
  }
}
