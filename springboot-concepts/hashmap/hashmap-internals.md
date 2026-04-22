# HashMap Internals

## Structure
- HashMap is an **array of buckets** (default 16 buckets)
- Each bucket holds a **linked list of Nodes** (to handle collisions)
- Each Node contains: `key`, `value`, `hash`, `next` (pointer to next node)

```
Buckets:  [0] [1] [2] ... [11] ... [15]
                            │
                     Node("name","Murali")
                            │
                     Node("city","tokyo")
                            │
                          null
```

## How put(key, value) Works
1. **Hash the key:** `int hash = key.hashCode()`
2. **Find the bucket:** `int index = hash & (capacity - 1)` (bitwise AND, faster than modulo)
3. **If bucket is empty:** insert the node directly
4. **If bucket has nodes (collision):** walk the linked list
   - If `key.equals(existingNode.key)` → **update** the value (same key = overwrite)
   - If you reach the end without finding the key → **append** new node

## How get(key) Works
1. **Hash the key** → find the bucket index
2. **If bucket is empty** → return null
3. **If bucket has nodes** → walk the linked list, compare each node's key using `.equals()`
4. **Found** → return value. **Not found** → return null.

## Collision Handling
Two different keys can hash to the same bucket index.
- Example: "name" and "city" both hash to bucket 11
- They're stored as a linked list chain in that bucket
- `hashCode()` finds the **bucket**, `equals()` finds the **exact key** within the chain

### Java 8 Improvement: Treeification
- When a bucket's chain exceeds **8 nodes** → converts linked list to **Red-Black Tree**
- Lookup improves from O(n) to O(log n) for that bucket
- When it shrinks below **6 nodes** → converts back to linked list

## Resizing
- **Capacity:** number of buckets (default 16)
- **Load factor:** threshold for resizing (default 0.75)
- When `size > capacity × loadFactor` (e.g., 12 items in 16 buckets) → **resize**
- Capacity doubles (16 → 32), all entries are **rehashed** into new positions
- Rehashing is O(n) — expensive. Set initial capacity if you know the size upfront.

## hashCode() + equals() Contract
| Rule | What it means |
|---|---|
| `a.equals(b)` is true | `a.hashCode()` **must** equal `b.hashCode()` |
| `a.hashCode() == b.hashCode()` | `a.equals(b)` is **NOT necessarily** true (collision) |
| Override `equals()` | You **must** also override `hashCode()` |

**Why?**
- HashMap uses `hashCode()` to pick the bucket
- Then uses `equals()` to find the exact key in the chain
- If equal objects have different hashCodes → they land in different buckets → you put a key in but can never find it again

## Key Performance Characteristics
| Operation | Average Case | Worst Case (all keys in one bucket) |
|---|---|---|
| put | O(1) | O(n) or O(log n) with treeification |
| get | O(1) | O(n) or O(log n) with treeification |
| remove | O(1) | O(n) or O(log n) with treeification |

## Learned By Building
Built a simplified HashMap from scratch in `HashMapImpl.java`:
- Array of 16 buckets
- Node class with key, value, next
- put: hash → index → check for duplicate key (walk chain) → insert or update
- get: hash → index → walk chain → compare with equals() → return value or null
- Proved collision handling: "name" and "city" both mapped to bucket 11, correctly stored and retrieved as separate entries