# @Component vs @Service vs @Repository vs @Controller

All four mark a class as a **Spring-managed bean**. Most are semantic — only one has real behavior.

---

## The Hierarchy

```
@Component (generic bean marker)
    │
    ├── @Service          → business logic (semantic only)
    ├── @Repository       → data access (+ exception translation)
    ├── @Controller       → MVC controller (returns views)
    └── @RestController   → REST API (returns JSON, = @Controller + @ResponseBody)
```

`@Service`, `@Repository`, `@Controller` are **meta-annotated with `@Component`** — meaning their source code literally has `@Component` on them:

```java
@Component    // ← @Service is itself annotated with @Component
public @interface Service { }
```

When Spring's component scan runs, it picks up any class marked with `@Component` **or anything meta-annotated with `@Component`** — so `@Service`, `@Repository`, `@Controller` all get detected too.

---

## When to Use Each

### @Component — generic
```java
@Component
public class CacheEvictionListener { }
```
Use when the class doesn't fit the other categories.

### @Service — business logic (semantic)
```java
@Service
public class OrderService { }
```
**Functionally identical to `@Component`.** Pure semantic marker.

### @Repository — data access (has real behavior)
```java
@Repository
public class CustomReportRepo {
    @PersistenceContext
    private EntityManager em;
}
```

**Extra behavior:** enables **exception translation** — wraps low-level DB exceptions (SQLException, HibernateException) into Spring's consistent `DataAccessException` hierarchy.

- Example: MySQL's duplicate key `SQLException` → `DataIntegrityViolationException` (same class regardless of DB vendor)

### @Controller — returns views (MVC)
```java
@Controller
public class HomeController {
    @GetMapping("/home")
    public String home() {
        return "home";   // view name → Spring renders home.html
    }
}
```
Used with template engines (Thymeleaf, JSP).

### @RestController — returns JSON/XML (REST APIs)
```java
@RestController
public class OrderApi {
    @GetMapping("/orders/{id}")
    public Order get(@PathVariable String id) {
        return orderService.findById(id);   // serialized to JSON as response body
    }
}
```
= `@Controller` + `@ResponseBody`. Default for REST APIs.

---

## Do I Need @Repository on a JpaRepository Interface?

**No.** When your interface extends `JpaRepository`:
```java
public interface URLShortenerRepo extends JpaRepository<URLShortener, UUID> { }
```

- Spring Data generates the implementation at runtime
- Registers it as a bean automatically
- Exception translation is already built in

### When you DO need @Repository
Only for **custom data-access classes** that don't use Spring Data:
```java
@Repository
public class CustomReportRepo {
    @PersistenceContext
    private EntityManager em;

    public List<Report> complexReport() {
        return em.createQuery(...).getResultList();
    }
}
```

### Why Not Add @Repository for Readability Anyway?
Fair argument. Counter: the `extends JpaRepository<T, ID>` is a **compiler-enforced contract** — not just a naming convention. The type system already tells you it's a repository. Adding `@Repository` is redundant, not wrong.

**Verdict:** skip it. But if your team prefers it for symmetry with `@Service`, that's a style choice, not a correctness issue.

---

## Summary Table

| Annotation | Goes On | Extra Behavior | Common Use |
|---|---|---|---|
| `@Component` | Class | None | Generic bean |
| `@Service` | Class | None (semantic only) | Business logic layer |
| `@Repository` | Class | **Exception translation** | Custom data-access classes |
| `@Controller` | Class | Hooks into Spring MVC | Returns view names |
| `@RestController` | Class | = `@Controller` + `@ResponseBody` | REST API, returns JSON |

---

## Why Use Specialized Annotations At All?

1. **Readability** — `@Service OrderService` = business logic at a glance
2. **Real behavior** — only `@Repository` adds exception translation
3. **Future-proofing** — Spring can add stereotype-specific behavior over time
4. **Tooling** — IDEs and AOP rules can target specific stereotypes

---

## Quick Revision

- All four = `@Component` underneath, all become Spring beans
- `@Service` is **semantic only** — no different from `@Component`
- `@Repository` is the **only one with real behavior** (exception translation)
- `@RestController` = `@Controller + @ResponseBody` (for REST APIs)
- **Don't add `@Repository` to `JpaRepository` interfaces** — redundant