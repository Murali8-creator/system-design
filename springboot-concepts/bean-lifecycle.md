# Bean Lifecycle — @PostConstruct & @PreDestroy

## The Problem
Sometimes a bean needs to do work **after** it's constructed + wired, or **before** it's destroyed. Neither the constructor nor `try-with-resources` fits.

### Why not the constructor?
- Constructor's job = **receive dependencies and assign fields**. Nothing else.
- Doing heavy work in a constructor mixes object construction with lifecycle logic.
- You can't unit test `new CacheService(mockDs)` without that heavy work running (e.g., hitting a real DB).

### Why not try-with-resources?
- That's for short-lived local resources inside a method (file, ResultSet)
- Spring beans are long-lived (often singletons) — need different cleanup hooks

---

## The Two Hooks

### @PostConstruct — runs after DI, before use
```java
@Service
public class CacheService {

    private final DataSource dataSource;

    public CacheService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void warmUp() {
        // runs ONCE, after construction + DI, before anyone uses this bean
        preloadCache();
    }
}
```

**Flow:**
1. Spring creates `CacheService` with `DataSource` injected
2. Spring sees `@PostConstruct` → calls `warmUp()`
3. Bean is now ready for use

### @PreDestroy — runs once at shutdown
```java
@PreDestroy
public void cleanup() {
    executor.shutdown();
    // close connections, flush buffers, release locks
}
```

Called when the ApplicationContext is closing (app shutdown, container stop).

---

## Full Example

```java
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class NotificationService {

    private ExecutorService executor;

    @PostConstruct
    public void init() {
        executor = Executors.newFixedThreadPool(10);
        System.out.println("NotificationService ready");
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        System.out.println("NotificationService shutting down");
    }

    public void notify(User user) {
        executor.submit(() -> sendEmail(user));
    }
}
```

---

## Important Details

- **Method signature:** `void`, no arguments
- **Works only on singleton-scoped beans.** Spring doesn't call `@PreDestroy` on `prototype` beans (it doesn't track them after creation)
- **Imports from `jakarta.annotation`**, not Spring:
  ```java
  import jakarta.annotation.PostConstruct;
  import jakarta.annotation.PreDestroy;
  ```
- These annotations come from **Jakarta EE specs**. Spring respects them, but they'd also work in a pure Jakarta EE container like WildFly.

---

## Common Use Cases

| Scenario | Hook |
|---|---|
| Warm up a cache on startup | `@PostConstruct` |
| Validate config values | `@PostConstruct` |
| Register a scheduled task | `@PostConstruct` |
| Preload data | `@PostConstruct` |
| Close a thread pool | `@PreDestroy` |
| Flush a write buffer | `@PreDestroy` |
| Release a distributed lock | `@PreDestroy` |
| Close a connection pool | `@PreDestroy` |

---

## Alternatives (Spring-Specific)

You can achieve the same thing with:

### 1. `InitializingBean` / `DisposableBean` interfaces
```java
public class MyBean implements InitializingBean, DisposableBean {
    @Override public void afterPropertiesSet() { /* init */ }
    @Override public void destroy() { /* cleanup */ }
}
```
Downside: couples your class to Spring interfaces.

### 2. `@Bean` with `initMethod` / `destroyMethod`
```java
@Bean(initMethod = "init", destroyMethod = "cleanup")
public MyBean myBean() { return new MyBean(); }
```
Useful when you don't want annotations on the class itself.

**Preferred: `@PostConstruct` + `@PreDestroy`**
- Portable (works in Spring + Jakarta EE)
- Clean (annotations, not interfaces)
- Discoverable (right on the method)

---

## Quick Revision

- `@PostConstruct` = run once, after DI, before bean is used
- `@PreDestroy` = run once, at app shutdown
- Singleton only — prototype beans don't get `@PreDestroy` calls
- Import from `jakarta.annotation`
- Prefer over `InitializingBean` or `initMethod` — cleaner and portable