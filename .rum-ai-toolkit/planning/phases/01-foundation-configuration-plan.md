# Implementation Plan: Phase 1 - Foundation & Configuration

**Phase Document:** [01-foundation-configuration.md](01-foundation-configuration.md)
**Status:** Not Started
**Generated:** 2026-02-17

## Overview

This plan implements the foundational infrastructure for partial view updates. We'll add a configuration flag to enable the feature, create data structures for tracking view state, and establish a skeleton `ViewEventTracker` class. All logic is deferred to later phases - this phase focuses purely on setup and structure.

**Key Principle:** This phase creates infrastructure without changing any existing behavior. The feature will be disabled by default and have no effect on current SDK operation.

## Prerequisites

- [ ] Development environment set up
- [ ] Able to build and run SDK tests
- [ ] Familiar with Kotlin data classes and builder pattern
- [ ] Read [SPEC.md](../SPEC.md) for context

## Implementation Tasks

### Task 1: Add Configuration Field to RumFeature.Configuration

**Objective:** Add `enablePartialViewUpdates` boolean field to the internal Configuration data class

**Files to modify:**
- `features/dd-sdk-android-rum/src/main/kotlin/com/datadog/android/rum/internal/RumFeature.kt`

**Implementation steps:**

1. Locate the `Configuration` data class (around line 731)
2. Add new field at the end of the data class before the closing parenthesis:
   ```kotlin
   val enablePartialViewUpdates: Boolean
   ```
3. Update `DEFAULT_RUM_CONFIG` (around line 784) to include the new field:
   ```kotlin
   enablePartialViewUpdates = false  // Disabled by default
   ```
4. Add a KDoc comment above the field explaining its purpose:
   ```kotlin
   /**
    * Enables partial view updates to reduce bandwidth and I/O.
    * When enabled, the SDK sends only changed fields in view update events.
    * Default: false (opt-in feature)
    */
   val enablePartialViewUpdates: Boolean
   ```

**Code pattern:**
```kotlin
// In Configuration data class (line ~731)
internal data class Configuration(
    // ... existing fields ...
    val insightsCollector: InsightsCollector,
    /**
     * Enables partial view updates to reduce bandwidth and I/O.
     * When enabled, the SDK sends only changed fields in view update events.
     * Default: false (opt-in feature)
     */
    val enablePartialViewUpdates: Boolean
)

// In DEFAULT_RUM_CONFIG (line ~784)
internal val DEFAULT_RUM_CONFIG = Configuration(
    // ... existing fields ...
    insightsCollector = NoOpInsightsCollector(),
    enablePartialViewUpdates = false
)
```

**Acceptance:**
- [ ] Configuration data class compiles with new field
- [ ] DEFAULT_RUM_CONFIG includes the field set to `false`
- [ ] No compilation errors in RumFeature.kt

---

### Task 2: Add Builder Method to RumConfiguration

**Objective:** Add public API method to RumConfiguration.Builder for enabling partial view updates

**Files to modify:**
- `features/dd-sdk-android-rum/src/main/kotlin/com/datadog/android/rum/RumConfiguration.kt`

**Implementation steps:**

1. Locate the `Builder` class (around line 51)
2. Add new builder method following the existing pattern (around line 377, before the `build()` method):

```kotlin
/**
 * Enables partial view updates to reduce bandwidth and I/O operations.
 *
 * When enabled, the SDK will send only changed fields in view update events
 * rather than resending the complete view state. This significantly reduces
 * bandwidth usage and disk I/O on mobile devices.
 *
 * This feature requires backend support for the `view_update` event type.
 *
 * Default: false (opt-in feature)
 *
 * @param enabled true to enable partial view updates, false to disable
 * @return this Builder instance
 */
fun setEnablePartialViewUpdates(enabled: Boolean): Builder {
    rumConfig = rumConfig.copy(enablePartialViewUpdates = enabled)
    return this
}
```

3. Verify the method follows the same pattern as other builder methods (uses `copy()`, returns `this`)

**Code pattern:**
```kotlin
// Around line 377, before trackAnonymousUser method
/**
 * Enables partial view updates to reduce bandwidth and I/O operations.
 *
 * When enabled, the SDK will send only changed fields in view update events
 * rather than resending the complete view state. This significantly reduces
 * bandwidth usage and disk I/O on mobile devices.
 *
 * This feature requires backend support for the `view_update` event type.
 *
 * Default: false (opt-in feature)
 *
 * @param enabled true to enable partial view updates, false to disable
 * @return this Builder instance
 */
fun setEnablePartialViewUpdates(enabled: Boolean): Builder {
    rumConfig = rumConfig.copy(enablePartialViewUpdates = enabled)
    return this
}

fun trackAnonymousUser(enabled: Boolean): Builder {
    // existing method
}
```

**Acceptance:**
- [ ] Builder method compiles and returns Builder instance
- [ ] Method follows existing pattern (uses `copy()`)
- [ ] KDoc comment is clear and complete
- [ ] No compilation errors in RumConfiguration.kt

---

### Task 3: Create ViewEventTracker Package and Class

**Objective:** Create new package and ViewEventTracker skeleton class with data structures

**Files to create:**
- `features/dd-sdk-android-rum/src/main/kotlin/com/datadog/android/rum/internal/domain/event/viewupdate/ViewEventTracker.kt`

**Implementation steps:**

1. Create new package directory:
   ```bash
   mkdir -p features/dd-sdk-android-rum/src/main/kotlin/com/datadog/android/rum/internal/domain/event/viewupdate
   ```

2. Create `ViewEventTracker.kt` with this content:

```kotlin
/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event.viewupdate

import com.datadog.android.rum.RumConfiguration

/**
 * Manages view event tracking and implements partial view update logic.
 *
 * This class is responsible for:
 * - Storing the last sent event data per view
 * - Tracking document version counters per view
 * - Determining when to send full view vs partial view_update events
 *
 * The implementation is split across phases:
 * - Phase 1: Data structures and skeleton (current)
 * - Phase 2: Integration with diff computation
 * - Phase 3: Complete event flow and integration
 *
 * @param config The RUM configuration containing feature flags
 */
internal class ViewEventTracker(
    private val config: RumConfiguration
) {

    /**
     * Stores the last sent event data for each active view.
     * Key: view.id
     * Value: Complete event data as sent (for diff computation)
     *
     * Memory is freed when view ends via [onViewEnded].
     */
    private val lastSentEvents: MutableMap<String, Map<String, Any?>> = mutableMapOf()

    /**
     * Tracks document version counter per view.
     * Key: view.id
     * Value: Last used document_version (starts at 0, increments before each send)
     *
     * Version counter is per-view and starts at 1 for the first event.
     */
    private val documentVersions: MutableMap<String, Int> = mutableMapOf()

    /**
     * Sends a view event or view_update based on configuration and view state.
     *
     * Decision logic:
     * - If feature disabled → send full view event
     * - If first event for this view.id → send full view event
     * - If subsequent update and feature enabled → send view_update with changes
     *
     * @param viewId The unique identifier for this view
     * @param currentViewData Complete current view state
     *
     * TODO(Phase 3): Implement event sending logic
     */
    fun sendViewUpdate(viewId: String, currentViewData: Map<String, Any?>) {
        TODO("Phase 3: Implement event sending logic")
    }

    /**
     * Sends a full view event.
     * Used for first event or when feature is disabled.
     *
     * @param viewId The unique identifier for this view
     * @param viewData Complete view state to send
     *
     * TODO(Phase 3): Implement full view event sending
     */
    private fun sendFullViewEvent(viewId: String, viewData: Map<String, Any?>) {
        TODO("Phase 3: Implement full view event sending")
    }

    /**
     * Sends a partial view_update event with only changed fields.
     *
     * @param viewId The unique identifier for this view
     * @param changes Map of changed fields from diff computation
     * @param fullCurrentState Complete current state (stored for next diff)
     *
     * TODO(Phase 3): Implement partial view_update sending
     */
    private fun sendPartialViewUpdate(
        viewId: String,
        changes: Map<String, Any?>,
        fullCurrentState: Map<String, Any?>
    ) {
        TODO("Phase 3: Implement partial view_update sending")
    }

    /**
     * Cleanup when view ends.
     * Frees memory by removing stored state.
     *
     * @param viewId The view identifier to clean up
     */
    fun onViewEnded(viewId: String) {
        lastSentEvents.remove(viewId)
        documentVersions.remove(viewId)
    }

    /**
     * Cleanup when SDK shuts down.
     * Removes all stored state for all views.
     */
    fun onSdkShutdown() {
        lastSentEvents.clear()
        documentVersions.clear()
    }

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

    /**
     * For testing: Get current document version for a view.
     * Returns null if no version has been assigned yet.
     */
    internal fun getDocumentVersion(viewId: String): Int? {
        return documentVersions[viewId]
    }

    /**
     * For testing: Check if we have stored last sent event for a view.
     */
    internal fun hasLastSentEvent(viewId: String): Boolean {
        return viewId in lastSentEvents
    }
}
```

3. Verify file compiles

**Acceptance:**
- [ ] New package created: `...internal.domain.event.viewupdate`
- [ ] ViewEventTracker class compiles
- [ ] Data structures (lastSentEvents, documentVersions) defined
- [ ] Helper methods (isPartialUpdatesEnabled, isFirstEvent) implemented
- [ ] Cleanup methods (onViewEnded, onSdkShutdown) implemented
- [ ] All TODO markers clearly indicate Phase 3 work

---

### Task 4: Add Copyright Header Constant

**Objective:** Ensure ViewEventTracker has the standard Apache license header

**Files to modify:**
- Already included in Task 3 code above

**Implementation steps:**
- Verify the file starts with the standard copyright header (already in code above)

**Acceptance:**
- [ ] File has Apache License 2.0 header
- [ ] Copyright notice includes Datadog attribution

---

## Testing Tasks

### Test 1: Configuration Default Value

**Type:** Unit Test
**Files:** `features/dd-sdk-android-rum/src/test/kotlin/com/datadog/android/rum/RumConfigurationBuilderTest.kt`
**Covers:** Default configuration behavior

**Implementation:**

Add test to existing test file:

```kotlin
@Test
fun `M disable partial updates by default W build() is called`() {
    // Given
    val builder = RumConfiguration.Builder(fakeApplicationId)

    // When
    val config = builder.build()

    // Then
    assertThat(config.featureConfiguration.enablePartialViewUpdates).isFalse()
}
```

**Test cases:**
- [ ] Default configuration has partial updates disabled
- [ ] Configuration field is accessible

---

### Test 2: Configuration Builder Method

**Type:** Unit Test
**Files:** `features/dd-sdk-android-rum/src/test/kotlin/com/datadog/android/rum/RumConfigurationBuilderTest.kt`
**Covers:** Builder method for enabling partial updates

**Implementation:**

Add tests to existing test file:

```kotlin
@Test
fun `M enable partial updates W setEnablePartialViewUpdates(true)`() {
    // Given
    val builder = RumConfiguration.Builder(fakeApplicationId)

    // When
    val config = builder
        .setEnablePartialViewUpdates(true)
        .build()

    // Then
    assertThat(config.featureConfiguration.enablePartialViewUpdates).isTrue()
}

@Test
fun `M disable partial updates W setEnablePartialViewUpdates(false)`() {
    // Given
    val builder = RumConfiguration.Builder(fakeApplicationId)

    // When
    val config = builder
        .setEnablePartialViewUpdates(false)
        .build()

    // Then
    assertThat(config.featureConfiguration.enablePartialViewUpdates).isFalse()
}

@Test
fun `M return builder instance W setEnablePartialViewUpdates()`() {
    // Given
    val builder = RumConfiguration.Builder(fakeApplicationId)

    // When
    val returnedBuilder = builder.setEnablePartialViewUpdates(true)

    // Then
    assertThat(returnedBuilder).isSameAs(builder)
}

@Test
fun `M chain configuration calls W setEnablePartialViewUpdates()`() {
    // Given / When
    val config = RumConfiguration.Builder(fakeApplicationId)
        .setEnablePartialViewUpdates(true)
        .setSessionSampleRate(50f)
        .trackBackgroundEvents(false)
        .build()

    // Then
    assertThat(config.featureConfiguration.enablePartialViewUpdates).isTrue()
    assertThat(config.featureConfiguration.sampleRate).isEqualTo(50f)
    assertThat(config.featureConfiguration.backgroundEventTracking).isFalse()
}
```

**Test cases:**
- [ ] Enabling partial updates sets flag to true
- [ ] Disabling partial updates sets flag to false
- [ ] Builder method returns builder instance (chainable)
- [ ] Can chain with other builder methods

---

### Test 3: ViewEventTracker Data Structure Management

**Type:** Unit Test
**Files:** `features/dd-sdk-android-rum/src/test/kotlin/com/datadog/android/rum/internal/domain/event/viewupdate/ViewEventTrackerTest.kt` (new file)
**Covers:** Data structure initialization and cleanup

**Implementation:**

Create new test file:

```kotlin
/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event.viewupdate

import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.internal.RumFeature
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
internal class ViewEventTrackerTest {

    private lateinit var testedTracker: ViewEventTracker

    private lateinit var fakeConfig: RumConfiguration

    @BeforeEach
    fun setUp() {
        fakeConfig = RumConfiguration(
            applicationId = "test-app-id",
            featureConfiguration = RumFeature.Configuration(
                customEndpointUrl = null,
                sampleRate = 100f,
                telemetrySampleRate = 20f,
                telemetryConfigurationSampleRate = 20f,
                userActionTracking = true,
                touchTargetExtraAttributesProviders = emptyList(),
                interactionPredicate = com.datadog.android.rum.internal.tracking.NoOpInteractionPredicate(),
                viewTrackingStrategy = null,
                longTaskTrackingStrategy = null,
                viewEventMapper = com.datadog.android.event.NoOpEventMapper(),
                errorEventMapper = com.datadog.android.event.NoOpEventMapper(),
                resourceEventMapper = com.datadog.android.event.NoOpEventMapper(),
                actionEventMapper = com.datadog.android.event.NoOpEventMapper(),
                longTaskEventMapper = com.datadog.android.event.NoOpEventMapper(),
                vitalOperationStepEventMapper = com.datadog.android.event.NoOpEventMapper(),
                vitalAppLaunchEventMapper = com.datadog.android.event.NoOpEventMapper(),
                telemetryConfigurationMapper = com.datadog.android.event.NoOpEventMapper(),
                backgroundEventTracking = false,
                trackFrustrations = true,
                trackNonFatalAnrs = true,
                vitalsMonitorUpdateFrequency = com.datadog.android.rum.configuration.VitalsUpdateFrequency.AVERAGE,
                sessionListener = com.datadog.android.rum.internal.noop.NoOpRumSessionListener(),
                initialResourceIdentifier = com.datadog.android.rum.metric.networksettled.NoOpInitialResourceIdentifier(),
                lastInteractionIdentifier = null,
                slowFramesConfiguration = null,
                composeActionTrackingStrategy = com.datadog.android.rum.tracking.NoOpActionTrackingStrategy(),
                additionalConfig = emptyMap(),
                trackAnonymousUser = true,
                rumSessionTypeOverride = null,
                collectAccessibility = false,
                disableJankStats = false,
                insightsCollector = com.datadog.android.rum.internal.instrumentation.insights.NoOpInsightsCollector(),
                enablePartialViewUpdates = false // Feature disabled for these tests
            )
        )

        testedTracker = ViewEventTracker(fakeConfig)
    }

    @Test
    fun `M return true W isFirstEvent() called for new view`() {
        // Given
        val viewId = "view-123"

        // When
        val result = testedTracker.isFirstEvent(viewId)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return false W hasLastSentEvent() called for new view`() {
        // Given
        val viewId = "view-123"

        // When
        val result = testedTracker.hasLastSentEvent(viewId)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return null W getDocumentVersion() called for new view`() {
        // Given
        val viewId = "view-123"

        // When
        val version = testedTracker.getDocumentVersion(viewId)

        // Then
        assertThat(version).isNull()
    }

    @Test
    fun `M clear stored state W onViewEnded() called`() {
        // Given
        val viewId = "view-123"
        // Manually add some state (normally would be done by sendViewUpdate in Phase 3)
        // For now, we'll test the cleanup logic by accessing internals via reflection if needed
        // Or we can just verify the methods don't throw exceptions

        // When
        testedTracker.onViewEnded(viewId)

        // Then
        assertThat(testedTracker.hasLastSentEvent(viewId)).isFalse()
        assertThat(testedTracker.getDocumentVersion(viewId)).isNull()
    }

    @Test
    fun `M clear all state W onSdkShutdown() called`() {
        // Given - tracker is initialized

        // When
        testedTracker.onSdkShutdown()

        // Then
        assertThat(testedTracker.hasLastSentEvent("any-view")).isFalse()
        assertThat(testedTracker.getDocumentVersion("any-view")).isNull()
    }
}
```

**Test cases:**
- [ ] isFirstEvent returns true for new view
- [ ] hasLastSentEvent returns false for new view
- [ ] getDocumentVersion returns null for new view
- [ ] onViewEnded clears stored state
- [ ] onSdkShutdown clears all state

---

## Task Checklist

- [ ] Task 1: Add configuration field to RumFeature.Configuration
- [ ] Task 2: Add builder method to RumConfiguration
- [ ] Task 3: Create ViewEventTracker package and class
- [ ] Task 4: Verify copyright headers
- [ ] Test 1: Configuration default value tests
- [ ] Test 2: Configuration builder method tests
- [ ] Test 3: ViewEventTracker data structure tests
- [ ] All tests passing
- [ ] Code compiles without errors
- [ ] Code review completed

## Implementation Order

Tasks can be done in parallel or in this recommended order:

1. **Task 1** (Configuration field) - Foundation for everything
2. **Task 2** (Builder method) - Depends on Task 1
3. **Task 3** (ViewEventTracker) - Can be done in parallel with Task 1-2
4. **Test 1-2** (Configuration tests) - After Tasks 1-2 complete
5. **Test 3** (ViewEventTracker tests) - After Task 3 completes

**Estimated time:** 4-6 hours for implementation, 2-3 hours for testing

## Verification Steps

After completing all tasks:

1. **Build the SDK:**
   ```bash
   ./gradlew :features:dd-sdk-android-rum:assemble
   ```

2. **Run unit tests:**
   ```bash
   ./gradlew :features:dd-sdk-android-rum:testDebugUnitTest
   ```

3. **Verify configuration API:**
   - Create a sample app configuration with `.setEnablePartialViewUpdates(true)`
   - Verify it compiles
   - Verify default is `false`

4. **Code review checklist:**
   - [ ] All public APIs have KDoc comments
   - [ ] Internal classes use `internal` modifier
   - [ ] Copyright headers present
   - [ ] No behavior changes to existing code
   - [ ] Feature flag defaults to `false`
   - [ ] Tests follow existing naming conventions

## Notes

### Code Style
- Follow existing Kotlin conventions in the codebase
- Use `internal` modifier for ViewEventTracker (not public API)
- Use `private` for implementation details
- Add comprehensive KDoc comments for all public/internal APIs

### Testing Conventions
- Test names follow format: `` M <expected behavior> W <condition> ``
- Use AssertJ for assertions (`assertThat`)
- Follow existing test structure in RumConfigurationBuilderTest

### Memory Management
- `lastSentEvents` and `documentVersions` are intentionally kept in memory
- Typical case: 1-2 active views × ~2KB = ~4KB overhead (acceptable)
- Memory freed via `onViewEnded()` when view stops
- All state cleared via `onSdkShutdown()` when SDK stops

### Feature Flag Pattern
- Configuration flag lives in `RumFeature.Configuration` (internal)
- Public API in `RumConfiguration.Builder` (public)
- Default value in `DEFAULT_RUM_CONFIG` (internal)
- This matches existing pattern for other feature flags

## Open Questions

1. **Thread safety:** Are view updates called from multiple threads?
   - **Answer for Phase 1:** Not critical yet since methods are TODO stubs
   - **Defer to Phase 3:** Will need to address when implementing actual logic

2. **EventWriter interface:** Does it exist already?
   - **Answer:** Yes, but integration deferred to Phase 3
   - **Phase 1 action:** No action needed, ViewEventTracker doesn't depend on it yet

3. **RumContext access:** How to get application/session IDs?
   - **Answer:** Deferred to Phase 3
   - **Phase 1 action:** No action needed yet

## Success Criteria

Phase 1 is complete when:

- [ ] Configuration flag can be set via public API
- [ ] Default configuration has feature disabled
- [ ] ViewEventTracker class exists with data structures
- [ ] All methods have clear TODO markers for Phase 3
- [ ] Unit tests pass with >90% coverage of new code
- [ ] No changes to existing SDK behavior
- [ ] Code review approved
- [ ] All acceptance criteria met

## Next Phase

**Phase 2: Diff Computation Engine** will implement the ViewDiffComputer class that computes differences between view states. ViewEventTracker will use this in Phase 3.
