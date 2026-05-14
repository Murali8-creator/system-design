# JPA Relationships

## The Four Types

| Type | Example | Direction |
|---|---|---|
| `@OneToOne` | User ↔ Profile | rare |
| `@OneToMany` | User → Posts | common |
| `@ManyToOne` | Post → User | most common |
| `@ManyToMany` | Post ↔ Tag | common |

`@OneToMany` and `@ManyToOne` are two sides of the same relationship.

---

## @ManyToOne — The Foundation

```java
@Entity
public class Post {
    @Id @GeneratedValue
    private Long id;

    private String title;

    @ManyToOne(fetch = FetchType.LAZY)   // ALWAYS make explicit LAZY
    @JoinColumn(name = "user_id")        // FK column in `post` table
    private User author;
}
```

SQL created:
```sql
CREATE TABLE post (
    id BIGINT PRIMARY KEY,
    title VARCHAR,
    user_id BIGINT REFERENCES user(id)
);
```

---

## @OneToMany — The Reverse Side

```java
@Entity
public class User {
    @Id @GeneratedValue
    private Long id;

    private String name;

    @OneToMany(mappedBy = "author")     // "author" is the field name in Post
    private List<Post> posts = new ArrayList<>();
}
```

`mappedBy` = "this is the inverse side — use the FK that's already on the other entity, don't create a separate join table."

---

## Owning vs Inverse Side

| Side | Marker | Description |
|---|---|---|
| **Owning** | `@JoinColumn` (has the FK) | Controls the relationship in DB |
| **Inverse** | `mappedBy` | Just a Java-side view of the relationship |

**Only the owning side controls persistence.** Changes to the inverse side's collection alone don't always update the DB. Best practice — keep both sides in sync via helpers:

```java
// in User class
public void addPost(Post post) {
    posts.add(post);
    post.setAuthor(this);
}
```

---

## Parent vs Child (Conceptual)

People mix this up with owning/inverse. They're different:

| Concept | Meaning |
|---|---|
| **Parent / Child** | Ownership in code — User owns Posts (User is the parent) |
| **Owning / Inverse side** | Who has the foreign key in DB (Post is the owning side because `post.user_id` is the FK) |

A class can be the parent **conceptually** while being the inverse side **technically**. That's exactly the case for `User → Posts`.

---

## Lazy vs Eager Loading

| Annotation | Default | Recommendation |
|---|---|---|
| `@OneToMany` | LAZY | Keep LAZY |
| `@ManyToMany` | LAZY | Keep LAZY |
| `@ManyToOne` | **EAGER** ⚠️ | **Override to LAZY** |
| `@OneToOne` | EAGER | Override to LAZY |

### Why EAGER `@ManyToOne` is Dangerous

It's not just "one extra query per post" — it's worse than that:

1. **Silent extra work** — every query in your app that touches `Post` also fires queries for `User`, even when you didn't ask for the user.
2. **It cascades** — `Post → User (eager) → Profile (eager) → Address (eager)`. One `findAll()` on posts becomes a join nightmare you didn't write.
3. **Adds up at scale** — multiplied across hundreds of queries an app fires per request, this becomes a real perf problem.

**Rule: explicitly mark everything LAZY. Use `JOIN FETCH` when you actually need related data.**

---

## The N+1 Problem

The classic JPA interview question.

```java
List<Post> posts = postRepo.findAll();   // 1 query
for (Post p : posts) {
    p.getAuthor().getName();              // N queries (one per post)
}
```

If `posts` has 100 items → 101 queries. Devastating at scale.

> Note: N+1 also happens silently with EAGER `@ManyToOne` even without a loop — see above.

### Fix 1: JOIN FETCH (custom query)

```java
@Query("SELECT p FROM Post p JOIN FETCH p.author")
List<Post> findAllWithAuthor();
```

**How JOIN FETCH works:** this is JPQL (not SQL). JPA translates it to:

```sql
SELECT p.*, u.*
FROM post p
INNER JOIN user u ON p.user_id = u.id
```

One query loads everything. Calling `post.getAuthor()` later does NOT trigger another query — the data is already there.

`JOIN FETCH` vs regular `JOIN`: regular `JOIN` filters rows but doesn't populate the related entity. `JOIN FETCH` actually loads it.

### Fix 2: @EntityGraph (declarative)

```java
@EntityGraph(attributePaths = {"author"})
List<Post> findAll();
```

Same effect as `JOIN FETCH`, no custom query. Cleaner.

You can fetch multiple paths:
```java
@EntityGraph(attributePaths = {"author", "comments"})
List<Post> findAll();
```

### Fix 3: @BatchSize (safety net)

```java
@OneToMany(fetch = FetchType.LAZY)
@BatchSize(size = 20)
private List<Comment> comments;
```

Fetches in batches of 20 instead of one-by-one. 100 children → 5 queries instead of 100.

**Rule of thumb:**
- Know you need it → `JOIN FETCH` or `@EntityGraph`
- Just want a safety net → `@BatchSize`

---

## Cascade — Parent Operations Propagate

```java
@OneToMany(mappedBy = "author", cascade = CascadeType.ALL)
private List<Post> posts;
```

`userRepo.save(user)` → also saves all posts in `user.getPosts()`.

| Cascade Type | Behavior |
|---|---|
| `PERSIST` | Save parent → save children |
| `MERGE` | Update parent → update children |
| `REMOVE` | Delete parent → delete children |
| `ALL` | All of the above |

⚠️ `REMOVE` can delete more than expected. Use carefully.

### Example: how does cascade save work?

```java
User user = new User();
user.setName("Alice");

Post p1 = new Post();
p1.setTitle("Hello");
p1.setAuthor(user);    // set both sides

user.getPosts().add(p1);

userRepo.save(user);   // with CascadeType.ALL → p1 also saved
```

Without cascade, you'd need to call `postRepo.save(p1)` separately.

---

## orphanRemoval — Removing a Child From the Collection

```java
@OneToMany(mappedBy = "author", orphanRemoval = true)
private List<Post> posts;
```

```java
user.getPosts().remove(p1);
userRepo.save(user);
// orphanRemoval = true → p1 DELETED from DB
// orphanRemoval = false → p1 stays (see below)
```

### What if `orphanRemoval = false`?

The behavior depends on whether the foreign key column is nullable:

| `user_id` nullable? | Result |
|---|---|
| Yes (default) | `user_id` set to NULL, post stays in DB (orphaned) |
| No (`nullable = false`) | Exception — NOT NULL constraint violation |

**This is why teams enable `orphanRemoval = true`** for relationships where a child can't exist without its parent (like Posts needing a User).

### Important Catch — Owning Side

`Post` is the owning side (has the FK). Just removing from `user.getPosts()` may not be enough — clear the owning side too:

```java
user.getPosts().remove(p1);
p1.setAuthor(null);              // also clear the owning side
userRepo.save(user);
```

---

## CascadeType.REMOVE vs orphanRemoval — The Key Distinction

This is the most confusing pair in JPA. They look similar but are triggered by **different events**:

| | Trigger | Effect |
|---|---|---|
| `CascadeType.REMOVE` | Delete the **parent** | All children deleted |
| `orphanRemoval = true` | Remove child from **parent's collection** | That single child deleted |

### Examples side by side

```java
// Scenario A — delete the parent
userRepo.delete(user);
// CascadeType.REMOVE → ALL posts deleted with the user
// orphanRemoval alone (without cascade) → DB error (FK constraint)

// Scenario B — remove one post from the collection
user.getPosts().remove(p1);
userRepo.save(user);
// orphanRemoval = true → p1 deleted from DB
// orphanRemoval = false → p1 stays (FK set to NULL, or constraint violation)
```

Different triggers. Different effects. **Often used together** for tight "child belongs to parent" relationships:

```java
@OneToMany(mappedBy = "author",
           cascade = CascadeType.ALL,
           orphanRemoval = true)
private List<Post> posts;
```

### Mental Model
- **CascadeType.REMOVE** = "parent is gone, so children go too"
- **orphanRemoval** = "parent doesn't want this child anymore, so the child goes"

---

## Complete Recommended Setup

```java
@Entity
public class Post {
    @Id @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)        // ALWAYS lazy
    @JoinColumn(name = "user_id")
    private User author;
}

@Entity
public class User {
    @Id @GeneratedValue
    private Long id;

    @OneToMany(mappedBy = "author",
               fetch = FetchType.LAZY,
               cascade = CascadeType.ALL,
               orphanRemoval = true)
    private List<Post> posts = new ArrayList<>();

    public void addPost(Post post) {
        posts.add(post);
        post.setAuthor(this);
    }
}
```

---

# Interview Q&A

Clear-cut answers with code. Use for revision.

---

## Q1. What's the difference between owning side and inverse side? Which side has `mappedBy`?

**Owning side** = the side with the foreign key (`@JoinColumn`). It controls persistence — changes here are written to the DB.

**Inverse side** = the side with `mappedBy`. It's just a Java-level view of the relationship. JPA looks at the owning side's FK; the inverse side doesn't create its own column or join table.

**`mappedBy`** means: "this relationship is already mapped by the foreign key in the field named X of the other entity — don't create a new column."

```java
// Post is the owning side
@ManyToOne
@JoinColumn(name = "user_id")
private User author;

// User is the inverse side
@OneToMany(mappedBy = "author")
private List<Post> posts;
```

---

## Q2. What are the default fetch types? Which default is dangerous and why?

| Annotation | Default |
|---|---|
| `@OneToMany` | LAZY |
| `@ManyToMany` | LAZY |
| `@ManyToOne` | **EAGER** ⚠️ |
| `@OneToOne` | EAGER |

**`@ManyToOne` being EAGER is dangerous** — not because of "one extra query" per call, but because:
- It fires queries silently on **every** query that touches the entity, even when you didn't ask for the related data
- It cascades through chains (`Post → User → Profile → Address`) — one `findAll()` becomes many joins
- Adds up across hundreds of queries an app fires per request

**Rule:** override everything to LAZY explicitly. Use `JOIN FETCH` when you need the related data.

---

## Q3. Explain N+1 and give two fixes with code.

```java
List<Post> posts = postRepo.findAll();   // 1 query
for (Post p : posts) {
    p.getAuthor().getName();              // N queries (one per post)
}
```

100 posts → 101 queries.

### Fix 1: JOIN FETCH
```java
@Query("SELECT p FROM Post p JOIN FETCH p.author")
List<Post> findAllWithAuthor();
```
Becomes one SQL JOIN.

### Fix 2: @EntityGraph
```java
@EntityGraph(attributePaths = {"author"})
List<Post> findAll();
```
Same effect, declarative.

### Bonus: @BatchSize
```java
@OneToMany @BatchSize(size = 20)
private List<Comment> comments;
```
Fetches in batches → 5 queries instead of 100.

---

## Q4. What's the difference between `CascadeType.REMOVE` and `orphanRemoval`?

**Different triggers, different effects:**

| | Trigger | Effect |
|---|---|---|
| `CascadeType.REMOVE` | Delete the **parent** | All children deleted |
| `orphanRemoval = true` | Remove child from **parent's collection** | That child deleted |

### Scenarios
```java
// Trigger for CascadeType.REMOVE:
userRepo.delete(user);

// Trigger for orphanRemoval:
user.getPosts().remove(p1);
userRepo.save(user);
```

Often used together for "child can't exist without parent":
```java
@OneToMany(mappedBy = "author",
           cascade = CascadeType.ALL,
           orphanRemoval = true)
private List<Post> posts;
```

---

## Q5. Make this LAZY with an explicit FK column. Why LAZY?

```java
@ManyToOne
@JoinColumn(name = "user_id")
private User author;
```

**Answer:**
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_id")
private User author;
```

**Why LAZY:** `@ManyToOne` defaults to EAGER, which fires extra queries silently across the entire app — even when you don't need the related entity. LAZY avoids that, and you can opt in to fetching the author with `JOIN FETCH` or `@EntityGraph` when you actually need it.

---

# Quick Revision

- **Owning side** = has FK (`@JoinColumn`). **Inverse side** = has `mappedBy`
- **Parent / child** (conceptual ownership) ≠ **owning / inverse side** (who has the FK)
- **Always make fetch LAZY** explicitly — `@ManyToOne` defaults to EAGER (silent N+1 across all queries)
- **N+1 problem** = lazy loading in a loop OR eager loading silently. Fix with `JOIN FETCH` / `@EntityGraph` / `@BatchSize`
- **JOIN FETCH** — runs one SQL JOIN, loads both entities in a single query
- **Cascade.REMOVE** = delete parent triggers delete of children
- **orphanRemoval** = remove from parent's collection triggers delete of that child
- **orphanRemoval = false** + nullable FK → child stays in DB with `user_id = NULL`
- **orphanRemoval = false** + NOT NULL FK → constraint violation exception
- Use **both Cascade + orphanRemoval together** for "child can't exist without parent"
- Keep both sides in sync via helper methods (`addPost`, `removePost`)