package com.pooja.urlshortener.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Persistent record mapping a short code to its original long URL.
 * The primary key (id) is a monotonically increasing long, which is
 * base62-encoded to produce the short code — this keeps generation
 * O(1) with no coordination needed across instances (no distributed
 * counter/lock required), which matters once this runs on more than
 * one replica.
 */
@Entity
@Table(name = "url_mapping", indexes = {
        @Index(name = "idx_short_code", columnList = "shortCode", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class UrlMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2048)
    private String longUrl;

    @Column(unique = true, length = 16)
    private String shortCode;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column
    private Instant expiresAt;

    @Column(nullable = false)
    private long clickCount = 0L;
}
