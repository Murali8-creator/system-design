package com.url_shortener.dto.request;

import jakarta.validation.constraints.NotBlank;

public record URLShortenerRequest(@NotBlank String URL) {
}
