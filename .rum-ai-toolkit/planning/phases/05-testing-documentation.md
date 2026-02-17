# Phase 5: Testing & Documentation

**Status:** Complete
**Estimated Effort:** 2-3 days
**Dependencies:** Phase 4 (requires RumViewScope integration complete)
**Completed:** 2026-02-17

## Objective

Validate the feature through comprehensive testing, verify performance characteristics, and provide clear documentation for SDK users. This phase ensures the implementation is production-ready and users can successfully adopt the feature.

## Scope

### In Scope

- Comprehensive end-to-end test scenarios
- Edge case testing and validation
- Performance benchmarking (<5ms overhead validation)
- Memory leak testing
- User-facing documentation (how to enable and use the feature)
- Internal documentation (architecture decisions, maintenance notes)
- Example code and migration guide
- Release notes content

### Out of Scope (Future Work)

- Production monitoring and telemetry (post-release)
- Backend validation (backend team responsibility)
- Gradual rollout infrastructure (out of scope per spec)
- Migration to enabled-by-default (future decision)

## Requirements Addressed

From the spec, this phase validates:
- **All FRs:** End-to-end validation that all functional requirements work correctly
- **NFR-1:** Performance overhead <5ms
- **NFR-2:** Memory footprint validation (one copy per active view)
- **NFR-3:** Backward compatibility verified
- Documentation of all features and usage patterns

## Implementation Approach

### 1. End-to-End Test Scenarios

**Scenario: Basic view lifecycle**
```kotlin
@Test
fun `basic view lifecycle with partial updates`() {
    // Setup
    val sdk = initializeSDK(partialUpdatesEnabled = true)
    val capturedEvents = mutableListOf<RumEvent>()
    interceptEvents { capturedEvents.add(it) }

    // Execute
    sdk.startView("product_details", "https://shop.com/product/123")
    Thread.sleep(100)
    sdk.addResource("https://api.shop.com/product/123")
    Thread.sleep(50)
    sdk.addAction("add_to_cart")
    Thread.sleep(50)
    sdk.stopView("product_details")

    // Verify
    assertEquals(4, capturedEvents.size)

    // First event: full view
    assertEquals("view", capturedEvents[0].type)
    assertEquals(1, capturedEvents[0].documentVersion)
    assertTrue(capturedEvents[0].hasAllViewFields())

    // Subsequent events: view_update
    assertEquals("view_update", capturedEvents[1].type)
    assertEquals(2, capturedEvents[1].documentVersion)
    assertTrue(capturedEvents[1].hasOnlyChangedFields())

    assertEquals("view_update", capturedEvents[2].type)
    assertEquals(3, capturedEvents[2].documentVersion)

    assertEquals("view_update", capturedEvents[3].type)
    assertEquals(4, capturedEvents[3].documentVersion)
}
```

**Scenario: Feature disabled (backward compatibility)**
```kotlin
@Test
fun `feature disabled behaves identically to old SDK`() {
    val sdkWithFeature = initializeSDK(partialUpdatesEnabled = false)
    val sdkOld = initializeOldSDK()

    val eventsWithFeature = captureEvents(sdkWithFeature) {
        performTypicalUserFlow(it)
    }

    val eventsOld = captureEvents(sdkOld) {
        performTypicalUserFlow(it)
    }

    // Events should be identical (except timestamps)
    assertEquals(eventsOld.size, eventsWithFeature.size)
    eventsWithFeature.forEachIndexed { index, event ->
        assertEventsEquivalent(event, eventsOld[index])
    }
}
```

**Scenario: Multiple concurrent views**
```kotlin
@Test
fun `handles multiple concurrent views independently`() {
    val sdk = initializeSDK(partialUpdatesEnabled = true)

    // Start two views
    sdk.startView("view1", "https://example.com/page1")
    sdk.startView("view2", "https://example.com/page2")

    // Update both views
    sdk.addActionToView("view1", "click")
    sdk.addResourceToView("view2", "https://api.example.com/data")
    sdk.addActionToView("view2", "scroll")
    sdk.addResourceToView("view1", "https://api.example.com/product")

    // Stop views in different order
    sdk.stopView("view2")
    sdk.stopView("view1")

    // Verify independent document_version tracking
    val view1Events = getEventsForView("view1")
    val view2Events = getEventsForView("view2")

    assertEquals(listOf(1, 2, 3, 4), view1Events.map { it.documentVersion })
    assertEquals(listOf(1, 2, 3, 4), view2Events.map { it.documentVersion })

    // Verify memory cleanup
    assertViewStateCleared("view1")
    assertViewStateCleared("view2")
}
```

**Scenario: Arrays grow over time**
```kotlin
@Test
fun `slow frames array sends only new elements`() {
    val sdk = initializeSDK(partialUpdatesEnabled = true)

    sdk.startView("heavy_screen", "https://example.com/heavy")

    // Detect slow frames over time
    sdk.reportSlowFrame(start = 100, duration = 20)
    sdk.reportSlowFrame(start = 250, duration = 15)
    sdk.reportSlowFrame(start = 400, duration = 18)

    val events = getEventsForView("heavy_screen")

    // First event: full view with first slow frame
    val firstEvent = events[0]
    assertEquals(1, firstEvent.view.slowFrames.size)

    // Second event: only new slow frame
    val secondEvent = events[1]
    assertEquals(1, secondEvent.view.slowFrames.size)
    assertEquals(250, secondEvent.view.slowFrames[0].start)

    // Third event: only newest slow frame
    val thirdEvent = events[2]
    assertEquals(1, thirdEvent.view.slowFrames.size)
    assertEquals(400, thirdEvent.view.slowFrames[0].start)
}
```

### 2. Edge Case Testing

**Edge Case: Empty diff (no event sent)**
```kotlin
@Test
fun `no event sent when nothing changes`() {
    val sdk = initializeSDK(partialUpdatesEnabled = true)

    sdk.startView("static_view", "https://example.com/static")
    val initialEventCount = getEventCount()

    // Trigger update with no actual changes
    sdk.updateView("static_view") // No properties changed

    // No new event should be sent
    assertEquals(initialEventCount, getEventCount())
}
```

**Edge Case: Very large view data**
```kotlin
@Test
fun `handles view with many fields efficiently`() {
    val sdk = initializeSDK(partialUpdatesEnabled = true)

    // Create view with 200+ fields (large context, many feature flags)
    val largeContext = (1..100).associate { "field_$it" to "value_$it" }
    val manyFlags = (1..100).associate { "flag_$it" to (it % 2 == 0) }

    sdk.startView("complex_view", "https://example.com/complex",
        context = largeContext,
        featureFlags = manyFlags
    )

    // Update one field
    val startTime = System.currentTimeMillis()
    sdk.updateTimeSpent("complex_view", 150)
    val elapsed = System.currentTimeMillis() - startTime

    // Should still be fast
    assertTrue(elapsed < 50, "Update took ${elapsed}ms")

    // Verify update event is small (only changed field + required fields)
    val updateEvent = getLastEvent()
    assertTrue(updateEvent.fieldCount < 20, "Update event has ${updateEvent.fieldCount} fields")
}
```

**Edge Case: Rapid updates**
```kotlin
@Test
fun `handles rapid successive updates correctly`() {
    val sdk = initializeSDK(partialUpdatesEnabled = true)

    sdk.startView("rapid_view", "https://example.com/rapid")

    // Fire 20 updates rapidly
    repeat(20) { i ->
        sdk.updateTimeSpent("rapid_view", (i + 1) * 50)
    }

    val events = getEventsForView("rapid_view")

    // Should have 21 events (1 initial + 20 updates)
    assertEquals(21, events.size)

    // Document versions should be sequential
    val versions = events.map { it.documentVersion }
    assertEquals((1..21).toList(), versions)
}
```

**Edge Case: SDK shutdown with active views**
```kotlin
@Test
fun `SDK shutdown cleans up all view state`() {
    val sdk = initializeSDK(partialUpdatesEnabled = true)

    sdk.startView("view1", "https://example.com/view1")
    sdk.startView("view2", "https://example.com/view2")
    sdk.startView("view3", "https://example.com/view3")

    // Don't stop views explicitly
    sdk.shutdown()

    // Memory should be cleaned up
    assertAllViewStateCleared()
}
```

### 3. Performance Benchmarking

```kotlin
@Test
fun `diff computation overhead is less than 5ms`() {
    val sdk = initializeSDK(partialUpdatesEnabled = true)

    // Create typical view with 100-150 fields
    val typicalView = createTypicalViewData(fieldCount = 150)
    sdk.startView("perf_test", "https://example.com/perf", viewData = typicalView)

    // Measure update time
    val measurements = mutableListOf<Long>()
    repeat(100) {
        val start = System.nanoTime()
        sdk.updateTimeSpent("perf_test", it * 100)
        val elapsed = System.nanoTime() - start
        measurements.add(elapsed)
    }

    val avgMs = measurements.average() / 1_000_000.0
    val p95Ms = measurements.sorted()[95] / 1_000_000.0
    val maxMs = measurements.max() / 1_000_000.0

    println("Performance: avg=${avgMs}ms, p95=${p95Ms}ms, max=${maxMs}ms")

    assertTrue(avgMs < 5.0, "Average overhead ${avgMs}ms exceeds 5ms")
    assertTrue(p95Ms < 10.0, "P95 overhead ${p95Ms}ms exceeds 10ms")
}

@Test
fun `memory footprint is minimal`() {
    val sdk = initializeSDK(partialUpdatesEnabled = true)
    val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

    // Create 10 active views
    repeat(10) { i ->
        sdk.startView("view_$i", "https://example.com/view_$i")
        sdk.updateTimeSpent("view_$i", 100)
    }

    val afterMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
    val memoryIncrease = (afterMemory - initialMemory) / 1024 // KB

    // Should be roughly 10 views × 2KB ≈ 20KB
    assertTrue(memoryIncrease < 100, "Memory increase ${memoryIncrease}KB is too high")
}
```

### 4. User Documentation

Create documentation in `/docs/partial_view_updates.md`:

```markdown
# Partial View Updates

## Overview

The Partial View Updates feature reduces bandwidth and battery usage by sending only changed fields in view update events, rather than resending the complete view state with every update.

**Benefits:**
- 43-93% reduction in bandwidth usage
- Lower battery consumption from reduced I/O
- Improved SDK performance on mobile devices

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

- **First view event:** Full `view` event with all properties
- **Subsequent updates:** `view_update` events with only changed fields
- **Backend:** Reconstructs complete view state by applying updates

## Example

```kotlin
// Setup
RumConfiguration.Builder(clientToken, environment, appId)
    .setEnablePartialViewUpdates(true)
    .build()

// First event (full view)
{
  "type": "view",
  "view": { "id": "v1", "url": "...", "time_spent": 0, "action": { "count": 0 } },
  "_dd": { "document_version": 1 }
}

// Second event (only what changed)
{
  "type": "view_update",
  "view": { "id": "v1", "time_spent": 150, "action": { "count": 1 } },
  "_dd": { "document_version": 2 }
}
```

## Requirements

- **SDK version:** X.Y.Z or later
- **Backend support:** Requires backend support for `view_update` event type
- **Opt-in:** Feature must be explicitly enabled via configuration

## Performance

- Diff computation overhead: <5ms per update
- Memory footprint: ~2KB per active view
- Bandwidth savings: 40-90% depending on update frequency

## When to Use

**Enable partial updates when:**
- Your app has many view updates (frequent resource loads, actions)
- You want to minimize SDK bandwidth usage
- Your users have limited data plans

**Keep disabled when:**
- Your app has very few view updates
- Compatibility with older backend infrastructure is required
- You're validating SDK behavior and want simpler events

## Troubleshooting

**Q: How do I verify the feature is working?**
A: Check your RUM events in Datadog - you should see `view_update` events instead of multiple `view` events for the same view.id.

**Q: Does this affect how data appears in Datadog?**
A: No, the backend reconstructs the complete view state. Your dashboards and queries work identically.

**Q: Can I enable this mid-session?**
A: No, the configuration is immutable. Set it during SDK initialization.

## Migration

If upgrading from an older SDK version:
1. Update to SDK version X.Y.Z or later
2. Add `.setEnablePartialViewUpdates(true)` to your configuration
3. Test in staging environment
4. Roll out to production

No code changes are required beyond configuration.
```

### 5. Internal Documentation

Add architecture documentation in `ARCHITECTURE.md`:

```markdown
# Partial View Updates - Architecture

## Overview

Implementation of RFC: RUM Event Format Limitation (Partial View Updates)

## Components

### ViewEventTracker
- Manages view state tracking
- Stores last sent event per view.id
- Sends full `view` or partial `view_update` based on configuration

### ViewDiffComputer
- Computes differences between view states
- Handles primitives, objects, arrays with specific rules
- Optimized for array append-only pattern

## Design Decisions

**Store-and-compare approach:**
- Chosen for simplicity and maintainability
- Alternative (dirty tracking) rejected due to complexity

**Array optimization:**
- Only send new elements for arrays
- Assumes append-only (valid for RUM slow_frames, page_states)

**Backend update rules:**
- SDK sends changes, backend applies merge logic
- Decouples SDK from schema evolution

## Maintenance Notes

**When adding new view fields:**
- No code changes needed in diff logic
- Automatically detected and diffed

**When modifying array fields:**
- Ensure append-only assumption holds
- If not, modify getNewArrayElements() logic

**Performance considerations:**
- Diff computation is O(n) where n = number of fields
- Typical view has 100-150 fields → <5ms overhead
```

## Key Components

- **End-to-end tests:** Validate complete user scenarios
- **Edge case tests:** Handle unusual conditions gracefully
- **Performance benchmarks:** Verify <5ms overhead requirement
- **User documentation:** Clear guide for SDK users
- **Internal documentation:** Architecture and maintenance notes

## Acceptance Criteria

- [ ] End-to-end tests cover all major user scenarios
- [ ] Edge case tests handle unusual conditions (empty diff, rapid updates, large views)
- [ ] Performance benchmarks validate <5ms overhead requirement
- [ ] Memory leak testing shows no retained state after view ends
- [ ] Backward compatibility verified (feature disabled = identical behavior)
- [ ] User documentation written and reviewed
- [ ] Internal architecture documentation complete
- [ ] Example code and migration guide provided
- [ ] Release notes content drafted
- [ ] All tests passing
- [ ] Code review completed
- [ ] Feature marked as production-ready

## Testing Strategy

### Test Categories

1. **Functional tests:** All features work as specified
2. **Performance tests:** Overhead within acceptable limits
3. **Memory tests:** No leaks, bounded memory usage
4. **Compatibility tests:** Feature disabled = old behavior
5. **Integration tests:** Works with existing SDK components

### Test Coverage Goals

- Unit tests: >90% code coverage
- Integration tests: All major user flows
- Edge cases: All identified edge cases
- Performance: <5ms overhead validated
- Memory: ~2KB per view validated

## Open Questions

1. **Backend readiness:**
   - Is backend support for `view_update` ready for testing?
   - How to validate end-to-end in staging?

2. **Documentation location:**
   - Where should user docs live? `/docs` folder?
   - Should they be published to public docs site?

3. **Telemetry:**
   - Should we add metrics to track feature usage?
   - Track: enabled %, bandwidth savings, diff computation time?

4. **Release notes:**
   - What version will this ship in?
   - How to communicate opt-in nature?

## Dependencies

**Phase 3 Complete:**
- Full implementation working end-to-end
- All unit and integration tests passing

**External:**
- Backend support for `view_update` event type (for full end-to-end validation)
- Documentation infrastructure (where to publish docs)

## Deliverables

- [ ] Comprehensive end-to-end test suite
- [ ] Edge case test suite
- [ ] Performance benchmarks and validation
- [ ] Memory leak tests
- [ ] User documentation (`docs/partial_view_updates.md`)
- [ ] Internal architecture documentation
- [ ] Example code and migration guide
- [ ] Release notes content
- [ ] All tests passing (>95% coverage)
- [ ] Phase 4 code review completed
- [ ] Feature approved for production release

## Success Metrics

Upon completion, the feature should:
- ✅ Pass all functional tests
- ✅ Pass all performance benchmarks (<5ms overhead)
- ✅ Show no memory leaks
- ✅ Have clear, comprehensive documentation
- ✅ Be ready for opt-in production release

## Post-Release

After this phase, the feature is ready for production release as an opt-in feature. Future work:
- Monitor feature adoption and performance in production
- Collect user feedback
- Decide timeline for making feature enabled-by-default
- Consider adding telemetry for observability
