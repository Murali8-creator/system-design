package com.url_shortener.service;

import com.url_shortener.entity.URLShortener;
import com.url_shortener.repository.URLShortenerRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class URLShortenerService {

    private static final Logger log = LoggerFactory.getLogger(URLShortenerService.class);

    public URLShortenerService(URLShortenerRepo urlShortenerRepo, StringRedisTemplate redisTemplate) {
        this.urlShortenerRepo = urlShortenerRepo;
        this.redisTemplate = redisTemplate;
    }

    private final URLShortenerRepo urlShortenerRepo;
    private final RedisTemplate redisTemplate;

    public String shortenURL(String url){
        String shortId = toBase62(redisTemplate.opsForValue().increment("url-counter"));

        URLShortener urlShortener = new URLShortener();
        urlShortener.setLongUrl(url);
        urlShortener.setShortId(shortId);
        urlShortener.setCreatedAt(Instant.now());

        urlShortenerRepo.save(urlShortener);
        return shortId;
    }

    private String toBase62(Long number) {
        String characters = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder();

        while (number > 0) {
            long rem = number % 62;
            sb.append(characters.charAt((int)rem));
            number = number / 62;
        }
        return sb.reverse().toString();
    }

    public String getLongURL(String shortId){
        String longUrl = (String) redisTemplate.opsForValue().get(shortId);
        if(longUrl != null) {
            log.info("Cache HIT!!");
            return longUrl;
        }
        log.info("Cache MISS!!");
        longUrl = urlShortenerRepo.findByShortId(shortId).get().getLongUrl();
        redisTemplate.opsForValue().set(shortId, longUrl);
        return longUrl;
    }
}
