package com.ronams.carmunity.community.internal.adapter.in.web.protection;

import com.ronams.carmunity.media.api.MediaAvailability;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@ConditionalOnProperty(prefix = "carmunity.community", name = "enabled", havingValue = "true")
public class CommunityDependencyAvailabilityFilter extends OncePerRequestFilter {
  private final MediaAvailability media;

  public CommunityDependencyAvailabilityFilter(MediaAvailability media) {
    this.media = media;
  }

  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return !path.startsWith("/api/v1/boards/")
        && !path.startsWith("/api/v1/posts/")
        && !path.startsWith("/api/v1/comments/")
        && !path.startsWith("/api/v1/media/")
        && !path.startsWith("/api/v1/notifications")
        && !path.startsWith("/api/v1/admin/community/");
  }

  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!media.isReady()) {
      response.setStatus(503);
      response.setContentType("application/problem+json");
      response
          .getWriter()
          .write(
              "{\"title\":\"Community unavailable\",\"status\":503,\"code\":\"COMMUNITY_CAPABILITY_UNAVAILABLE\"}");
      return;
    }
    chain.doFilter(request, response);
  }
}
