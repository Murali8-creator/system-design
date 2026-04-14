# URL Shortener — System Design Learnings

## What We're Building
A service where: user gives a long URL → gets a short URL. Someone visits the short URL → gets redirected.

## Design Thinking — Ask These First
For any system design problem, always start with:
1. What are the core features? (shorten + redirect)
2. Who uses it and how much? (read-heavy: 100:1 read-to-write ratio)
3. What does that usage pattern tell you about where to focus?

## Brute Force Design
- POST /shorten → generate short ID, store in DB, return short ID
- GET /{shortId} → look up in DB, redirect (HTTP 302)
- Short ID generation: UUID.randomUUID() → take first 7 chars
- Storage: single table (id, short_id, long_url, created_at)

## Problems Discovered

### 1. Short ID Collision (FIXED)
- Taking 7 chars from UUID = 16^7 = ~268 million possible values
- Birthday problem: collisions happen much sooner than you'd expect
- Without unique constraint on short_id, duplicate rows get inserted
- findByShortId returns multiple results → 500 error
- Fix applied: unique constraint on short_id + check-before-insert loop
- DB throws `DataIntegrityViolationException` (Spring wrapper) on duplicate insert
  - Underlying: `JdbcSQLIntegrityConstraintViolationException` (H2-specific)
  - Always catch the Spring exception, not the DB-specific one — makes your code DB-agnostic
- Tried catch-and-retry on save() — doesn't work because Hibernate session is broken after DataIntegrityViolationException. Can't reuse the same transaction.
- Working approach: check if shortId exists before saving (loop until unique)
- Deeper fix: replaced UUID with Base62 counter (see below)

### 4. Better ID Generation — Base62 Counter (FIXED)
- UUID approach: random, only hex chars (0-9, a-f), 16^7 = ~268M combinations for 7 chars
- Base62 approach: uses 0-9, a-z, A-Z = 62 chars per position, 62^7 = ~3.5 trillion combinations
- Same short URL length, massively more IDs, zero collisions
- How it works: keep an incrementing counter, convert each number to base62
  - Base62 conversion: divide by 62, take remainder as character, repeat (same as decimal → binary)
- Counter must survive app restarts — stored in Redis via `increment("url-counter")`
  - `increment` on non-existent key auto-creates it starting from 0 → returns 1
  - Atomic operation — thread-safe, no race conditions
- With a counter, collisions are impossible — no need for collision check loop
- AtomicInteger (in-memory counter) doesn't work — resets on restart

### 2. Database Bottleneck Under Load (FIXED)
- Every redirect = 1 DB query
- 10,000 redirects/sec = 10,000 DB queries/sec
- The mapping (short → long URL) never changes — it's static data
- Reading the same unchanging row thousands of times = waste
- Fix applied: Redis cache using cache-aside pattern
  - Request → check Redis → hit? return. miss? query DB → store in Redis → return
- Use `StringRedisTemplate` not raw `RedisTemplate` — default JDK serializer is slow and bloated
- No custom RedisConfig needed with StringRedisTemplate — Spring Boot auto-creates it
- Performance gotcha: when testing redirect endpoints, disable "Automatically follow redirects" in Postman — otherwise you're measuring the target site's load time, not your app's

### 3. Slow Lookups at Scale (Billion URLs)
- Without an index on short_id, DB does a full table scan
- With a billion rows, that's extremely slow
- Fix progression: index → cache → sharding (much later)
- Unique constraint on short_id automatically creates an index

### 5. Scaling — Multiple App Servers
- Redis `increment` is atomic — multiple servers calling it simultaneously get unique numbers
- Single Redis handles ~100K+ ops/sec — counter won't bottleneck easily
- At very high scale: range-based allocation (each server reserves a batch of IDs, uses locally)

## Architecture Evolution
```
Brute force:   Client → Spring Boot → DB
Final:         Client → Spring Boot → Redis (cache + counter) → DB (persistence)
```

## Spring Boot Notes
- @Entity comes from jakarta.persistence, not Spring
- @CreatedDate needs Spring Data auditing enabled, or set manually
- JpaRepository<EntityType, IdType> — entity first, ID second
- Spring Data auto-generates queries from method names (findByShortId)
- RedirectView handles HTTP redirects (302)
- StringRedisTemplate over RedisTemplate for String key-value ops
- Spring Boot auto-configures Redis beans when starter-data-redis is on classpath
