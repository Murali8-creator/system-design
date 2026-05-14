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

**Only the owning side controls persistence.** Changes to the inverse side's collection alone don't always update the DB. Best practice — keep both sides in sync:

```java
// in User class
public void addPost(Post post) {
    posts.add(post);
    post.setAuthor(this);
}
```

---

## Lazy vs Eager Loading

| Annotation | Default | Recommendation |
|---|---|---|
| `@OneToMany` | LAZY | Keep LAZY |
| `@ManyToMany` | LAZY | Keep LAZY |
| `@ManyToOne` | **EAGER** ⚠️ | **Override to LAZY** |
| `@OneToOne` | EAGER | Override to LAZY |

### Why EAGER `@ManyToOne` is Dangerous
- Fires extra queries silently on **every** query in your app
- Cascades: if `Post` eagerly loads `User`, and `User` eagerly loads `Profile`, one `findAll()` becomes a join nightmare
- You pay for joins you didn't ask for

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

### Fix 1: JOIN FETCH (custom query)
```java
@Query("SELECT p FROM Post p JOIN FETCH p.author")
List<Post> findAllWithAuthor();
```
Translates to one SQL JOIN:
```sql
SELECT p.*, u.* FROM post p INNER JOIN user u ON p.user_id = u.id
```

### Fix 2: @EntityGraph (declarative)
```java
@EntityGraph(attributePaths = {"author"})
List<Post> findAll();
```
Same effect, no custom query.

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
// orphanRemoval = false → p1 stays
```

### orphanRemoval = false: what happens?

Depends on whether the FK is nullable:

| `user_id` nullable? | Result |
|---|---|
| Yes (default) | `user_id` set to NULL, post stays |
| No (`nullable = false`) | Exception — constraint violation |

This is often why teams enable `orphanRemoval = true` for "child can't exist without parent" relationships.

### Important Catch
The `Post` is the **owning side** (has the FK). For the change to actually persist:
```java
user.getPosts().remove(p1);
p1.setAuthor(null);              // ← also clear the owning side
userRepo.save(user);
```

---

## CascadeType.REMOVE vs orphanRemoval

| | Trigger | Effect |
|---|---|---|
| `CascadeType.REMOVE` | Delete the **parent** | All children deleted |
| `orphanRemoval = true` | Remove child from **parent's collection** | That child deleted |

**Different triggers. Different effects. Often used together** for tight "child belongs to parent" relationships:

```java
@OneToMany(mappedBy = "author",
           cascade = CascadeType.ALL,
           orphanRemoval = true)
private List<Post> posts;
```

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

## Quick Revision

- **Owning side** = has FK (`@JoinColumn`). **Inverse side** = has `mappedBy`
- **Always make fetch LAZY** explicitly — `@ManyToOne` defaults to EAGER (bad)
- **N+1 problem** = lazy loading in a loop. Fix with `JOIN FETCH` / `@EntityGraph` / `@BatchSize`
- **Cascade.REMOVE** = delete parent triggers delete of children
- **orphanRemoval** = remove from parent's collection triggers delete of that child
- Use **both together** for "child can't exist without parent" relationships
- Keep both sides in sync via helper methods (`addPost`, `removePost`)