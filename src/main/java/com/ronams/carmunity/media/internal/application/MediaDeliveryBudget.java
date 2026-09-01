package com.ronams.carmunity.media.internal.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "carmunity.community", name = "enabled", havingValue = "true")
public class MediaDeliveryBudget {
  private static final double CAPACITY = 64.0 * 1024 * 1024;
  private double available = CAPACITY;
  private long lastRefill = System.nanoTime();

  public synchronized boolean allow(int bytes) {
    long now = System.nanoTime();
    available =
        Math.min(CAPACITY, available + (now - lastRefill) / 1_000_000_000.0 * CAPACITY / 60.0);
    lastRefill = now;
    if (bytes < 0 || available < bytes) return false;
    available -= bytes;
    return true;
  }
}
