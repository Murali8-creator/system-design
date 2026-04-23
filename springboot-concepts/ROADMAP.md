# Spring Boot + Java — Interview Prep Roadmap

## How to Use
Go topic by topic. Each one has a status: `[ ]` (not started), `[~]` (in progress), `[x]` (done).
Concepts marked with ⭐ are frequently asked in interviews.

---

## Phase 1: Java Core

### Collections & Data Structures
- [ ] ⭐ HashMap internals — how hashing, buckets, and resizing work
- [ ] ⭐ HashMap vs ConcurrentHashMap vs Hashtable
- [ ] ArrayList vs LinkedList — when to use which
- [ ] HashSet internals (uses HashMap under the hood)
- [ ] TreeMap vs LinkedHashMap — ordering guarantees
- [ ] Comparable vs Comparator

### Concurrency & Multithreading
- [ ] ⭐ Thread lifecycle and states
- [ ] ⭐ synchronized, volatile, and the Java Memory Model
- [ ] ⭐ ExecutorService and thread pools
- [ ] ⭐ CompletableFuture — async programming
- [ ] Callable vs Runnable
- [ ] CountDownLatch, CyclicBarrier, Semaphore
- [ ] ⭐ Race conditions, deadlocks — how to identify and prevent
- [ ] Atomic classes (AtomicInteger, AtomicReference)

### OOP & Design
- [ ] ⭐ SOLID principles with real examples
- [ ] ⭐ Design patterns — Singleton, Factory, Builder, Strategy, Observer
- [ ] Abstract class vs Interface (Java 8+ default methods)
- [ ] Composition vs Inheritance

### Java 8+ Features
- [ ] ⭐ Streams API — map, filter, reduce, collect
- [ ] ⭐ Functional interfaces (Predicate, Function, Consumer, Supplier)
- [ ] Lambda expressions
- [ ] Optional — proper usage, anti-patterns
- [ ] Method references

### Memory & Performance
- [ ] ⭐ JVM architecture — heap, stack, metaspace
- [ ] ⭐ Garbage Collection — types (G1, ZGC), how it works
- [ ] String pool and immutability
- [ ] Memory leaks — common causes in Java
- [ ] == vs .equals() vs hashCode() contract

### Exception Handling
- [ ] Checked vs Unchecked exceptions
- [ ] Custom exceptions — when and how
- [ ] try-with-resources

---

## Phase 2: Spring Core

### Dependency Injection & IoC
- [x] ⭐ What is IoC (Inversion of Control) — why it exists
- [x] ⭐ DI types — constructor vs setter vs field injection (and which to prefer)
- [x] ⭐ @Autowired — how Spring resolves beans
- [x] @Qualifier, @Primary — resolving ambiguity
- [x] Circular dependencies

### Beans & Lifecycle
- [x] ⭐ Bean scopes — singleton, prototype, request, session
- [x] @Configuration and @Bean — manual bean definition
- [x] @ConditionalOnMissingBean — how Spring Boot auto-config works
- [x] ⭐ @Component vs @Service vs @Repository vs @Controller — subtle differences
- [x] Bean lifecycle — creation → initialization → destruction
- [x] @PostConstruct, @PreDestroy

### Configuration
- [x] ⭐ application.properties vs application.yaml
- [x] @Value — injecting properties
- [x] @ConfigurationProperties — type-safe config binding
- [x] Profiles (@Profile, spring.profiles.active) — env-specific config

### AOP (Aspect-Oriented Programming)
- [ ] ⭐ What AOP is — cross-cutting concerns
- [ ] ⭐ How @Transactional works behind the scenes (proxy-based AOP)
- [ ] @Aspect, @Before, @After, @Around
- [ ] Common use cases — logging, security, transactions

---

## Phase 3: Spring Web (REST APIs)

### Request Handling
- [ ] ⭐ Request lifecycle — filter → DispatcherServlet → interceptor → controller
- [ ] @RestController vs @Controller
- [ ] @RequestMapping, @GetMapping, @PostMapping — URL mapping
- [ ] @PathVariable, @RequestParam, @RequestBody — extracting data
- [ ] @ResponseStatus — custom HTTP status codes

### Serialization
- [ ] ⭐ Jackson — how Java objects become JSON (and back)
- [ ] @JsonProperty, @JsonIgnore, @JsonFormat
- [ ] DTOs — why separate from entities, record classes

### Exception Handling
- [ ] ⭐ @ControllerAdvice + @ExceptionHandler — global error handling
- [ ] ResponseEntity — flexible response building
- [ ] Custom error response format

### Validation
- [ ] ⭐ @Valid, @NotNull, @Size, @Email — Bean Validation
- [ ] Custom validators
- [ ] Validation groups

### Filters & Interceptors
- [x] ⭐ Filter vs Interceptor — when to use which (learned in rate limiter)
- [ ] OncePerRequestFilter
- [ ] Filter ordering

---

## Phase 4: Spring Data & JPA

### JPA / Hibernate Basics
- [ ] ⭐ What ORM is and why it exists
- [ ] Entity mapping — @Entity, @Table, @Column, @Id
- [ ] ⭐ @GeneratedValue strategies — AUTO, IDENTITY, SEQUENCE, TABLE
- [ ] Spring Data JPA — how method names become queries

### Relationships
- [ ] ⭐ @OneToMany, @ManyToOne, @ManyToMany, @OneToOne
- [ ] ⭐ Lazy vs Eager loading — what, why, and the default for each
- [ ] Cascade types — PERSIST, MERGE, REMOVE, ALL
- [ ] orphanRemoval

### Querying
- [ ] ⭐ Derived queries (findByNameAndAge)
- [ ] @Query — JPQL and native SQL
- [ ] Pagination and Sorting (Pageable)
- [ ] Projections and DTOs in queries

### Transactions
- [ ] ⭐ @Transactional — how it works, propagation levels
- [ ] ⭐ ACID properties
- [ ] Isolation levels — READ_COMMITTED, REPEATABLE_READ, SERIALIZABLE
- [ ] Optimistic vs Pessimistic locking

### Performance
- [ ] ⭐ N+1 query problem — how to detect and fix
- [ ] JOIN FETCH
- [ ] @BatchSize
- [ ] Second-level cache (Hibernate)

---

## Phase 5: Spring Security

- [ ] ⭐ Authentication vs Authorization
- [ ] ⭐ JWT (JSON Web Tokens) — how token-based auth works
- [ ] SecurityFilterChain — how Spring Security intercepts requests
- [ ] Password encoding (BCrypt)
- [ ] Role-based access control (@PreAuthorize, @Secured)
- [ ] OAuth2 basics

---

## Phase 6: Microservices & Distributed Systems

- [ ] ⭐ Monolith vs Microservices — tradeoffs
- [ ] ⭐ API Gateway pattern
- [ ] Service discovery (Eureka)
- [ ] ⭐ Circuit Breaker pattern (Resilience4j)
- [ ] Inter-service communication — REST vs gRPC vs message queues
- [ ] ⭐ Distributed transactions — Saga pattern
- [ ] ⭐ CAP theorem
- [ ] ⭐ Event-driven architecture — Kafka / RabbitMQ
- [ ] Idempotency — why it matters in distributed systems

---

## Phase 7: Testing

- [ ] ⭐ Unit testing with JUnit 5 + Mockito
- [ ] @MockBean, @Mock, @InjectMocks
- [ ] Integration testing with @SpringBootTest
- [ ] @WebMvcTest — testing controllers
- [ ] @DataJpaTest — testing repositories
- [ ] TestContainers — testing with real databases

---

## Already Covered (from system design projects)
- [x] Redis — caching, cache-aside pattern, StringRedisTemplate
- [x] Redis Lua scripts — atomic operations
- [x] Filter vs Interceptor
- [x] Redis Hash — opsForHash()
- [x] JPA basics — @Entity, @Column, @GeneratedValue, unique constraints
- [x] Spring Data — derived query methods (findByShortId)
- [x] Base62 encoding — ID generation
- [x] Race conditions — proving and fixing with atomic operations