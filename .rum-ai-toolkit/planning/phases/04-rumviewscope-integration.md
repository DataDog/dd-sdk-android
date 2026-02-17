# Phase 4: RumViewScope Integration

**Status:** Complete
**Estimated Effort:** 1-2 days
**Dependencies:** Phase 3 (requires ViewEventTracker complete)

## Objective

Integrate ViewEventTracker into RumViewScope to actually use partial view updates in the SDK's event flow. This phase connects all the components built in previous phases to make the feature functional end-to-end.

## Scope

### In Scope

- ViewEvent to Map<String, Any?> converter
- RumViewScope integration to call ViewEventTracker
- EventWriter adapter to connect to SDK's DataWriter
- Integration with existing event serialization
- Basic integration tests to verify end-to-end flow
- Handling of document_version in existing event metadata

### Out of Scope (Deferred)

- Comprehensive end-to-end testing (Phase 5)
- Performance benchmarking infrastructure (Phase 5)
- User documentation (Phase 5)
- Migration strategy for existing SDK users (Phase 5)

## Requirements Addressed

From the spec, this phase completes:
- **FR-1:** First view event is complete - Actually sends full view in production flow
- **FR-2:** Subsequent updates are partial - Actually sends view_update in production flow
- **FR-4:** Document version tracking - Integrates with existing event metadata
- **FR-7:** Feature isolation - When disabled, uses existing flow (zero behavior change)

## Implementation Approach

### 1. Create ViewEvent to Map Converter

Create a utility to extract Map<String, Any?> from ViewEvent model objects:

```kotlin
internal object ViewEventConverter {
    /**
     * Converts a ViewEvent model to a Map representation.
     * This allows ViewEventTracker to compute diffs on ViewEvent objects.
     *
     * @param event The ViewEvent model object
     * @return Map representation of the event
     */
    fun toMap(event: ViewEvent): Map<String, Any?> {
        // Convert ViewEvent to Map
        // This can use reflection or manual mapping
        // Manual mapping is more explicit and safer
    }
}
```

### 2. Create EventWriter Adapter

Create an adapter that connects ViewEventTracker's EventWriter to the SDK's DataWriter:

```kotlin
internal class RumEventWriterAdapter(
    private val dataWriter: DataWriter<Any>
) : EventWriter {

    override fun write(event: Any): Boolean {
        // Convert Map to appropriate event type
        // Write using DataWriter
        // Return success status
    }
}
```

### 3. Integrate into RumViewScope

Modify RumViewScope to use ViewEventTracker when feature is enabled:

```kotlin
internal class RumViewScope {

    private val viewEventTracker: ViewEventTracker? by lazy {
        if (config.featureConfiguration.enablePartialViewUpdates) {
            ViewEventTracker(
                config = rumConfig,
                writer = RumEventWriterAdapter(writer),
                diffComputer = ViewDiffComputer()
            )
        } else {
            null
        }
    }

    private fun sendViewEvent() {
        val event = buildViewEvent() // Existing logic

        if (viewEventTracker != null) {
            // Use ViewEventTracker for partial updates
            val eventMap = ViewEventConverter.toMap(event)
            viewEventTracker.sendViewUpdate(event.view.id, eventMap)
        } else {
            // Use existing flow
            writer.write(event)
        }
    }
}
```

### 4. Handle Document Version

Ensure document_version from ViewEventTracker is properly included in event metadata:

```kotlin
// In RumEventWriterAdapter
override fun write(event: Any): Boolean {
    when (event) {
        is Map<*, *> -> {
            val eventType = event["type"]
            val documentVersion = (event["_dd"] as? Map<*, *>)?.get("document_version") as? Int

            // Convert to appropriate model or handle as raw map
            // Include document_version in metadata
        }
    }
}
```

## Key Components

- **ViewEventConverter:** Converts ViewEvent models to Map representation
- **RumEventWriterAdapter:** Adapts EventWriter interface to DataWriter
- **RumViewScope modifications:** Integration point for ViewEventTracker
- **Document version handling:** Ensures version propagates through metadata

## Acceptance Criteria

- [ ] ViewEventConverter can convert ViewEvent to Map<String, Any?>
- [ ] RumEventWriterAdapter connects EventWriter to DataWriter
- [ ] RumViewScope uses ViewEventTracker when feature is enabled
- [ ] RumViewScope uses existing flow when feature is disabled
- [ ] Document version is properly included in event metadata
- [ ] First view event for a view sends full event
- [ ] Subsequent view updates send partial events
- [ ] Integration tests verify end-to-end flow
- [ ] No regression in existing SDK behavior when feature is disabled
- [ ] Code compiles and existing tests still pass

## Testing Strategy

### Integration Tests

**Scenario: Feature enabled, multiple view updates**
```kotlin
@Test
fun `M send view then view_update W feature enabled`() {
    // Given - enable partial updates
    val config = RumConfiguration.Builder(appId)
        .setEnablePartialViewUpdates(true)
        .build()

    // When - start view and make changes
    rumMonitor.startView(key, name, attributes)
    rumMonitor.addAction(...)  // Causes view update
    rumMonitor.addResource(...) // Causes another view update

    // Then - verify events
    val events = capturedEvents()
    assertThat(events[0].type).isEqualTo("view")
    assertThat(events[1].type).isEqualTo("view_update")
    assertThat(events[2].type).isEqualTo("view_update")
}
```

**Scenario: Feature disabled, no behavior change**
```kotlin
@Test
fun `M send only view events W feature disabled`() {
    // Given - feature disabled (default)
    val config = RumConfiguration.Builder(appId).build()

    // When - start view and make changes
    rumMonitor.startView(key, name, attributes)
    rumMonitor.addAction(...)
    rumMonitor.addResource(...)

    // Then - verify only view events (existing behavior)
    val events = capturedEvents()
    assertThat(events.all { it.type == "view" }).isTrue()
}
```

## Open Questions

1. **ViewEvent to Map conversion approach?**
   - Manual mapping (safer, more explicit)
   - Reflection-based (more automatic, riskier)
   - JSON serialization/deserialization (slower, but complete)
   - **Recommendation:** Start with manual mapping for critical fields

2. **What to do with event mappers?**
   - ViewEventMapper still needs to apply transformations
   - Should it apply before or after ViewEventTracker?
   - **Recommendation:** Apply mappers after ViewEventTracker (on final event)

3. **How to handle view_update event type in serialization?**
   - ViewEvent model doesn't have view_update type
   - Need to either extend model or handle as special case
   - **Recommendation:** Handle as Map for view_update, serialize differently

4. **Thread safety considerations?**
   - RumViewScope methods called from single RUM thread?
   - ViewEventTracker needs synchronization?
   - **Defer to Phase 5** if complex

## Dependencies

**Phase 3 Complete:**
- ViewEventTracker fully implemented
- EventWriter interface defined
- ViewDiffComputer working

**External:**
- RumViewScope (existing SDK component)
- DataWriter interface (existing SDK component)
- ViewEvent model (existing SDK component)

## Notes

### RumViewScope Complexity

RumViewScope is a large file (~1700 lines) that handles:
- View lifecycle management
- Event counting (actions, resources, errors)
- Performance metrics collection
- View event building

**Integration strategy:** Minimize changes to RumViewScope. Add ViewEventTracker as optional component that wraps existing event sending logic.

### Backward Compatibility

**Critical:** When feature is disabled (default), SDK must behave exactly as before:
- Same events sent
- Same metadata
- Same performance characteristics
- Zero regression risk

### Performance Impact

Adding ViewEventTracker adds minimal overhead when enabled:
- Diff computation: <5ms (validated in Phase 2)
- Map conversion: ~1-2ms (one-time per event)
- Total overhead: <7ms per update (acceptable)

When disabled: Zero overhead (not instantiated).

## Success Criteria

Phase 4 is complete when:

- [ ] ViewEventConverter implemented and tested
- [ ] RumEventWriterAdapter implemented and tested
- [ ] RumViewScope integrated with ViewEventTracker
- [ ] Feature flag controls which path is used
- [ ] Document version propagates correctly
- [ ] Integration tests pass (feature enabled)
- [ ] Regression tests pass (feature disabled)
- [ ] No behavior change when feature is disabled
- [ ] End-to-end flow works: config → diff → send
- [ ] Code review approved
- [ ] All acceptance criteria met

## Next Phase

**Phase 5: Testing & Documentation** will add comprehensive end-to-end testing, performance benchmarking, edge case validation, and user-facing documentation.
