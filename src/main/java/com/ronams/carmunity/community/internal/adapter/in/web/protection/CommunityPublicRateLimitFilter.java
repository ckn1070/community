package com.ronams.carmunity.community.internal.adapter.in.web.protection;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@ConditionalOnProperty(prefix = "carmunity.community", name = "enabled", havingValue = "true")
public class CommunityPublicRateLimitFilter extends OncePerRequestFilter {
  private static final int MAX_CLIENTS = 10_000;
  private static final long IDLE_NANOS = Duration.ofMinutes(10).toNanos();
  private static final Set<String> PUBLIC_PREFIXES =
      Set.of("/api/v1/boards/", "/api/v1/posts/", "/api/v1/comments/", "/api/v1/media/images/");
  private final ConcurrentHashMap<String, BucketPair> clients = new ConcurrentHashMap<>();
  private final Semaphore imageStreams = new Semaphore(8);
  private final MeterRegistry meters;
  private final List<IpAddressMatcher> trustedProxies;
  private final AtomicLong lastCleanup = new AtomicLong();

  public CommunityPublicRateLimitFilter(
      MeterRegistry meters,
      @Value("${carmunity.community.trusted-proxy-cidrs:}") String trustedProxyCidrs) {
    this.meters = meters;
    this.trustedProxies =
        trustedProxyCidrs
            .lines()
            .flatMap(line -> java.util.Arrays.stream(line.split(",")))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .map(IpAddressMatcher::new)
            .toList();
    meters.gauge("community.limiter.clients", clients, ConcurrentHashMap::size);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !"GET".equals(request.getMethod())
        || PUBLIC_PREFIXES.stream().noneMatch(request.getRequestURI()::startsWith);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    boolean image = request.getRequestURI().endsWith("/content");
    String key = trustedClientKey(request);
    long now = System.nanoTime();
    cleanup(now);
    BucketPair buckets = clients.get(key);
    if (buckets == null && clients.size() >= MAX_CLIENTS) {
      reject(response, "capacity");
      return;
    }
    buckets = clients.computeIfAbsent(key, ignored -> new BucketPair(now));
    if (!(image ? buckets.image.tryConsume(now) : buckets.general.tryConsume(now))) {
      reject(response, "rate");
      return;
    }
    if (image && !imageStreams.tryAcquire()) {
      reject(response, "concurrency");
      return;
    }
    try {
      chain.doFilter(request, response);
    } finally {
      buckets.lastSeen = System.nanoTime();
      if (image) imageStreams.release();
    }
  }

  private String trustedClientKey(HttpServletRequest request) {
    String peer = request.getRemoteAddr();
    String forwarded = request.getHeader("X-Forwarded-For");
    if (!isTrusted(peer) || forwarded == null || forwarded.isBlank()) return peer;
    List<String> chain = new java.util.ArrayList<>(List.of(forwarded.split(",")));
    for (int index = chain.size() - 1; index >= 0; index--) {
      String hop = chain.get(index).trim();
      if (!isTrusted(hop)) return hop;
    }
    return peer;
  }

  private boolean isTrusted(String address) {
    return trustedProxies.stream().anyMatch(matcher -> matcher.matches(address));
  }

  private void cleanup(long now) {
    long previous = lastCleanup.get();
    if (now - previous < IDLE_NANOS || !lastCleanup.compareAndSet(previous, now)) return;
    clients.entrySet().removeIf(entry -> now - entry.getValue().lastSeen > IDLE_NANOS);
  }

  private void reject(HttpServletResponse response, String reason) throws IOException {
    meters.counter("community.limiter.rejected", "reason", reason).increment();
    response.setStatus(429);
    response.setHeader("Retry-After", "1");
    response.setContentType("application/problem+json");
    response
        .getWriter()
        .write(
            "{\"title\":\"Too Many Requests\",\"status\":429,\"code\":\"COMMUNITY_RATE_LIMITED\"}");
  }

  private static final class BucketPair {
    private final Bucket general = new Bucket(20, 1.0);
    private final Bucket image = new Bucket(4, 1.0 / 6.0);
    private volatile long lastSeen;

    private BucketPair(long now) {
      lastSeen = now;
      general.last = now;
      image.last = now;
    }
  }

  private static final class Bucket {
    private final double capacity, perSecond;
    private double tokens;
    private long last;

    private Bucket(double capacity, double perSecond) {
      this.capacity = capacity;
      this.perSecond = perSecond;
      tokens = capacity;
    }

    private synchronized boolean tryConsume(long now) {
      tokens = Math.min(capacity, tokens + (now - last) / 1_000_000_000.0 * perSecond);
      last = now;
      if (tokens < 1) return false;
      tokens--;
      return true;
    }
  }
}
