# Specification: Partial View Updates for Android RUM SDK

## Overview

This specification defines the implementation of partial view updates for the Android RUM SDK. Instead of resending the complete view state with every update, the SDK will send only the fields that have changed, significantly reducing bandwidth usage, I/O operations, and battery consumption on mobile devices.

**Type:** New Feature
**Target:** Android RUM SDK
**Based on:** [RFC - RUM Event Format Limitation](../../../partial-updates-rfc.md)

## Scope

### In Scope

- Implementation of `view_update` event type in Android SDK
- Diff computation logic to identify changed fields
- Smart array handling (send only new elements)
- Per-view `document_version` counter management
- Opt-in configuration flag
- Unit tests for diff computation and event building
- Documentation for SDK users

### Out of Scope

- iOS SDK implementation (separate effort)
- Browser SDK implementation (separate effort)
- Backend ingestion pipeline changes (handled by backend team)
- Automatic migration for existing SDK users
- Performance benchmarking infrastructure
- Feature flag infrastructure for gradual rollout

## Problem Statement

*(From RFC - Rum Event Format Limitation)*

The current RUM SDK architecture requires resending the complete view state with every update. This creates several problems:

**Redundant data transmission:** View events are updated frequently (resource counts, time spent, performance metrics), but most fields remain unchanged. Currently, every update resends all fields, creating significant redundancy:
- Browser SDK: 52-73% redundancy in uploads
- Android SDK: 49-93% redundancy in disk writes, 43-46% in uploads

**Costly I/O operations on mobile:** On Android, each view update is independently serialized and saved to disk. Large updates increase disk usage, while frequent updates lead to higher battery and CPU consumption.

**Limited scalability:** As the SDK collects more data (accessibility attributes, feature flags, slow frames, page states), the view event becomes increasingly bloated. This is a "serious scaling and performance bottleneck" for future data collection.

**Customer impact:** Redundant data re-upload costs money for users with limited data plans. Some customers (e.g., Square) closely monitor SDK bandwidth usage.

## Requirements

### Functional Requirements

- **FR-1: First view event is complete** - When a view starts, the SDK MUST send a full `view` event containing all view properties
- **FR-2: Subsequent updates are partial** - After the first event, the SDK MUST send `view_update` events containing only fields that have changed
- **FR-3: Array optimization** - For array fields (e.g., `view.slow_frames[]`), the SDK MUST send only newly added elements, not the entire array
- **FR-4: Document version tracking** - Each view MUST maintain its own `_dd.document_version` counter, starting at 1 and incrementing with each sent event
- **FR-5: Opt-in configuration** - The feature MUST be controlled by a configuration flag, disabled by default
- **FR-6: Empty diff handling** - If no fields have changed since the last sent event, the SDK SHOULD NOT send an update event
- **FR-7: Feature isolation** - When the feature is disabled, the SDK MUST behave exactly as before (always send full `view` events)

### Non-Functional Requirements

- **NFR-1: Performance overhead** - Diff computation MUST add less than 5ms overhead to event sending
- **NFR-2: Memory footprint** - The SDK MUST store only one copy of the last sent event per active view (typically 1-2 views)
- **NFR-3: Backward compatibility** - Existing SDK behavior MUST be preserved when the feature is disabled (default)
- **NFR-4: Configuration immutability** - The feature flag MUST be set at SDK initialization and MUST NOT change during runtime
- **NFR-5: Correctness** - The diff computation MUST correctly identify all changed fields with zero false negatives

## Architecture

### High-Level Design

The implementation follows a **store-and-compare** approach:

1. **View starts** → Build and send full `view` event → Store copy as "last sent"
2. **View updates** → Build current view state → Compute diff vs "last sent" → Send `view_update` with changes → Update "last sent"

This approach was chosen for:
- **Simplicity:** Centralized diff logic, no instrumentation of field setters
- **Maintainability:** New fields automatically supported without additional code
- **Correctness:** Single source of truth for change detection
- **Performance:** Acceptable overhead given infrequent update frequency (2-37 updates per view)

### Component Architecture

```
┌─────────────────────────────────────────────────────────┐
│ RumMonitor                                              │
│  └─ updateView(viewId, updates)                        │
└────────────────┬────────────────────────────────────────┘
                 │
                 v
┌─────────────────────────────────────────────────────────┐
│ ViewEventTracker                                        │
│  - lastSentEvents: Map<ViewId, RumViewEvent>           │
│  - documentVersions: Map<ViewId, Int>                   │
│  + sendViewEvent(viewId)                                │
│  + sendViewUpdateIfEnabled(viewId)                      │
└────────────────┬────────────────────────────────────────┘
                 │
                 v
┌─────────────────────────────────────────────────────────┐
│ ViewDiffComputer                                        │
│  + computeDiff(lastSent, current): Map<String, Any?>   │
│  - diffPrimitives()                                     │
│  - diffObjects()                                        │
│  - diffArrays()  // Only new elements                   │
└─────────────────────────────────────────────────────────┘
                 │
                 v
┌─────────────────────────────────────────────────────────┐
│ EventWriter                                             │
│  + writeEvent(type, data)                               │
└─────────────────────────────────────────────────────────┘
```

### Change Tracking Strategy

**Option chosen: Store last sent event, compute diff at send time**

Each `ViewEventTracker` maintains:
- `lastSentEvents: Map<ViewId, RumViewEvent>` - The complete last sent event per view
- `documentVersions: Map<ViewId, Int>` - Counter per view.id

When sending an update:
1. Build current view state as a map/object
2. Retrieve last sent event for this `view.id`
3. If no last sent event exists → send full `view` event (first event)
4. Otherwise → compute diff → send `view_update` event
5. Store current state as new "last sent"

**Memory management:**
- Last sent event cleared when view ends
- Typical case: 1-2 active views × ~2KB = ~4KB overhead
- Acceptable for modern Android devices

## API Design

### Public Configuration API

```kotlin
// Opt-in configuration (disabled by default)
val rumConfig = RumConfiguration.Builder(clientToken, environment, applicationId)
    .setEnablePartialViewUpdates(true)  // Default: false
    .build()

DatadogSdk.initialize(context, rumConfig)
```

**Configuration properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enablePartialViewUpdates` | `Boolean` | `false` | Enables `view_update` events for view changes after initial event |

**Constraints:**
- Configuration is immutable after SDK initialization
- Cannot be changed during runtime (prevents mid-session inconsistencies)
- When disabled, SDK behavior is identical to current implementation

### Internal Implementation

#### ViewEventTracker Interface

```kotlin
internal class ViewEventTracker(
    private val config: RumConfiguration,
    private val eventWriter: EventWriter,
    private val diffComputer: ViewDiffComputer
) {
    private val lastSentEvents = mutableMapOf<String, Map<String, Any?>>()
    private val documentVersions = mutableMapOf<String, Int>()

    /**
     * Sends a view event (first event or when feature disabled)
     */
    fun sendViewEvent(viewId: String, viewData: Map<String, Any?>) {
        val version = documentVersions.getOrPut(viewId) { 0 } + 1
        documentVersions[viewId] = version

        val event = viewData.toMutableMap().apply {
            put("type", "view")
            put("_dd", mapOf("document_version" to version))
        }

        eventWriter.writeEvent(event)
        lastSentEvents[viewId] = viewData
    }

    /**
     * Sends a view_update event if feature is enabled and there are changes
     */
    fun sendViewUpdate(viewId: String, currentViewData: Map<String, Any?>) {
        if (!config.enablePartialViewUpdates) {
            // Feature disabled: send full view event
            sendViewEvent(viewId, currentViewData)
            return
        }

        val lastSent = lastSentEvents[viewId]
        if (lastSent == null) {
            // First event: send full view
            sendViewEvent(viewId, currentViewData)
            return
        }

        // Compute diff
        val changes = diffComputer.computeDiff(lastSent, currentViewData)

        if (changes.isEmpty()) {
            // No changes: skip sending
            return
        }

        // Send view_update with only changed fields
        val version = documentVersions.getOrPut(viewId) { 0 } + 1
        documentVersions[viewId] = version

        val event = buildMinimalEvent(viewId).apply {
            putAll(changes)
            put("type", "view_update")
            put("_dd", mapOf("document_version" to version))
        }

        eventWriter.writeEvent(event)
        lastSentEvents[viewId] = currentViewData
    }

    /**
     * Builds minimal event with required fields for view_update
     */
    private fun buildMinimalEvent(viewId: String): MutableMap<String, Any?> {
        return mutableMapOf(
            "application" to mapOf("id" to applicationId),
            "session" to mapOf("id" to sessionId),
            "view" to mapOf("id" to viewId)
        )
    }

    /**
     * Cleanup when view ends
     */
    fun onViewEnded(viewId: String) {
        lastSentEvents.remove(viewId)
        documentVersions.remove(viewId)
    }
}
```

#### ViewDiffComputer Interface

```kotlin
internal class ViewDiffComputer {

    /**
     * Computes the difference between last sent and current view state.
     * Returns a map containing only changed fields.
     *
     * For arrays: returns only newly added elements
     * For objects: returns full current value
     * For primitives: returns new value
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
                // No change
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

                // Field deleted (null in current)
                currentValue == null -> {
                    diff[key] = null
                }

                // All other cases: value changed
                else -> {
                    diff[key] = currentValue
                }
            }
        }

        // Check for deleted fields (present in lastSent but not in current)
        for (key in lastSent.keys) {
            if (key !in current) {
                diff[key] = null
            }
        }

        return diff
    }

    /**
     * For arrays, returns only newly appended elements.
     * Assumes arrays only grow (elements are not removed or reordered).
     */
    private fun getNewArrayElements(
        lastSent: List<*>,
        current: List<*>
    ): List<*> {
        return if (current.size > lastSent.size) {
            // Return elements added at the end
            current.subList(lastSent.size, current.size)
        } else {
            // Array didn't grow or shrunk (unexpected): no new elements
            emptyList<Any>()
        }
    }
}
```

## Data Model

### View State Representation

The SDK represents view state as a nested map structure:

```kotlin
Map<String, Any?> viewState = {
    "application": { "id": "app-123" },
    "session": { "id": "session-456" },
    "view": {
        "id": "view-789",
        "url": "https://example.com",
        "time_spent": 1500,
        "action": { "count": 3 },
        "error": { "count": 0 },
        "resource": { "count": 12 },
        "slow_frames": [
            { "start": 100, "duration": 20 },
            { "start": 250, "duration": 15 }
        ],
        "performance": {
            "lcp": { "timestamp": 341 }
        }
    },
    "context": {
        "custom_attr": "value"
    },
    "feature_flags": {
        "flag1": true,
        "flag2": false
    },
    "_dd": {
        "document_version": 2
    }
}
```

### Last Sent Event Storage

```kotlin
// Stored per view.id
data class LastSentEvent(
    val viewId: String,
    val eventData: Map<String, Any?>,
    val documentVersion: Int
)

// In-memory storage
private val lastSentEvents: MutableMap<String, Map<String, Any?>> = mutableMapOf()
private val documentVersions: MutableMap<String, Int> = mutableMapOf()
```

### Document Version Counter

- **Scope:** Per `view.id`
- **Initial value:** 1 (for first event)
- **Increment:** Each time an event is sent (whether `view` or `view_update`)
- **Type:** Monotonically increasing integer
- **Lifecycle:** Cleared when view ends

## Behavior

### Happy Path

1. **View starts**
   - User navigates to a new screen
   - SDK generates `view.id` = "v1"
   - No last sent event exists for "v1"

2. **First event sent**
   ```json
   {
     "type": "view",
     "view": { "id": "v1", "url": "...", "time_spent": 0, ... },
     "_dd": { "document_version": 1 }
   }
   ```
   - Full view event with all properties
   - Stored as "last sent" for view "v1"

3. **View properties change**
   - Resource loaded → `resource.count` = 1
   - Time elapsed → `time_spent` = 150

4. **Diff computation**
   - Compare current state vs last sent
   - Changes detected: `view.resource.count`, `view.time_spent`

5. **Update event sent**
   ```json
   {
     "type": "view_update",
     "application": { "id": "..." },
     "session": { "id": "..." },
     "view": {
       "id": "v1",
       "time_spent": 150,
       "resource": { "count": 1 }
     },
     "_dd": { "document_version": 2 }
   }
   ```
   - Only changed fields included
   - Updated state stored as new "last sent"

6. **More changes**
   - Slow frame detected → `slow_frames` array grows
   - Only new array element sent in next update

7. **View ends**
   - Final update sent
   - Last sent event and document version cleared from memory

### Array Handling (Special Case)

**Scenario:** Slow frames array grows over time

1. **First event**
   ```json
   "view": {
     "slow_frames": [
       { "start": 100, "duration": 20 }
     ]
   }
   ```

2. **More slow frames detected**
   - Array grows to 3 elements
   - Current: `[{100, 20}, {250, 15}, {400, 18}]`
   - Last sent: `[{100, 20}]`

3. **Update event**
   ```json
   "view": {
     "slow_frames": [
       { "start": 250, "duration": 15 },
       { "start": 400, "duration": 18 }
     ]
   }
   ```
   - Only new elements `[1]` and `[2]` sent
   - Backend applies APPEND rule → reconstructs full array

**Why this works:**
- SDK sends: only new elements
- Backend receives: `view_update` with partial array
- Backend applies: APPEND rule (defined in RFC update rules)
- Result: Complete array reconstructed on backend

### Edge Cases

**1. No changes since last update**
- Diff computation returns empty map
- No event sent (optimization)

**2. Feature disabled**
```kotlin
config.enablePartialViewUpdates = false // default
```
- Always sends full `view` events
- No diff computation
- Identical to current SDK behavior

**3. View ends immediately**
- Only one event sent (full `view`)
- No updates needed

**4. Array shrinks (unexpected)**
- Diff computation detects size decrease
- Sends empty list or omits field
- Backend behavior: TBD (arrays shouldn't shrink in RUM)

**5. Nested object changes**
- Example: `view.performance.lcp.timestamp` changes
- Diff includes full nested path: `{ "view": { "performance": { "lcp": { "timestamp": 341 } } } }`
- Backend applies MERGE rule for standard objects

**6. Custom context replaced**
- Example: `context` changes from `{a:1, b:2}` to `{c:3}`
- Diff includes full new object: `{ "context": {c:3} }`
- Backend applies REPLACE rule for custom objects

## Update Rules Reference

*(From RFC - Update Rules Section)*

The backend implements update rules to reconstruct the complete view state from `view` and `view_update` events. The SDK's role is to send changed fields; the backend's role is to apply appropriate merge logic.

### SDK Responsibilities

- **Primitives:** Send new value when changed
- **Standard objects:** Send changed nested fields (backend will MERGE)
- **Custom objects:** Send full object when changed (backend will REPLACE)
- **Arrays:** Send only new elements (backend will APPEND)
- **Deleted fields:** Send `null` (backend will DELETE)

### Backend Responsibilities

The backend applies these rules when processing `view_update` events:

- **Standard Objects → MERGE:** Objects with fixed schema (e.g., `view.*`, `view.performance.*`). Updates merge changed fields; existing fields remain.
- **Custom Objects → REPLACE:** Objects with `additionalProperties:true` (e.g., `context`, `usr`). Updates replace entire object.
- **Arrays → APPEND:** Array fields (e.g., `view.slow_frames[]`). Updates append new elements to existing array.
- **Primitives → REPLACE:** Simple fields (e.g., `view.time_spent`). Updates overwrite previous value.
- **Optionals → DELETE:** When `null` is sent, field is removed.
- **Special case - feature_flags → MERGE:** Flag evaluations are additive; new flags merge with existing.

### Update Ordering

- Both `view` and `view_update` carry `_dd.document_version`
- Backend applies updates in document_version order
- If events arrive out-of-order, backend buffers until correct order established
- For conflicts, highest document_version wins per field

## Testing Strategy

### Unit Tests (Primary Focus)

**Diff Computation Tests:**
- ✓ Primitives: Detect changed string, int, boolean, double
- ✓ Primitives: Ignore unchanged values
- ✓ Objects: Detect nested field changes
- ✓ Objects: Preserve unchanged nested fields
- ✓ Arrays: Detect new elements only
- ✓ Arrays: Handle empty arrays
- ✓ Arrays: Handle unchanged arrays
- ✓ Deleted fields: Include null in diff
- ✓ Empty diff: Return empty map when nothing changed

**Event Building Tests:**
- ✓ First event: Always send full `view` with `document_version = 1`
- ✓ Subsequent events: Send `view_update` with only changes
- ✓ Required fields: `view_update` includes `application.id`, `session.id`, `view.id`, `_dd.document_version`
- ✓ Document version: Increments correctly per view.id

**Configuration Tests:**
- ✓ Feature disabled: Always sends full `view` events
- ✓ Feature enabled: Sends `view` then `view_update`
- ✓ Feature immutable: Cannot change after initialization

**Memory Management Tests:**
- ✓ Last sent event stored after first event
- ✓ Last sent event updated after each update
- ✓ Last sent event cleared when view ends

### Integration Tests (Secondary)

- ✓ End-to-end: Create view, update properties, verify events sent
- ✓ Multiple views: Track document_version per view.id independently
- ✓ View lifecycle: First event full, updates partial, cleanup on end

### Performance Tests (Optional)

- ⚠ Diff computation overhead < 5ms for typical view (100-150 fields)
- ⚠ Memory footprint: ~2KB per active view

## Acceptance Criteria

- [x] Configuration flag `enablePartialViewUpdates` controls feature (default: false)
- [x] First event for each view.id is always full `view` event
- [x] Subsequent events (when enabled) are `view_update` with only changed fields
- [x] Arrays include only new elements appended since last sent event
- [x] `_dd.document_version` increments per view.id starting at 1
- [x] Diff computation correctly identifies all changed fields (zero false negatives)
- [x] Diff computation excludes unchanged fields (zero false positives)
- [x] Empty diff results in no event sent
- [x] Feature disabled results in identical behavior to current SDK
- [x] Last sent event cleared when view ends (no memory leak)
- [x] Unit tests achieve >90% code coverage for new components
- [x] Documentation updated with configuration example

## Performance Expectations

*(From RFC Benchmarks - Qualitative Summary)*

Based on RFC benchmarks with Shopist Android app:

**Bandwidth savings:**
- Typical view: 100-150 fields in full event
- Typical update: 5-20 fields changed
- Expected redundancy reduction: 49-93% for disk writes, 43-46% for uploads
- Long-lived views: Up to 91-97% redundancy reduction over time

**I/O savings:**
- Each `view_update` event is significantly smaller than full `view`
- Fewer bytes written to disk per update
- Reduced battery and CPU consumption from serialization

**Trade-offs:**
- Slight CPU overhead for diff computation (~1-5ms per update)
- Minimal memory overhead (~2KB per active view)
- More events at ingestion (cannot merge multiple `view_update` events before sending)

## Migration Path

### For SDK Users

**Opt-in adoption:**
1. Update to SDK version X.Y.Z (contains this feature)
2. Enable feature in configuration:
   ```kotlin
   .setEnablePartialViewUpdates(true)
   ```
3. Deploy and monitor

**Future default:**
- After 1-2 quarters of validation, feature will become enabled by default
- Users can still opt-out if needed
- Timeline: TBD based on production feedback

### For SDK Developers

**Implementation phases:**
1. ✓ Schema changes (backend team - separate effort)
2. ✓ Implement diff computation logic
3. ✓ Implement ViewEventTracker changes
4. ✓ Add configuration flag
5. ✓ Write unit tests
6. ✓ Internal testing and validation
7. ✓ Documentation
8. ✓ Release as opt-in feature

## Draft Origin

**Source:** `./partial-updates-rfc.md`

**Sections preserved from draft:**
- Problem statement and motivation
- Solution overview and update rules
- Benchmark results (referenced qualitatively)
- Alternative solutions considered
- Pros & cons analysis

**Sections expanded:**
- SDK implementation architecture (not in RFC)
- API design and configuration (not in RFC)
- Diff computation algorithm (high-level in RFC, detailed here)
- Testing strategy (not in RFC)
- Data model and memory management (not in RFC)

**Sections added:**
- Android-specific implementation details
- Code examples and interfaces
- Acceptance criteria
- Migration path for SDK users

## Open Questions

1. **Backend implementation timeline?**
   - When will backend support for `view_update` event type be ready?
   - Can we start SDK development in parallel?

2. **Feature flag on backend?**
   - Should backend have a feature flag to disable update processing if issues arise?
   - Would provide instant revert capability independent of SDK versions

3. **When to flip to enabled-by-default?**
   - After how many production validations?
   - What metrics indicate it's safe to make default?
   - Timeline: Q2 2026? Q3 2026?

4. **Array shrinking behavior?**
   - Current spec assumes arrays only grow (append-only)
   - What should happen if array shrinks? (Shouldn't happen in RUM, but...)
   - Should SDK detect and handle this edge case?

5. **Event size limit implications?**
   - RFC mentions 256 KiB event size limit
   - Does `view_update` help avoid hitting this limit?
   - Should SDK enforce size checks on `view_update` events?

6. **Monitoring and observability?**
   - Should SDK emit telemetry about feature usage?
   - Track: redundancy reduction achieved, number of view_update vs view events, diff computation time?

## References

- [RFC - RUM Event Format Limitation](../../../partial-updates-rfc.md) - Original problem statement and solution design
- [RUM Events Format Schema](https://github.com/DataDog/rum-events-format) - View event schema definition
- [View Event Structure Spreadsheet](https://docs.google.com/spreadsheets/d/1UhGCUW-hzW4CE2m9QyBlVkm-PI95iRnFwKel-FWRXPw/edit?usp=sharing) - 224 field breakdown