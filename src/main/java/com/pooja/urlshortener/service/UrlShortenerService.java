package com.pooja.urlshortener.service;

import com.pooja.urlshortener.dto.ShortenRequest;
import com.pooja.urlshortener.dto.ShortenResponse;
import com.pooja.urlshortener.exception.ShortUrlNotFoundException;
import com.pooja.urlshortener.exception.UrlExpiredException;
import com.pooja.urlshortener.model.UrlMapping;
import com.pooja.urlshortener.repository.UrlMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UrlShortenerService {

    private final UrlMappingRepository repository;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    /**
     * Two-step write: insert first to obtain the DB-assigned ID, then
     * derive the short code from that ID and persist it. This avoids
     * needing a separate distributed ID generator (Snowflake, Redis
     * INCR, etc.) — Postgres's own auto-increment already gives us a
     * globally unique, monotonic counter.
     */
    @Transactional
    public ShortenResponse shorten(ShortenRequest request) {
        UrlMapping mapping = new UrlMapping();
        mapping.setLongUrl(request.longUrl());
        if (request.ttlMinutes() != null) {
            mapping.setExpiresAt(Instant.now().plusSeconds(request.ttlMinutes() * 60));
        }

        UrlMapping saved = repository.save(mapping);
        String shortCode = Base62Encoder.encode(saved.getId());
        saved.setShortCode(shortCode);
        repository.save(saved);

        return toResponse(saved);
    }

    /**
     * Cache-aside read. On a cache hit this never touches Postgres.
     * The click-count increment is intentionally NOT part of the cached
     * value — it's a separate atomic DB update — so cached reads stay
     * fast and consistent while counts still update correctly.
     */
    @Cacheable(value = "urlCache", key = "#shortCode")
    public UrlMapping resolve(String shortCode) {
        UrlMapping mapping = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortUrlNotFoundException(shortCode));

        if (mapping.getExpiresAt() != null && mapping.getExpiresAt().isBefore(Instant.now())) {
            throw new UrlExpiredException(shortCode);
        }
        return mapping;
    }

    @Transactional
    public void recordClick(String shortCode) {
        repository.incrementClickCount(shortCode);
    }

    @CacheEvict(value = "urlCache", key = "#shortCode")
    public void evict(String shortCode) {
        // Exposed so an admin/delete endpoint can invalidate a stale cache entry.
    }

    private ShortenResponse toResponse(UrlMapping mapping) {
        return new ShortenResponse(
                mapping.getShortCode(),
                baseUrl + "/" + mapping.getShortCode(),
                mapping.getLongUrl(),
                mapping.getExpiresAt()
        );
    }
}
