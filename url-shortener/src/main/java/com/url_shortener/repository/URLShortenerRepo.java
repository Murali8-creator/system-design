package com.url_shortener.repository;

import com.url_shortener.entity.URLShortener;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface URLShortenerRepo extends JpaRepository<URLShortener, UUID> {

    public Optional<URLShortener> findByShortId(String shortId);
}
