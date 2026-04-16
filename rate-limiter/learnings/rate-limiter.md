# Rate Limiter — System Design Learnings

## What We're Building
A component that limits how many requests a user can make in a given time period. Protects services from abuse, overload, and ensures fair usage.

## Where Rate Limiting Lives
- Implemented as a **Filter** (not Interceptor)
- Filter runs at the Servlet level — before Spring even touches the request
- If a user is over their limit, reject immediately with 429 — don't waste resources routing through Spring MVC

## Approach 1: Fixed Window Counter (Brute Force)
- Pick a time window (e.g., 1 minute)
- Redis key: `rate:{ip}:{current_minute}` → value: request count
- `increment(key)` on each request, `expire(key, 60s)` on first request (count == 1)
- If count > limit → reject (429)

### Problem: Boundary Burst
- User sends 100 requests at 11:00:58 (allowed — within window)
- User sends 100 more at 11:01:01 (allowed — new window, counter reset)
- Result: 200 requests in 3 seconds with a "100 per minute" limit
- The counter resets all at once at the window boundary

## Approach 2: Token Bucket (Production-Grade)
Used by AWS, Stripe, and most real systems.

### Concept
- Each user has a "bucket" that holds tokens (max = capacity)
- Each request consumes 1 token
- Tokens refill at a fixed rate over time
- Bucket empty → reject (429)
- Allows short bursts (up to capacity) but enforces average rate

### Configuration
- `LIMIT = 5` (bucket capacity)
- `REFILL_INTERVAL = 12000ms` (1 token every 12 seconds = 5 per minute)

### Algorithm
1. Check if user exists in Redis → if not, create with full tokens + current timestamp
2. Read `lastRefill` timestamp, calculate elapsed time
3. Calculate `tokensToAdd = elapsed / refillInterval`
4. If tokensToAdd > 0 → add tokens (capped at LIMIT), update lastRefill
5. If tokens > 0 → decrement, allow request
6. If tokens == 0 → reject 429

### Redis Storage
Uses Redis Hash — two fields under one key:
```
Key: "bucket:192.168.1.1"
  ├── "tokens" → "5"
  └── "lastRefill" → "1713200000000"
```
- `opsForHash().put(key, field, value)` to write
- `opsForHash().get(key, field)` to read
- All values stored as Strings with StringRedisTemplate

### Why Token Bucket > Fixed Window
- No boundary burst — tokens refill gradually, not all at once
- Allows controlled bursts (up to bucket capacity)
- Smooth, predictable traffic flow
- Industry standard

## Problem: Race Condition (Discovered & Fixed)

### The Problem
The Java implementation makes multiple Redis calls per request:
`hasKey → get tokens → get lastRefill → put tokens → put lastRefill`

When concurrent requests arrive, threads interleave between these calls:
```
Thread A: reads tokens = 5
Thread B: reads tokens = 5  (before A could decrement)
Thread A: decrements to 4, allows request
Thread B: decrements to 4, allows request  (should have seen 4, not 5)
```

### How We Proved It
- Sent 20 concurrent requests with a limit of 5
- **Without fix:** 13-20 requests got through (all 20 in worst case)
- Added logging to `hasKey` check: 7 threads entered bucket creation simultaneously
  - All within 10ms — classic check-then-act race condition
  - Each reset the bucket to 5 tokens, allowing far more requests than the limit

### Why `synchronized` Doesn't Work
- `synchronized` locks within **one JVM** only
- Multiple servers behind a load balancer = multiple JVMs = multiple independent locks
- Even on a single server: blocks all threads on every request — performance bottleneck

### Why Double-Checked Locking Doesn't Work (Singleton Pattern Analogy)
- Same problem — Singleton guarantees one instance **per JVM**, not globally
- If you need globally shared state across servers, it must live outside the JVM (Redis, ZooKeeper, DB)

### The Fix: Redis Lua Script
Lua scripts execute **atomically** inside Redis — single-threaded, no interruption possible.

Instead of 5-6 separate Redis calls from Java, one script does everything atomically.

**After fix:** exactly 5 out of 20 concurrent requests allowed. Perfect enforcement.

### Full Code: Java ↔ Lua Script Side by Side

**Java (Filter) — sends one call to Redis:**
```java
// --- Constructor: load script once ---
private final DefaultRedisScript<Long> rateLimitScript;

public RateLimiterFilter(StringRedisTemplate stringRedisTemplate) {
    this.stringRedisTemplate = stringRedisTemplate;
    this.rateLimitScript = new DefaultRedisScript<>();
    this.rateLimitScript.setLocation(new ClassPathResource("scripts/rate-limiter.lua"));
    this.rateLimitScript.setResultType(Long.class);
}

// --- doFilter: one Redis call does everything ---
String key = "bucket:" + request.getRemoteAddr();

Long result = stringRedisTemplate.execute(
    rateLimitScript,
    List.of(key),                                // → KEYS[1] in Lua
    String.valueOf(LIMIT),                       // → ARGV[1] in Lua (5)
    String.valueOf(REFILL_INTERVAL),             // → ARGV[2] in Lua (12000)
    String.valueOf(System.currentTimeMillis())   // → ARGV[3] in Lua (current time)
);

if (result == 1)
    chain.doFilter(request, response);   // allowed
else
    handleRateLimitException(response);  // 429
```

**Lua Script (`src/main/resources/scripts/rate-limiter.lua`) — runs atomically inside Redis:**
```lua
-- Step 1: Unpack inputs from Java
local key = KEYS[1]                           -- "bucket:0:0:0:0:0:0:0:1"
local limit = tonumber(ARGV[1])               -- 5 (max tokens)
local refillInterval = tonumber(ARGV[2])      -- 12000 (ms per token refill)
local now = tonumber(ARGV[3])                 -- current timestamp in ms
-- Everything arrives as strings, tonumber() converts for math

-- Step 2: Read user's current bucket from Redis
local tokens = tonumber(redis.call('hget', key, 'tokens'))
local lastRefill = tonumber(redis.call('hget', key, 'lastRefill'))
-- Same as Java's: opsForHash().get(key, "tokens")
-- Returns nil if user has never made a request

-- Step 3: First-time user — create bucket with full tokens
if tokens == nil then
    tokens = limit          -- start with 5 tokens
    lastRefill = now        -- refill clock starts now
end
-- Replaces the hasKey() check from Java
-- No race condition because entire script is atomic

-- Step 4: Refill tokens based on elapsed time
local elapsed = now - lastRefill
local tokensToAdd = math.floor(elapsed / refillInterval)
-- Example: 36 seconds passed, interval = 12s → tokensToAdd = 3
if tokensToAdd > 0 then
    tokens = math.min(tokens + tokensToAdd, limit)
    -- Cap at max so bucket never overflows
    -- User had 1 token + 3 added = 4 (not more than 5)
    lastRefill = now
end

-- Step 5: Allow or reject
if tokens > 0 then
    tokens = tokens - 1                                    -- consume one token
    redis.call('hset', key, 'tokens', tokens)              -- save to Redis
    redis.call('hset', key, 'lastRefill', lastRefill)
    return 1                                               -- → Java gets 1 (allowed)
else
    redis.call('hset', key, 'tokens', tokens)              -- save state (still 0)
    redis.call('hset', key, 'lastRefill', lastRefill)
    return 0                                               -- → Java gets 0 (rejected)
end
```

Script stored at: `src/main/resources/scripts/rate-limiter.lua`

## Key Takeaway
**When multiple clients share state, operations on that state must be atomic.**
Whether it's a database transaction, a Redis Lua script, or a distributed lock — the principle is the same. The gap between "read" and "write" is where race conditions live.

## Implementation Notes
- Filter must `return` after sending 429 — otherwise `chain.doFilter()` still runs
- `StringRedisTemplate` requires all values as Strings — use `String.valueOf()`
- `hasKey(key)` to check existence, not `opsForValue().get()` when using hashes
- Time calculations: `System.currentTimeMillis()` returns ms — match your interval units
- Identify users by IP: `request.getRemoteAddr()`
- Load Lua script in constructor, not in `doFilter` — avoid reading from disk on every request
- Don't inject `DefaultRedisScript` as a Spring bean — create it yourself in the constructor