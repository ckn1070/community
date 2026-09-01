package com.ronams.carmunity.media.internal.application;

import java.nio.file.Path;

public interface MediaObjectStorage {
  void put(String key, byte[] bytes, String mediaType);

  void put(String key, Path file, long byteSize, String mediaType);

  byte[] get(String key);

  void delete(String key);

  boolean exists(String key);
}
