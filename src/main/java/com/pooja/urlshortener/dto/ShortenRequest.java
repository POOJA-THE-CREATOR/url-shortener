package com.pooja.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ShortenRequest(
        @NotBlank(message = "longUrl must not be blank")
        @Pattern(regexp = "^(https?)://.+", message = "longUrl must start with http:// or https://")
        String longUrl,

        // Optional: caller can request the link expire after N minutes
        Long ttlMinutes
) {
}
