package com.url_shortener.controller;

import com.url_shortener.dto.request.URLShortenerRequest;
import com.url_shortener.dto.response.URLShortenerResponse;
import com.url_shortener.service.URLShortenerService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

@RestController
@RequestMapping("/api/v1/url-shortener")
public class URLShortenerController {

    URLShortenerService urlShortenerService;

    public URLShortenerController(URLShortenerService urlShortenerService) {
        this.urlShortenerService = urlShortenerService;
    }

    @PostMapping("/shorten")
    public String shortenURL(@RequestBody @Valid  URLShortenerRequest request) {
        return urlShortenerService.shortenURL(request.URL());
    }

    @GetMapping("/{shortId}")
    public RedirectView getLongURL(@PathVariable String shortId){
//        return urlShortenerService.getLongURL(shortId);

        return new RedirectView(urlShortenerService.getLongURL(shortId));
    }
}
