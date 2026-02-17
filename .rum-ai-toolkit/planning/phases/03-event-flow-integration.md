# Phase 3: Event Flow Integration

**Status:** Not Started
**Estimated Effort:** 3-4 days
**Dependencies:** Phase 1 & Phase 2 (requires ViewEventTracker skeleton and ViewDiffComputer)

## Objective

Integrate the diff computation logic with the event sending pipeline. Implement the complete ViewEventTracker to send full `view` events for the first update and partial `view_update` events for subsequent changes. This phase brings all components together to deliver the functional partial updates feature.

## Scope

### In Scope

- Complete ViewEventTracker implementation
- First event: build and send full `view` event
- Subsequent events: compute diff and send `view_update` event
- Document version management (increment per event)
- Minimal event building for `view_update` (required fields only)
- Integration with existing RUM event pipeline
- Memory cleanup on view lifecycle events
- Unit tests for event flow logic
- Integration tests for end-to-end scenarios

### Out of Scope (Deferred)

- Comprehensive end-to-end testing (Phase 4)
- User documentation (Phase 4)
- Performance benchmarking infrastructure (Phase 4)
- Backend changes (backend team)

## Requirements Addressed

From the spec, this phase implements:
- **FR-1:** First view event is complete - Send full `view` event with all properties
- **FR-2:** Subsequent updates are partial - Send `view_update` with only changed fields
- **FR-4:** Document version tracking - Per-view counter starting at 1, incrementing
- **FR-6:** Empty diff handling - Skip sending if nothing changed
- **NFR-2:** Memory footprint - Store only one copy per active view
- Partial **NFR-1:** Performance overhead - Actual overhead measurement in Phase 4

## Implementation Approach

### 1. Complete ViewEventTracker

Implement the event sending logic:

```kotlin
internal class ViewEventTracker(
    private val config: RumConfiguration,
    private val eventWriter: EventWriter,
    private val diffComputer: ViewDiffComputer = ViewDiffComputer()
) {
    private val lastSentEvents = mutableMapOf<String, Map<String, Any?>>()
    private val documentVersions = mutableMapOf<String, Int>()

    /**
     * Sends a view event or view_update based on configuration and view state.
     *
     * - If feature disabled: always send full view event
     * - If first event for this view.id: send full view event
     * - If subsequent update and feature enabled: send view_update with only changes
     *
     * @param viewId The unique identifier for this view
     * @param currentViewData Complete current view state
     */
    fun sendViewUpdate(viewId: String, currentViewData: Map<String, Any?>) {
        if (!config.enablePartialViewUpdates) {
            // Feature disabled: use legacy behavior (full view events)
            sendFullViewEvent(viewId, currentViewData)
            return
        }

        val lastSent = lastSentEvents[viewId]
        if (lastSent == null) {
            // First event: send full view
            sendFullViewEvent(viewId, currentViewData)
            return
        }

        // Compute diff
        val changes = diffComputer.computeDiff(lastSent, currentViewData)

        if (changes.isEmpty()) {
            // No changes: skip sending event
            return
        }

        // Send view_update with only changed fields
        sendPartialViewUpdate(viewId, changes, currentViewData)
    }

    /**
     * Sends a full view event.
     * Used for first event or when feature is disabled.
     */
    private fun sendFullViewEvent(viewId: String, viewData: Map<String, Any?>) {
        val version = incrementDocumentVersion(viewId)

        val event = viewData.toMutableMap().apply {
            put("type", "view")
            put("_dd", mapOf("document_version" to version))
        }

        eventWriter.writeEvent(event)
        lastSentEvents[viewId] = viewData
    }

    /**
     * Sends a partial view_update event with only changed fields.
     */
    private fun sendPartialViewUpdate(
        viewId: String,
        changes: Map<String, Any?>,
        fullCurrentState: Map<String, Any?>
    ) {
        val version = incrementDocumentVersion(viewId)

        // Build minimal event with required fields
        val event = buildMinimalViewUpdate(viewId).apply {
            // Add all changed fields
            putAll(changes)
            // Override type and version
            put("type", "view_update")
            put("_dd", mapOf("document_version" to version))
        }

        eventWriter.writeEvent(event)
        lastSentEvents[viewId] = fullCurrentState
    }

    /**
     * Builds the minimal required fields for a view_update event.
     * Per spec: must include application.id, session.id, view.id
     */
    private fun buildMinimalViewUpdate(viewId: String): MutableMap<String, Any?> {
        return mutableMapOf(
            "application" to mapOf("id" to getApplicationId()),
            "session" to mapOf("id" to getCurrentSessionId()),
            "view" to mapOf("id" to viewId)
        )
    }

    /**
     * Increments and returns the document version for a view.
     */
    private fun incrementDocumentVersion(viewId: String): Int {
        val currentVersion = documentVersions.getOrDefault(viewId, 0)
        val newVersion = currentVersion + 1
        documentVersions[viewId] = newVersion
        return newVersion
    }

    /**
     * Cleanup when view ends.
     * Frees memory by removing stored state.
     */
    fun onViewEnded(viewId: String) {
        lastSentEvents.remove(viewId)
        documentVersions.remove(viewId)
    }

    /**
     * Cleanup when SDK shuts down.
     * Removes all stored state.
     */
    fun onSdkShutdown() {
        lastSentEvents.clear()
        documentVersions.clear()
    }

    // Helper methods to access application and session context
    private fun getApplicationId(): String {
        // Access from RumContext or equivalent
        return rumContext.applicationId
    }

    private fun getCurrentSessionId(): String {
        // Access from RumContext or equivalent
        return rumContext.sessionId
    }
}
```

### 2. Integration with RumMonitor

Hook ViewEventTracker into the existing view update flow:

```kotlin
internal class RumMonitor(
    private val config: RumConfiguration,
    private val eventWriter: EventWriter
) {
    private val viewEventTracker: ViewEventTracker by lazy {
        ViewEventTracker(config, eventWriter)
    }

    /**
     * Called when view properties change (resource loaded, time elapsed, etc.)
     */
    fun updateView(viewId: String) {
        // Build current view state from ViewScope or equivalent
        val currentViewData = buildCurrentViewState(viewId)

        // Send update (either full view or view_update based on configuration)
        viewEventTracker.sendViewUpdate(viewId, currentViewData)
    }

    /**
     * Called when view ends
     */
    fun stopView(viewId: String) {
        // Send final update
        val currentViewData = buildCurrentViewState(viewId)
        viewEventTracker.sendViewUpdate(viewId, currentViewData)

        // Cleanup
        viewEventTracker.onViewEnded(viewId)
    }

    private fun buildCurrentViewState(viewId: String): Map<String, Any?> {
        // Build complete view state from ViewScope
        // This is existing SDK logic, just extract it into a map
        return mapOf(
            "application" to mapOf("id" to applicationId),
            "session" to mapOf("id" to sessionId),
            "view" to mapOf(
                "id" to viewId,
                "url" to viewScope.url,
                "time_spent" to viewScope.timeSpent,
                "action" to mapOf("count" to viewScope.actionCount),
                "resource" to mapOf("count" to viewScope.resourceCount),
                "error" to mapOf("count" to viewScope.errorCount)
                // ... all other view fields
            ),
            "context" to viewScope.customContext,
            "feature_flags" to viewScope.featureFlags
            // ... etc
        )
    }
}
```

### 3. Event Type Support

Ensure eventWriter supports both event types:

```kotlin
internal interface EventWriter {
    /**
     * Writes an event to storage/upload queue.
     *
     * @param event Event data including "type" field ("view" or "view_update")
     */
    fun writeEvent(event: Map<String, Any?>)
}
```

If existing implementation only supports specific event types, extend it to handle `view_update`:

```kotlin
internal class RumEventWriter : EventWriter {
    override fun writeEvent(event: Map<String, Any?>) {
        val eventType = event["type"] as? String ?: "unknown"

        when (eventType) {
            "view", "view_update" -> {
                // Serialize and write
                val jsonString = serializeToJson(event)
                writeToStorage(jsonString)
            }
            // ... other event types
        }
    }
}
```

### 4. Document Version in Existing View Events

Ensure existing full `view` events include `_dd.document_version`:

```kotlin
// Existing view event building logic should already include document_version
// If not, add it during the legacy path in ViewEventTracker
```

## Key Components

- **ViewEventTracker (complete):** Full implementation with event sending
- **RumMonitor integration:** Hook view updates into tracker
- **EventWriter support:** Handle `view_update` event type
- **Context accessors:** Get application and session IDs for minimal event

## Acceptance Criteria

- [ ] ViewEventTracker sends full `view` event for first update
- [ ] ViewEventTracker sends `view_update` for subsequent updates (when feature enabled)
- [ ] `view_update` includes only changed fields from diff computation
- [ ] `view_update` includes required fields: `application.id`, `session.id`, `view.id`, `_dd.document_version`
- [ ] Document version starts at 1 and increments correctly
- [ ] Empty diff results in no event sent
- [ ] Feature disabled results in full `view` events (legacy behavior)
- [ ] Last sent event updated after each send
- [ ] Memory cleanup on view end
- [ ] Memory cleanup on SDK shutdown
- [ ] Integration with RumMonitor works correctly
- [ ] Both `view` and `view_update` events written successfully
- [ ] Unit tests for event flow logic
- [ ] Integration tests for complete scenarios
- [ ] Code review completed

## Testing Strategy

### Unit Tests

**Event Flow Tests:**
```kotlin
@Test
fun `first update sends full view event`() {
    val tracker = ViewEventTracker(enabledConfig, mockWriter)
    val viewData = mapOf("view" to mapOf("id" to "v1", "url" to "https://example.com"))

    tracker.sendViewUpdate("v1", viewData)

    verify(mockWriter).writeEvent(argThat { event ->
        event["type"] == "view" &&
        event["_dd"] == mapOf("document_version" to 1)
    })
}

@Test
fun `subsequent update sends view_update event`() {
    val tracker = ViewEventTracker(enabledConfig, mockWriter)

    // First event
    tracker.sendViewUpdate("v1", mapOf("time_spent" to 100))

    // Second event
    tracker.sendViewUpdate("v1", mapOf("time_spent" to 200))

    verify(mockWriter, times(2)).writeEvent(any())

    // Verify second event is view_update
    val secondEvent = captureLastEvent(mockWriter)
    assertEquals("view_update", secondEvent["type"])
    assertEquals(2, (secondEvent["_dd"] as Map<*, *>)["document_version"])
}

@Test
fun `view_update includes only changed fields`() {
    val tracker = ViewEventTracker(enabledConfig, mockWriter)

    tracker.sendViewUpdate("v1", mapOf(
        "time_spent" to 100,
        "action_count" to 0,
        "url" to "https://example.com"
    ))

    tracker.sendViewUpdate("v1", mapOf(
        "time_spent" to 200,
        "action_count" to 0,
        "url" to "https://example.com"
    ))

    val secondEvent = captureLastEvent(mockWriter)

    // Only time_spent should be in the update (plus required fields)
    assertTrue(secondEvent.contains("time_spent"))
    assertEquals(200, secondEvent["time_spent"])

    // Unchanged fields should not be present (except required fields)
    assertFalse(secondEvent.contains("action_count"))
}

@Test
fun `empty diff skips sending event`() {
    val tracker = ViewEventTracker(enabledConfig, mockWriter)

    tracker.sendViewUpdate("v1", mapOf("time_spent" to 100))
    tracker.sendViewUpdate("v1", mapOf("time_spent" to 100)) // No change

    // Only one event should be sent
    verify(mockWriter, times(1)).writeEvent(any())
}

@Test
fun `feature disabled sends full view events`() {
    val tracker = ViewEventTracker(disabledConfig, mockWriter)

    tracker.sendViewUpdate("v1", mapOf("time_spent" to 100))
    tracker.sendViewUpdate("v1", mapOf("time_spent" to 200))

    // Both should be "view" events
    verify(mockWriter, times(2)).writeEvent(argThat { it["type"] == "view" })
}

@Test
fun `document version increments correctly`() {
    val tracker = ViewEventTracker(enabledConfig, mockWriter)

    tracker.sendViewUpdate("v1", mapOf("time_spent" to 100))
    tracker.sendViewUpdate("v1", mapOf("time_spent" to 200))
    tracker.sendViewUpdate("v1", mapOf("time_spent" to 300))

    val events = captureAllEvents(mockWriter)
    assertEquals(1, getDocVersion(events[0]))
    assertEquals(2, getDocVersion(events[1]))
    assertEquals(3, getDocVersion(events[2]))
}

@Test
fun `document version is per view id`() {
    val tracker = ViewEventTracker(enabledConfig, mockWriter)

    tracker.sendViewUpdate("v1", mapOf("time_spent" to 100))
    tracker.sendViewUpdate("v2", mapOf("time_spent" to 50))
    tracker.sendViewUpdate("v1", mapOf("time_spent" to 200))
    tracker.sendViewUpdate("v2", mapOf("time_spent" to 100))

    val events = captureAllEvents(mockWriter)

    // v1 events should have versions 1, 2
    assertEquals(1, getDocVersion(events[0]))
    assertEquals(2, getDocVersion(events[2]))

    // v2 events should have versions 1, 2
    assertEquals(1, getDocVersion(events[1]))
    assertEquals(2, getDocVersion(events[3]))
}

@Test
fun `onViewEnded clears stored state`() {
    val tracker = ViewEventTracker(enabledConfig, mockWriter)

    tracker.sendViewUpdate("v1", mapOf("time_spent" to 100))
    tracker.onViewEnded("v1")

    // Next update for v1 should be first event again
    tracker.sendViewUpdate("v1", mapOf("time_spent" to 200))

    val event = captureLastEvent(mockWriter)
    assertEquals("view", event["type"])
    assertEquals(1, getDocVersion(event))
}
```

### Integration Tests

**End-to-End Scenarios:**
```kotlin
@Test
fun `complete view lifecycle with partial updates enabled`() {
    val sdk = initializeSDK(partialUpdatesEnabled = true)

    // Start view
    sdk.startView("screen1", "https://example.com/screen1")

    // Verify first event is full view
    val event1 = getLastWrittenEvent()
    assertEquals("view", event1.type)
    assertEquals(1, event1.documentVersion)

    // Load resource
    sdk.addResource("https://api.example.com/data")

    // Verify view_update sent
    val event2 = getLastWrittenEvent()
    assertEquals("view_update", event2.type)
    assertEquals(2, event2.documentVersion)
    assertTrue(event2.hasField("view.resource.count"))

    // User action
    sdk.addAction("button_click")

    // Verify another view_update
    val event3 = getLastWrittenEvent()
    assertEquals("view_update", event3.type)
    assertEquals(3, event3.documentVersion)
    assertTrue(event3.hasField("view.action.count"))

    // Stop view
    sdk.stopView("screen1")

    // Verify final update sent
    val event4 = getLastWrittenEvent()
    assertEquals("view_update", event4.type)
}

@Test
fun `multiple concurrent views tracked independently`() {
    val sdk = initializeSDK(partialUpdatesEnabled = true)

    sdk.startView("screen1", "https://example.com/screen1")
    sdk.startView("screen2", "https://example.com/screen2")

    // Update screen1
    sdk.addActionToView("screen1", "click")

    // Update screen2
    sdk.addActionToView("screen2", "scroll")

    // Each view should have independent document_version
    val screen1Events = getEventsForView("screen1")
    val screen2Events = getEventsForView("screen2")

    assertEquals(listOf(1, 2), screen1Events.map { it.documentVersion })
    assertEquals(listOf(1, 2), screen2Events.map { it.documentVersion })
}
```

## Open Questions

1. **View state building:**
   - Where does `buildCurrentViewState()` logic live in existing SDK?
   - Is there already a method that serializes ViewScope to a map?
   - Or do we need to create this serialization?

2. **EventWriter interface:**
   - Does a generic EventWriter interface exist?
   - Or is event writing tightly coupled to event types?
   - Do we need to refactor existing event writing?

3. **Required fields for view_update:**
   - Spec says: `application.id`, `session.id`, `view.id`, `_dd.document_version`
   - Are there other fields that should always be included?
   - What about `date` timestamp?

4. **Thread safety:**
   - Are view updates called from multiple threads?
   - Do we need to synchronize access to lastSentEvents and documentVersions?
   - Should we use ConcurrentHashMap?

5. **Error handling:**
   - What if diff computation throws an exception?
   - Should we fall back to sending full view event?
   - How to handle serialization errors?

## Dependencies

**Phase 1 & 2 Complete:**
- ViewEventTracker skeleton with data structures
- ViewDiffComputer with working diff logic
- Configuration flag available

**External:**
- Existing RumMonitor or view tracking component
- Existing EventWriter or event persistence layer
- Existing ViewScope or view state representation

## Deliverables

- [ ] Complete ViewEventTracker implementation
- [ ] Integration with RumMonitor (or equivalent)
- [ ] EventWriter support for `view_update` type
- [ ] Unit tests for event flow (>90% coverage)
- [ ] Integration tests for end-to-end scenarios
- [ ] Code documentation (KDoc comments)
- [ ] Phase 3 code review completed

## Next Phase

**Phase 4: Testing & Documentation** will add comprehensive end-to-end tests, edge case validation, performance benchmarks, and user-facing documentation.
