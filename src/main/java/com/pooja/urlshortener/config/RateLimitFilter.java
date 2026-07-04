package com.pooja.urlshortener.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-client token bucket rate limiter.
 *
 * NOTE ON SCOPE: this in-memory version buckets per-instance, which is fine
 * for a single container but NOT correct once you run more than one replica
 * (each instance would allow the full quota independently, so N replicas =
 * N times the intended limit). The fix for production is to back this with
 * Redis (e.g. bucket4j-redis + Lettuce) so all instances share one counter —
 * that's the "distributed" part of distributed rate limiting. Left as an
 * in-memory bucket here to keep the demo runnable with zero extra
 * infrastructure; swap `buckets` for a RedisProxyManager-backed bucket to
 * make it correct at scale.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int CAPACITY = 20;
    private static final Duration REFILL_PERIOD = Duration.ofMinutes(1);

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        String clientKey = resolveClientKey(request);
        Bucket bucket = buckets.computeIfAbsent(clientKey, k -> newBucket());

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429); // Too Many Requests
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"rate_limit_exceeded\",\"message\":\"Too many requests. Limit: "
                            + CAPACITY + " per minute.\"}"
            );
        }
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(CAPACITY, Refill.greedy(CAPACITY, REFILL_PERIOD));
        return Bucket.builder().addLimit(limit).build();
    }

    private String resolveClientKey(HttpServletRequest request) {
        // Prefer an API key header if present; fall back to remote IP.
        // In front of a real load balancer, read X-Forwarded-For instead.
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey;
        }
        return request.getRemoteAddr();
    }
}
