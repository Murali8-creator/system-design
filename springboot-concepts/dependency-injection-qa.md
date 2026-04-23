# Dependency Injection — Interview Q&A

Clear-cut answers with code examples. Use for revision before interviews.

---

## Q1. What problem does Dependency Injection solve? Why is it better than `new`?

**Answer:**
DI removes the responsibility of a class to construct its own dependencies.

### Without DI — Problems
```java
public class OrderService {
    private Repo repo = new Repo();   // creates its own dependency
}
```
- **Tight coupling:** Service is glued to `Repo`. Can't swap implementations.
- **No unit testing:** `new Repo()` hits a real DB. Can't inject a mock.
- **Complex wiring:** If `Repo` needs a `DataSource`, and `DataSource` needs a `ConnectionPool`, the service has to know how to build all of it.

### With DI — Benefits
```java
public class OrderService {
    private final Repo repo;
    public OrderService(Repo repo) { this.repo = repo; }   // receives dep
}
```
- **Loose coupling:** Works with any `Repo` implementation.
- **Unit testable:** `new OrderService(mockRepo)` in tests.
- **Framework wires everything:** Spring figures out the dependency graph.

---

## Q2. Constructor vs Field vs Setter injection — which to prefer and why?

**Answer: Constructor injection. Three reasons:**

### 1. `final` fields → thread-safe and immutable
```java
@Service
public class Foo {
    private final Bar bar;
    public Foo(Bar bar) { this.bar = bar; }   // final, never reassigned
}
```

### 2. Impossible to create the object without dependencies
You can't instantiate `new Foo()` — the compiler forces you to pass `Bar`. No NullPointerException at runtime from a missing dependency.

### 3. Easy to test without Spring
```java
@Test
void myTest() {
    Foo foo = new Foo(mockBar);   // plain Java, no Spring context needed
}
```

### Bonus: fails fast on circular dependencies (more on this in Q5)

### Why NOT field injection
```java
@Autowired private Bar bar;   // bad
```
- Hidden dependencies (must scan class body to know what's needed)
- Can't be `final`
- Hard to test without Spring (needs reflection to set the field)
- **Hides circular dependencies** silently → runtime breakage

---

## Q3. You have two implementations of the same interface — how does Spring pick one?

**Problem setup:**
```java
public interface EmailSender { }

@Service
public class SmtpEmailSender implements EmailSender { }

@Service
public class SesEmailSender implements EmailSender { }

@Service
public class NotificationService {
    public NotificationService(EmailSender emailSender) { }   // ambiguous!
}
```

**What happens:** Startup fails with `NoUniqueBeanDefinitionException`.
> No qualifying bean of type 'EmailSender' — expected single matching bean but found 2

### Solution 1: `@Primary` — designate a default
```java
@Service
@Primary
public class SmtpEmailSender implements EmailSender { }
```
Spring picks `SmtpEmailSender` by default anywhere `EmailSender` is requested.

### Solution 2: `@Qualifier` — override at the injection point
```java
public NotificationService(
    @Qualifier("sesEmailSender") EmailSender emailSender   // force SES
) { }
```
You can customize the qualifier name with `@Service("ses")` → then inject with `@Qualifier("ses")`.

**Rule of thumb:**
- `@Primary` → the default choice for most places
- `@Qualifier` → override in specific places that need the non-default

---

## Q4. What is a bean scope? What are the scopes and when to use each?

**Answer:**
A scope defines **how many instances of the bean exist** and **how long each one lives** in the Spring container.

| Scope | Instances | When to use |
|---|---|---|
| `singleton` (default) | One per application | Stateless services, repositories, controllers (99% of cases) |
| `prototype` | New instance every time injected or retrieved | Stateful throw-away objects |
| `request` | One per HTTP request | Per-request state (web only) |
| `session` | One per user session | User-specific state (web only) |

### Syntax
```java
@Service
@Scope("prototype")
public class ShoppingCart { }
```

### Gotcha
If you inject a `prototype` bean into a `singleton`, you get **one instance** (wired once at startup). To get a fresh one each call:
```java
@Autowired
private ObjectProvider<ShoppingCart> cartProvider;

public void checkout() {
    ShoppingCart cart = cartProvider.getObject();   // fresh each call
}
```

---

## Q5. What is a circular dependency? What happens in Spring?

**Setup:**
```java
@Service class ServiceA {
    ServiceA(ServiceB b) { }
}

@Service class ServiceB {
    ServiceB(ServiceA a) { }
}
```

Chicken and egg — Spring can't create `A` without `B`, and can't create `B` without `A`.

### With constructor injection → FAILS AT STARTUP ✅
```
BeanCurrentlyInCreationException: circular reference
```
This is **good** — the error is loud and visible. You're forced to fix the design.

### With field injection → SILENTLY "WORKS" ❌
```java
@Service class ServiceA {
    @Autowired ServiceB b;
}
@Service class ServiceB {
    @Autowired ServiceA a;
}
```
- Spring creates both as **half-initialized shells** first
- Injects the fields via reflection afterward
- **App starts successfully**
- At runtime: NPE, broken behavior, partially initialized beans
- The bug is **hidden** until production blows up

### The real fix
Circular dependency = **design smell**, not a Spring problem.
1. Merge the two classes into one
2. Extract shared logic into a third class that both depend on
3. Use `ApplicationEventPublisher` if one just needs to notify the other without a direct reference

---

## Q6. Difference between `@Component` and `@Bean`? When to use each?

**Answer:**

| Aspect | `@Component` | `@Bean` |
|---|---|---|
| Goes on | A **class** | A **method** (inside `@Configuration`) |
| Who creates the object | Spring (via component scan) | **You** (by calling `new` in the method) |
| Can it be used on library code? | No (can't modify their classes) | Yes (you create an instance yourself) |
| Typical use | Your own classes | Third-party classes or complex construction |

### `@Component` — your own class
```java
@Component
public class InvoicePrinter {
    public void print(Invoice i) { }
}
```
Spring scans, finds the `@Component`, creates an instance, stores it as a bean.

### `@Bean` — library class or complex construction
```java
@Configuration
public class AppConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper m = new ObjectMapper();
        m.registerModule(new JavaTimeModule());
        m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return m;
    }
}
```
- `ObjectMapper` is from Jackson library — you can't add `@Component` to it
- You want specific configuration applied
- Method name (`objectMapper`) becomes the bean name

### `@Bean` with injected dependencies
```java
@Bean
public EmailService emailService(ObjectMapper mapper, Environment env) {
    return new EmailService(mapper, env.getProperty("smtp.host"));
}
```
Spring sees this method needs `ObjectMapper` and `Environment`, injects them, then calls your method.

### Aliases of @Component
- `@Service` → business logic (semantic, functionally same)
- `@Repository` → data access (adds DB exception translation)
- `@Controller` → MVC controller (returns views)
- `@RestController` → `@Controller` + `@ResponseBody` (returns JSON/XML directly)

---

## Q7 (Bonus). What is `@ConditionalOnMissingBean`? How does Spring Boot "just work"?

**Answer:**
`@ConditionalOnMissingBean` tells Spring to create a bean **only if nobody else has defined one of the same type**. This is the mechanism behind Spring Boot's auto-configuration.

### Example from Spring Boot source
```java
@Configuration
public class RedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(StringRedisTemplate.class)
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory f) {
        return new StringRedisTemplate(f);
    }
}
```

- You don't define your own `StringRedisTemplate` → Spring Boot creates the default
- You define your own → Spring Boot's default is skipped

This is why Spring Boot gives you sensible defaults but stays out of your way when you override them.

### Common conditional annotations
| Annotation | When it activates |
|---|---|
| `@ConditionalOnMissingBean` | No bean of this type exists |
| `@ConditionalOnBean` | A bean of this type exists |
| `@ConditionalOnClass` | A class is on the classpath |
| `@ConditionalOnProperty` | A config property is set (e.g., `feature.x.enabled=true`) |
| `@Profile("dev")` | Specific profile active |

---

## Quick Revision — The One-Liners

- **DI** = receive deps, don't create them
- **IoC** = the framework controls the wiring
- **Constructor injection** = always prefer (`final`, no NPE, testable)
- **@Primary** = default pick, **@Qualifier** = explicit override
- **Default scope** = singleton
- **Circular dep with constructor inj** = `BeanCurrentlyInCreationException` at startup (good — forces a fix)
- **Circular dep with field inj** = silent half-init → runtime NPE (bad — hidden bug)
- **@Component** = on your classes / **@Bean** = on methods returning library classes
- **@ConditionalOnMissingBean** = the magic behind Spring Boot defaults you can override