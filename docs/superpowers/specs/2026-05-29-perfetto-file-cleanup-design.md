# Perfetto Result File Cleanup — Design Spec

**Ticket**: RUM-13679  
**Date**: 2026-05-29

## Problem

`PerfettoProfiler.resultCallback` receives a `resultFilePath` from the Android profiling API pointing to a ~5 MB `.perfetto-stack-sample` file written by the system. After the profiling session data is persisted to the Datadog batch, the file is never deleted. Over time (one file per 60-second continuous-profiling cycle) this fills the app's data directory.

## Constraints

- `withWriteContext` dispatches to an I/O executor — the actual file read (`readProfilingData`) happens asynchronously on the I/O thread. The caller of `dataWriter.write()` cannot safely delete the file synchronously after `write()` returns.
- There is a skip path in `ProfilingFeature.tryWriteProfilingEvent()` for the CONTINUOUS case when there are no RUM events: `dataWriter.write()` is never called, so the file must still be cleaned up.
- If the feature is not initialized (`getFeature()` returns null), `withWriteContext` is never reached and the file must still be deleted.
- The fix must be extendable to orphan recovery (files left behind by process kills) without requiring a rewrite.

## Design

### Ownership

`ProfilingDataWriter` owns both the read and the delete. `ProfilingFeature` never touches the file path after passing it in `PerfettoResult`.

### Deletion sites

**Site 1 — feature not initialized:**  
At the top of `ProfilingDataWriter.write()`, if `sdkCore.getFeature()` returns null, call `safeDelete(resultFilePath)` immediately and return.

**Site 2 — inside `writeScope` (normal path):**  
Inside the `writeScope` lambda (I/O thread), after the `buildRawBatchEvent` call and any `writer.write()` attempt, call `safeDelete(resultFilePath)` unconditionally. This fires whether the batch write succeeded, failed, or was skipped because `buildRawBatchEvent` returned null (empty events).

```kotlin
override fun write(profilingResult: PerfettoResult, longTasks: ..., anrEvents: ..., vitalEvents: ...) {
    val feature = sdkCore.getFeature(Feature.PROFILING_FEATURE_NAME)
    if (feature == null) {
        safeDelete(profilingResult.resultFilePath)   // site 1
        return
    }
    feature.withWriteContext { context, writeScope ->
        writeScope { writer ->
            buildRawBatchEvent(context, profilingResult, longTasks, anrEvents, vitalEvents)?.let {
                synchronized(this) { writer.write(it, null, EventType.DEFAULT) }
            }
            safeDelete(profilingResult.resultFilePath)   // site 2
        }
    }
}
```

`buildRawBatchEvent` is unchanged — it still returns null for empty events. The private `writeWithContext` helper is inlined into `write()` to make both deletion sites visible together.

### `safeDelete` helper

Same pattern as `AnrProfilingTriggerRegistrar.safeDelete()` (line 146):

```kotlin
private fun safeDelete(path: String) {
    try {
        val deleted = File(path).delete()
        if (!deleted) {
            sdkCore.internalLogger.log(WARN, MAINTAINER, { LOG_FILE_DELETE_FAILED })
        }
    } catch (t: Throwable) {
        sdkCore.internalLogger.log(WARN, MAINTAINER, { LOG_FILE_DELETE_FAILED }, t)
    }
}
```

### `ProfilingFeature.tryWriteProfilingEvent()` — CONTINUOUS branch

Remove the early return that skips `dataWriter.write()` when there are no RUM events. Always call `write()` so that `ProfilingDataWriter` owns cleanup. Keep the log, moved after the write call:

```kotlin
ProfilingStartReason.CONTINUOUS -> {
    val scheduler = continuousProfilingScheduler ?: return
    scheduler.onActiveWindowEnded()
    val (longTasks, anrEvents, vitalEvents) = pendingRumEvents.drain()
    dataWriter.write(profilingResult = result, longTasks = longTasks, anrEvents = anrEvents, vitalEvents = vitalEvents)
    if (longTasks.isEmpty() && anrEvents.isEmpty() && vitalEvents.isEmpty()) {
        logToUser(LOG_CONTINUOUS_PROFILING_DROPPED_NO_RUM_EVENTS)
    }
}
```

APPLICATION_LAUNCH is unchanged — `isTtidProfileSent` already ensures `write()` runs at most once.

## Files changed

| File | Change |
|------|--------|
| `features/dd-sdk-android-profiling/src/main/java/com/datadog/android/profiling/internal/ProfilingDataWriter.kt` | Inline `writeWithContext`, add two deletion sites, add `safeDelete` helper |
| `features/dd-sdk-android-profiling/src/main/java/com/datadog/android/profiling/internal/ProfilingFeature.kt` | Remove CONTINUOUS early-return, always call `dataWriter.write()` |
| `features/dd-sdk-android-profiling/src/test/kotlin/com/datadog/android/profiling/internal/ProfilingDataWriterTest.kt` | Add tests for deletion on normal write, empty events, feature null |
| `features/dd-sdk-android-profiling/src/test/kotlin/com/datadog/android/profiling/ProfilingFeatureTest.kt` | Update CONTINUOUS test to assert `write()` is always called |

## Extension point for orphan recovery

When ready, add to `ProfilingFeature.onInitialize()`: scan the profiling output directory (derived from `context.filesDir.resolve("profiling")` or the parent of the first received `resultFilePath`) for leftover `.perfetto-stack-sample` files from prior processes, and attempt re-upload or delete. This is an additive change that does not touch any of the in-session cleanup code above.
