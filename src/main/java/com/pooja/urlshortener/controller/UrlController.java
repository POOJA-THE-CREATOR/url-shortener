package com.pooja.urlshortener.controller;

import com.pooja.urlshortener.dto.ShortenRequest;
import com.pooja.urlshortener.dto.ShortenResponse;
import com.pooja.urlshortener.model.UrlMapping;
import com.pooja.urlshortener.service.UrlShortenerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class UrlController {

    private final UrlShortenerService service;

    @PostMapping("/api/v1/shorten")
    public ResponseEntity<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest request) {
        ShortenResponse response = service.shorten(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Redirect endpoint. This is the hot path — the one that needs to be
     * fast and needs the cache. A 302 (not 301) is used deliberately:
     * a permanent redirect would let browsers cache it forever, which
     * would make click counts and expiry invisible to us.
     */
    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        UrlMapping mapping = service.resolve(shortCode);
        service.recordClick(shortCode);

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, mapping.getLongUrl())
                .build();
    }

    @GetMapping("/api/v1/{shortCode}/info")
    public ResponseEntity<UrlMapping> info(@PathVariable String shortCode) {
        return ResponseEntity.ok(service.resolve(shortCode));
    }
}
