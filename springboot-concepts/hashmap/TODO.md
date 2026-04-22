# HashMap — Next Steps

## Status
- [x] Built basic HashMap from scratch
  - Array of buckets (default 16)
  - Node class with key, value, next
  - put with collision handling (chain walk + duplicate key update)
  - get with chain walk using equals()
- [x] Tested with collisions — "name" and "city" both land in bucket 11, correctly stored/retrieved
- [x] Documented internals in `hashmap-internals.md`

## Pending

### 1. Implement Resizing
- Add `size` field (increment on every new key added)
- Add `LOAD_FACTOR = 0.75`
- After each `put`, check: `if (size > capacity * LOAD_FACTOR)` → call `resize()`
- `resize()`:
  - Create new array with **double the capacity**
  - Walk through every node in the old array
  - **Rehash** each node into the new array — `hash & (newCapacity - 1)` gives different indices
  - Replace old array with new array
- Tip: change `DEFAULT_CAPACITY = 4` for easy testing — triggers resize after 3 entries

### 2. (Optional, Advanced) Treeification
- When a bucket's chain exceeds 8 nodes → convert linked list to Red-Black Tree
- When it shrinks below 6 nodes → convert back
- This is what real Java 8+ HashMap does. Skip for now unless you want the challenge.

### 3. Interview Round
- After completing resizing, take the HashMap interview
- Topics to review: hashCode + equals contract, collision handling, treeification, resizing, load factor

## Key Questions to Answer
- Why is `hash & (capacity - 1)` used instead of `hash % capacity`?
- Why does capacity have to be a power of 2?
- Why is rehashing needed during resize?
- What happens if hashCode() is inconsistent with equals()?