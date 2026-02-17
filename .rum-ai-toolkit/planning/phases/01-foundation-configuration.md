# Phase 1: Foundation & Configuration

**Status:** Complete
**Estimated Effort:** 2-3 days
**Dependencies:** None

## Objective

Establish the foundational infrastructure for partial view updates: configuration API, data structures for tracking view state, and basic scaffolding for the ViewEventTracker. This phase focuses on setup without implementing diff logic or changing existing event flow.

## Scope

### In Scope

- Add `enablePartialViewUpdates` configuration flag to `RumConfiguration`
- Create `lastSentEvents` and `documentVersions` storage structures
- Implement basic `ViewEventTracker` class skeleton
- Add feature flag gating logic (when disabled, use existing behavior)
- Unit tests for configuration handling
- Internal documentation for new components

### Out of Scope (Deferred)

- Diff computation logic (Phase 2)
- Actual event sending changes (Phase 3)
- Integration with RUM event pipeline (Phase 3)
- `view_update` event creation (Phase 3)

## Requirements Addressed

From the spec, this phase implements:
- **FR-5:** Opt-in configuration - Feature controlled by configuration flag, disabled by default
- **FR-7:** Feature isolation - When disabled, SDK behaves exactly as before
- **NFR-3:** Backward compatibility - Existing SDK behavior preserved when feature disabled
- **NFR-4:** Configuration immutability - Feature flag set at SDK initialization, doesn't change during runtime

## Implementation Approach

### 1. Configuration API

Add new configuration property to `RumConfiguration.Builder`:

```kotlin
class RumConfiguration private constructor(
    // ... existing properties
    internal val enablePartialViewUpdates: Boolean
) {
    class Builder(
        private val clientToken: String,
        private val environment: String,
        private val applicationId: String
    ) {
        private var enablePartialViewUpdates: Boolean = false  // Default: disabled

        /**
         * Enables partial view updates to reduce bandwidth and I/O.
         * When enabled, the SDK sends only changed fields in view update events.
         *
         * Default: false (opt-in feature)
         *
         * @param enabled true to enable partial view updates
         * @return this Builder instance
         */
        fun setEnablePartialViewUpdates(enabled: Boolean): Builder {
            this.enablePartialViewUpdates = enabled
            return this
        }

        fun build(): RumConfiguration {
            return RumConfiguration(
                // ... existing parameters
                enablePartialViewUpdates = enablePartialViewUpdates
            )
        }
    }
}
```

### 2. Data Structures

Create storage for tracking view state in `ViewEventTracker`:

```kotlin
internal class ViewEventTracker(
    private val config: RumConfiguration,
    private val eventWriter: EventWriter
) {
    /**
     * Stores the last sent event data for each active view.
     * Key: view.id
     * Value: Complete event data as sent (for diff computation)
     */
    private val lastSentEvents: MutableMap<String, Map<String, Any?>> =
        mutableMapOf()

    /**
     * Tracks document version counter per view.
     * Key: view.id
     * Value: Last used document_version (starts at 0, increments before each send)
     */
    private val documentVersions: MutableMap<String, Int> =
        mutableMapOf()

    // Methods to be implemented in later phases
    fun sendViewEvent(viewId: String, viewData: Map<String, Any?>) {
        TODO("Phase 3: Implement event sending")
    }

    fun sendViewUpdate(viewId: String, currentViewData: Map<String, Any?>) {
        TODO("Phase 3: Implement update sending")
    }

    /**
     * Cleanup when view ends.
     * Removes stored state to prevent memory leaks.
     */
    fun onViewEnded(viewId: String) {
        lastSentEvents.remove(viewId)
        documentVersions.remove(viewId)
    }
}
```

### 3. Feature Flag Gating

Add helper method to check if partial updates are enabled:

```kotlin
internal class ViewEventTracker(/* ... */) {

    /**
     * Returns true if partial view updates feature is enabled.
     */
    private fun isPartialUpdatesEnabled(): Boolean {
        return config.enablePartialViewUpdates
    }

    /**
     * Determines if this is the first event for a given view.
     */
    private fun isFirstEvent(viewId: String): Boolean {
        return viewId !in lastSentEvents
    }
}
```

### 4. Integration Point

Add placeholder integration with existing `RumMonitor`:

```kotlin
// In RumMonitor or equivalent
internal class RumMonitor(
    private val config: RumConfiguration
) {
    private val viewEventTracker: ViewEventTracker by lazy {
        ViewEventTracker(config, eventWriter)
    }

    fun onViewUpdate(viewId: String, viewData: Map<String, Any?>) {
        // Phase 3 will implement the actual logic
        // For now, continue using existing event sending
        sendViewEventLegacy(viewId, viewData)
    }
}
```

## Key Components

- **RumConfiguration:** Extended with `enablePartialViewUpdates` flag
- **ViewEventTracker:** New class managing view state tracking
- **Data storage:** Maps for last sent events and document versions
- **Feature gating:** Logic to check if feature is enabled

## Acceptance Criteria

- [ ] `RumConfiguration.Builder` has `setEnablePartialViewUpdates(boolean)` method
- [ ] Default value is `false` (feature disabled)
- [ ] Configuration is immutable after SDK initialization
- [ ] `ViewEventTracker` class exists with data structures defined
- [ ] `lastSentEvents` map stores view data by view.id
- [ ] `documentVersions` map stores counters by view.id
- [ ] `onViewEnded()` clears stored data for the view
- [ ] Feature flag can be checked via `isPartialUpdatesEnabled()`
- [ ] When feature is disabled, existing code path is used (no behavior change)
- [ ] Unit tests verify configuration handling
- [ ] Unit tests verify data structure initialization and cleanup
- [ ] Code review completed

## Testing Strategy

### Unit Tests

**Configuration Tests:**
```kotlin
@Test
fun `default configuration has partial updates disabled`() {
    val config = RumConfiguration.Builder(token, env, appId).build()
    assertFalse(config.enablePartialViewUpdates)
}

@Test
fun `can enable partial updates via configuration`() {
    val config = RumConfiguration.Builder(token, env, appId)
        .setEnablePartialViewUpdates(true)
        .build()
    assertTrue(config.enablePartialViewUpdates)
}

@Test
fun `configuration is immutable after build`() {
    val config = RumConfiguration.Builder(token, env, appId)
        .setEnablePartialViewUpdates(true)
        .build()
    // Verify config object is immutable
    // (Kotlin data class with val properties ensures this)
}
```

**ViewEventTracker Tests:**
```kotlin
@Test
fun `initial state has empty data structures`() {
    val tracker = ViewEventTracker(config, eventWriter)
    // Use reflection or internal accessors to verify maps are empty
}

@Test
fun `onViewEnded clears stored data`() {
    val tracker = ViewEventTracker(config, eventWriter)
    // Manually add data to maps
    tracker.lastSentEvents["view1"] = mapOf("key" to "value")
    tracker.documentVersions["view1"] = 5

    tracker.onViewEnded("view1")

    assertNull(tracker.lastSentEvents["view1"])
    assertNull(tracker.documentVersions["view1"])
}

@Test
fun `isFirstEvent returns true when no last sent event exists`() {
    val tracker = ViewEventTracker(config, eventWriter)
    assertTrue(tracker.isFirstEvent("view1"))
}

@Test
fun `isFirstEvent returns false after event is stored`() {
    val tracker = ViewEventTracker(config, eventWriter)
    tracker.lastSentEvents["view1"] = mapOf("data" to "value")
    assertFalse(tracker.isFirstEvent("view1"))
}
```

### Manual Testing

- Build SDK with feature disabled → verify existing behavior unchanged
- Build SDK with feature enabled → verify no crashes (even though logic not implemented yet)

## Open Questions

1. **Where does ViewEventTracker live in the codebase?**
   - Should it be a new file, or part of an existing component?
   - What package should it belong to?

2. **How is eventWriter accessed?**
   - Is there an existing interface for writing events?
   - Do we need to refactor existing event writing to support new event types?

3. **Memory management concerns?**
   - Should `lastSentEvents` have a maximum size limit?
   - What if app has many views open simultaneously?

4. **Thread safety?**
   - Are view updates called from multiple threads?
   - Do we need synchronization for the maps?

## Dependencies

**External:**
- None (foundational phase)

**Internal:**
- Existing `RumConfiguration` class
- Existing event writing infrastructure (for integration points)

## Deliverables

- [ ] Updated `RumConfiguration` with new flag
- [ ] New `ViewEventTracker` class file
- [ ] Unit tests for configuration
- [ ] Unit tests for data structure management
- [ ] Internal code documentation (KDoc comments)
- [ ] Phase 1 code review completed

## Next Phase

**Phase 2: Diff Computation Engine** will implement the `ViewDiffComputer` class that uses these data structures to compute changed fields.
