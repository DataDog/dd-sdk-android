# Implementation Plan: Phase 3 - Event Flow Integration

**Phase Document:** [03-event-flow-integration.md](03-event-flow-integration.md)
**Status:** Not Started
**Generated:** 2026-02-17

## Overview

This plan completes the ViewEventTracker implementation by replacing TODO stubs with actual logic, integrating ViewDiffComputer, and establishing hooks for RumViewScope integration. Given the complexity of full RumViewScope integration (1700+ lines), this phase focuses on making ViewEventTracker feature-complete and testable, with integration hooks prepared for future work.

**Key Principle:** Complete ViewEventTracker functionality with comprehensive tests. Provide clear integration points for RumViewScope without requiring massive refactoring in this phase.

## Prerequisites

- [x] Phase 1 completed (ViewEventTracker skeleton)
- [x] Phase 2 completed (ViewDiffComputer)
- [ ] Familiar with RUM SDK event pipeline
- [ ] Read Phase 3 document for context

## Implementation Tasks

### Task 1: Update ViewEventTracker Constructor

**Objective:** Add ViewDiffComputer and DataWriter dependencies to ViewEventTracker

**Files to modify:**
- `features/dd-sdk-android-rum/src/main/kotlin/com/datadog/android/rum/internal/domain/event/viewupdate/ViewEventTracker.kt`

**Implementation steps:**

1. Update constructor to accept DataWriter<Any> and ViewDiffComputer
2. Store as private fields
3. Update existing tests to provide mock dependencies

**Code pattern:**
```kotlin
package com.datadog.android.rum.internal.domain.event.viewupdate

import com.datadog.android.api.storage.DataWriter
import com.datadog.android.rum.RumConfiguration

/**
 * Manages view event tracking and implements partial view update logic.
 *
 * This class is responsible for:
 * - Storing the last sent event data per view
 * - Tracking document version counters per view
 * - Determining when to send full view vs partial view_update events
 * - Computing diffs and building minimal view_update events
 *
 * @param config The RUM configuration containing feature flags
 * @param writer The data writer for persisting events
 * @param diffComputer The diff computation engine (defaults to new instance)
 */
internal class ViewEventTracker(
    private val config: RumConfiguration,
    private val writer: DataWriter<Any>,
    private val diffComputer: ViewDiffComputer = ViewDiffComputer()
) {
    // ... existing code ...
}
```

**Acceptance:**
- [ ] Constructor updated with new parameters
- [ ] DataWriter and ViewDiffComputer stored as fields
- [ ] Existing tests still compile (with mock dependencies)

---

### Task 2: Implement sendViewUpdate Method

**Objective:** Replace TODO stub with actual implementation logic

**Files to modify:**
- `features/dd-sdk-android-rum/src/main/kotlin/com/datadog/android/rum/internal/domain/event/viewupdate/ViewEventTracker.kt`

**Implementation steps:**

1. Remove TODO from `sendViewUpdate()`
2. Implement decision logic:
   - If feature disabled → call `sendFullViewEvent()`
   - If first event → call `sendFullViewEvent()`
   - If subsequent + enabled → compute diff and call `sendPartialViewUpdate()`
3. Handle empty diff (skip sending)

**Code pattern:**
```kotlin
/**
 * Sends a view event or view_update based on configuration and view state.
 *
 * Decision logic:
 * - If feature disabled → send full view event
 * - If first event for this view.id → send full view event
 * - If subsequent update and feature enabled → send view_update with changes
 *
 * @param viewId The unique identifier for this view
 * @param currentViewData Complete current view state as Map
 */
fun sendViewUpdate(viewId: String, currentViewData: Map<String, Any?>) {
    if (!isPartialUpdatesEnabled()) {
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
```

**Acceptance:**
- [ ] sendViewUpdate() implemented (no TODO)
- [ ] Feature flag checked
- [ ] First event detection works
- [ ] Diff computation called
- [ ] Empty diff handled (no event sent)

---

### Task 3: Implement sendFullViewEvent Method

**Objective:** Implement full view event sending logic

**Files to modify:**
- Same as Task 2

**Implementation steps:**

1. Remove TODO from `sendFullViewEvent()`
2. Increment document version
3. Add type="view" and _dd.document_version to event
4. Write event via DataWriter
5. Store in lastSentEvents

**Code pattern:**
```kotlin
/**
 * Sends a full view event.
 * Used for first event or when feature is disabled.
 *
 * @param viewId The unique identifier for this view
 * @param viewData Complete view state to send
 */
private fun sendFullViewEvent(viewId: String, viewData: Map<String, Any?>) {
    val version = incrementDocumentVersion(viewId)

    // Build event with type and version
    val event = viewData.toMutableMap().apply {
        put("type", "view")
        // Merge or add _dd section
        val existingDd = this["_dd"] as? Map<*, *>
        val updatedDd = (existingDd?.toMutableMap() ?: mutableMapOf<String, Any?>()).apply {
            put("document_version", version)
        }
        put("_dd", updatedDd)
    }

    // Write to storage
    writer.write(event)

    // Store for next diff
    lastSentEvents[viewId] = viewData
}

/**
 * Increments and returns the document version for a view.
 * Version starts at 1 for first event.
 *
 * @param viewId The view identifier
 * @return The new document version
 */
private fun incrementDocumentVersion(viewId: String): Int {
    val currentVersion = documentVersions.getOrDefault(viewId, 0)
    val newVersion = currentVersion + 1
    documentVersions[viewId] = newVersion
    return newVersion
}
```

**Acceptance:**
- [ ] sendFullViewEvent() implemented (no TODO)
- [ ] Document version incremented correctly
- [ ] type="view" added to event
- [ ] _dd.document_version added
- [ ] Event written via DataWriter
- [ ] Last sent event stored

---

### Task 4: Implement sendPartialViewUpdate Method

**Objective:** Implement partial view_update event sending logic

**Files to modify:**
- Same as Task 2

**Implementation steps:**

1. Remove TODO from `sendPartialViewUpdate()`
2. Increment document version
3. Build minimal event with required fields
4. Add changed fields from diff
5. Add type="view_update" and _dd.document_version
6. Write event
7. Store full current state

**Code pattern:**
```kotlin
/**
 * Sends a partial view_update event with only changed fields.
 *
 * The event includes:
 * - Required fields: application.id, session.id, view.id
 * - Changed fields from diff
 * - type="view_update"
 * - _dd.document_version
 *
 * @param viewId The unique identifier for this view
 * @param changes Map of changed fields from diff computation
 * @param fullCurrentState Complete current state (stored for next diff)
 */
private fun sendPartialViewUpdate(
    viewId: String,
    changes: Map<String, Any?>,
    fullCurrentState: Map<String, Any?>
) {
    val version = incrementDocumentVersion(viewId)

    // Build event starting with changed fields
    val event = changes.toMutableMap().apply {
        // Ensure required fields are present (if not in changes)
        if (!containsKey("application")) {
            put("application", fullCurrentState["application"])
        }
        if (!containsKey("session")) {
            put("session", fullCurrentState["session"])
        }
        if (!containsKey("view") || (this["view"] as? Map<*, *>)?.containsKey("id") != true) {
            // Merge view.id if needed
            val currentView = this["view"] as? MutableMap<String, Any?> ?: mutableMapOf()
            val fullView = fullCurrentState["view"] as? Map<*, *>
            if (fullView != null && !currentView.containsKey("id")) {
                currentView["id"] = fullView["id"]
            }
            put("view", currentView)
        }

        // Set event type
        put("type", "view_update")

        // Merge or add _dd section with document_version
        val existingDd = this["_dd"] as? Map<*, *>
        val updatedDd = (existingDd?.toMutableMap() ?: mutableMapOf<String, Any?>()).apply {
            put("document_version", version)
        }
        put("_dd", updatedDd)
    }

    // Write to storage
    writer.write(event)

    // Store full current state for next diff
    lastSentEvents[viewId] = fullCurrentState
}
```

**Acceptance:**
- [ ] sendPartialViewUpdate() implemented (no TODO)
- [ ] Document version incremented
- [ ] Required fields included (application.id, session.id, view.id)
- [ ] Changed fields from diff included
- [ ] type="view_update" set
- [ ] _dd.document_version set
- [ ] Event written
- [ ] Full current state stored

---

### Task 5: Update Existing Helper Methods

**Objective:** Ensure helper methods work with completed implementation

**Files to modify:**
- Same as Task 2

**Implementation steps:**

1. Verify `isPartialUpdatesEnabled()` accesses config correctly
2. Verify `isFirstEvent()` checks lastSentEvents
3. Update test helper methods if needed

**Code pattern:**
```kotlin
/**
 * Returns true if partial view updates feature is enabled in configuration.
 */
private fun isPartialUpdatesEnabled(): Boolean {
    return config.featureConfiguration.enablePartialViewUpdates
}

/**
 * Returns true if this is the first event for the given view.
 * A view is considered "first" if no last sent event exists for it.
 */
internal fun isFirstEvent(viewId: String): Boolean {
    return viewId !in lastSentEvents
}
```

**Acceptance:**
- [ ] isPartialUpdatesEnabled() works correctly
- [ ] isFirstEvent() works correctly
- [ ] Test helpers work with new implementation

---

## Testing Tasks

### Test 1: Update ViewEventTrackerTest

**Type:** Unit Test
**Files:** `features/dd-sdk-android-rum/src/test/kotlin/com/datadog/android/rum/internal/domain/event/viewupdate/ViewEventTrackerTest.kt`
**Covers:** Updated constructor and mock dependencies

**Implementation:**

Update existing test setup to provide mock DataWriter:

```kotlin
@BeforeEach
fun setUp() {
    // Mock DataWriter
    mockWriter = mock()
    whenever(mockWriter.write(any())).thenReturn(true)

    // Create config with feature enabled/disabled
    fakeConfigEnabled = createRumConfig(enablePartialViewUpdates = true)
    fakeConfigDisabled = createRumConfig(enablePartialViewUpdates = false)

    testedTracker = ViewEventTracker(
        config = fakeConfigEnabled,
        writer = mockWriter
    )
}

private fun createRumConfig(enablePartialViewUpdates: Boolean): RumConfiguration {
    return RumConfiguration(
        applicationId = "test-app-id",
        featureConfiguration = RumFeature.Configuration(
            // ... all required fields ...
            enablePartialViewUpdates = enablePartialViewUpdates
        )
    )
}
```

**Test cases:**
- [ ] Constructor accepts DataWriter parameter
- [ ] Mock writer is used in tests
- [ ] Config with feature enabled/disabled can be created

---

### Test 2: Event Sending Logic Tests

**Type:** Unit Test
**Files:** Same as Test 1
**Covers:** Core event sending logic

**Implementation:**

```kotlin
@Test
fun `M send full view event W sendViewUpdate() first time`() {
    // Given
    val viewId = "view-123"
    val viewData = mapOf(
        "application" to mapOf("id" to "app-123"),
        "session" to mapOf("id" to "session-456"),
        "view" to mapOf("id" to viewId, "url" to "https://example.com"),
        "time_spent" to 100
    )

    // When
    testedTracker.sendViewUpdate(viewId, viewData)

    // Then
    argumentCaptor<Any>().apply {
        verify(mockWriter).write(capture())
        val event = firstValue as Map<*, *>
        assertThat(event["type"]).isEqualTo("view")
        assertThat((event["_dd"] as Map<*, *>)["document_version"]).isEqualTo(1)
        assertThat(event["time_spent"]).isEqualTo(100)
    }
}

@Test
fun `M send view_update event W sendViewUpdate() subsequent time`() {
    // Given
    val viewId = "view-123"
    val initialData = mapOf(
        "application" to mapOf("id" to "app-123"),
        "session" to mapOf("id" to "session-456"),
        "view" to mapOf("id" to viewId, "url" to "https://example.com"),
        "time_spent" to 100
    )
    val updatedData = initialData.toMutableMap().apply {
        put("time_spent", 200)
    }

    testedTracker.sendViewUpdate(viewId, initialData)
    reset(mockWriter)

    // When
    testedTracker.sendViewUpdate(viewId, updatedData)

    // Then
    argumentCaptor<Any>().apply {
        verify(mockWriter).write(capture())
        val event = firstValue as Map<*, *>
        assertThat(event["type"]).isEqualTo("view_update")
        assertThat((event["_dd"] as Map<*, *>)["document_version"]).isEqualTo(2)
        assertThat(event["time_spent"]).isEqualTo(200)
        // Other unchanged fields should not be present
        assertThat(event.containsKey("url")).isFalse()
    }
}

@Test
fun `M skip sending W sendViewUpdate() no changes`() {
    // Given
    val viewId = "view-123"
    val viewData = mapOf(
        "application" to mapOf("id" to "app-123"),
        "session" to mapOf("id" to "session-456"),
        "view" to mapOf("id" to viewId),
        "time_spent" to 100
    )

    testedTracker.sendViewUpdate(viewId, viewData)
    reset(mockWriter)

    // When
    testedTracker.sendViewUpdate(viewId, viewData) // Same data

    // Then
    verify(mockWriter, never()).write(any())
}

@Test
fun `M send full view W sendViewUpdate() feature disabled`() {
    // Given
    val trackerDisabled = ViewEventTracker(
        config = fakeConfigDisabled,
        writer = mockWriter
    )
    val viewId = "view-123"
    val viewData1 = mapOf("time_spent" to 100)
    val viewData2 = mapOf("time_spent" to 200)

    // When
    trackerDisabled.sendViewUpdate(viewId, viewData1)
    trackerDisabled.sendViewUpdate(viewId, viewData2)

    // Then
    argumentCaptor<Any>().apply {
        verify(mockWriter, times(2)).write(capture())
        val events = allValues.map { it as Map<*, *> }
        assertThat(events[0]["type"]).isEqualTo("view")
        assertThat(events[1]["type"]).isEqualTo("view") // Still full view, not update
    }
}
```

**Test cases:**
- [ ] First event sends full view with document_version=1
- [ ] Subsequent event sends view_update with only changes
- [ ] Empty diff skips sending
- [ ] Feature disabled always sends full view

---

### Test 3: Document Version Management

**Type:** Unit Test
**Files:** Same as Test 1
**Covers:** Document version incrementing

**Implementation:**

```kotlin
@Test
fun `M increment document version W sendViewUpdate() multiple times`() {
    // Given
    val viewId = "view-123"
    val viewData = mapOf("time_spent" to 0)

    // When
    testedTracker.sendViewUpdate(viewId, mapOf("time_spent" to 100))
    testedTracker.sendViewUpdate(viewId, mapOf("time_spent" to 200))
    testedTracker.sendViewUpdate(viewId, mapOf("time_spent" to 300))

    // Then
    argumentCaptor<Any>().apply {
        verify(mockWriter, times(3)).write(capture())
        val events = allValues.map { it as Map<*, *> }
        assertThat((events[0]["_dd"] as Map<*, *>)["document_version"]).isEqualTo(1)
        assertThat((events[1]["_dd"] as Map<*, *>)["document_version"]).isEqualTo(2)
        assertThat((events[2]["_dd"] as Map<*, *>)["document_version"]).isEqualTo(3)
    }
}

@Test
fun `M use separate counters W sendViewUpdate() different views`() {
    // Given
    val viewId1 = "view-1"
    val viewId2 = "view-2"

    // When
    testedTracker.sendViewUpdate(viewId1, mapOf("time_spent" to 100))
    testedTracker.sendViewUpdate(viewId2, mapOf("time_spent" to 50))
    testedTracker.sendViewUpdate(viewId1, mapOf("time_spent" to 200))
    testedTracker.sendViewUpdate(viewId2, mapOf("time_spent" to 100))

    // Then
    argumentCaptor<Any>().apply {
        verify(mockWriter, times(4)).write(capture())
        val events = allValues.map { it as Map<*, *> }

        // View 1 events
        assertThat((events[0]["_dd"] as Map<*, *>)["document_version"]).isEqualTo(1)
        assertThat((events[2]["_dd"] as Map<*, *>)["document_version"]).isEqualTo(2)

        // View 2 events
        assertThat((events[1]["_dd"] as Map<*, *>)["document_version"]).isEqualTo(1)
        assertThat((events[3]["_dd"] as Map<*, *>)["document_version"]).isEqualTo(2)
    }
}
```

**Test cases:**
- [ ] Document version increments per event
- [ ] Different views have independent counters
- [ ] Counter starts at 1

---

### Test 4: Required Fields in view_update

**Type:** Unit Test
**Files:** Same as Test 1
**Covers:** view_update contains required fields

**Implementation:**

```kotlin
@Test
fun `M include required fields W sendViewUpdate() sends view_update`() {
    // Given
    val viewId = "view-123"
    val initialData = mapOf(
        "application" to mapOf("id" to "app-123"),
        "session" to mapOf("id" to "session-456"),
        "view" to mapOf("id" to viewId, "url" to "https://example.com"),
        "time_spent" to 100
    )
    val updatedData = initialData.toMutableMap().apply {
        put("time_spent", 200)
    }

    testedTracker.sendViewUpdate(viewId, initialData)
    reset(mockWriter)

    // When
    testedTracker.sendViewUpdate(viewId, updatedData)

    // Then
    argumentCaptor<Any>().apply {
        verify(mockWriter).write(capture())
        val event = firstValue as Map<*, *>

        // Required fields must be present
        assertThat(event["application"]).isNotNull()
        assertThat((event["application"] as Map<*, *>)["id"]).isEqualTo("app-123")
        assertThat(event["session"]).isNotNull()
        assertThat((event["session"] as Map<*, *>)["id"]).isEqualTo("session-456")
        assertThat(event["view"]).isNotNull()
        assertThat((event["view"] as Map<*, *>)["id"]).isEqualTo(viewId)
        assertThat(event["_dd"]).isNotNull()
        assertThat((event["_dd"] as Map<*, *>)["document_version"]).isNotNull()
    }
}
```

**Test cases:**
- [ ] view_update includes application.id
- [ ] view_update includes session.id
- [ ] view_update includes view.id
- [ ] view_update includes _dd.document_version

---

### Test 5: Memory Management

**Type:** Unit Test
**Files:** Same as Test 1
**Covers:** Cleanup and memory management

**Implementation:**

```kotlin
@Test
fun `M clear state W onViewEnded() called`() {
    // Given
    val viewId = "view-123"
    testedTracker.sendViewUpdate(viewId, mapOf("time_spent" to 100))

    // When
    testedTracker.onViewEnded(viewId)

    // Then
    assertThat(testedTracker.isFirstEvent(viewId)).isTrue()
    assertThat(testedTracker.getDocumentVersion(viewId)).isNull()
}

@Test
fun `M clear all state W onSdkShutdown() called`() {
    // Given
    testedTracker.sendViewUpdate("view-1", mapOf("time_spent" to 100))
    testedTracker.sendViewUpdate("view-2", mapOf("time_spent" to 50))

    // When
    testedTracker.onSdkShutdown()

    // Then
    assertThat(testedTracker.isFirstEvent("view-1")).isTrue()
    assertThat(testedTracker.isFirstEvent("view-2")).isTrue()
    assertThat(testedTracker.getDocumentVersion("view-1")).isNull()
    assertThat(testedTracker.getDocumentVersion("view-2")).isNull()
}
```

**Test cases:**
- [ ] onViewEnded() clears state for that view
- [ ] onSdkShutdown() clears all state
- [ ] Memory not leaked after cleanup

---

## Task Checklist

- [ ] Task 1: Update ViewEventTracker constructor
- [ ] Task 2: Implement sendViewUpdate method
- [ ] Task 3: Implement sendFullViewEvent method
- [ ] Task 4: Implement sendPartialViewUpdate method
- [ ] Task 5: Update helper methods
- [ ] Test 1: Update existing tests for new constructor
- [ ] Test 2: Event sending logic tests
- [ ] Test 3: Document version management tests
- [ ] Test 4: Required fields tests
- [ ] Test 5: Memory management tests
- [ ] All tests passing
- [ ] Code compiles without errors
- [ ] Code review completed

## Implementation Order

1. **Task 1** - Update constructor (30 min)
2. **Task 5** - Verify helper methods (15 min)
3. **Task 3** - Implement sendFullViewEvent (45 min)
4. **Task 4** - Implement sendPartialViewUpdate (1 hour)
5. **Task 2** - Implement sendViewUpdate (45 min)
6. **Test 1** - Update test setup (30 min)
7. **Test 2-5** - Implement all tests (3 hours)

**Estimated time:** 7-8 hours total

## Verification Steps

After completing all tasks:

1. **Build the SDK:**
   ```bash
   ./gradlew :features:dd-sdk-android-rum:assemble
   ```

2. **Run unit tests:**
   ```bash
   ./gradlew :features:dd-sdk-android-rum:testDebugUnitTest --tests "*ViewEventTrackerTest"
   ```

3. **Verify all TODOs removed:**
   ```bash
   grep -r "TODO.*Phase" features/dd-sdk-android-rum/src/main/kotlin/com/datadog/android/rum/internal/domain/event/viewupdate/
   ```
   Should return no results

4. **Verify test coverage:**
   - Target: >90% coverage for ViewEventTracker

## Notes

### DataWriter Interface

The SDK uses `DataWriter<Any>` interface from the core SDK:
- Events are written as `Any` type (usually model objects or Maps)
- DataWriter handles serialization and persistence
- For this phase, ViewEventTracker works with `Map<String, Any?>` directly

### RumViewScope Integration

**Deferred to future work:** Full integration with RumViewScope requires:
- Converting ViewEvent model to Map<String, Any?> or vice versa
- Modifying the 1700+ line RumViewScope class
- Extensive testing to ensure no regression

**Current approach:** ViewEventTracker is feature-complete and testable. Integration hooks are in place. Future work can add a thin adapter layer to connect RumViewScope to ViewEventTracker.

### Map vs Model Objects

ViewEventTracker uses `Map<String, Any?>` for flexibility:
- Easier to compute diffs (no reflection needed)
- Simpler to add/remove fields dynamically
- Can work with both ViewEvent models (after conversion) and raw maps

### Thread Safety

**Not addressed in this phase:** ViewEventTracker is not thread-safe. Defer to Phase 4 if needed:
- Add synchronization if called from multiple threads
- Or document that it must be called from single thread (RUM thread)

## Open Questions

1. **Should we add an adapter for ViewEvent → Map conversion?**
   - **Recommendation:** Yes, but defer to future work
   - Would enable easier RumViewScope integration
   - Not required for ViewEventTracker functionality

2. **How to test DataWriter integration?**
   - **Answer:** Use mock DataWriter in tests
   - Verify write() called with correct event data
   - Don't test actual persistence (that's DataWriter's responsibility)

3. **Should we add telemetry for partial updates?**
   - **Recommendation:** Defer to Phase 4
   - Track: feature usage, bandwidth saved, event types sent
   - Requires telemetry infrastructure setup

## Success Criteria

Phase 3 is complete when:

- [ ] All TODO stubs removed from ViewEventTracker
- [ ] sendViewUpdate() implemented with decision logic
- [ ] sendFullViewEvent() implemented
- [ ] sendPartialViewUpdate() implemented
- [ ] Document version management works correctly
- [ ] Required fields included in view_update events
- [ ] Unit tests pass with >90% coverage
- [ ] Feature flag controls behavior (enabled vs disabled)
- [ ] Empty diffs handled (no event sent)
- [ ] Memory cleanup works (onViewEnded, onSdkShutdown)
- [ ] Code compiles and builds successfully
- [ ] Code review approved
- [ ] All acceptance criteria met

## Next Phase

**Phase 4: Testing & Documentation** will add comprehensive end-to-end tests, performance validation, edge case testing, and user documentation. Will also consider RumViewScope integration strategy.
