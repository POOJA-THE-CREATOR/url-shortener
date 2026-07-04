package com.pooja.urlshortener;

import com.pooja.urlshortener.dto.ShortenRequest;
import com.pooja.urlshortener.dto.ShortenResponse;
import com.pooja.urlshortener.model.UrlMapping;
import com.pooja.urlshortener.service.UrlShortenerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
class UrlShortenerServiceTest {

    @Autowired
    private UrlShortenerService service;

    @Test
    void shortenThenResolveReturnsOriginalUrl() {
        ShortenRequest request = new ShortenRequest("https://example.com/some/very/long/path", null);

        ShortenResponse response = service.shorten(request);
        assertNotNull(response.shortCode());

        UrlMapping resolved = service.resolve(response.shortCode());
        assertEquals("https://example.com/some/very/long/path", resolved.getLongUrl());
    }

    @Test
    void differentUrlsProduceDifferentShortCodes() {
        ShortenResponse first = service.shorten(new ShortenRequest("https://example.com/a", null));
        ShortenResponse second = service.shorten(new ShortenRequest("https://example.com/b", null));

        assertNotEquals(first.shortCode(), second.shortCode());
    }
}
