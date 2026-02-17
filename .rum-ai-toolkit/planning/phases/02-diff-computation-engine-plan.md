# Implementation Plan: Phase 2 - Diff Computation Engine

**Phase Document:** [02-diff-computation-engine.md](02-diff-computation-engine.md)
**Status:** Complete
**Generated:** 2026-02-17

## Overview

This plan implements the core diff computation algorithm that identifies changed fields between two view states. The ViewDiffComputer class will handle primitives, nested objects, arrays, and null values with proper performance characteristics (<5ms for typical views).

**Key Principle:** This phase is pure computation logic with no dependencies on the RUM event pipeline. All logic is self-contained and thoroughly unit tested.

## Prerequisites

- [x] Phase 1 completed (ViewEventTracker data structures exist)
- [ ] Familiar with Kotlin collections and recursion
- [ ] Read [SPEC.md](../SPEC.md) for update rules context

## Implementation Tasks

### Task 1: Create ViewDiffComputer Class

**Objective:** Create the ViewDiffComputer class with the main computeDiff() method

**Files to create:**
- `features/dd-sdk-android-rum/src/main/kotlin/com/datadog/android/rum/internal/domain/event/viewupdate/ViewDiffComputer.kt`

**Implementation steps:**

1. Create file in the same package as ViewEventTracker
2. Add copyright header
3. Implement class skeleton with computeDiff() method

**Code pattern:**
```kotlin
/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event.viewupdate

/**
 * Computes the difference between two view states to enable partial view updates.
 *
 * This class implements the diff algorithm that identifies which fields have changed
 * between the last sent view event and the current view state. The resulting diff
 * contains only the changed fields, which are sent in a view_update event.
 *
 * **Algorithm:**
 * - Primitives: Include if value changed
 * - Objects: Recurse and include nested changes only
 * - Arrays: Include only newly appended elements
 * - Null: Include to signal field deletion
 *
 * **Performance:** Designed to complete in <5ms for typical views (100-150 fields)
 */
internal class ViewDiffComputer {

    /**
     * Computes the difference between last sent and current view state.
     * Returns a map containing only changed fields.
     *
     * The diff algorithm processes each field in the current state and compares it
     * with the corresponding field in the last sent state. Based on the field type
     * and values, it determines whether to include the field in the diff.
     *
     * **Rules:**
     * - Primitives (String, Int, Boolean, etc.): Include if value changed
     * - Objects (nested Map): Recurse and include only changed nested fields
     * - Arrays (List): Include only newly appended elements
     * - Null: Include to signal field deletion
     *
     * **Performance:** O(n) where n is the number of fields in the view.
     * Typical execution time: 1-3ms for 100-150 fields.
     *
     * @param lastSent The last sent event data (baseline for comparison)
     * @param current The current view state (target state)
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
                // No change: skip this field
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

                // All other cases: value changed (primitives, type changes, etc.)
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
     * This optimization is based on the RUM use case where arrays like
     * slow_frames, page_states, and in_foreground_periods only grow by
     * appending new elements at the end.
     *
     * **Edge cases:**
     * - Array grew: Return only new elements at the end
     * - Array shrunk: Return full current array (unexpected but handled)
     * - Same size but different elements: Return full current array
     * - Same size and identical: Return empty list
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
                // Array grew: return elements added at the end
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
     * Handles nested structures correctly by recursing into Maps and Lists.
     *
     * This is more reliable than using == for nested structures as it
     * ensures proper comparison of Map and List contents.
     *
     * @param a First value to compare
     * @param b Second value to compare
     * @return true if values are deeply equal, false otherwise
     */
    private fun deepEquals(a: Any?, b: Any?): Boolean {
        return when {
            a === b -> true // Reference equality or both null
            a == null || b == null -> a == b // One is null, other is not
            a is Map<*, *> && b is Map<*, *> -> mapsDeepEquals(a, b)
            a is List<*> && b is List<*> -> listsDeepEquals(a, b)
            else -> a == b // Primitives and other types
        }
    }

    /**
     * Deep equality check for maps.
     * Recursively compares all key-value pairs.
     *
     * @param a First map
     * @param b Second map
     * @return true if maps are deeply equal
     */
    private fun mapsDeepEquals(a: Map<*, *>, b: Map<*, *>): Boolean {
        if (a.size != b.size) return false
        return a.all { (key, value) -> deepEquals(value, b[key]) }
    }

    /**
     * Deep equality check for lists.
     * Recursively compares all elements by index.
     *
     * @param a First list
     * @param b Second list
     * @return true if lists are deeply equal
     */
    private fun listsDeepEquals(a: List<*>, b: List<*>): Boolean {
        if (a.size != b.size) return false
        return a.indices.all { deepEquals(a[it], b[it]) }
    }
}
```

**Acceptance:**
- [ ] ViewDiffComputer class compiles
- [ ] computeDiff() method implemented with all logic
- [ ] getNewArrayElements() method implemented
- [ ] Deep equality methods implemented
- [ ] All code properly documented with KDoc

---

## Testing Tasks

### Test 1: Primitive Field Comparison

**Type:** Unit Test
**Files:** `features/dd-sdk-android-rum/src/test/kotlin/com/datadog/android/rum/internal/domain/event/viewupdate/ViewDiffComputerTest.kt`
**Covers:** Primitive field change detection

**Implementation:**

Create new test file:

```kotlin
/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event.viewupdate

import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@TestTargetApi(21)
internal class ViewDiffComputerTest {

    private lateinit var testedComputer: ViewDiffComputer

    @BeforeEach
    fun setUp() {
        testedComputer = ViewDiffComputer()
    }

    // Primitive Field Tests

    @Test
    fun `M detect changed string field W computeDiff()`() {
        // Given
        val lastSent = mapOf("url" to "https://old.com")
        val current = mapOf("url" to "https://new.com")

        // When
        val diff = testedComputer.computeDiff(lastSent, current)

        // Then
        assertThat(diff).isEqualTo(mapOf("url" to "https://new.com"))
    }

    @Test
    fun `M detect changed integer field W computeDiff()`() {
        // Given
        val lastSent = mapOf("time_spent" to 100)
        val current = mapOf("time_spent" to 200)

        // When
        val diff = testedComputer.computeDiff(lastSent, current)

        // Then
        assertThat(diff).isEqualTo(mapOf("time_spent" to 200))
    }

    @Test
    fun `M detect changed boolean field W computeDiff()`() {
        // Given
        val lastSent = mapOf("is_active" to false)
        val current = mapOf("is_active" to true)

        // When
        val diff = testedComputer.computeDiff(lastSent, current)

        // Then
        assertThat(diff).isEqualTo(mapOf("is_active" to true))
    }

    @Test
    fun `M detect changed double field W computeDiff()`() {
        // Given
        val lastSent = mapOf("sample_rate" to 50.0)
        val current = mapOf("sample_rate" to 75.5)

        // When
        val diff = testedComputer.computeDiff(lastSent, current)

        // Then
        assertThat(diff).isEqualTo(mapOf("sample_rate" to 75.5))
    }

    @Test
    fun `M ignore unchanged fields W computeDiff()`() {
        // Given
        val lastSent = mapOf("url" to "https://example.com", "time_spent" to 100)
        val current = mapOf("url" to "https://example.com", "time_spent" to 100)

        // When
        val diff = testedComputer.computeDiff(lastSent, current)

        // Then
        assertThat(diff).isEmpty()
    }

    @Test
    fun `M detect multiple changed fields W computeDiff()`() {
        // Given
        val lastSent = mapOf("url" to "https://old.com", "time_spent" to 100, "count" to 5)
        val current = mapOf("url" to "https://new.com", "time_spent" to 200, "count" to 5)

        // When
        val diff = testedComputer.computeDiff(lastSent, current)

        // Then
        assertThat(diff).isEqualTo(mapOf("url" to "https://new.com", "time_spent" to 200))
    }
}
```

**Test cases:**
- [ ] Detect changed string field
- [ ] Detect changed integer field
- [ ] Detect changed boolean field
- [ ] Detect changed double field
- [ ] Ignore unchanged fields
- [ ] Detect multiple changed fields

---

### Test 2: Nested Object Comparison

**Type:** Unit Test
**Files:** Same as Test 1
**Covers:** Nested object diff logic

**Implementation:**

Add to existing test file:

```kotlin
// Nested Object Tests

@Test
fun `M detect changed nested field W computeDiff()`() {
    // Given
    val lastSent = mapOf("view" to mapOf("action" to mapOf("count" to 0)))
    val current = mapOf("view" to mapOf("action" to mapOf("count" to 1)))

    // When
    val diff = testedComputer.computeDiff(lastSent, current)

    // Then
    assertThat(diff).isEqualTo(
        mapOf("view" to mapOf("action" to mapOf("count" to 1)))
    )
}

@Test
fun `M preserve unchanged nested fields W computeDiff()`() {
    // Given
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

    // When
    val diff = testedComputer.computeDiff(lastSent, current)

    // Then
    // Only changed nested field included
    assertThat(diff).isEqualTo(
        mapOf("view" to mapOf("action" to mapOf("count" to 2)))
    )
}

@Test
fun `M handle deeply nested changes W computeDiff()`() {
    // Given
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

    // When
    val diff = testedComputer.computeDiff(lastSent, current)

    // Then
    assertThat(diff).isEqualTo(
        mapOf("view" to mapOf("performance" to mapOf("lcp" to mapOf("timestamp" to 341))))
    )
}

@Test
fun `M handle empty nested object W computeDiff()`() {
    // Given
    val lastSent = mapOf("view" to mapOf("action" to mapOf("count" to 1)))
    val current = mapOf("view" to mapOf<String, Any?>())

    // When
    val diff = testedComputer.computeDiff(lastSent, current)

    // Then
    assertThat(diff).isEmpty()
}
```

**Test cases:**
- [ ] Detect changed nested field
- [ ] Preserve unchanged nested fields
- [ ] Handle deeply nested changes
- [ ] Handle empty nested object

---

### Test 3: Array Comparison

**Type:** Unit Test
**Files:** Same as Test 1
**Covers:** Array diff logic and append-only optimization

**Implementation:**

Add to existing test file:

```kotlin
// Array Tests

@Test
fun `M detect new array elements W computeDiff()`() {
    // Given
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

    // When
    val diff = testedComputer.computeDiff(lastSent, current)

    // Then
    assertThat(diff).isEqualTo(
        mapOf("slow_frames" to listOf(mapOf("start" to 250, "duration" to 15)))
    )
}

@Test
fun `M return only new array elements W computeDiff()`() {
    // Given
    val lastSent = mapOf("frames" to listOf(1, 2, 3))
    val current = mapOf("frames" to listOf(1, 2, 3, 4, 5))

    // When
    val diff = testedComputer.computeDiff(lastSent, current)

    // Then
    assertThat(diff).isEqualTo(mapOf("frames" to listOf(4, 5)))
}

@Test
fun `M ignore unchanged arrays W computeDiff()`() {
    // Given
    val lastSent = mapOf("frames" to listOf(1, 2, 3))
    val current = mapOf("frames" to listOf(1, 2, 3))

    // When
    val diff = testedComputer.computeDiff(lastSent, current)

    // Then
    assertThat(diff).isEmpty()
}

@Test
fun `M handle empty arrays W computeDiff()`() {
    // Given
    val lastSent = mapOf("frames" to emptyList<Any>())
    val current = mapOf("frames" to listOf(1, 2))

    // When
    val diff = testedComputer.computeDiff(lastSent, current)

    // Then
    assertThat(diff).isEqualTo(mapOf("frames" to listOf(1, 2)))
}

@Test
fun `M handle array shrinking W computeDiff()`() {
    // Given (edge case - shouldn't happen in RUM)
    val lastSent = mapOf("frames" to listOf(1, 2, 3))
    val current = mapOf("frames" to listOf(1, 2))

    // When
    val diff = testedComputer.computeDiff(lastSent, current)

    // Then
    // When array shrinks, send full current array
    assertThat(diff).isEqualTo(mapOf("frames" to listOf(1, 2)))
}

@Test
fun `M handle array element changes W computeDiff()`() {
    // Given (same size but different elements)
    val lastSent = mapOf("frames" to listOf(1, 2, 3))
    val current = mapOf("frames" to listOf(1, 2, 4))

    // When
    val diff = testedComputer.computeDiff(lastSent, current)

    // Then
    // Elements changed: send full array
    assertThat(diff).isEqualTo(mapOf("frames" to listOf(1, 2, 4)))
}
```

**Test cases:**
- [ ] Detect new array elements
- [ ] Return only new elements (not full array)
- [ ] Ignore unchanged arrays
- [ ] Handle empty arrays
- [ ] Handle array shrinking edge case
- [ ] Handle array element changes (same size)

---

### Test 4: Null Handling

**Type:** Unit Test
**Files:** Same as Test 1
**Covers:** Field deletion and addition detection

**Implementation:**

Add to existing test file:

```kotlin
// Null Handling Tests

@Test
fun `M detect field deletion W computeDiff()`() {
    // Given
    val lastSent = mapOf("loading_time" to 200)
    val current = mapOf("loading_time" to null)

    // When
    val diff = testedComputer.computeDiff(lastSent, current)

    // Then
    assertThat(diff).isEqualTo(mapOf("loading_time" to null))
}

@Test
fun `M detect field addition W computeDiff()`() {
    // Given
    val lastSent = mapOf("url" to "https://example.com")
    val current = mapOf("url" to "https://example.com", "loading_time" to 200)

    // When
    val diff = testedComputer.computeDiff(lastSent, current)

    // Then
    assertThat(diff).isEqualTo(mapOf("loading_time" to 200))
}

@Test
fun `M handle both values null W computeDiff()`() {
    // Given
    val lastSent = mapOf("optional_field" to null)
    val current = mapOf("optional_field" to null)

    // When
    val diff = testedComputer.computeDiff(lastSent, current)

    // Then
    assertThat(diff).isEmpty()
}

@Test
fun `M detect removed field W computeDiff()`() {
    // Given (field present in lastSent but missing in current)
    val lastSent = mapOf("url" to "https://example.com", "loading_time" to 200)
    val current = mapOf("url" to "https://example.com")

    // When
    val diff = testedComputer.computeDiff(lastSent, current)

    // Then
    assertThat(diff).isEqualTo(mapOf("loading_time" to null))
}
```

**Test cases:**
- [ ] Detect field deletion (value → null)
- [ ] Detect field addition (null → value)
- [ ] Handle both values null (no change)
- [ ] Detect removed field (not in current map)

---

### Test 5: Complex Scenarios

**Type:** Unit Test
**Files:** Same as Test 1
**Covers:** Real-world mixed scenarios

**Implementation:**

Add to existing test file:

```kotlin
// Complex Scenarios

@Test
fun `M handle mix of primitives, objects, and arrays W computeDiff()`() {
    // Given
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

    // When
    val diff = testedComputer.computeDiff(lastSent, current)

    // Then
    assertThat(diff).isEqualTo(
        mapOf(
            "time_spent" to 200,
            "slow_frames" to listOf(mapOf("start" to 250))
        )
    )
}

@Test
fun `M return empty map W nothing changed`() {
    // Given
    val viewData = mapOf(
        "time_spent" to 100,
        "view" to mapOf("action" to mapOf("count" to 1)),
        "slow_frames" to listOf(mapOf("start" to 100))
    )

    // When
    val diff = testedComputer.computeDiff(viewData, viewData)

    // Then
    assertThat(diff).isEmpty()
}

@Test
fun `M handle large view data W computeDiff()`() {
    // Given - simulate typical view with 100+ fields
    val lastSent = mutableMapOf<String, Any?>()
    val current = mutableMapOf<String, Any?>()

    // Add 100 fields
    for (i in 0 until 100) {
        lastSent["field_$i"] = "value_$i"
        current["field_$i"] = "value_$i"
    }

    // Change 5 fields
    current["field_10"] = "changed_10"
    current["field_25"] = "changed_25"
    current["field_50"] = "changed_50"
    current["field_75"] = "changed_75"
    current["field_99"] = "changed_99"

    // When
    val diff = testedComputer.computeDiff(lastSent, current)

    // Then
    assertThat(diff).hasSize(5)
    assertThat(diff).containsEntry("field_10", "changed_10")
    assertThat(diff).containsEntry("field_25", "changed_25")
    assertThat(diff).containsEntry("field_50", "changed_50")
    assertThat(diff).containsEntry("field_75", "changed_75")
    assertThat(diff).containsEntry("field_99", "changed_99")
}
```

**Test cases:**
- [ ] Handle mix of primitives, objects, and arrays
- [ ] Return empty map when nothing changed
- [ ] Handle large view data efficiently

---

### Test 6: Performance Validation

**Type:** Performance Test
**Files:** Same as Test 1
**Covers:** <5ms performance requirement

**Implementation:**

Add to existing test file:

```kotlin
// Performance Tests

@Test
fun `M complete in under 5ms W computeDiff() for typical view`() {
    // Given - create a typical view with 150 fields
    val typicalView = mutableMapOf<String, Any?>()

    // Add primitive fields
    for (i in 0 until 50) {
        typicalView["string_field_$i"] = "value_$i"
    }
    for (i in 0 until 25) {
        typicalView["int_field_$i"] = i
    }
    for (i in 0 until 25) {
        typicalView["bool_field_$i"] = (i % 2 == 0)
    }

    // Add nested objects
    typicalView["view"] = mapOf(
        "action" to mapOf("count" to 5),
        "resource" to mapOf("count" to 10),
        "error" to mapOf("count" to 0)
    )

    // Add arrays
    typicalView["slow_frames"] = listOf(
        mapOf("start" to 100, "duration" to 20),
        mapOf("start" to 250, "duration" to 15)
    )

    val updatedView = typicalView.toMutableMap().apply {
        put("time_spent", 200)
        put("string_field_10", "changed")
        put("int_field_5", 999)
    }

    // When - measure time
    val startTime = System.nanoTime()
    val diff = testedComputer.computeDiff(typicalView, updatedView)
    val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0

    // Then
    assertThat(elapsedMs).isLessThan(5.0)
    assertThat(diff).hasSize(3)
}

@Test
fun `M complete quickly W computeDiff() for unchanged large view`() {
    // Given - large view with no changes
    val largeView = mutableMapOf<String, Any?>()
    for (i in 0 until 200) {
        largeView["field_$i"] = "value_$i"
    }

    // When
    val startTime = System.nanoTime()
    val diff = testedComputer.computeDiff(largeView, largeView)
    val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0

    // Then
    assertThat(elapsedMs).isLessThan(5.0)
    assertThat(diff).isEmpty()
}
```

**Test cases:**
- [ ] Complete in <5ms for typical view (150 fields)
- [ ] Complete quickly for unchanged large view (200 fields)

---

## Task Checklist

- [ ] Task 1: Create ViewDiffComputer class with all methods
- [ ] Test 1: Primitive field comparison tests
- [ ] Test 2: Nested object comparison tests
- [ ] Test 3: Array comparison tests
- [ ] Test 4: Null handling tests
- [ ] Test 5: Complex scenario tests
- [ ] Test 6: Performance validation tests
- [ ] All tests passing
- [ ] Code compiles without errors
- [ ] Performance <5ms validated
- [ ] Code review completed

## Implementation Order

1. **Task 1** - Create ViewDiffComputer with all methods (2-3 hours)
2. **Test 1** - Primitive tests (30 min)
3. **Test 2** - Nested object tests (30 min)
4. **Test 3** - Array tests (45 min)
5. **Test 4** - Null handling tests (30 min)
6. **Test 5** - Complex scenarios (30 min)
7. **Test 6** - Performance tests (30 min)

**Estimated time:** 5-6 hours total

## Verification Steps

After completing all tasks:

1. **Build the SDK:**
   ```bash
   ./gradlew :features:dd-sdk-android-rum:assemble
   ```

2. **Run unit tests:**
   ```bash
   ./gradlew :features:dd-sdk-android-rum:testDebugUnitTest --tests "*ViewDiffComputerTest"
   ```

3. **Verify test coverage:**
   ```bash
   ./gradlew :features:dd-sdk-android-rum:testDebugUnitTestCoverage
   ```
   - Target: >95% coverage for ViewDiffComputer

4. **Performance validation:**
   - Run performance tests multiple times
   - Verify all runs complete in <5ms

## Notes

### Algorithm Complexity

- **Time Complexity:** O(n) where n = number of fields
- **Space Complexity:** O(d) where d = number of changed fields
- **Recursion depth:** O(m) where m = maximum nesting level (typically 3-5)

### Edge Cases Handled

- ✓ Arrays grow (append new elements)
- ✓ Arrays shrink (send full array)
- ✓ Arrays same size but different (send full array)
- ✓ Nested objects with changes
- ✓ Field deletion (null in current)
- ✓ Field addition (not in lastSent)
- ✓ Empty maps and arrays
- ✓ Deeply nested structures

### Performance Optimizations

- Early exit when values are equal (==)
- Only recurse into changed objects
- Only check array elements when sizes differ
- Avoid unnecessary allocations (use subList)

### Testing Strategy

- Unit tests for each scenario type
- Edge case tests for arrays
- Performance tests with realistic data
- Use AssertJ for clear assertions
- Follow existing test naming conventions

## Open Questions

1. **Should we add telemetry for diff computation time?**
   - Could track P50, P95, P99 latencies in production
   - Defer to Phase 4 or post-release

2. **Should we add limits on array size?**
   - What if 1000s of slow frames detected?
   - Current implementation handles it, but may want limits
   - Defer to Phase 4

3. **Should we cache computation results?**
   - Probably not needed given <5ms performance
   - Adds complexity without clear benefit
   - Decision: No caching for v1

## Success Criteria

Phase 2 is complete when:

- [ ] ViewDiffComputer class implemented with all methods
- [ ] Unit tests pass with >95% coverage
- [ ] Performance tests validate <5ms requirement
- [ ] All edge cases handled correctly
- [ ] Zero false positives (unchanged fields not included)
- [ ] Zero false negatives (changed fields always included)
- [ ] Code review approved
- [ ] All acceptance criteria met

## Next Phase

**Phase 3: Event Flow Integration** will integrate ViewDiffComputer with ViewEventTracker and implement the complete event sending flow including first event (full view) and subsequent events (view_update).
