# Redis — Learnings

## What is Redis?
- An in-memory key-value store that lives outside your application
- Like a giant HashMap, but as a separate server
- Super fast because everything is in memory (microseconds vs milliseconds for DB)
- Default port: 6379
- Check if running: `redis-cli ping` → should return `PONG`

## Why Redis for Caching?
- Our URL shortener is read-heavy (100:1 read-to-write ratio)
- The short → long URL mapping never changes once created (static data)
- Hitting the DB thousands of times for the same unchanging data is wasteful
- Redis sits between app and DB, serves repeated reads from memory

## Cache-Aside Pattern (Lazy Loading)
1. Request comes in → check Redis first
2. Cache hit → return immediately (no DB call)
3. Cache miss → query DB → store result in Redis → return

## Spring Boot + Redis Setup

### Dependency
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

### RedisConnectionFactory
- Spring Boot auto-creates this bean based on your application.yaml config
- Holds connection details: host, port, password
- You don't create it — just inject it where needed

### RedisTemplate vs StringRedisTemplate
- `RedisTemplate<Object, Object>` — default, uses JdkSerializationRedisSerializer (slow, bloated)
- `StringRedisTemplate` — preconfigured for String keys and String values, fast and simple
- For our use case (shortId → longUrl), StringRedisTemplate is the right choice
- Spring Boot auto-creates StringRedisTemplate when `spring-boot-starter-data-redis` is on classpath
- No custom @Configuration/@Bean class needed — just inject it via constructor

### Other Spring Data Templates
- `RedisTemplate` — generic Redis operations (key-value, any types)
- `StringRedisTemplate` — Redis but optimized for String-String (what we use)
- `JdbcTemplate` — raw SQL queries (when JPA/Hibernate is overkill)
- `MongoTemplate` — MongoDB operations
- `RestTemplate` — HTTP calls to other services (deprecated, use WebClient now)
- `WebClient` — non-blocking HTTP client (replacement for RestTemplate)
- `KafkaTemplate` — sending messages to Kafka topics
- `RabbitTemplate` — sending messages to RabbitMQ queues

Pattern: Spring uses the "Template" pattern a lot — each one wraps a technology and gives you a clean, consistent API to work with it.

### What to keep in application.yaml
```yaml
spring:
  data:
    redis:
      host: localhost    # where Redis runs (default: localhost)
      port: 6379         # Redis port (default: 6379)
```
Keep this — in production Redis won't be on localhost. Even though defaults match, explicit config is good practice.

### Basic operations
```java
// Write: key = shortId, value = longUrl
redisTemplate.opsForValue().set("abc123", "https://google.com");

// Read: returns longUrl or null if not in cache
String longUrl = redisTemplate.opsForValue().get("abc123");
```

- `opsForValue()` = simple key → value operations
- Redis also supports lists, sets, hashes — but we just need key-value here