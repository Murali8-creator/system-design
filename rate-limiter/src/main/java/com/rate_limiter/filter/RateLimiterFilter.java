package com.rate_limiter.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class RateLimiterFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterFilter.class);
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DefaultRedisScript<Long> rateLimitScript;

    private static final int LIMIT = 5;
    private static final long REFILL_INTERVAL = 12000; // 12 seconds in milliseconds

    public RateLimiterFilter(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.rateLimitScript = new DefaultRedisScript<>();
        this.rateLimitScript.setLocation(new ClassPathResource("scripts/rate-limiter.lua"));
        this.rateLimitScript.setResultType(Long.class);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        String key = "bucket:"+request.getRemoteAddr();
        Long result = stringRedisTemplate.execute(this.rateLimitScript, List.of(key),
                String.valueOf(LIMIT), String.valueOf(REFILL_INTERVAL), String.valueOf(System.currentTimeMillis()));
        if (result == 1)
            chain.doFilter(request, response);
        else
            handleRateLimitException((HttpServletResponse) response);
    }

    private void handleRateLimitException(
            HttpServletResponse response)
            throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("error", "RATE_LIMIT");
        errorDetails.put("message", "Limit Exceeded");
        errorDetails.put("timestamp", System.currentTimeMillis());
        errorDetails.put("status", 429);

        response.getWriter().write(objectMapper.writeValueAsString(errorDetails));
    }
}
