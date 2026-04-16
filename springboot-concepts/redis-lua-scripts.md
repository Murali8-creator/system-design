# Redis Lua Scripts — Atomic Operations

## The Problem They Solve
When you need to read-check-update in Redis, multiple commands from Java can be interleaved by concurrent threads. The gap between "read" and "write" is where race conditions live.

## How Lua Scripts Fix It
- Redis executes Lua scripts **single-threaded** — no other command runs while your script is executing
- The entire script is **atomic** — either all commands execute, or none
- Works across multiple app servers — atomicity is at the Redis level, not the JVM level

## Anatomy of a Redis Lua Script

```lua
-- KEYS[1], KEYS[2]... = Redis keys passed from Java
-- ARGV[1], ARGV[2]... = Arguments passed from Java

-- Read
local value = redis.call('hget', KEYS[1], 'field')

-- Convert (everything in Redis is a string)
local num = tonumber(value)

-- Write
redis.call('hset', KEYS[1], 'field', num + 1)

-- Return result to Java
return 1
```

- `redis.call()` = execute any Redis command inside the script
- `KEYS` = list of Redis keys (passed as first arg from Java)
- `ARGV` = list of additional arguments (passed as remaining args)
- `tonumber()` = Lua's string-to-number conversion

## Spring Boot Integration

### 1. Store script in resources
`src/main/resources/scripts/my-script.lua`

### 2. Load once (constructor, not per-request)
```java
DefaultRedisScript<Long> script = new DefaultRedisScript<>();
script.setLocation(new ClassPathResource("scripts/my-script.lua"));
script.setResultType(Long.class);  // matches the Lua return type
```

### 3. Execute
```java
Long result = stringRedisTemplate.execute(
    script,
    List.of("myKey"),           // KEYS
    "arg1", "arg2", "arg3"     // ARGV
);
```

## When to Use
- Rate limiting (read tokens → check → decrement)
- Distributed locks (check if locked → acquire)
- Atomic counters with conditions
- Any read-then-write that must be uninterruptible

## When NOT to Use
- Simple single-command operations (increment, set, get) — already atomic
- Complex business logic — keep Lua scripts short and focused on the atomic part