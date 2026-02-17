# Implementation Plan: Phase 5 - Testing & Documentation

**Phase Document:** [05-testing-documentation.md](05-testing-documentation.md)
**Status:** Not Started
**Generated:** 2026-02-17

## Overview

This plan completes the Partial View Updates feature by adding comprehensive testing and documentation. The focus is on validating production-readiness through end-to-end scenarios, edge cases, performance benchmarks, and memory leak detection. Documentation will enable users to adopt the feature and maintainers to understand the architecture.

**Strategy:** Test comprehensively at multiple levels (unit, integration, performance), document for both users and maintainers, ensure feature is production-ready.

## Prerequisites

- [x] Phase 1 completed: Configuration flag available
- [x] Phase 2 completed: ViewDiffComputer working with unit tests
- [x] Phase 3 completed: ViewEventTracker fully implemented with unit tests
- [x] Phase 4 completed: RumViewScope integration complete
- [x] Existing test infrastructure: JUnit 5, Mockito, Elmyr/Forge

## Implementation Tasks

### Task 1: End-to-End Integration Tests

**Objective:** Validate complete feature flow from RumMonitor through RumViewScope to event writing

**Files to create:**
- `features/dd-sdk-android-rum/src/test/kotlin/com/datadog/android/rum/internal/domain/scope/RumViewScopePartialUpdatesTest.kt` - Integration tests

**Implementation steps:**

1. Create new test class extending RumViewScopeTest base (if available) or using similar setup
2. Implement test helper to capture events written by DataWriter
3. Create RumViewScope with feature enabled/disabled configurations
4. Implement scenarios from phase document

**Test scenarios to implement:**

**Scenario 1: Basic view lifecycle with partial updates**
```kotlin
@Test
fun `M send view then view_update events W feature enabled and view updated`() {
    // Given - feature enabled
    val capturedEvents = mutableListOf<Any>()
    val mockWriter = mockDataWriter(capturedEvents)
    val rumViewScope = createRumViewScope(
        enablePartialUpdates = true,
        writer = mockWriter
    )

    // When - start view and make updates
    rumViewScope.handleEvent(
        RumRawEvent.StartView(key, name, emptyMap()),
        mockDatadogContext,
        mockWriteScope,
        mockWriter
    )

    // Simulate resource added
    rumViewScope.handleEvent(
        RumRawEvent.ResourceSent(viewId = viewId, ...),
        mockDatadogContext,
        mockWriteScope,
        mockWriter
    )

    // Simulate action added
    rumViewScope.handleEvent(
        RumRawEvent.ActionSent(viewId = viewId, ...),
        mockDatadogContext,
        mockWriteScope,
        mockWriter
    )

    // Then - verify event sequence
    assertThat(capturedEvents).hasSize(3)

    // First event: full view
    val event1 = capturedEvents[0] as Map<*, *>
    assertThat(event1["type"]).isEqualTo("view")
    assertThat((event1["_dd"] as Map<*, *>)["document_version"]).isEqualTo(1)
    assertThat(event1).containsKey("view")
    assertThat(event1).containsKey("application")
    assertThat(event1).containsKey("session")

    // Second event: view_update with changes only
    val event2 = capturedEvents[1] as Map<*, *>
    assertThat(event2["type"]).isEqualTo("view_update")
    assertThat((event2["_dd"] as Map<*, *>)["document_version"]).isEqualTo(2)
    // Verify it's smaller (partial)
    assertThat(event2.keys.size).isLessThan(event1.keys.size)

    // Third event: another view_update
    val event3 = capturedEvents[2] as Map<*, *>
    assertThat(event3["type"]).isEqualTo("view_update")
    assertThat((event3["_dd"] as Map<*, *>)["document_version"]).isEqualTo(3)
}
```

**Scenario 2: Feature disabled - backward compatibility**
```kotlin
@Test
fun `M send only view events W feature disabled`() {
    // Given - feature disabled (default)
    val capturedEvents = mutableListOf<Any>()
    val mockWriter = mockDataWriter(capturedEvents)
    val rumViewScope = createRumViewScope(
        enablePartialUpdates = false,
        writer = mockWriter
    )

    // When - same operations as above
    rumViewScope.handleEvent(RumRawEvent.StartView(...), ...)
    rumViewScope.handleEvent(RumRawEvent.ResourceSent(...), ...)
    rumViewScope.handleEvent(RumRawEvent.ActionSent(...), ...)

    // Then - all events are "view" type
    assertThat(capturedEvents).hasSize(3)
    capturedEvents.forEach { event ->
        assertThat((event as Map<*, *>)["type"]).isEqualTo("view")
    }

    // Verify events match old SDK behavior (no view_update type)
    capturedEvents.forEach { event ->
        assertThat(event).containsKey("view")
        assertThat(event).containsKey("application")
        assertThat(event).containsKey("session")
        // Full event every time
    }
}
```

**Scenario 3: Multiple concurrent views**
```kotlin
@Test
fun `M track document versions independently W multiple concurrent views`() {
    // Given - feature enabled with two views
    val capturedEvents = mutableListOf<Any>()
    val mockWriter = mockDataWriter(capturedEvents)

    val rumViewScope1 = createRumViewScope("view1", mockWriter, enablePartialUpdates = true)
    val rumViewScope2 = createRumViewScope("view2", mockWriter, enablePartialUpdates = true)

    // When - updates to both views
    rumViewScope1.handleEvent(RumRawEvent.StartView(key = "view1", ...), ...)
    rumViewScope2.handleEvent(RumRawEvent.StartView(key = "view2", ...), ...)

    rumViewScope1.handleEvent(RumRawEvent.ActionSent(viewId = "view1", ...), ...)
    rumViewScope2.handleEvent(RumRawEvent.ResourceSent(viewId = "view2", ...), ...)
    rumViewScope2.handleEvent(RumRawEvent.ActionSent(viewId = "view2", ...), ...)
    rumViewScope1.handleEvent(RumRawEvent.ResourceSent(viewId = "view1", ...), ...)

    // Then - verify independent document versions
    val view1Events = capturedEvents.filter {
        ((it as Map<*, *>)["view"] as Map<*, *>)["id"] == "view1"
    }
    val view2Events = capturedEvents.filter {
        ((it as Map<*, *>)["view"] as Map<*, *>)["id"] == "view2"
    }

    assertThat(view1Events.map {
        ((it as Map<*, *>)["_dd"] as Map<*, *>)["document_version"]
    }).isEqualTo(listOf(1, 2, 3))

    assertThat(view2Events.map {
        ((it as Map<*, *>)["_dd"] as Map<*, *>)["document_version"]
    }).isEqualTo(listOf(1, 2, 3))
}
```

**Scenario 4: Arrays grow over time (slow frames)**
```kotlin
@Test
fun `M send only new array elements W slow frames detected over time`() {
    // Given - feature enabled
    val capturedEvents = mutableListOf<Any>()
    val mockWriter = mockDataWriter(capturedEvents)
    val rumViewScope = createRumViewScope(mockWriter, enablePartialUpdates = true)

    // When - view with slow frames detected incrementally
    rumViewScope.handleEvent(RumRawEvent.StartView(...), ...)

    // First slow frame
    triggerSlowFrame(rumViewScope, start = 100L, duration = 20L)

    // Second slow frame
    triggerSlowFrame(rumViewScope, start = 250L, duration = 15L)

    // Third slow frame
    triggerSlowFrame(rumViewScope, start = 400L, duration = 18L)

    // Then - verify array optimization
    val events = capturedEvents.map { it as Map<*, *> }

    // First event: full view with first slow frame
    val slowFrames1 = ((events[0]["view"] as Map<*, *>)["slow_frames"] as? List<*>) ?: emptyList()
    assertThat(slowFrames1).hasSize(1)

    // Second event: only new slow frame
    val slowFrames2 = ((events[1]["view"] as Map<*, *>)["slow_frames"] as? List<*>) ?: emptyList()
    assertThat(slowFrames2).hasSize(1)
    assertThat((slowFrames2[0] as Map<*, *>)["start"]).isEqualTo(250L)

    // Third event: only newest slow frame
    val slowFrames3 = ((events[2]["view"] as Map<*, *>)["slow_frames"] as? List<*>) ?: emptyList()
    assertThat(slowFrames3).hasSize(1)
    assertThat((slowFrames3[0] as Map<*, *>)["start"]).isEqualTo(400L)
}
```

**Acceptance:**
- ✓ All 4+ end-to-end scenarios implemented and passing
- ✓ Tests use realistic RumViewScope setup (not overly mocked)
- ✓ Event capture mechanism works correctly
- ✓ Tests verify both event structure and behavior

---

### Task 2: Edge Case Tests

**Objective:** Validate feature handles unusual conditions gracefully

**Files to create/modify:**
- `features/dd-sdk-android-rum/src/test/kotlin/com/datadog/android/rum/internal/domain/scope/RumViewScopeEdgeCasesTest.kt` - Edge case tests

**Implementation steps:**

1. Create test class for edge cases
2. Implement each edge case scenario
3. Verify graceful handling (no crashes, correct behavior)

**Edge case scenarios:**

**Edge Case 1: Empty diff - no event sent**
```kotlin
@Test
fun `M not send event W no changes detected`() {
    // Given - feature enabled, view started
    val capturedEvents = mutableListOf<Any>()
    val rumViewScope = createRumViewScope(mockWriter(capturedEvents), enablePartialUpdates = true)
    rumViewScope.handleEvent(RumRawEvent.StartView(...), ...)

    val initialEventCount = capturedEvents.size

    // When - trigger update with no actual property changes
    // (time passes but no counters change)
    rumViewScope.sendViewUpdate(
        event = RumRawEvent.ApplicationStarted(...),
        datadogContext = mockDatadogContext,
        writeScope = mockWriteScope,
        writer = mockWriter
    )

    // Then - no new event sent
    assertThat(capturedEvents).hasSize(initialEventCount)
}
```

**Edge Case 2: Very large view data**
```kotlin
@Test
fun `M handle efficiently W view with many fields`() {
    // Given - view with 200+ fields (large context, many feature flags)
    val largeContext = (1..100).associate { "field_$it" to "value_$it" }
    val manyFlags = (1..100).associate { "flag_$it" to (it % 2 == 0) }

    val capturedEvents = mutableListOf<Any>()
    val rumViewScope = createRumViewScope(
        mockWriter(capturedEvents),
        enablePartialUpdates = true,
        initialAttributes = largeContext,
        featureFlags = manyFlags
    )

    // When - start view and update single field
    val startTime = System.currentTimeMillis()
    rumViewScope.handleEvent(RumRawEvent.StartView(...), ...)
    rumViewScope.handleEvent(RumRawEvent.ResourceSent(...), ...)  // Change one counter
    val elapsed = System.currentTimeMillis() - startTime

    // Then - should still be fast
    assertThat(elapsed).isLessThan(100) // <100ms for full operation

    // Update event should be small (only changed field + required fields)
    val updateEvent = capturedEvents[1] as Map<*, *>
    assertThat(updateEvent["type"]).isEqualTo("view_update")
    // Should not include all 200 context fields
    assertThat(updateEvent.keys.size).isLessThan(20)
}
```

**Edge Case 3: Rapid successive updates**
```kotlin
@Test
fun `M handle correctly W rapid successive updates`() {
    // Given - feature enabled
    val capturedEvents = mutableListOf<Any>()
    val rumViewScope = createRumViewScope(mockWriter(capturedEvents), enablePartialUpdates = true)
    rumViewScope.handleEvent(RumRawEvent.StartView(...), ...)

    // When - fire 20 updates rapidly
    repeat(20) { i ->
        rumViewScope.handleEvent(
            RumRawEvent.ResourceSent(viewId = viewId, ...),
            ...
        )
    }

    // Then - should have 21 events (1 initial + 20 updates)
    assertThat(capturedEvents).hasSize(21)

    // Document versions should be sequential 1..21
    val versions = capturedEvents.map {
        ((it as Map<*, *>)["_dd"] as Map<*, *>)["document_version"]
    }
    assertThat(versions).isEqualTo((1..21).toList())
}
```

**Edge Case 4: View scope stopped with active view**
```kotlin
@Test
fun `M cleanup state W view scope stopped`() {
    // Given - feature enabled, active view
    val capturedEvents = mutableListOf<Any>()
    val rumViewScope = createRumViewScope(mockWriter(capturedEvents), enablePartialUpdates = true)
    rumViewScope.handleEvent(RumRawEvent.StartView(...), ...)
    rumViewScope.handleEvent(RumRawEvent.ResourceSent(...), ...)

    // When - view stopped
    rumViewScope.handleEvent(
        RumRawEvent.StopView(key = key),
        mockDatadogContext,
        mockWriteScope,
        mockWriter
    )

    // Then - state should be cleaned up (verify via internal methods if available)
    // This may require adding test-only access to ViewEventTracker state
    // For now, verify no memory leak by checking scope can be GC'd
}
```

**Edge Case 5: SDK shutdown with active views**
```kotlin
@Test
fun `M cleanup all state W SDK shutdown with active views`() {
    // Given - multiple active views
    val scope1 = createRumViewScope("view1", mockWriter, enablePartialUpdates = true)
    val scope2 = createRumViewScope("view2", mockWriter, enablePartialUpdates = true)
    val scope3 = createRumViewScope("view3", mockWriter, enablePartialUpdates = true)

    scope1.handleEvent(RumRawEvent.StartView(key = "view1", ...), ...)
    scope2.handleEvent(RumRawEvent.StartView(key = "view2", ...), ...)
    scope3.handleEvent(RumRawEvent.StartView(key = "view3", ...), ...)

    // When - SDK shutdown (simulate by calling cleanup on all scopes)
    // This tests the ViewEventTracker.onSdkShutdown() method

    // Then - all memory should be cleaned up
    // Verify no leaks (implementation depends on test infrastructure)
}
```

**Acceptance:**
- ✓ All 5+ edge case scenarios implemented
- ✓ Tests verify graceful handling (no crashes)
- ✓ Tests verify correct behavior for each edge case
- ✓ All tests passing

---

### Task 3: Performance Benchmarking

**Objective:** Validate diff computation overhead is <5ms as required by NFR-1

**Files to create:**
- `features/dd-sdk-android-rum/src/test/kotlin/com/datadog/android/rum/internal/domain/event/viewupdate/ViewDiffComputerPerformanceTest.kt` - Performance tests

**Implementation steps:**

1. Create performance test class
2. Generate realistic view data with 100-150 fields
3. Measure diff computation time over many iterations
4. Calculate average, P95, and max overhead
5. Assert against <5ms requirement

**Code pattern:**

```kotlin
@Test
fun `M compute diff in less than 5ms W typical view data`() {
    // Given - typical view with 100-150 fields
    val lastSent = createTypicalViewData(fieldCount = 150)
    val current = lastSent.toMutableMap().apply {
        // Modify a few fields to simulate typical update
        put("view", (get("view") as Map<*, *>).toMutableMap().apply {
            put("time_spent", 1500L)
            put("action", mapOf("count" to 3))
            put("resource", mapOf("count" to 12))
        })
    }

    val diffComputer = ViewDiffComputer()

    // When - measure diff computation time (100 iterations)
    val measurements = mutableListOf<Long>()
    repeat(100) {
        val start = System.nanoTime()
        diffComputer.computeDiff(lastSent, current)
        val elapsed = System.nanoTime() - start
        measurements.add(elapsed)
    }

    // Then - verify performance requirements
    val avgMs = measurements.average() / 1_000_000.0
    val p95Ms = measurements.sorted()[95] / 1_000_000.0
    val maxMs = measurements.maxOrNull()!! / 1_000_000.0

    println("Performance: avg=${avgMs}ms, p95=${p95Ms}ms, max=${maxMs}ms")

    assertThat(avgMs).isLessThan(5.0)
    assertThat(p95Ms).isLessThan(10.0)  // Allow P95 slightly higher
}

@Test
fun `M handle large view efficiently W 200+ fields`() {
    // Given - very large view (stress test)
    val lastSent = createTypicalViewData(fieldCount = 250)
    val current = lastSent.toMutableMap().apply {
        put("view", (get("view") as Map<*, *>).toMutableMap().apply {
            put("time_spent", 2000L)
        })
    }

    val diffComputer = ViewDiffComputer()

    // When - measure with large view
    val start = System.nanoTime()
    val diff = diffComputer.computeDiff(lastSent, current)
    val elapsed = System.nanoTime() - start

    val elapsedMs = elapsed / 1_000_000.0

    // Then - should still be reasonably fast
    println("Large view diff time: ${elapsedMs}ms")
    assertThat(elapsedMs).isLessThan(15.0)  // Allow more time for very large views

    // Diff should still be minimal
    assertThat(diff.keys.size).isLessThan(5)
}

private fun createTypicalViewData(fieldCount: Int): Map<String, Any?> {
    return mutableMapOf<String, Any?>().apply {
        put("type", "view")
        put("date", System.currentTimeMillis())
        put("application", mapOf("id" to "app-123"))
        put("session", mapOf("id" to "session-456"))
        put("view", mutableMapOf<String, Any?>().apply {
            put("id", "view-789")
            put("url", "https://example.com/page")
            put("name", "Example Page")
            put("time_spent", 1000L)
            put("action", mapOf("count" to 2))
            put("resource", mapOf("count" to 10))
            put("error", mapOf("count" to 0))
            // Add more fields to reach target count
            repeat(fieldCount - 10) { i ->
                put("field_$i", "value_$i")
            }
        })
        put("context", (1..20).associate { "ctx_$it" to "value_$it" })
        put("feature_flags", (1..20).associate { "flag_$it" to (it % 2 == 0) })
    }
}
```

**Acceptance:**
- ✓ Performance test implemented
- ✓ Average diff computation time <5ms for typical views (100-150 fields)
- ✓ P95 diff computation time <10ms
- ✓ Large views (200+ fields) complete in <15ms
- ✓ Test runs on CI without flakiness

---

### Task 4: Memory Footprint Tests

**Objective:** Validate memory usage is minimal (~2KB per active view) per NFR-2

**Files to create:**
- `features/dd-sdk-android-rum/src/test/kotlin/com/datadog/android/rum/internal/domain/event/viewupdate/ViewEventTrackerMemoryTest.kt` - Memory tests

**Implementation steps:**

1. Create memory test class
2. Measure memory before and after creating views
3. Verify memory increase is within acceptable bounds
4. Verify memory is freed when views end

**Code pattern:**

```kotlin
@Test
fun `M use minimal memory W feature enabled and active views`() {
    // Given - measure initial memory
    System.gc()
    Thread.sleep(100)  // Allow GC to complete
    val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

    // When - create 10 active views
    val trackers = mutableListOf<ViewEventTracker>()
    repeat(10) { i ->
        val tracker = ViewEventTracker(
            config = createConfigWithPartialUpdates(enabled = true),
            writer = mockEventWriter(),
            diffComputer = ViewDiffComputer()
        )

        // Start view and send one update
        val viewData = createTypicalViewData(fieldCount = 150)
        tracker.sendViewUpdate("view_$i", viewData)

        // Send update
        val updatedData = viewData.toMutableMap().apply {
            put("view", (get("view") as Map<*, *>).toMutableMap().apply {
                put("time_spent", 1500L)
            })
        }
        tracker.sendViewUpdate("view_$i", updatedData)

        trackers.add(tracker)
    }

    // Measure memory after
    System.gc()
    Thread.sleep(100)
    val afterMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
    val memoryIncrease = (afterMemory - initialMemory) / 1024  // KB

    // Then - memory increase should be reasonable
    // 10 views × ~2KB per view ≈ 20KB
    println("Memory increase: ${memoryIncrease}KB for 10 views")
    assertThat(memoryIncrease).isLessThan(100)  // Allow some overhead
}

@Test
fun `M free memory W views ended`() {
    // Given - views with stored state
    val tracker = ViewEventTracker(
        config = createConfigWithPartialUpdates(enabled = true),
        writer = mockEventWriter(),
        diffComputer = ViewDiffComputer()
    )

    repeat(5) { i ->
        val viewData = createTypicalViewData(fieldCount = 150)
        tracker.sendViewUpdate("view_$i", viewData)
    }

    // Verify state is stored (internal access may be needed)
    assertThat(tracker.hasLastSentEvent("view_0")).isTrue()
    assertThat(tracker.hasLastSentEvent("view_4")).isTrue()

    // When - views ended
    repeat(5) { i ->
        tracker.onViewEnded("view_$i")
    }

    // Then - state should be freed
    assertThat(tracker.hasLastSentEvent("view_0")).isFalse()
    assertThat(tracker.hasLastSentEvent("view_4")).isFalse()
}
```

**Acceptance:**
- ✓ Memory footprint test implemented
- ✓ 10 active views use <100KB memory
- ✓ Memory is freed when views end
- ✓ No memory leaks detected
- ✓ Test is stable and reproducible

---

### Task 5: Create User Documentation

**Objective:** Document how SDK users can enable and use the feature

**Files to create:**
- `docs/rum/partial_view_updates.md` - User-facing documentation

**Implementation steps:**

1. Create documentation directory if needed
2. Write comprehensive user guide
3. Include code examples
4. Add troubleshooting section
5. Document migration path

**Documentation structure:**

```markdown
# Partial View Updates

## Overview

The Partial View Updates feature reduces bandwidth and battery usage by sending only changed fields in view update events, rather than resending the complete view state with every update.

**Benefits:**
- 43-93% reduction in bandwidth usage
- Lower battery consumption from reduced I/O
- Improved SDK performance on mobile devices

**Availability:** SDK version X.Y.Z or later

## Enabling the Feature

The feature is opt-in and disabled by default. Enable it during SDK initialization:

```kotlin
val rumConfig = RumConfiguration.Builder(
    clientToken = "<YOUR_CLIENT_TOKEN>",
    environment = "<YOUR_ENVIRONMENT>",
    applicationId = "<YOUR_APPLICATION_ID>"
)
    .setEnablePartialViewUpdates(true)  // Enable partial updates
    .build()

DatadogSdk.initialize(this, rumConfig)
```

## How It Works

### Event Types

- **First view event:** Full `view` event with all properties
- **Subsequent updates:** `view_update` events with only changed fields
- **Backend:** Reconstructs complete view state by applying updates

### Example

```kotlin
// First event (full view)
{
  "type": "view",
  "view": {
    "id": "v1",
    "url": "...",
    "time_spent": 0,
    "action": { "count": 0 },
    "resource": { "count": 0 }
  },
  "_dd": { "document_version": 1 }
}

// Second event (only what changed)
{
  "type": "view_update",
  "view": {
    "id": "v1",
    "time_spent": 150,
    "action": { "count": 1 },
    "resource": { "count": 3 }
  },
  "_dd": { "document_version": 2 }
}
```

The backend automatically reconstructs the full view state, so your dashboards and queries work identically.

## Requirements

- **SDK version:** X.Y.Z or later
- **Backend support:** Requires backend support for `view_update` event type (automatically available)
- **Opt-in:** Feature must be explicitly enabled via configuration

## Performance

- **Diff computation overhead:** <5ms per update
- **Memory footprint:** ~2KB per active view
- **Bandwidth savings:** 40-90% depending on update frequency

## When to Use

**Enable partial updates when:**
- Your app has many view updates (frequent resource loads, actions)
- You want to minimize SDK bandwidth usage
- Your users have limited data plans
- You care about battery optimization

**Keep disabled when:**
- Your app has very few view updates (minimal benefit)
- You're validating SDK behavior and want simpler events
- You have specific compatibility requirements

## Troubleshooting

### How do I verify the feature is working?

Check your RUM events in Datadog - you should see `view_update` events instead of multiple `view` events for the same view.id.

You can also enable SDK debug logging to see diff computation:

```kotlin
Datadog.setVerbosity(Log.VERBOSE)
```

### Does this affect how data appears in Datadog?

No, the backend reconstructs the complete view state. Your dashboards and queries work identically whether the feature is enabled or disabled.

### Can I enable this mid-session?

No, the configuration is immutable after SDK initialization. Set it during `DatadogSdk.initialize()`.

### Will this work with older backend versions?

The backend automatically supports `view_update` events. No special configuration needed.

### What if I want to disable it after enabling?

Update your SDK configuration and redeploy your app. The SDK will revert to sending full `view` events.

## Migration

If upgrading from an older SDK version:

1. Update to SDK version X.Y.Z or later
2. Add `.setEnablePartialViewUpdates(true)` to your RUM configuration
3. Test in staging environment
4. Roll out to production

No code changes are required beyond configuration.

## Best Practices

1. **Test before production:** Enable in staging first to validate behavior
2. **Monitor bandwidth:** Use Datadog metrics to verify bandwidth reduction
3. **Gradual rollout:** Consider enabling for a percentage of users first
4. **Keep SDK updated:** Stay on latest SDK version for bug fixes and improvements

## Technical Details

For implementation details and architecture, see [Internal Architecture Documentation](../architecture/partial_view_updates.md).
```

**Acceptance:**
- ✓ User documentation created
- ✓ Includes clear examples
- ✓ Covers troubleshooting
- ✓ Documents migration path
- ✓ Reviewed for clarity and accuracy

---

### Task 6: Create Internal Documentation

**Objective:** Document architecture and maintenance notes for SDK developers

**Files to create:**
- `docs/architecture/partial_view_updates.md` - Internal architecture documentation

**Documentation structure:**

```markdown
# Partial View Updates - Internal Architecture

## Overview

Implementation of RFC: RUM Event Format Limitation (Partial View Updates)

**RFC:** [partial-updates-rfc.md](../../partial-updates-rfc.md)
**Spec:** [.rum-ai-toolkit/planning/SPEC.md](../../.rum-ai-toolkit/planning/SPEC.md)

## Components

### ViewEventTracker

**Location:** `com.datadog.android.rum.internal.domain.event.viewupdate.ViewEventTracker`

**Responsibilities:**
- Manages view state tracking
- Stores last sent event per view.id
- Tracks document_version counter per view
- Determines when to send full `view` or partial `view_update`
- Cleanup on view end and SDK shutdown

**Key Methods:**
- `sendViewUpdate(viewId, currentViewData)` - Main entry point, branches based on config
- `sendFullViewEvent(viewId, viewData)` - Sends complete view event
- `sendPartialViewUpdate(viewId, changes, fullCurrentState)` - Sends diff event
- `onViewEnded(viewId)` - Cleanup for ended view
- `onSdkShutdown()` - Cleanup all state

**State Management:**
- `lastSentEvents: Map<String, Map<String, Any?>>` - Last sent data per view
- `documentVersions: Map<String, Int>` - Version counter per view
- Cleaned up when view ends (no leaks)

### ViewDiffComputer

**Location:** `com.datadog.android.rum.internal.domain.event.viewupdate.ViewDiffComputer`

**Responsibilities:**
- Computes differences between view states
- Handles primitives, objects, arrays with specific rules
- Optimized for array append-only pattern (slow_frames, page_states)

**Algorithm:**
- Primitives: Include if value changed
- Objects: Recurse and include nested changes only
- Arrays: Include only newly appended elements (backend applies APPEND rule)
- Null: Include to signal field deletion

**Performance:**
- O(n) where n = number of fields
- Typical execution: 1-3ms for 100-150 fields
- Requirement: <5ms average

**Key Methods:**
- `computeDiff(lastSent, current)` - Main diff computation
- `getNewArrayElements(lastSent, current)` - Array optimization
- `deepEquals(a, b)` - Deep equality for complex structures

### ViewEventConverter

**Location:** `com.datadog.android.rum.internal.domain.event.viewupdate.ViewEventConverter`

**Responsibilities:**
- Converts ViewEvent model objects to Map<String, Any?> representation
- Enables ViewEventTracker to compute diffs on ViewEvent objects

**Approach:**
- Uses Gson JSON serialization for accurate field extraction
- Avoids kotlin-reflect dependency
- Handles all ViewEvent fields recursively

### RumEventWriterAdapter

**Location:** `com.datadog.android.rum.internal.domain.event.viewupdate.RumEventWriterAdapter`

**Responsibilities:**
- Adapts EventWriter interface to SDK's DataWriter<Any>
- Bridges ViewEventTracker to SDK event writing infrastructure

### RumViewScope Integration

**Location:** `com.datadog.android.rum.internal.domain.scope.RumViewScope`

**Integration Points:**
- `sendViewUpdate()` method branches based on feature flag
- ViewEventTracker lazily initialized when feature enabled
- Cleanup via `onViewEnded()` when view stops

**Configuration Access:**
- Accesses RumConfiguration via `sdkCore.getFeature<RumFeature>()`
- Feature flag: `config.featureConfiguration.enablePartialViewUpdates`

## Design Decisions

### Store-and-Compare Approach

**Chosen:** Store last sent event, compute diff at send time

**Rationale:**
- Simplicity: Centralized diff logic, no instrumentation needed
- Maintainability: New fields automatically supported
- Correctness: Single source of truth for change detection
- Performance: Acceptable overhead given infrequent updates (2-37 per view)

**Alternative rejected:** Dirty tracking with field-level flags
- More complex: Would require instrumenting all view property setters
- More error-prone: Easy to forget marking fields as dirty
- Harder to maintain: Schema changes require code updates

### Array Optimization

**Approach:** Only send new elements, backend applies APPEND rule

**Assumption:** Arrays only grow (append-only)
- Valid for: slow_frames, page_states, custom timings
- Invalid for: Arrays that can shrink or reorder (none in RUM schema)

**Benefit:** Significant bandwidth savings for long-lived views with many slow frames

### Backend Update Rules

**SDK sends:** Changed fields only
**Backend applies:** Merge logic based on field type

- Standard Objects → MERGE
- Custom Objects → REPLACE
- Arrays → APPEND
- Primitives → REPLACE
- Optionals → DELETE (when null)

**Decoupling:** SDK doesn't need to know merge rules, only send changes

## Maintenance Notes

### Adding New View Fields

**No code changes needed** in diff logic - automatically detected and diffed.

Just ensure new fields follow RUM schema conventions.

### Modifying Array Fields

If adding array field that **doesn't** follow append-only pattern:
1. Update `getNewArrayElements()` logic to detect modifications
2. Consider sending full array or using different strategy

### Performance Tuning

If diff computation exceeds 5ms:
1. Profile with realistic view data
2. Check for deep object nesting (recursion depth)
3. Consider caching frequently accessed nested paths
4. Optimize deep equality checks

### Testing New Changes

Always run:
1. Unit tests: ViewEventTrackerTest, ViewDiffComputerTest
2. Integration tests: RumViewScopePartialUpdatesTest
3. Performance tests: ViewDiffComputerPerformanceTest
4. Backward compatibility: Verify feature disabled = old behavior

## Troubleshooting

### Events not getting smaller

**Symptoms:** view_update events are same size as view events

**Possible causes:**
1. Feature flag not enabled in configuration
2. All fields changing every update (no benefit from feature)
3. Diff computation returning full map instead of changes

**Debug:**
- Enable verbose logging
- Check configuration: `config.featureConfiguration.enablePartialViewUpdates`
- Inspect diff computation output

### Memory growing over time

**Symptoms:** Memory usage increases with number of views

**Possible causes:**
1. `onViewEnded()` not called when views end
2. ViewEventTracker state not cleared on SDK shutdown
3. Memory leak in lastSentEvents map

**Debug:**
- Check ViewEventTracker.hasLastSentEvent() over time
- Verify cleanup methods are called
- Use memory profiler to identify leak source

### Document versions out of sequence

**Symptoms:** Backend reports out-of-order document_version

**Possible causes:**
1. Multiple ViewEventTracker instances for same view
2. Concurrent updates to same view (shouldn't happen in RUM)
3. Version counter not incrementing correctly

**Debug:**
- Verify single ViewEventTracker per RumViewScope
- Check version increment logic
- Review event timestamps and version sequence

## Performance Characteristics

**Diff Computation:**
- Average: 1-3ms for 100-150 fields
- P95: <5ms
- Max: ~10ms for very large views (200+ fields)

**Memory Footprint:**
- ~2KB per active view (last sent event stored)
- Typical: 1-2 active views = ~4KB overhead
- Cleaned up when view ends

**Bandwidth Savings:**
- Typical: 40-60% reduction in upload size
- Long-lived views: 70-90% reduction over time
- Highly variable views: 20-40% reduction

## References

- [RFC - RUM Event Format Limitation](../../partial-updates-rfc.md)
- [SPEC.md](.rum-ai-toolkit/planning/SPEC.md)
- [Phase Documents](.rum-ai-toolkit/planning/phases/)
- [RUM Events Format Schema](https://github.com/DataDog/rum-events-format)
```

**Acceptance:**
- ✓ Internal documentation created
- ✓ Covers all major components
- ✓ Documents design decisions and rationale
- ✓ Includes maintenance notes
- ✓ Provides troubleshooting guide

---

### Task 7: Create Example Code

**Objective:** Provide clear examples of feature usage

**Files to create:**
- `features/dd-sdk-android-rum/sample/src/main/kotlin/com/datadog/android/sample/PartialViewUpdatesExample.kt` - Sample code

**Implementation steps:**

1. Create example application demonstrating feature
2. Show both enabled and disabled configurations
3. Demonstrate typical usage patterns
4. Add comments explaining behavior

**Example code:**

```kotlin
package com.datadog.android.sample

import android.app.Application
import com.datadog.android.Datadog
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.RumMonitor

/**
 * Example demonstrating Partial View Updates feature.
 *
 * This feature reduces bandwidth by sending only changed fields
 * in view update events after the first full view event.
 */
class PartialViewUpdatesExample : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize SDK with partial view updates enabled
        initializeDatadogWithPartialUpdates()

        // Simulate typical app usage
        simulateAppFlow()
    }

    /**
     * Initialize Datadog SDK with Partial View Updates enabled.
     */
    private fun initializeDatadogWithPartialUpdates() {
        val rumConfig = RumConfiguration.Builder(
            clientToken = "YOUR_CLIENT_TOKEN",
            environment = "production",
            applicationId = "YOUR_APPLICATION_ID"
        )
            // Enable partial view updates
            .setEnablePartialViewUpdates(true)
            .build()

        Datadog.initialize(this, rumConfig)
    }

    /**
     * Simulate typical app flow showing how partial updates work.
     */
    private fun simulateAppFlow() {
        val rumMonitor = GlobalRum.get()

        // Start a view - this sends a FULL view event
        rumMonitor.startView(
            key = "product_details",
            name = "Product Details",
            attributes = mapOf("category" to "electronics")
        )

        // These actions trigger view updates
        // With partial updates enabled: only changed fields sent
        // Without partial updates: full view sent each time

        // Load product image - triggers view update with resource count change
        rumMonitor.startResource(
            key = "product_image",
            method = "GET",
            url = "https://example.com/product.jpg",
            attributes = emptyMap()
        )
        rumMonitor.stopResource(
            key = "product_image",
            statusCode = 200,
            size = 50000,
            kind = "image",
            attributes = emptyMap()
        )
        // → Sends view_update with: resource.count = 1, time_spent = X

        // User clicks "Add to Cart" - triggers action
        rumMonitor.addAction(
            type = "tap",
            name = "add_to_cart",
            attributes = mapOf("product_id" to "12345")
        )
        // → Sends view_update with: action.count = 1, time_spent = Y

        // Load product reviews - another resource
        rumMonitor.startResource(
            key = "reviews",
            method = "GET",
            url = "https://api.example.com/reviews",
            attributes = emptyMap()
        )
        rumMonitor.stopResource(
            key = "reviews",
            statusCode = 200,
            size = 2000,
            kind = "xhr",
            attributes = emptyMap()
        )
        // → Sends view_update with: resource.count = 2, time_spent = Z

        // Stop the view - sends final update
        rumMonitor.stopView(
            key = "product_details",
            attributes = emptyMap()
        )
        // → Sends view_update with final state (is_active = false)

        // At this point:
        // - With feature ENABLED: Sent 1 view + 4 view_update events
        // - With feature DISABLED: Sent 5 full view events
        // - Bandwidth saved: ~60-80% (only changes sent after first event)
    }

    /**
     * Example: Disabling the feature (default behavior).
     */
    private fun initializeDatadogWithoutPartialUpdates() {
        val rumConfig = RumConfiguration.Builder(
            clientToken = "YOUR_CLIENT_TOKEN",
            environment = "production",
            applicationId = "YOUR_APPLICATION_ID"
        )
            // Feature disabled by default (can explicitly set to false)
            .setEnablePartialViewUpdates(false)
            .build()

        Datadog.initialize(this, rumConfig)

        // With this configuration:
        // - All events are full "view" events
        // - Behavior identical to older SDK versions
        // - No bandwidth optimization
    }

    /**
     * Example: Long-lived view with many updates (best case for feature).
     */
    private fun longLivedViewExample() {
        val rumMonitor = GlobalRum.get()

        // Start dashboard view that user stays on for minutes
        rumMonitor.startView(
            key = "dashboard",
            name = "Dashboard",
            attributes = emptyMap()
        )

        // Over time, many resources load (charts, data, images)
        repeat(20) { i ->
            rumMonitor.startResource(
                key = "chart_$i",
                method = "GET",
                url = "https://api.example.com/chart/$i",
                attributes = emptyMap()
            )
            rumMonitor.stopResource(
                key = "chart_$i",
                statusCode = 200,
                size = 5000,
                kind = "xhr",
                attributes = emptyMap()
            )
            // Each update sends only: resource.count += 1, time_spent += X
            // Full view might be 20KB, update is ~1KB
            // Savings: ~95% over 20 updates
        }

        rumMonitor.stopView(key = "dashboard", attributes = emptyMap())
    }
}
```

**Acceptance:**
- ✓ Example code created
- ✓ Demonstrates typical usage
- ✓ Shows both enabled and disabled configurations
- ✓ Well-commented and clear
- ✓ Compiles and runs successfully

---

### Task 8: Write Release Notes

**Objective:** Draft release notes for SDK users

**Files to create:**
- `.rum-ai-toolkit/planning/RELEASE_NOTES.md` - Release notes content

**Content:**

```markdown
# Release Notes - Partial View Updates

## New Feature: Partial View Updates (Opt-in)

### Overview

We're excited to introduce **Partial View Updates**, a new opt-in feature that significantly reduces bandwidth usage and improves battery life by sending only changed fields in RUM view update events.

### Benefits

- **43-93% bandwidth reduction** for view events
- **Lower battery consumption** from reduced I/O operations
- **Improved performance** on mobile devices with limited connectivity
- **Cost savings** for users with limited data plans

### How It Works

When enabled, the SDK sends a full view event when a view starts, then sends smaller update events containing only the fields that have changed. The Datadog backend automatically reconstructs the complete view state, so your dashboards and queries continue to work exactly as before.

### Enabling the Feature

Add `.setEnablePartialViewUpdates(true)` to your RUM configuration:

```kotlin
val rumConfig = RumConfiguration.Builder(clientToken, environment, applicationId)
    .setEnablePartialViewUpdates(true)  // NEW: Enable partial updates
    .build()

DatadogSdk.initialize(context, rumConfig)
```

### Requirements

- Android RUM SDK version X.Y.Z or later
- No backend configuration needed (automatically supported)

### Migration

Existing apps can enable this feature by:
1. Updating to SDK version X.Y.Z or later
2. Adding the configuration flag shown above
3. Testing in staging before production rollout

No other code changes required.

### Performance

- Diff computation overhead: <5ms per view update
- Memory footprint: ~2KB per active view
- Typical bandwidth savings: 40-90% depending on update frequency

### When to Use

**Recommended for:**
- Apps with frequent view updates (resource loads, user actions)
- Apps targeting users with limited data plans
- Apps focused on battery optimization

**Optional for:**
- Apps with very few view updates
- Apps where bandwidth is not a concern

### Documentation

- [User Guide](docs/rum/partial_view_updates.md) - Complete usage guide
- [Architecture](docs/architecture/partial_view_updates.md) - Internal implementation details

### Backward Compatibility

- Feature is opt-in and disabled by default
- When disabled, SDK behaves identically to previous versions
- No breaking changes to public API

### Known Limitations

- Configuration is immutable after SDK initialization (cannot toggle mid-session)
- Requires SDK version X.Y.Z or later
- Feature cannot be enabled retroactively for already-running sessions

### Feedback

We'd love to hear your feedback! Please report issues or suggestions at:
- GitHub: [datadog/dd-sdk-android](https://github.com/DataDog/dd-sdk-android)
- Support: support@datadoghq.com

---

## Other Changes in This Release

[Include other release notes for this version]

---

## Upgrade Notes

To upgrade and enable Partial View Updates:

1. Update your dependency:
   ```gradle
   implementation 'com.datadoghq:dd-sdk-android-rum:X.Y.Z'
   ```

2. Enable the feature:
   ```kotlin
   .setEnablePartialViewUpdates(true)
   ```

3. Test in staging environment

4. Monitor bandwidth usage in Datadog to verify savings

5. Roll out to production

---

## Technical Details

For SDK developers and maintainers, see:
- [Implementation Spec](.rum-ai-toolkit/planning/SPEC.md)
- [Phase Documents](.rum-ai-toolkit/planning/phases/)
- [Architecture Documentation](docs/architecture/partial_view_updates.md)
```

**Acceptance:**
- ✓ Release notes drafted
- ✓ Includes clear description of feature
- ✓ Documents benefits and usage
- ✓ Provides migration guidance
- ✓ Mentions known limitations

---

## Task Checklist

**Testing Tasks:**
- [ ] Task 1: End-to-end integration tests (4+ scenarios)
- [ ] Task 2: Edge case tests (5+ scenarios)
- [ ] Task 3: Performance benchmarking tests
- [ ] Task 4: Memory footprint tests

**Documentation Tasks:**
- [ ] Task 5: User documentation
- [ ] Task 6: Internal architecture documentation
- [ ] Task 7: Example code
- [ ] Task 8: Release notes

**Validation:**
- [ ] All new tests passing
- [ ] All Phase 1-4 tests still passing (no regression)
- [ ] Code coverage >90% for new components
- [ ] Documentation reviewed for clarity and accuracy
- [ ] Performance benchmarks meet requirements (<5ms)
- [ ] Memory tests pass (<100KB for 10 views)

## Implementation Order

Recommended order to maximize value:

**Week 1: Testing**
1. Task 1 (Integration tests) - Validates end-to-end flow
2. Task 2 (Edge cases) - Ensures robustness
3. Task 3 (Performance) - Validates NFR-1
4. Task 4 (Memory) - Validates NFR-2

**Week 2: Documentation**
5. Task 5 (User docs) - Enables user adoption
6. Task 6 (Internal docs) - Supports maintenance
7. Task 7 (Examples) - Provides clear usage patterns
8. Task 8 (Release notes) - Prepares for release

Tasks within each week can be parallelized if multiple contributors available.

## Notes

### Test Infrastructure

The SDK uses:
- **JUnit 5** for test framework
- **Mockito** for mocking
- **Elmyr/Forge** for data generation
- **AssertJ** for assertions

Follow existing patterns in `ViewEventTrackerTest.kt` and `ViewDiffComputerTest.kt`.

### Documentation Location

Confirm with team:
- Where should user docs live? (`docs/rum/` or elsewhere?)
- Should docs be published to public docs site? (docs.datadoghq.com)
- Internal docs location? (`docs/architecture/` or elsewhere?)

### Performance Testing

Performance tests may be flaky on CI if system is under load. Consider:
- Running performance tests separately from regular unit tests
- Using more lenient thresholds on CI (e.g., 10ms instead of 5ms)
- Averaging multiple runs to reduce noise

### Memory Testing

Memory tests are inherently flaky due to GC non-determinism. Consider:
- Running multiple times and averaging
- Using generous thresholds (100KB vs target 20KB)
- Documenting expected variance in test comments

### Release Notes Version

Replace "X.Y.Z" with actual version number when known. Coordinate with release team for:
- Target release version
- Release timeline
- Announcement plan

## Open Questions

1. **Backend readiness:**
   - Is backend support for `view_update` ready for testing?
   - How to validate end-to-end in staging environment?
   - **Resolution:** Coordinate with backend team for staging test

2. **Documentation location:**
   - Where should user docs live? `/docs` folder or separate repo?
   - Should they be published to docs.datadoghq.com?
   - **Resolution:** Check with documentation team

3. **Telemetry:**
   - Should we add metrics to track feature usage?
   - What metrics: enabled %, bandwidth savings, diff computation time?
   - **Resolution:** Defer to post-release monitoring phase

4. **Release version:**
   - What version will this ship in?
   - Timeline for release?
   - **Resolution:** Coordinate with release team

5. **Gradual rollout:**
   - Should we support enabling for % of users?
   - Or leave as simple boolean flag?
   - **Resolution:** Keep simple boolean flag (users can control rollout at app level)

## Success Criteria

Phase 5 is complete when:

- [ ] End-to-end integration tests implemented (4+ scenarios)
- [ ] Edge case tests implemented (5+ scenarios)
- [ ] Performance benchmarks validate <5ms overhead
- [ ] Memory tests show <100KB for 10 views, cleanup works
- [ ] All tests passing (100+ tests total across all phases)
- [ ] Code coverage >90% for all new components
- [ ] User documentation written and reviewed
- [ ] Internal architecture documentation complete
- [ ] Example code created and tested
- [ ] Release notes drafted
- [ ] All Phase 1-4 tests still passing (no regression)
- [ ] Feature marked as production-ready
- [ ] Code review completed and approved

## Next Steps After Phase 5

Once Phase 5 is complete, the feature is ready for:

1. **Final code review** - Team review of all changes
2. **QA testing** - Manual testing in test app
3. **Staging deployment** - Deploy to staging environment
4. **Backend validation** - Verify backend correctly processes view_update events
5. **Performance monitoring** - Validate overhead in realistic conditions
6. **Documentation review** - Technical writing review of user docs
7. **Release preparation** - Merge to release branch, tag version
8. **Announcement** - Blog post, release notes, SDK update

**Post-release:**
- Monitor feature adoption metrics
- Collect user feedback
- Address any issues reported
- Consider timeline for enabled-by-default (future milestone)
