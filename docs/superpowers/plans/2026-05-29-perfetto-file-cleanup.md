# Perfetto File Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Delete the Perfetto result file from disk after its bytes are read and written to the Datadog batch, covering all exit paths (success, empty-events skip, feature uninitialized).

**Architecture:** `ProfilingDataWriter.write()` owns both the file read and the file delete. Deletion is placed unconditionally at the end of the `writeScope` lambda (I/O thread) and as an early guard when the profiling feature scope is null. `ProfilingFeature.tryWriteProfilingEvent()` is simplified so it always calls `dataWriter.write()` for the CONTINUOUS case, delegating cleanup responsibility to the writer.

**Tech Stack:** Kotlin, JUnit 5, Mockito-Kotlin, Elmyr Forge, `@TempDir`

---

## File Map

| File | Change |
|------|--------|
| `features/dd-sdk-android-profiling/src/main/java/com/datadog/android/profiling/internal/ProfilingDataWriter.kt` | Inline `writeWithContext`, add `safeDelete`, add two deletion sites, add `LOG_FILE_DELETE_FAILED` constant |
| `features/dd-sdk-android-profiling/src/main/java/com/datadog/android/profiling/internal/ProfilingFeature.kt` | Remove CONTINUOUS early-return, always call `dataWriter.write()` |
| `features/dd-sdk-android-profiling/src/test/kotlin/com/datadog/android/profiling/internal/ProfilingDataWriterTest.kt` | Add three new deletion tests |
| `features/dd-sdk-android-profiling/src/test/kotlin/com/datadog/android/profiling/ProfilingFeatureTest.kt` | Update CONTINUOUS no-events test to assert `write()` is always called |

---

## Task 1: File deletion in `ProfilingDataWriter`

**Files:**
- Modify: `features/dd-sdk-android-profiling/src/main/java/com/datadog/android/profiling/internal/ProfilingDataWriter.kt`
- Test: `features/dd-sdk-android-profiling/src/test/kotlin/com/datadog/android/profiling/internal/ProfilingDataWriterTest.kt`

### Background

The `writeScope` lambda is dispatched onto an I/O executor by `withWriteContext` — this is where `buildRawBatchEvent` (and therefore `readProfilingData`) actually runs. Deletion must happen on that same thread, after the read+write attempt. There is a second deletion site for when `getFeature()` returns null and `writeScope` is never reached.

`safeDelete` only catches and logs exceptions; it does **not** log when `File.delete()` returns `false`. This avoids spurious WARN logs in tests where `fakeResult.resultFilePath` is a random string that never existed on disk.

- [ ] **Step 1: Write three failing tests**

Add the following three tests to `ProfilingDataWriterTest`. Each creates a real file in `@TempDir` and asserts the file is gone after `write()` returns.

```kotlin
@Test
fun `M delete result file W write {feature not initialized}`(
    @Forgery fakeResult: PerfettoResult,
    forge: Forge
) {
    // Given
    whenever(mockSdkCore.getFeature(Feature.PROFILING_FEATURE_NAME)) doReturn null
    val file = File(tmp, "fake_profile.perfetto-stack-sample")
    file.writeBytes(forge.aString().toByteArray())

    // When
    testedDataWriterTest.write(
        profilingResult = fakeResult.copy(resultFilePath = file.absolutePath),
        vitalEvents = emptyList(),
        anrEvents = emptyList(),
        longTasks = emptyList()
    )

    // Then
    assertThat(file.exists()).isFalse()
    verifyNoInteractions(mockEventBatchWriter)
}

@Test
fun `M delete result file W write {events present}`(
    @Forgery fakeResult: PerfettoResult,
    @Forgery fakeVitals: List<ProfilerEvent.RumVitalEvent>,
    forge: Forge
) {
    // Given
    val file = File(tmp, "fake_profile.perfetto-stack-sample")
    file.writeBytes(forge.aString().toByteArray())
    val rumContext = fakeVitals.first().rumContext
    val alignedVitals = fakeVitals.map {
        it.copy(
            rumContext = it.rumContext.copy(
                applicationId = rumContext.applicationId,
                sessionId = rumContext.sessionId
            )
        )
    }

    // When
    testedDataWriterTest.write(
        profilingResult = fakeResult.copy(resultFilePath = file.absolutePath),
        vitalEvents = alignedVitals,
        anrEvents = emptyList(),
        longTasks = emptyList()
    )

    // Then
    assertThat(file.exists()).isFalse()
}

@Test
fun `M delete result file W write {no rum events}`(
    @Forgery fakeResult: PerfettoResult,
    forge: Forge
) {
    // Given — file exists but there are no events, so buildRawBatchEvent returns null
    val file = File(tmp, "fake_profile.perfetto-stack-sample")
    file.writeBytes(forge.aString().toByteArray())

    // When
    testedDataWriterTest.write(
        profilingResult = fakeResult.copy(resultFilePath = file.absolutePath),
        vitalEvents = emptyList(),
        anrEvents = emptyList(),
        longTasks = emptyList()
    )

    // Then
    assertThat(file.exists()).isFalse()
    verifyNoInteractions(mockEventBatchWriter)
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./gradlew :features:dd-sdk-android-profiling:testDebugUnitTest \
  --tests "com.datadog.android.profiling.internal.ProfilingDataWriterTest.M delete result file*" \
  --info 2>&1 | tail -30
```

Expected: three failures — files still exist after `write()` returns.

- [ ] **Step 3: Implement the changes in `ProfilingDataWriter`**

Replace the entire `write()` function and `writeWithContext()` function, and add `safeDelete` and the new constant. The full updated section of the file:

```kotlin
override fun write(
    profilingResult: PerfettoResult,
    longTasks: List<ProfilerEvent.RumLongTaskEvent>,
    anrEvents: List<ProfilerEvent.RumAnrEvent>,
    vitalEvents: List<ProfilerEvent.RumVitalEvent>
) {
    val feature = sdkCore.getFeature(Feature.PROFILING_FEATURE_NAME)
    if (feature == null) {
        safeDelete(profilingResult.resultFilePath)
        return
    }
    feature.withWriteContext { context, writeScope ->
        writeScope { writer ->
            buildRawBatchEvent(
                context = context,
                profilingResult = profilingResult,
                longTaskEvents = longTasks,
                anrEvents = anrEvents,
                vitalEvents = vitalEvents
            )?.let {
                synchronized(this) {
                    writer.write(event = it, batchMetadata = null, eventType = EventType.DEFAULT)
                }
            }
            safeDelete(profilingResult.resultFilePath)
        }
    }
}

private fun safeDelete(path: String) {
    try {
        @Suppress("UnsafeThirdPartyFunctionCall")
        File(path).delete()
    } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
        sdkCore.internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            { LOG_FILE_DELETE_FAILED },
            t
        )
    }
}
```

Delete the old `writeWithContext` private function entirely (it is only called from `write()` and is now inlined).

In the `companion object`, add:

```kotlin
private const val LOG_FILE_DELETE_FAILED = "Failed to delete Perfetto trace file."
```

- [ ] **Step 4: Run the three new tests to confirm they pass**

```bash
./gradlew :features:dd-sdk-android-profiling:testDebugUnitTest \
  --tests "com.datadog.android.profiling.internal.ProfilingDataWriterTest.M delete result file*" \
  --info 2>&1 | tail -30
```

Expected: three PASSes.

- [ ] **Step 5: Run the full `ProfilingDataWriterTest` suite to confirm no regressions**

```bash
./gradlew :features:dd-sdk-android-profiling:testDebugUnitTest \
  --tests "com.datadog.android.profiling.internal.ProfilingDataWriterTest" \
  --info 2>&1 | tail -30
```

Expected: all tests PASS.

- [ ] **Step 6: Commit**

```bash
git add features/dd-sdk-android-profiling/src/main/java/com/datadog/android/profiling/internal/ProfilingDataWriter.kt \
        features/dd-sdk-android-profiling/src/test/kotlin/com/datadog/android/profiling/internal/ProfilingDataWriterTest.kt
git commit -m "RUM-13679: Delete Perfetto result file after batch write in ProfilingDataWriter"
```

---

## Task 2: Always invoke `dataWriter.write()` in `ProfilingFeature` CONTINUOUS path

**Files:**
- Modify: `features/dd-sdk-android-profiling/src/main/java/com/datadog/android/profiling/internal/ProfilingFeature.kt:260-279`
- Test: `features/dd-sdk-android-profiling/src/test/kotlin/com/datadog/android/profiling/ProfilingFeatureTest.kt:561-586`

### Background

`tryWriteProfilingEvent()` currently returns early without calling `dataWriter.write()` when there are no RUM events in the CONTINUOUS case (line 264-267 in `ProfilingFeature.kt`). This means `safeDelete` inside `writeScope` never fires and the file is never cleaned up. The fix: always call `write()` and move the user-facing log to after the call.

- [ ] **Step 1: Update the existing test for the no-events CONTINUOUS case**

Find the test `M skip writing W continuous profiling result received {no RUM events}` in `ProfilingFeatureTest.kt` (currently at line 561). It currently asserts `verifyNoInteractions(mockDataWriter)`. Change the assertion to verify that `write()` IS called with empty event lists:

```kotlin
@Test
fun `M write with empty events W continuous profiling result received {no RUM events}`(
    @Forgery fakePerfettoResult: PerfettoResult
) {
    // Given
    testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
    testedFeature.dataWriter = mockDataWriter
    whenever(mockProfiler.isRunning(fakeInstanceName)) doReturn true
    whenever(mockSdkCore.getFeature(Feature.PROFILING_FEATURE_NAME)) doReturn mockProfilingFeatureScope
    val callbackCaptor = argumentCaptor<ProfilerCallback>()
    testedFeature.onInitialize(mockContext)
    verify(mockProfiler).registerProfilingCallback(
        eq(mockContext),
        eq(fakeInstanceName),
        callbackCaptor.capture()
    )
    testedFeature.onReceive(fakeTTID)

    // When
    callbackCaptor.firstValue.onSuccess(
        fakePerfettoResult.copy(startReason = ProfilingStartReason.CONTINUOUS)
    )

    // Then
    verify(mockDataWriter).write(
        profilingResult = fakePerfettoResult.copy(startReason = ProfilingStartReason.CONTINUOUS),
        longTasks = emptyList(),
        anrEvents = emptyList(),
        vitalEvents = emptyList()
    )
}
```

Note: the test name is updated to reflect the new behavior — `write()` is always called, not skipped.

- [ ] **Step 2: Run the updated test to confirm it fails**

```bash
./gradlew :features:dd-sdk-android-profiling:testDebugUnitTest \
  --tests "com.datadog.android.profiling.ProfilingFeatureTest.M write with empty events W continuous profiling result received*" \
  --info 2>&1 | tail -20
```

Expected: FAIL — `mockDataWriter.write()` is not currently called for empty events.

- [ ] **Step 3: Update `ProfilingFeature.tryWriteProfilingEvent()` CONTINUOUS branch**

Replace lines 260–279 in `ProfilingFeature.kt` (the `ProfilingStartReason.CONTINUOUS ->` branch):

```kotlin
ProfilingStartReason.CONTINUOUS -> {
    val scheduler = continuousProfilingScheduler ?: return
    scheduler.onActiveWindowEnded()
    val (longTasks, anrEvents, vitalEvents) = pendingRumEvents.drain()
    dataWriter.write(
        profilingResult = result,
        longTasks = longTasks,
        anrEvents = anrEvents,
        vitalEvents = vitalEvents
    )
    if (longTasks.isEmpty() && anrEvents.isEmpty() && vitalEvents.isEmpty()) {
        logToUser(LOG_CONTINUOUS_PROFILING_DROPPED_NO_RUM_EVENTS)
    } else {
        logToUser(
            LOG_CONTINUOUS_PROFILING_WRITTEN.format(
                Locale.US,
                longTasks.size,
                anrEvents.size
            )
        )
    }
}
```

Also remove the `@Suppress("ReturnCount")` annotation from `tryWriteProfilingEvent()` if the suppression count drops — verify by running detekt after.

- [ ] **Step 4: Run the updated test to confirm it passes**

```bash
./gradlew :features:dd-sdk-android-profiling:testDebugUnitTest \
  --tests "com.datadog.android.profiling.ProfilingFeatureTest.M write with empty events W continuous profiling result received*" \
  --info 2>&1 | tail -20
```

Expected: PASS.

- [ ] **Step 5: Run the full `ProfilingFeatureTest` suite to confirm no regressions**

```bash
./gradlew :features:dd-sdk-android-profiling:testDebugUnitTest \
  --tests "com.datadog.android.profiling.ProfilingFeatureTest" \
  --info 2>&1 | tail -30
```

Expected: all tests PASS.

- [ ] **Step 6: Run the full profiling module test suite**

```bash
./gradlew :features:dd-sdk-android-profiling:testDebugUnitTest --info 2>&1 | tail -30
```

Expected: all tests PASS.

- [ ] **Step 7: Run detekt on the profiling module**

```bash
./gradlew :features:dd-sdk-android-profiling:detekt --info 2>&1 | tail -30
```

Expected: no new violations. If `ReturnCount` suppression on `tryWriteProfilingEvent` is no longer needed (return count dropped to ≤2), remove it.

- [ ] **Step 8: Commit**

```bash
git add features/dd-sdk-android-profiling/src/main/java/com/datadog/android/profiling/internal/ProfilingFeature.kt \
        features/dd-sdk-android-profiling/src/test/kotlin/com/datadog/android/profiling/ProfilingFeatureTest.kt
git commit -m "RUM-13679: Always call dataWriter.write() in CONTINUOUS path so file cleanup always runs"
```
