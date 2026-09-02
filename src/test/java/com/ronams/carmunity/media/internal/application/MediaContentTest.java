package com.ronams.carmunity.media.internal.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MediaContentTest {

  @Test
  void comparesByteContentAndMediaTypeByValue() {
    var first = new MediaService.MediaContent(new byte[] {1, 2, 3}, "image/png");
    var same = new MediaService.MediaContent(new byte[] {1, 2, 3}, "image/png");
    var differentBytes = new MediaService.MediaContent(new byte[] {1, 2, 4}, "image/png");
    var differentType = new MediaService.MediaContent(new byte[] {1, 2, 3}, "image/jpeg");

    assertThat(first)
        .isEqualTo(same)
        .hasSameHashCodeAs(same)
        .isNotEqualTo(differentBytes)
        .isNotEqualTo(differentType)
        .isNotEqualTo("not media content")
        .hasToString("MediaContent[bytes=3 bytes, mediaType=image/png]");
  }
}
