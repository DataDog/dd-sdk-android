# Implementation Plan: Phase 4 - RumViewScope Integration

**Phase Document:** [04-rumviewscope-integration.md](04-rumviewscope-integration.md)
**Status:** Not Started
**Generated:** 2026-02-17

## Overview

This plan connects the ViewEventTracker (built in Phase 3) to the actual RUM event pipeline in RumViewScope. The integration will allow the SDK to send partial view updates in production. The key challenge is minimal disruption to RumViewScope (~1700 lines) while adding the new capability.

**Strategy:** Wrap existing event sending logic with ViewEventTracker when feature is enabled, ensuring zero behavior change when disabled.

## Prerequisites

- [x] Phase 1 completed: Configuration flag available
- [x] Phase 2 completed: ViewDiffComputer working
- [x] Phase 3 completed: ViewEventTracker fully implemented
- [x] EventWriter interface defined
- [x] All unit tests for Phases 1-3 passing

## Implementation Tasks

### Task 1: Create ViewEventConverter

**Objective:** Convert ViewEvent model objects to Map<String, Any?> representation for diff computation

**Files to create:**
- `features/dd-sdk-android-rum/src/main/kotlin/com/datadog/android/rum/internal/domain/event/viewupdate/ViewEventConverter.kt` - Converter utility

**Implementation steps:**

1. Create internal object ViewEventConverter with toMap() method
2. Use reflection-based approach initially (can optimize later if needed)
3. Handle all ViewEvent fields recursively (nested objects, arrays, nulls)
4. Ensure generated JSON-like structure matches what backend expects
5. Add KDoc documentation explaining the conversion strategy

**Code pattern:**

```kotlin
/**
 * Converts ViewEvent model objects to Map representation.
 *
 * This allows ViewEventTracker to compute diffs on ViewEvent objects
 * by converting them to a flexible Map<String, Any?> structure that
 * mirrors the JSON schema.
 */
internal object ViewEventConverter {

    /**
     * Converts a ViewEvent to its Map representation.
     *
     * Uses reflection to extract all fields and nested objects.
     * The resulting map structure matches the JSON schema.
     *
     * @param event The ViewEvent model object
     * @return Map representation suitable for diff computation
     */
    fun toMap(event: ViewEvent): Map<String, Any?> {
        // Use reflection to convert ViewEvent to Map
        // This approach is simple and handles schema changes automatically
        return convertToMap(event)
    }

    /**
     * Recursively converts any object to Map/List/primitive structure.
     */
    private fun convertToMap(obj: Any?): Any? {
        return when (obj) {
            null -> null
            is String, is Number, is Boolean -> obj
            is List<*> -> obj.map { convertToMap(it) }
            is Map<*, *> -> obj.mapValues { convertToMap(it.value) }
            else -> {
                // Use reflection for data classes
                val kClass = obj::class
                val constructor = kClass.constructors.firstOrNull()
                    ?: return obj.toString()

                val properties = kClass.memberProperties
                properties.associate { prop ->
                    prop.name to convertToMap(prop.getter.call(obj))
                }
            }
        }
    }
}
```

**Alternative (Manual Mapping):**
If reflection proves too slow or brittle, fall back to manual mapping:

```kotlin
fun toMap(event: ViewEvent): Map<String, Any?> {
    return mutableMapOf<String, Any?>().apply {
        put("type", "view")
        put("date", event.date)
        put("application", mapOf("id" to event.application.id, ...))
        put("session", mapOf("id" to event.session.id, ...))
        put("view", mapOf(
            "id" to event.view.id,
            "name" to event.view.name,
            "url" to event.view.url,
            "time_spent" to event.view.timeSpent,
            ...
        ))
        // ... all other fields
    }
}
```

**Decision point:** Start with reflection, measure performance. If >2ms, switch to manual.

**Acceptance:**
- ✓ Converts complete ViewEvent to Map<String, Any?>
- ✓ Handles all field types (primitives, objects, arrays, nulls)
- ✓ Conversion time <2ms for typical view
- ✓ Unit tests verify all critical fields present in output

---

### Task 2: Create RumEventWriterAdapter

**Objective:** Adapt EventWriter interface to SDK's DataWriter<Any>

**Files to create:**
- `features/dd-sdk-android-rum/src/main/kotlin/com/datadog/android/rum/internal/domain/event/viewupdate/RumEventWriterAdapter.kt` - Adapter implementation

**Implementation steps:**

1. Create internal class RumEventWriterAdapter implementing EventWriter
2. Accept DataWriter<Any> in constructor
3. Convert Map event to appropriate format for DataWriter
4. Handle both "view" and "view_update" event types
5. Return success/failure status

**Code pattern:**

```kotlin
/**
 * Adapts EventWriter interface to SDK's DataWriter<Any>.
 *
 * This adapter allows ViewEventTracker to write events through the SDK's
 * standard event writing infrastructure without depending directly on
 * DataWriter.
 *
 * @param dataWriter The SDK's DataWriter for persisting events
 */
internal class RumEventWriterAdapter(
    private val dataWriter: DataWriter<Any>
) : EventWriter {

    /**
     * Writes an event using the SDK's DataWriter.
     *
     * The event is expected to be a Map<String, Any?> with "type" field
     * indicating whether it's a "view" or "view_update" event.
     *
     * @param event The event to write (Map or ViewEvent object)
     * @return true if write succeeded, false otherwise
     */
    override fun write(event: Any): Boolean {
        return when (event) {
            is Map<*, *> -> {
                // Event from ViewEventTracker (already in Map form)
                // Convert to appropriate model or write as-is
                dataWriter.write(event)
                true
            }
            is ViewEvent -> {
                // Fallback: ViewEvent object (shouldn't happen in normal flow)
                dataWriter.write(event)
                true
            }
            else -> {
                // Unexpected type
                false
            }
        }
    }
}
```

**Note on DataWriter signature:**
- DataWriter<Any> accepts any object
- ViewEvent objects are currently written directly
- Maps will need serialization (handled by DataWriter's serializer)

**Acceptance:**
- ✓ Implements EventWriter interface
- ✓ Correctly delegates to DataWriter<Any>
- ✓ Handles both Map and ViewEvent inputs
- ✓ Returns appropriate success/failure status
- ✓ Unit tests verify write delegation

---

### Task 3: Integrate ViewEventTracker into RumViewScope

**Objective:** Modify RumViewScope to use ViewEventTracker when feature is enabled

**Files to modify:**
- `features/dd-sdk-android-rum/src/main/kotlin/com/datadog/android/rum/internal/domain/scope/RumViewScope.kt` - Add ViewEventTracker integration

**Implementation steps:**

1. Add optional ViewEventTracker field to RumViewScope (lazy initialized if feature enabled)
2. Modify sendViewUpdate() method to branch based on feature flag
3. When enabled: Convert ViewEvent to Map, call ViewEventTracker
4. When disabled: Use existing write logic (no change)
5. Add onViewEnded() cleanup call to ViewEventTracker
6. Ensure thread safety (RumViewScope methods already called from single RUM thread)

**Code pattern:**

```kotlin
@Suppress("TooManyFunctions", "LargeClass", "LongParameterList")
internal open class RumViewScope(
    // ... existing parameters
) : RumScope {

    // ... existing fields

    /**
     * Optional ViewEventTracker for partial view updates.
     * Lazy initialized only when feature is enabled.
     */
    private val viewEventTracker: ViewEventTracker? by lazy {
        val rumConfig = (parentScope as? RumApplicationScope)?.rumConfiguration
        if (rumConfig?.featureConfiguration?.enablePartialViewUpdates == true) {
            ViewEventTracker(
                config = rumConfig,
                writer = RumEventWriterAdapter(writer), // Need to capture writer from context
                diffComputer = ViewDiffComputer()
            )
        } else {
            null
        }
    }

    // ... existing methods

    @Suppress("LongMethod", "ComplexMethod")
    internal fun sendViewUpdate(
        event: RumRawEvent,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>,
        eventType: EventType = EventType.DEFAULT
    ) {
        // ... existing logic to build ViewEvent (lines 1124-1339)

        val viewEvent = ViewEvent(
            // ... all existing ViewEvent construction
        )

        // NEW: Branch based on feature flag
        val tracker = viewEventTracker
        if (tracker != null) {
            // Feature enabled: use ViewEventTracker
            val eventMap = ViewEventConverter.toMap(viewEvent)
            tracker.sendViewUpdate(viewId, eventMap)
        } else {
            // Feature disabled: use existing logic
            sdkCore.newRumEventWriteOperation(datadogContext, writeScope, writer, eventType) {
                viewEvent
            }
        }
    }

    // ... existing methods

    private fun onStop(event: RumRawEvent, writer: DataWriter<Any>) {
        // ... existing stop logic

        // NEW: Cleanup ViewEventTracker state
        viewEventTracker?.onViewEnded(viewId)
    }
}
```

**Challenge: Accessing writer in lazy init:**
The writer is passed as parameter to sendViewUpdate(), not stored as field. Solutions:

**Option A: Pass writer to tracker on each call (simplest)**
```kotlin
private var viewEventTracker: ViewEventTracker? = null

internal fun sendViewUpdate(..., writer: DataWriter<Any>, ...) {
    // Lazy initialize on first call
    if (viewEventTracker == null && shouldEnableFeature()) {
        viewEventTracker = ViewEventTracker(config, RumEventWriterAdapter(writer))
    }

    val tracker = viewEventTracker
    if (tracker != null) {
        // Use tracker
    } else {
        // Existing logic
    }
}
```

**Option B: Store writer as field in RumViewScope**
- Requires refactoring RumViewScope constructor
- More invasive change
- Defer unless Option A proves problematic

**Recommended:** Start with Option A (simpler, less invasive)

**Acceptance:**
- ✓ ViewEventTracker initialized when feature enabled
- ✓ ViewEventTracker is null when feature disabled
- ✓ sendViewUpdate branches correctly based on feature flag
- ✓ ViewEvent converted to Map when using tracker
- ✓ ViewEventTracker.onViewEnded() called when view stops
- ✓ No behavior change when feature disabled
- ✓ Integration tests verify end-to-end flow

---

### Task 4: Handle RumConfiguration Access

**Objective:** Ensure RumViewScope can access RumConfiguration for feature flag check

**Files to modify:**
- `features/dd-sdk-android-rum/src/main/kotlin/com/datadog/android/rum/internal/domain/scope/RumViewScope.kt` - Access configuration

**Implementation steps:**

1. Identify how RumViewScope currently accesses configuration
2. RumViewScope receives parentScope (RumApplicationScope or RumSessionScope)
3. Navigate to RumApplicationScope to get RumConfiguration
4. Cache configuration reference for efficient access

**Code pattern:**

```kotlin
internal open class RumViewScope(
    override val parentScope: RumScope,
    // ... other params
) : RumScope {

    /**
     * Cached RUM configuration from parent scope.
     */
    private val rumConfiguration: RumConfiguration? by lazy {
        // Navigate parent chain to find RumApplicationScope
        var scope: RumScope? = parentScope
        while (scope != null) {
            if (scope is RumApplicationScope) {
                return@lazy scope.rumConfiguration
            }
            scope = scope.parentScope
        }
        null
    }

    private val isPartialUpdatesEnabled: Boolean by lazy {
        rumConfiguration?.featureConfiguration?.enablePartialViewUpdates == true
    }

    private fun shouldInitializeTracker(): Boolean {
        return isPartialUpdatesEnabled
    }
}
```

**Alternative:** If RumApplicationScope doesn't expose rumConfiguration, pass it through constructor chain or access via sdkCore feature registry.

**Acceptance:**
- ✓ RumViewScope can access RumConfiguration
- ✓ Feature flag check works correctly
- ✓ Configuration access is efficient (cached)
- ✓ No compilation errors

---

## Testing Tasks

### Test 1: ViewEventConverter Tests

**Type:** Unit
**Files:** `features/dd-sdk-android-rum/src/test/kotlin/com/datadog/android/rum/internal/domain/event/viewupdate/ViewEventConverterTest.kt`
**Covers:** ViewEvent to Map conversion

**Test cases:**
- [ ] Converts simple ViewEvent with required fields
- [ ] Handles nested objects (view, application, session)
- [ ] Handles arrays (slow_frames, custom timings)
- [ ] Handles null fields correctly
- [ ] Handles additionalProperties maps (context, featureFlags)
- [ ] Performance: conversion completes in <2ms
- [ ] Converts complex ViewEvent with all optional fields populated

---

### Test 2: RumEventWriterAdapter Tests

**Type:** Unit
**Files:** `features/dd-sdk-android-rum/src/test/kotlin/com/datadog/android/rum/internal/domain/event/viewupdate/RumEventWriterAdapterTest.kt`
**Covers:** EventWriter to DataWriter adaptation

**Test cases:**
- [ ] Writes Map event to DataWriter
- [ ] Writes ViewEvent object to DataWriter
- [ ] Returns true on successful write
- [ ] Returns false on DataWriter failure
- [ ] Preserves event data (no corruption)

---

### Test 3: RumViewScope Integration Tests

**Type:** Integration
**Files:** `features/dd-sdk-android-rum/src/test/kotlin/com/datadog/android/rum/internal/domain/scope/RumViewScopePartialUpdatesTest.kt`
**Covers:** End-to-end partial updates flow

**Test cases:**
- [ ] Feature enabled: First event is full view
- [ ] Feature enabled: Second event is view_update
- [ ] Feature enabled: view_update contains only changed fields
- [ ] Feature enabled: document_version increments correctly
- [ ] Feature disabled: All events are full view
- [ ] Feature disabled: Behavior identical to old SDK
- [ ] ViewEventTracker state cleaned up when view ends
- [ ] Multiple views tracked independently

**Test setup:**
```kotlin
@Test
fun `M send view then view_update W feature enabled`() {
    // Given - enable partial updates
    val rumConfig = RumConfiguration.Builder(appId, clientToken, env)
        .setEnablePartialViewUpdates(true)
        .build()

    val capturedEvents = mutableListOf<Any>()
    val mockWriter = mock<DataWriter<Any>> {
        on { write(any()) } doAnswer {
            capturedEvents.add(it.arguments[0])
            true
        }
    }

    // When - start view and make changes
    val scope = createRumViewScope(rumConfig, mockWriter)
    scope.handleEvent(RumRawEvent.StartView(...), context, writeScope, mockWriter)
    scope.handleEvent(RumRawEvent.AddAction(...), context, writeScope, mockWriter)
    scope.handleEvent(RumRawEvent.AddResource(...), context, writeScope, mockWriter)

    // Then - verify events
    assertThat(capturedEvents).hasSize(3)

    val event1 = capturedEvents[0] as Map<*, *>
    assertThat(event1["type"]).isEqualTo("view")
    assertThat((event1["_dd"] as Map<*, *>)["document_version"]).isEqualTo(1)

    val event2 = capturedEvents[1] as Map<*, *>
    assertThat(event2["type"]).isEqualTo("view_update")
    assertThat((event2["_dd"] as Map<*, *>)["document_version"]).isEqualTo(2)
    // Verify event2 is smaller than event1
    assertThat(event2.keys.size).isLessThan(event1.keys.size)
}
```

---

## Task Checklist

- [ ] Task 1: ViewEventConverter implemented
- [ ] Task 2: RumEventWriterAdapter implemented
- [ ] Task 3: RumViewScope integration complete
- [ ] Task 4: RumConfiguration access working
- [ ] Test 1: ViewEventConverter tests passing
- [ ] Test 2: RumEventWriterAdapter tests passing
- [ ] Test 3: RumViewScope integration tests passing
- [ ] All Phase 1-3 tests still passing (no regression)
- [ ] Code compiles without errors
- [ ] All acceptance criteria verified

## Implementation Order

Recommended order to minimize dependencies:

1. **Task 4** (Configuration access) - Establish how to check feature flag
2. **Task 1** (ViewEventConverter) - Can be tested independently
3. **Task 2** (RumEventWriterAdapter) - Can be tested independently
4. **Task 3** (RumViewScope integration) - Depends on Tasks 1, 2, 4
5. **Test 3** (Integration tests) - Validates complete flow

Tasks 1 and 2 can be done in parallel (no dependencies).

## Notes

### RumViewScope Complexity

RumViewScope is ~1700 lines and handles many concerns:
- View lifecycle management
- Event counting (actions, resources, errors, crashes)
- Performance metrics collection
- Slow frames tracking
- Vital monitors
- Accessibility snapshots
- Battery and display info

**Integration philosophy:** Touch as little as possible. Add ViewEventTracker as an optional wrapper around existing event building logic.

### Thread Safety

RumViewScope methods are called from the RUM executor thread (single-threaded). No additional synchronization needed for ViewEventTracker fields.

### Backward Compatibility

**Critical:** When feature is disabled (default), SDK must behave exactly as before:
- Same events sent (full view events only)
- Same metadata structure
- Same performance characteristics
- Zero regression risk

**Verification strategy:**
1. Run existing RumViewScope tests with feature disabled - all must pass
2. Add new integration tests with feature enabled
3. Compare event payloads (feature disabled) vs old SDK - must be identical

### Performance Considerations

Adding ViewEventTracker overhead when enabled:
- ViewEvent → Map conversion: ~1-2ms
- Diff computation: <5ms (validated in Phase 2)
- Total overhead: <7ms per update

When disabled: Zero overhead (not instantiated).

### Open Questions

1. **DataWriter serialization:** Does DataWriter<Any> correctly serialize Map<String, Any?>?
   - **Resolution:** Test with mock DataWriter in integration tests
   - If Map not supported, may need to convert back to ViewEvent object

2. **Event type "view_update":** Does existing serialization handle new type?
   - **Resolution:** May need to extend ViewEvent model or use custom serialization
   - Check how EventType.DEFAULT interacts with event.type field

3. **RumConfiguration access pattern:** Best way for RumViewScope to access config?
   - **Resolution:** Check if parentScope exposes configuration, otherwise pass in constructor

4. **ViewEventConverter performance:** Reflection fast enough or need manual mapping?
   - **Resolution:** Implement reflection first, benchmark, switch to manual if >2ms

## Success Criteria

Phase 4 is complete when:

- [ ] ViewEventConverter converts ViewEvent to Map<String, Any?>
- [ ] RumEventWriterAdapter connects EventWriter to DataWriter<Any>
- [ ] RumViewScope uses ViewEventTracker when feature enabled
- [ ] RumViewScope uses existing flow when feature disabled
- [ ] Document version propagates correctly through events
- [ ] First view event sends full view data
- [ ] Subsequent view updates send partial data
- [ ] Integration tests pass (feature enabled and disabled)
- [ ] No behavior change when feature disabled (regression tests pass)
- [ ] End-to-end flow works: config → RumViewScope → ViewEventTracker → diff → DataWriter
- [ ] Code review approved
- [ ] All acceptance criteria from phase document met

## Next Phase

**Phase 5: Testing & Documentation** will add comprehensive end-to-end testing, performance benchmarking, edge case validation, memory leak testing, and user-facing documentation.
