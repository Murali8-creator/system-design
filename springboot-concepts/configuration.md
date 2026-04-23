# Configuration in Spring Boot

## Where Config Lives
`src/main/resources/application.yaml` (or `.properties`):

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mydb

app:
  rate-limit: 100
  timeout-ms: 5000
  support-email: help@example.com
```

YAML is preferred over properties files — hierarchical, cleaner.

---

## 1. @Value — Quick & Simple

Inject a single value:

```java
@Service
public class SupportService {

    @Value("${app.support-email}")
    private String supportEmail;

    @Value("${app.rate-limit:50}")   // 50 = default if property missing
    private int rateLimit;
}
```

- `${key}` = property placeholder
- `${key:default}` = inline default

### Problems
- Each value is a separate field → no structure
- Typos in key fail at runtime, not compile time
- Hard to reuse (same value imported in 10 classes)
- No built-in validation

**Use for one-off values. Avoid for groups.**

---

## 2. @ConfigurationProperties — Type-Safe (PREFERRED)

Bind a whole group of related properties into a dedicated class.

### With a record (Spring Boot 2.2+)
```java
@ConfigurationProperties(prefix = "app")
public record AppProperties(
    int rateLimit,
    int timeoutMs,
    String supportEmail
) { }
```

- Immutable (perfect for config)
- No getters/setters — use `props.rateLimit()`
- YAML `rate-limit` → field `rateLimit` (relaxed binding)

### With a regular class
```java
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private int rateLimit;
    private int timeoutMs;
    private String supportEmail;
    // getters & setters required
}
```

### Registering it
Either:
```java
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class MyApp { }
```
Or annotate the class with `@Component` (if scanning picks it up).

### Using it
```java
@Service
public class SupportService {
    private final AppProperties props;

    public SupportService(AppProperties props) {
        this.props = props;
    }

    public void sendSupportEmail(String msg) {
        sendTo(props.supportEmail(), msg);
    }
}
```

### Benefits
- Related properties grouped
- Type-safe (int, String, boolean, nested objects, lists all work)
- IDE autocomplete
- Can validate with `@NotNull`, `@Min`, etc.
- Reusable across the codebase

---

## 3. Profiles — Different Config Per Environment

### Multiple YAMLs
```
application.yaml           # shared / default
application-dev.yaml       # dev overrides
application-prod.yaml      # prod overrides
```

### Activating a profile
- Command line: `java -jar app.jar --spring.profiles.active=prod`
- Env variable: `SPRING_PROFILES_ACTIVE=prod`
- `application.yaml`: `spring.profiles.active: dev`

### Example
**application.yaml:**
```yaml
app:
  support-email: help@example.com
  rate-limit: 100
```

**application-prod.yaml:**
```yaml
app:
  rate-limit: 1000     # prod overrides
server:
  port: 80
```

When `prod` is active → Spring merges both, `prod` values override.

### @Profile — Bean available in specific profiles only
```java
@Service
@Profile("dev")
public class FakePaymentService implements PaymentService { }

@Service
@Profile("prod")
public class StripePaymentService implements PaymentService { }
```

- `dev` active → only `FakePaymentService` is a bean
- `prod` active → only `StripePaymentService` is a bean
- Anyone injecting `PaymentService` gets the right one automatically

### Profile expressions
```java
@Profile("!dev")              // everything except dev
@Profile("dev & aws")         // both active
@Profile({"dev", "test"})     // dev OR test
```

---

## Summary Table

| Approach | When to use |
|---|---|
| `@Value("${key}")` | One-off values, prototyping |
| `@ConfigurationProperties` | **Preferred** — groups of related config |
| `@Profile` + multiple YAMLs | Per-environment behavior |

---

## Property Resolution Order (highest priority last — later overrides earlier)

1. `application.yaml` in classpath
2. `application-{profile}.yaml` in classpath
3. Environment variables (`APP_SUPPORT_EMAIL` → `app.support-email`)
4. Command-line arguments (`--app.support-email=foo@bar.com`)

**Implication:** you can override any property via env var in Docker/Kubernetes without rebuilding.

---

## Relaxed Binding
Spring matches these YAML keys to the same Java field:
- `rate-limit` (kebab-case — recommended in YAML)
- `rateLimit` (camelCase)
- `rate_limit` (snake_case)
- `RATE_LIMIT` (env var style — uppercase)

All bind to Java field `rateLimit`. This is why env vars can override YAML values.

---

## Quick Revision
- **`@Value`** = single values, quick but not type-safe
- **`@ConfigurationProperties`** = preferred, type-safe, groups config
- **Records** work beautifully with `@ConfigurationProperties` (immutable, no getters)
- **Profiles** = different config per environment (`application-dev.yaml`, etc.)
- **`@Profile`** on a bean = bean only exists when that profile is active
- **Override order**: YAML → env vars → CLI args (later wins)
- **Relaxed binding** = `rate-limit`, `rateLimit`, `RATE_LIMIT` all map to the same field