# Phase 2: Diff Computation Engine

**Status:** Not Started
**Estimated Effort:** 3-5 days
**Dependencies:** Phase 1 (requires data structures from ViewEventTracker)

## Objective

Implement the core diff computation logic that identifies changed fields between two view states. This is the algorithmic heart of the partial updates feature, responsible for determining what to include in `view_update` events.

## Scope

### In Scope

- Implement `ViewDiffComputer` class with `computeDiff()` method
- Primitive field comparison (strings, integers, booleans, doubles, nulls)
- Object comparison (nested objects, recursive diff)
- Array comparison (detect newly added elements only)
- Null handling (field deletion detection)
- Comprehensive unit tests for all diff scenarios
- Performance validation (ensure <5ms for typical view)

### Out of Scope (Deferred)

- Integration with ViewEventTracker (Phase 3)
- Actual event sending (Phase 3)
- Backend update rules (backend team responsibility)
- Edge cases that require full event flow (Phase 3)

## Requirements Addressed

From the spec, this phase implements:
- **FR-2:** Subsequent updates are partial - Logic to identify only changed fields
- **FR-3:** Array optimization - Send only newly added elements for arrays
- **FR-6:** Empty diff handling - Return empty map when nothing changed
- **NFR-1:** Performance overhead - Diff computation <5ms
- **NFR-5:** Correctness - Zero false negatives, zero false positives

## Implementation Approach

### Core Algorithm

The diff algorithm compares two maps (last sent vs current) and returns a map containing only changed fields:

```kotlin
internal class ViewDiffComputer {

    /**
     * Computes the difference between last sent and current view state.
     * Returns a map containing only changed fields.
     *
     * Rules:
     * - Primitives: Include if value changed
     * - Objects: Recurse and include nested changes
     * - Arrays: Include only newly appended elements
     * - Null: Include to signal field deletion
     *
     * @param lastSent The last sent event data
     * @param current The current view state
     * @return Map containing only changed fields, empty if no changes
     */
    fun computeDiff(
        lastSent: Map<String, Any?>,
        current: Map<String, Any?>
    ): Map<String, Any?> {
        val diff = mutableMapOf<String, Any?>()

        // Check all fields in current state
        for ((key, currentValue) in current) {
            val lastValue = lastSent[key]

            when {
                // No change: skip
                currentValue == lastValue -> continue

                // Both are lists: check for appended elements
                currentValue is List<*> && lastValue is List<*> -> {
                    val newElements = getNewArrayElements(lastValue, currentValue)
                    if (newElements.isNotEmpty()) {
                        diff[key] = newElements
                    }
                }

                // Both are maps: recurse for nested objects
                currentValue is Map<*, *> && lastValue is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val nestedDiff = computeDiff(
                        lastValue as Map<String, Any?>,
                        currentValue as Map<String, Any?>
                    )
                    if (nestedDiff.isNotEmpty()) {
                        diff[key] = nestedDiff
                    }
                }

                // Field deleted (null in current, non-null in last)
                currentValue == null && lastValue != null -> {
                    diff[key] = null
                }

                // Field added (null in last, non-null in current)
                currentValue != null && lastValue == null -> {
                    diff[key] = currentValue
                }

                // All other cases: value changed
                else -> {
                    diff[key] = currentValue
                }
            }
        }

        // Check for deleted fields (present in lastSent but not in current)
        for (key in lastSent.keys) {
            if (key !in current && lastSent[key] != null) {
                diff[key] = null
            }
        }

        return diff
    }

    /**
     * For arrays, returns only newly appended elements.
     * Assumes arrays only grow (elements are not removed or reordered).
     *
     * @param lastSent Previously sent array
     * @param current Current array state
     * @return List containing only new elements, empty if array didn't grow
     */
    private fun getNewArrayElements(
        lastSent: List<*>,
        current: List<*>
    ): List<*> {
        return when {
            current.size > lastSent.size -> {
                // Return elements added at the end
                current.subList(lastSent.size, current.size)
            }
            current.size < lastSent.size -> {
                // Array shrunk (unexpected in RUM): treat as full replacement
                // This should not happen in normal RUM operation
                current
            }
            else -> {
                // Same size: check if elements are identical
                if (current == lastSent) {
                    emptyList<Any>()
                } else {
                    // Elements changed but size same: treat as full replacement
                    current
                }
            }
        }
    }

    /**
     * Helper to check if two values are deeply equal.
     * Handles nested structures correctly.
     */
    private fun deepEquals(a: Any?, b: Any?): Boolean {
        return when {
            a === b -> true
            a == null || b == null -> a == b
            a is Map<*, *> && b is Map<*, *> -> mapsDeepEquals(a, b)
            a is List<*> && b is List<*> -> listsDeepEquals(a, b)
            else -> a == b
        }
    }

    private fun mapsDeepEquals(a: Map<*, *>, b: Map<*, *>): Boolean {
        if (a.size != b.size) return false
        return a.all { (key, value) -> deepEquals(value, b[key]) }
    }

    private fun listsDeepEquals(a: List<*>, b: List<*>): Boolean {
        if (a.size != b.size) return false
        return a.indices.all { deepEquals(a[it], b[it]) }
    }
}
```

### Key Design Decisions

1. **Recursive approach for nested objects:** Preserves structure and only includes changed nested fields
2. **Array append-only assumption:** Optimized for RUM use case where arrays grow monotonically
3. **Null semantics:** `null` in diff means "delete this field" (for optional fields)
4. **Empty diff optimization:** Returns empty map when nothing changed (caller can skip sending event)

## Key Components

- **ViewDiffComputer:** Main class with diff logic
- **computeDiff():** Primary public method
- **getNewArrayElements():** Array-specific diff logic
- **deepEquals():** Deep equality checking for complex types

## Acceptance Criteria

- [ ] `ViewDiffComputer` class implemented
- [ ] `computeDiff()` correctly identifies changed primitive fields
- [ ] `computeDiff()` correctly handles nested objects (recursive diff)
- [ ] `computeDiff()` correctly handles arrays (new elements only)
- [ ] `computeDiff()` correctly handles null values (field deletion)
- [ ] `computeDiff()` returns empty map when nothing changed
- [ ] Array shrinking case handled (though not expected in RUM)
- [ ] Performance: diff computation takes <5ms for typical view (100-150 fields)
- [ ] Unit tests achieve >95% code coverage
- [ ] All edge cases tested
- [ ] Code review completed

## Testing Strategy

### Unit Tests

**Primitive Field Comparison:**
```kotlin
@Test
fun `detects changed string field`() {
    val lastSent = mapOf("url" to "https://old.com")
    val current = mapOf("url" to "https://new.com")

    val diff = computer.computeDiff(lastSent, current)

    assertEquals(mapOf("url" to "https://new.com"), diff)
}

@Test
fun `detects changed integer field`() {
    val lastSent = mapOf("time_spent" to 100)
    val current = mapOf("time_spent" to 200)

    val diff = computer.computeDiff(lastSent, current)

    assertEquals(mapOf("time_spent" to 200), diff)
}

@Test
fun `ignores unchanged fields`() {
    val lastSent = mapOf("url" to "https://example.com", "time_spent" to 100)
    val current = mapOf("url" to "https://example.com", "time_spent" to 100)

    val diff = computer.computeDiff(lastSent, current)

    assertTrue(diff.isEmpty())
}

@Test
fun `detects multiple changed fields`() {
    val lastSent = mapOf("url" to "https://old.com", "time_spent" to 100, "count" to 5)
    val current = mapOf("url" to "https://new.com", "time_spent" to 200, "count" to 5)

    val diff = computer.computeDiff(lastSent, current)

    assertEquals(mapOf("url" to "https://new.com", "time_spent" to 200), diff)
}
```

**Nested Object Comparison:**
```kotlin
@Test
fun `detects changed nested field`() {
    val lastSent = mapOf("view" to mapOf("action" to mapOf("count" to 0)))
    val current = mapOf("view" to mapOf("action" to mapOf("count" to 1)))

    val diff = computer.computeDiff(lastSent, current)

    assertEquals(
        mapOf("view" to mapOf("action" to mapOf("count" to 1))),
        diff
    )
}

@Test
fun `preserves unchanged nested fields`() {
    val lastSent = mapOf(
        "view" to mapOf(
            "action" to mapOf("count" to 1),
            "error" to mapOf("count" to 0)
        )
    )
    val current = mapOf(
        "view" to mapOf(
            "action" to mapOf("count" to 2),
            "error" to mapOf("count" to 0)
        )
    )

    val diff = computer.computeDiff(lastSent, current)

    // Only changed nested field included
    assertEquals(
        mapOf("view" to mapOf("action" to mapOf("count" to 2))),
        diff
    )
}

@Test
fun `handles deeply nested changes`() {
    val lastSent = mapOf(
        "view" to mapOf(
            "performance" to mapOf(
                "lcp" to mapOf("timestamp" to 100)
            )
        )
    )
    val current = mapOf(
        "view" to mapOf(
            "performance" to mapOf(
                "lcp" to mapOf("timestamp" to 341)
            )
        )
    )

    val diff = computer.computeDiff(lastSent, current)

    assertEquals(
        mapOf("view" to mapOf("performance" to mapOf("lcp" to mapOf("timestamp" to 341)))),
        diff
    )
}
```

**Array Comparison:**
```kotlin
@Test
fun `detects new array elements`() {
    val lastSent = mapOf(
        "slow_frames" to listOf(
            mapOf("start" to 100, "duration" to 20)
        )
    )
    val current = mapOf(
        "slow_frames" to listOf(
            mapOf("start" to 100, "duration" to 20),
            mapOf("start" to 250, "duration" to 15)
        )
    )

    val diff = computer.computeDiff(lastSent, current)

    assertEquals(
        mapOf("slow_frames" to listOf(mapOf("start" to 250, "duration" to 15))),
        diff
    )
}

@Test
fun `returns only new array elements, not entire array`() {
    val lastSent = mapOf("frames" to listOf(1, 2, 3))
    val current = mapOf("frames" to listOf(1, 2, 3, 4, 5))

    val diff = computer.computeDiff(lastSent, current)

    assertEquals(mapOf("frames" to listOf(4, 5)), diff)
}

@Test
fun `ignores unchanged arrays`() {
    val lastSent = mapOf("frames" to listOf(1, 2, 3))
    val current = mapOf("frames" to listOf(1, 2, 3))

    val diff = computer.computeDiff(lastSent, current)

    assertTrue(diff.isEmpty())
}

@Test
fun `handles empty arrays`() {
    val lastSent = mapOf("frames" to emptyList<Any>())
    val current = mapOf("frames" to listOf(1, 2))

    val diff = computer.computeDiff(lastSent, current)

    assertEquals(mapOf("frames" to listOf(1, 2)), diff)
}

@Test
fun `handles array shrinking (edge case)`() {
    // This shouldn't happen in RUM, but test the behavior
    val lastSent = mapOf("frames" to listOf(1, 2, 3))
    val current = mapOf("frames" to listOf(1, 2))

    val diff = computer.computeDiff(lastSent, current)

    // When array shrinks, send full current array
    assertEquals(mapOf("frames" to listOf(1, 2)), diff)
}
```

**Null Handling:**
```kotlin
@Test
fun `detects field deletion (null in current)`() {
    val lastSent = mapOf("loading_time" to 200)
    val current = mapOf("loading_time" to null)

    val diff = computer.computeDiff(lastSent, current)

    assertEquals(mapOf("loading_time" to null), diff)
}

@Test
fun `detects field addition (null in last)`() {
    val lastSent = mapOf("url" to "https://example.com")
    val current = mapOf("url" to "https://example.com", "loading_time" to 200)

    val diff = computer.computeDiff(lastSent, current)

    assertEquals(mapOf("loading_time" to 200), diff)
}

@Test
fun `handles both values null as no change`() {
    val lastSent = mapOf("optional_field" to null)
    val current = mapOf("optional_field" to null)

    val diff = computer.computeDiff(lastSent, current)

    assertTrue(diff.isEmpty())
}
```

**Complex Scenarios:**
```kotlin
@Test
fun `handles mix of primitives, objects, and arrays`() {
    val lastSent = mapOf(
        "time_spent" to 100,
        "view" to mapOf("action" to mapOf("count" to 1)),
        "slow_frames" to listOf(mapOf("start" to 100))
    )
    val current = mapOf(
        "time_spent" to 200,
        "view" to mapOf("action" to mapOf("count" to 1)),
        "slow_frames" to listOf(
            mapOf("start" to 100),
            mapOf("start" to 250)
        )
    )

    val diff = computer.computeDiff(lastSent, current)

    assertEquals(
        mapOf(
            "time_spent" to 200,
            "slow_frames" to listOf(mapOf("start" to 250))
        ),
        diff
    )
}

@Test
fun `returns empty map when nothing changed`() {
    val viewData = mapOf(
        "time_spent" to 100,
        "view" to mapOf("action" to mapOf("count" to 1)),
        "slow_frames" to listOf(mapOf("start" to 100))
    )

    val diff = computer.computeDiff(viewData, viewData)

    assertTrue(diff.isEmpty())
}
```

**Performance Tests:**
```kotlin
@Test
fun `diff computation completes in under 5ms for typical view`() {
    // Create a typical view with 100-150 fields
    val typicalView = generateTypicalViewData(fieldCount = 150)
    val updatedView = typicalView.toMutableMap().apply {
        put("time_spent", 200)
        put("action_count", 5)
    }

    val startTime = System.nanoTime()
    val diff = computer.computeDiff(typicalView, updatedView)
    val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0

    assertTrue(elapsedMs < 5.0, "Diff took ${elapsedMs}ms, expected <5ms")
    assertEquals(2, diff.size)
}
```

## Open Questions

1. **Array element equality:**
   - How do we compare complex objects in arrays for equality?
   - Should we use deep equality or reference equality?
   - Impact on performance for large arrays?

2. **Maximum array size:**
   - Should we limit how many new elements can be sent in one update?
   - What if 1000s of slow frames are detected?

3. **Custom object handling:**
   - The spec mentions custom objects (context, feature_flags) follow REPLACE rule on backend
   - Should our diff logic treat them differently, or always send full object?
   - Current implementation diffs them like standard objects - is this correct?

4. **Type safety:**
   - Current implementation uses `Map<String, Any?>` for flexibility
   - Should we use stronger typing with data classes?
   - Trade-off between type safety and flexibility?

## Dependencies

**Phase 1 Complete:**
- Data structures defined in ViewEventTracker
- Configuration flag available

**External:**
- None (pure computation logic)

## Deliverables

- [ ] `ViewDiffComputer` class implemented
- [ ] Unit tests for all diff scenarios (>95% coverage)
- [ ] Performance benchmarks (<5ms validation)
- [ ] Code documentation (KDoc comments)
- [ ] Phase 2 code review completed

## Next Phase

**Phase 3: Event Flow Integration** will integrate this diff computation with ViewEventTracker and implement the actual event sending logic for `view` and `view_update` events.
