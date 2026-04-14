# Filter vs Interceptor

## Request Flow in Spring Boot

```
HTTP Request
  │
  ▼
  Filter  (Servlet level — before Spring touches it)
  │
  ▼
  DispatcherServlet  (Spring's front controller)
  │
  ▼
  Interceptor  (Spring MVC level — before controller)
  │
  ▼
  Controller → Service → Repository
```

## Filter
- Part of **Servlet API** (Jakarta), not Spring-specific
- Runs before Spring's DispatcherServlet
- Has access to raw `HttpServletRequest` and `HttpServletResponse`
- Can block the request entirely — Spring never sees it
- Works on **all requests** including static files, error pages, etc.
- Implement: `jakarta.servlet.Filter` interface
- Register: `@Component` or via `FilterRegistrationBean`

## Interceptor
- Part of **Spring MVC**
- Runs after DispatcherServlet, before the controller
- Has access to `HandlerMethod` — knows which controller method will be called
- Only works on requests that map to a controller
- Implement: `HandlerInterceptor` interface
- Register: via `WebMvcConfigurer.addInterceptors()`

## When to Use Which?

| Use Case | Filter or Interceptor? | Why? |
|---|---|---|
| Rate limiting | Filter | Block early, don't waste Spring's time |
| Authentication | Filter | Reject unauthenticated requests before Spring processes them |
| Logging all requests | Filter | Catches everything, even non-controller requests |
| CORS headers | Filter | Needs to run on all requests |
| Authorization (role-based) | Interceptor | Needs to know which controller/method is being called |
| Audit logging (who called what endpoint) | Interceptor | Has access to HandlerMethod for endpoint details |
| Request/response modification for specific endpoints | Interceptor | Can target specific paths with Spring's path matching |

## Key Difference
- **Filter** = "Should this request even enter my application?"
- **Interceptor** = "This request is valid, but should this specific endpoint handle it?"