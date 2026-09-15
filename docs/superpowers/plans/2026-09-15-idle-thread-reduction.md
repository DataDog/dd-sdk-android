# Idle Thread Reduction (RUM-16893) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cut ~5 permanently-alive but mostly-idle threads per SDK instance, by enabling core-thread timeout on the executors the core factories already return and by removing the profiling quota check's dedicated thread entirely.

**Architecture:** `BackPressureExecutorService` is already `ThreadPoolExecutor(1, 1, keepAlive = 5s, …)`, but `allowCoreThreadTimeOut` is called nowhere in the repo, so that keep-alive is dead code. Turning it on at the `CoreFeature.createExecutorService` / `createScheduledExecutorService` seam converts every *feature-created* executor to on-demand with no public API change and no call-site migration, while core-owned `upload`/`storage`/`context` keep stable permanent threads. Separately, the profiling quota check moves from a blocking `execute()` to `enqueue()`, which lets its dedicated executor be deleted outright.

**Tech Stack:** Kotlin, Gradle (Kotlin DSL), JUnit 5, Mockito, Elmyr (Forge), AssertJ, OkHttp 4, detekt, ktlint.

**Spec:** `docs/superpowers/specs/2026-09-15-idle-thread-reduction-design.md`

## Global Constraints

- **No public API change.** `./gradlew checkApiSurfaceChangesAll` must stay clean. This is the proof the chosen approach was cheaper than adding `createOnDemandExecutorService` to `FeatureSdkCore`. Do not add, remove, or change the signature of anything in a non-`internal` package. In particular, **do not** modify the `FlushableExecutorService.Factory` interface — it is public API and user-suppliable.
- **Never mutate a user-supplied executor.** Because `FlushableExecutorService.Factory` is public, a customer may return their own `ExecutorService`. Pool-policy changes apply *only* to `BackPressureExecutorService` and `LoggingScheduledThreadPoolExecutor`.
- **Keep-alive constant:** 5 seconds, expressed as `TimeUnit.SECONDS.toMillis(5)`, matching the value already in `BackPressureExecutorService`.
- **`allowCoreThreadTimeOut(true)` throws `IllegalArgumentException` when keep-alive is not > 0.** `ScheduledThreadPoolExecutor` defaults keep-alive to 0. Always call `setKeepAliveTime(...)` *before* `allowCoreThreadTimeOut(true)`.
- **Test conventions** (from `dd-sdk-android/CLAUDE.md`): JUnit5 + `MockitoExtension` + `ForgeExtension`, `@MockitoSettings(strictness = Strictness.LENIENT)`, `@ForgeConfiguration(Configurator::class)`. Object under test prefixed `tested`, verified mocks `mock`, stubs `stub`, fixtures `fake`. Test names: `` `M <expected> W <method()> {context}` ``.
- **Commit titles** must be prefixed `RUM-16893: `.
- **Every source file** carries the existing Apache-2.0 header comment. Copy it when creating files.
- **Third-party calls** that can throw must be annotated `@Suppress("UnsafeThirdPartyFunctionCall")` with a comment explaining why the call is safe — follow surrounding style.
- Run `./gradlew <module>:ktlintFormat` and `./gradlew <module>:detekt` before each commit.

---

## File Structure

| File | Responsibility | Task |
| --- | --- | --- |
| `dd-sdk-android-core/src/main/kotlin/com/datadog/android/core/internal/thread/ThreadPoolExecutorExt.kt` | **Modify.** Add `enableIdleThreadTimeout()` — the single place that decides which executors become on-demand. | 1 |
| `dd-sdk-android-core/src/test/kotlin/com/datadog/android/core/internal/thread/ThreadPoolExecutorExtTest.kt` | **Modify.** Unit tests for the extension, plus the two tests that lock in the JDK behavior the design rests on. | 1 |
| `dd-sdk-android-core/src/main/kotlin/com/datadog/android/core/internal/CoreFeature.kt:339-345` | **Modify.** Apply the extension at the two factory seams. `setupExecutors()` deliberately untouched. | 2 |
| `dd-sdk-android-core/src/test/kotlin/com/datadog/android/core/internal/CoreFeatureTest.kt` | **Modify.** Assert feature-created executors time out and core-owned ones do not. | 2 |
| `features/dd-sdk-android-profiling/src/main/java/com/datadog/android/profiling/internal/quota/ProfilingQuotaChecker.kt` | **Modify.** `execute()` → `enqueue()`; drop the `executor` constructor param; `Future` → `Call` cancellation. | 3 |
| `features/dd-sdk-android-profiling/src/main/java/com/datadog/android/profiling/internal/ProfilingFeature.kt` | **Modify.** Delete `quotaExecutor` field, its creation, and its `shutdownNow()`. | 3 |
| `features/dd-sdk-android-profiling/src/test/kotlin/com/datadog/android/profiling/internal/quota/QuotaCheckerTest.kt` | **Modify.** Re-point from `execute()` stubbing to `enqueue()` callback capture. | 3 |
| `features/dd-sdk-android-profiling/src/main/java/com/datadog/android/profiling/Profiling.kt:143-151` | **Modify.** Named, on-demand scheduler for the process-wide profiler. | 4 |

---

## Task 1: Idle-thread-timeout extension

**Files:**
- Modify: `dd-sdk-android-core/src/main/kotlin/com/datadog/android/core/internal/thread/ThreadPoolExecutorExt.kt`
- Test: `dd-sdk-android-core/src/test/kotlin/com/datadog/android/core/internal/thread/ThreadPoolExecutorExtTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks. Uses existing `BackPressureExecutorService` and `LoggingScheduledThreadPoolExecutor` (both `internal`, same module, same package).
- Produces: `internal fun ExecutorService.enableIdleThreadTimeout()` and `internal val IDLE_THREAD_KEEP_ALIVE_MS: Long` in package `com.datadog.android.core.internal.thread`. Task 2 calls both.

**Why this shape:** the extension is declared on `ExecutorService` (not `ThreadPoolExecutor`) because the two call sites in Task 2 return `ExecutorService` and `ScheduledExecutorService`. Keeping the type check *inside* the extension means the "never mutate a user-supplied executor" rule lives in exactly one place.

- [ ] **Step 1: Write the failing tests**

Append to `ThreadPoolExecutorExtTest.kt`. That class already has the
`MockitoExtension`/`ForgeExtension` annotations, a `mockInternalLogger` and a
`mockTimeProvider`, and already imports `assertThat` and `ThreadPoolExecutor`. Add only the
missing imports:

```kotlin
import com.datadog.android.core.configuration.BackPressureMitigation
import com.datadog.android.core.configuration.BackPressureStrategy
import fr.xgouchet.elmyr.annotation.StringForgery
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
```

Then add the tests:

```kotlin
// region enableIdleThreadTimeout

private fun fakeBackPressureStrategy() = BackPressureStrategy(
    32,
    {},
    {},
    BackPressureMitigation.DROP_OLDEST
)

@Test
fun `M enable core thread timeout W enableIdleThreadTimeout() {BackPressureExecutorService}`(
    @StringForgery fakeExecutorContext: String
) {
    // Given
    val testedExecutor = BackPressureExecutorService(
        mockInternalLogger,
        fakeExecutorContext,
        fakeBackPressureStrategy(),
        mockTimeProvider
    )

    // When
    testedExecutor.enableIdleThreadTimeout()

    // Then
    assertThat(testedExecutor.allowsCoreThreadTimeOut()).isTrue()
    assertThat(testedExecutor.getKeepAliveTime(TimeUnit.MILLISECONDS))
        .isEqualTo(IDLE_THREAD_KEEP_ALIVE_MS)
    testedExecutor.shutdownNow()
}

@Test
fun `M enable core thread timeout W enableIdleThreadTimeout() {LoggingScheduledThreadPoolExecutor}`(
    @StringForgery fakeExecutorContext: String
) {
    // Given
    val testedExecutor = LoggingScheduledThreadPoolExecutor(
        1,
        fakeExecutorContext,
        mockInternalLogger,
        fakeBackPressureStrategy()
    )

    // When
    testedExecutor.enableIdleThreadTimeout()

    // Then
    assertThat(testedExecutor.allowsCoreThreadTimeOut()).isTrue()
    assertThat(testedExecutor.getKeepAliveTime(TimeUnit.MILLISECONDS))
        .isEqualTo(IDLE_THREAD_KEEP_ALIVE_MS)
    testedExecutor.shutdownNow()
}

@Test
fun `M do nothing W enableIdleThreadTimeout() {foreign executor}`() {
    // Given a third-party executor, as a customer FlushableExecutorService.Factory may return
    val testedExecutor: ExecutorService = ThreadPoolExecutor(
        1,
        1,
        1_000L,
        TimeUnit.MILLISECONDS,
        LinkedBlockingQueue()
    )

    // When
    testedExecutor.enableIdleThreadTimeout()

    // Then
    assertThat((testedExecutor as ThreadPoolExecutor).allowsCoreThreadTimeOut()).isFalse()
    assertThat(testedExecutor.getKeepAliveTime(TimeUnit.MILLISECONDS)).isEqualTo(1_000L)
    testedExecutor.shutdownNow()
}

// endregion

// region JDK guarantees this design depends on

@Test
fun `M still run pending delayed task W pool idles past keep alive`(
    @StringForgery fakeExecutorContext: String
) {
    // Given a scheduled pool whose worker may time out far sooner than the task's delay.
    // ThreadPoolExecutor#getTask() will not let the LAST worker exit while the queue is
    // non-empty, and a not-yet-due delayed task counts as non-empty. If that guarantee
    // ever breaks, sparse scheduled work would be silently dropped.
    val testedExecutor = LoggingScheduledThreadPoolExecutor(
        1,
        fakeExecutorContext,
        mockInternalLogger,
        fakeBackPressureStrategy()
    )
    testedExecutor.setKeepAliveTime(20L, TimeUnit.MILLISECONDS)
    testedExecutor.allowCoreThreadTimeOut(true)
    val latch = CountDownLatch(1)

    // When
    testedExecutor.schedule({ latch.countDown() }, 300L, TimeUnit.MILLISECONDS)

    // Then
    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
    testedExecutor.shutdownNow()
}

@Test
fun `M reclaim worker W pool idles past keep alive with empty queue`(
    @StringForgery fakeExecutorContext: String
) {
    // Given
    val testedExecutor = LoggingScheduledThreadPoolExecutor(
        1,
        fakeExecutorContext,
        mockInternalLogger,
        fakeBackPressureStrategy()
    )
    testedExecutor.setKeepAliveTime(20L, TimeUnit.MILLISECONDS)
    testedExecutor.allowCoreThreadTimeOut(true)
    val latch = CountDownLatch(1)
    testedExecutor.schedule({ latch.countDown() }, 0L, TimeUnit.MILLISECONDS)
    check(latch.await(5, TimeUnit.SECONDS))

    // When the queue drains, the worker is no longer retained
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (testedExecutor.poolSize > 0 && System.nanoTime() < deadline) {
        Thread.sleep(20L)
    }

    // Then
    assertThat(testedExecutor.poolSize).isZero()
    testedExecutor.shutdownNow()
}

@Test
fun `M retain worker W periodic task pending across keep alive windows`(
    @StringForgery fakeExecutorContext: String
) {
    // Given a periodic task always leaves itself queued, so its worker must never be reclaimed.
    // This is what makes the change a no-op for rum-vital, the flags flush and client stats.
    val testedExecutor = LoggingScheduledThreadPoolExecutor(
        1,
        fakeExecutorContext,
        mockInternalLogger,
        fakeBackPressureStrategy()
    )
    testedExecutor.setKeepAliveTime(20L, TimeUnit.MILLISECONDS)
    testedExecutor.allowCoreThreadTimeOut(true)
    val runs = CountDownLatch(3)

    // When
    testedExecutor.scheduleWithFixedDelay(
        { runs.countDown() },
        0L,
        60L,
        TimeUnit.MILLISECONDS
    )

    // Then the task keeps firing across several keep-alive windows, and a worker is still held
    assertThat(runs.await(5, TimeUnit.SECONDS)).isTrue()
    assertThat(testedExecutor.poolSize).isEqualTo(1)
    testedExecutor.shutdownNow()
}

// endregion
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :dd-sdk-android-core:testDebugUnitTest --tests "*ThreadPoolExecutorExtTest*"`

Expected: FAIL — compilation error, `unresolved reference: enableIdleThreadTimeout` and `unresolved reference: IDLE_THREAD_KEEP_ALIVE_MS`.

- [ ] **Step 3: Write the minimal implementation**

In `ThreadPoolExecutorExt.kt`, add the import `java.util.concurrent.ExecutorService` and append:

```kotlin
internal val IDLE_THREAD_KEEP_ALIVE_MS = TimeUnit.SECONDS.toMillis(5)

/**
 * Lets the single core thread of a Datadog-owned pool die once it has been idle for
 * [IDLE_THREAD_KEEP_ALIVE_MS], so sparse consumers stop holding a permanently-alive thread.
 *
 * A worker is only reclaimed when the queue is empty: [ThreadPoolExecutor.getTask] refuses to
 * let the last worker exit while any task is pending, including a delayed task that is not yet
 * due. So one-shot consumers release their thread, while periodic consumers keep theirs and see
 * no behavior change.
 *
 * This is a no-op for anything other than a Datadog-owned pool: `FlushableExecutorService.Factory`
 * is public API, and a customer-supplied executor must keep whatever pool policy its author chose.
 */
internal fun ExecutorService.enableIdleThreadTimeout() {
    val executor: ThreadPoolExecutor = when (this) {
        is BackPressureExecutorService -> this
        is LoggingScheduledThreadPoolExecutor -> this
        else -> return
    }
    // ScheduledThreadPoolExecutor defaults keep-alive to 0, and allowCoreThreadTimeOut rejects a
    // non-positive keep-alive, so the keep-alive must be set first.
    @Suppress("UnsafeThirdPartyFunctionCall") // keep-alive is a positive constant
    executor.setKeepAliveTime(IDLE_THREAD_KEEP_ALIVE_MS, TimeUnit.MILLISECONDS)
    @Suppress("UnsafeThirdPartyFunctionCall") // keep-alive was just set to a positive value
    executor.allowCoreThreadTimeOut(true)
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :dd-sdk-android-core:testDebugUnitTest --tests "*ThreadPoolExecutorExtTest*"`

Expected: PASS, all 6 new tests green.

- [ ] **Step 5: Lint and commit**

```bash
./gradlew :dd-sdk-android-core:ktlintFormat :dd-sdk-android-core:detekt
git add dd-sdk-android-core/src/main/kotlin/com/datadog/android/core/internal/thread/ThreadPoolExecutorExt.kt \
        dd-sdk-android-core/src/test/kotlin/com/datadog/android/core/internal/thread/ThreadPoolExecutorExtTest.kt
git commit -m "RUM-16893: Add enableIdleThreadTimeout for Datadog-owned pools"
```

---

## Task 2: Apply the timeout to feature-created executors

**Files:**
- Modify: `dd-sdk-android-core/src/main/kotlin/com/datadog/android/core/internal/CoreFeature.kt:339-345`
- Test: `dd-sdk-android-core/src/test/kotlin/com/datadog/android/core/internal/CoreFeatureTest.kt`

**Interfaces:**
- Consumes: `enableIdleThreadTimeout()` and `IDLE_THREAD_KEEP_ALIVE_MS` from Task 1.
- Produces: no signature change. `CoreFeature.createExecutorService(String): ExecutorService` and `CoreFeature.createScheduledExecutorService(String): ScheduledExecutorService` keep their exact signatures; only the returned pool's policy changes. Every existing caller — the 15 `FeatureSdkCore.createXExecutorService` call sites across RUM, Flags, Profiling, Trace and Session Replay — is untouched.

**Critical scope note:** do **not** touch `setupExecutors()`. `upload`, `storage` and `context` are core-owned hot paths and must keep permanent, stably-named threads — `BatchMetricsDispatcher` reports `Thread.currentThread().name` as telemetry from those threads, and `DatadogThreadFactory` increments a per-spawn index, so letting them churn would create unbounded thread-name cardinality. Note `persistenceExecutorService` is built by calling `executorServiceFactory.create(...)` *directly* in `setupExecutors()`, not via `createExecutorService()`, so it is already excluded — leave it that way.

- [ ] **Step 1: Write the failing tests**

Add to `CoreFeatureTest.kt`. Required imports:

```kotlin
import com.datadog.android.core.internal.thread.IDLE_THREAD_KEEP_ALIVE_MS
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
```

`createExecutorService` reads the `lateinit backpressureStrategy`, which is only assigned in `initialize()`, so these tests must initialize first. They also need the **real** default factories — the shared `testedFeature` is built with mock factories, on which the extension correctly no-ops.

```kotlin
// region idle thread timeout

private fun coreFeatureWithRealExecutorFactories() = CoreFeature(
    mockInternalLogger,
    mockAppStartTimeProvider,
    executorServiceFactory = CoreFeature.DEFAULT_FLUSHABLE_EXECUTOR_SERVICE_FACTORY,
    scheduledExecutorServiceFactory = CoreFeature.DEFAULT_SCHEDULED_EXECUTOR_SERVICE_FACTORY,
    buildSdkVersionProvider = mockBuildSdkVersionProvider
).apply {
    initialize(appContext.mockInstance, fakeSdkInstanceId, fakeConfig, fakeConsent)
}

@Test
fun `M enable idle thread timeout W createExecutorService()`(
    @StringForgery fakeExecutorContext: String
) {
    // Given
    val coreFeature = coreFeatureWithRealExecutorFactories()

    // When
    val executor = coreFeature.createExecutorService(fakeExecutorContext)

    // Then
    check(executor is ThreadPoolExecutor)
    assertThat(executor.allowsCoreThreadTimeOut()).isTrue()
    assertThat(executor.getKeepAliveTime(TimeUnit.MILLISECONDS))
        .isEqualTo(IDLE_THREAD_KEEP_ALIVE_MS)
    executor.shutdownNow()
    coreFeature.stop()
}

@Test
fun `M enable idle thread timeout W createScheduledExecutorService()`(
    @StringForgery fakeExecutorContext: String
) {
    // Given
    val coreFeature = coreFeatureWithRealExecutorFactories()

    // When
    val executor = coreFeature.createScheduledExecutorService(fakeExecutorContext)

    // Then
    check(executor is ThreadPoolExecutor)
    assertThat(executor.allowsCoreThreadTimeOut()).isTrue()
    assertThat(executor.getKeepAliveTime(TimeUnit.MILLISECONDS))
        .isEqualTo(IDLE_THREAD_KEEP_ALIVE_MS)
    executor.shutdownNow()
    coreFeature.stop()
}

@Test
fun `M not enable idle thread timeout W initialize() {core owned executors}`() {
    // Given core-owned hot paths must keep permanent, stably-named threads
    val coreFeature = coreFeatureWithRealExecutorFactories()

    // Then
    val upload = coreFeature.uploadExecutorService
    check(upload is ThreadPoolExecutor)
    assertThat(upload.allowsCoreThreadTimeOut()).isFalse()

    val persistence = coreFeature.persistenceExecutorService
    check(persistence is ThreadPoolExecutor)
    assertThat(persistence.allowsCoreThreadTimeOut()).isFalse()

    assertThat(coreFeature.contextExecutorService.allowsCoreThreadTimeOut()).isFalse()
    coreFeature.stop()
}

// endregion
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :dd-sdk-android-core:testDebugUnitTest --tests "*CoreFeatureTest*"`

Expected: the two `M enable idle thread timeout …` tests FAIL with `expected: true but was: false`. The `M not enable …` test should already PASS — it is a regression guard, and its passing now is the point.

- [ ] **Step 3: Write the minimal implementation**

In `CoreFeature.kt`, replace lines 339-345:

```kotlin
    fun createExecutorService(executorContext: String): ExecutorService {
        return executorServiceFactory.create(internalLogger, executorContext, backpressureStrategy, timeProvider)
            .apply { enableIdleThreadTimeout() }
    }

    fun createScheduledExecutorService(executorContext: String): ScheduledExecutorService {
        return scheduledExecutorServiceFactory.create(internalLogger, executorContext, backpressureStrategy)
            .apply { enableIdleThreadTimeout() }
    }
```

Add the import `com.datadog.android.core.internal.thread.enableIdleThreadTimeout` if the package is not already wildcard-imported.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :dd-sdk-android-core:testDebugUnitTest --tests "*CoreFeatureTest*"`

Expected: PASS, including the unchanged `M initialize executors W initialize()`.

- [ ] **Step 5: Verify no public API changed**

Run: `./gradlew :dd-sdk-android-core:checkApiSurfaceChanges :dd-sdk-android-core:apiCheck`

Expected: PASS with no diff. If either reports a change, the implementation leaked out of `internal` — fix rather than regenerate the surface files.

- [ ] **Step 6: Lint and commit**

```bash
./gradlew :dd-sdk-android-core:ktlintFormat :dd-sdk-android-core:detekt
git add dd-sdk-android-core/src/main/kotlin/com/datadog/android/core/internal/CoreFeature.kt \
        dd-sdk-android-core/src/test/kotlin/com/datadog/android/core/internal/CoreFeatureTest.kt
git commit -m "RUM-16893: Reclaim idle threads from feature-created executors"
```

---

## Task 3: Make the profiling quota check async and delete its executor

**Files:**
- Modify: `features/dd-sdk-android-profiling/src/main/java/com/datadog/android/profiling/internal/quota/ProfilingQuotaChecker.kt`
- Modify: `features/dd-sdk-android-profiling/src/main/java/com/datadog/android/profiling/internal/ProfilingFeature.kt`
- Test: `features/dd-sdk-android-profiling/src/test/kotlin/com/datadog/android/profiling/internal/quota/QuotaCheckerTest.kt`

**Interfaces:**
- Consumes: nothing from Tasks 1-2.
- Produces: `ProfilingQuotaChecker(callFactory: Call.Factory, internalLogger: InternalLogger, onResult: (QuotaResult) -> Unit = {})` — the `executor: ExecutorService` parameter is **removed**. The `QuotaChecker` interface (`lastResult`, `checkAsync(sessionId, datadogContext)`, `reset()`) is unchanged, so `ProfilingFeature`'s use of it is unaffected apart from construction.

**Why one task:** dropping the constructor parameter breaks `ProfilingFeature` compilation, so the checker rewrite and the feature cleanup must land together to keep the build green.

**Behavior that must not change:** the 5 s budget comes from `callTimeout(QUOTA_CHECK_TIMEOUT_MS, MILLISECONDS)` on the OkHttp client in `ProfilingFeature.onInitialize`, and applies to `enqueue()` exactly as it did to `execute()`. Result mapping stays identical: `API_ERROR` for `IOException` and for unexpected exceptions, `QUOTA_EXCEEDED` for HTTP 429, and the existing JSON parsing. Do **not** introduce `QuotaResult.FAIL_OPEN`; it is declared but referenced nowhere in main source.

- [ ] **Step 1: Write the failing tests**

In `QuotaCheckerTest.kt`: delete the `private val executor = FakeSameThreadExecutorService()` field, drop `executor = executor` from the `ProfilingQuotaChecker(...)` call in `set up`, and remove the now-unused `com.datadog.android.api.threads.FakeSameThreadExecutorService` import.

Every existing test stubs `whenever(mockCall.execute()) doReturn makeResponse(...)`. Replace that pattern with a helper that captures the enqueued callback and drives it synchronously. Add these imports:

```kotlin
import okhttp3.Callback
import org.mockito.kotlin.never
```

Add the helpers:

```kotlin
/** Runs the callback that `checkAsync` enqueued, as OkHttp would on a successful response. */
private fun respondWith(response: Response) {
    val captor = argumentCaptor<Callback>()
    verify(mockCall).enqueue(captor.capture())
    captor.firstValue.onResponse(mockCall, response)
}

/** Runs the callback that `checkAsync` enqueued, as OkHttp would on a transport failure. */
private fun failWith(exception: IOException) {
    val captor = argumentCaptor<Callback>()
    verify(mockCall).enqueue(captor.capture())
    captor.firstValue.onFailure(mockCall, exception)
}
```

Then convert each existing test from stub-then-act to act-then-respond. For example, `M return ALLOWED W quota_ok decision in response` becomes:

```kotlin
@Test
fun `M return ALLOWED W quota_ok decision in response`() {
    // Given
    val body = """{"data":{"attributes":{"admitted":true,"reason":"quota_ok"}}}"""

    // When
    testedChecker.checkAsync(fakeSessionId, fakeDatadogContext)
    respondWith(makeResponse(200, body))

    // Then
    assertThat(testedChecker.lastResult)
        .isEqualTo(QuotaResult(QuotaResult.Decision.ALLOWED, QuotaReason.QUOTA_OK))
}
```

Apply the same act-then-respond conversion to the remaining tests. There are **21
`mockCall.execute()` stubbings across 22 tests**, so work through the list rather than by
search-and-replace; map the old `doThrow(IOException(...))` on `execute()` to
`failWith(IOException(...))`. The full set to convert:

`M return ALLOWED W quota_ok decision in response` (worked example above),
`M return DENIED W quota_ko decision in response`,
`M return UNDEFINED reason W unknown reason string in response`,
`M return API_ERROR W non-200 response`,
`M return DENIED W 429 response`,
`M return API_ERROR W network IOException` (use `failWith`),
`M return API_ERROR W malformed JSON body`,
`M invoke onResult callback W check completes`,
`M use quota subdomain and session_id param W building request`,
`M return ALLOWED W decision field absent in response`,
`M return DENIED W backend_unavailable reason and admitted false`,
`M normalize to BACKEND_UNAVAILABLE W backend_client_not_initialized reason`,
`M return DENIED W backend_client_not_initialized reason and admitted false`,
`M return DENIED W org_disabled reason and admitted false`,
`M return UNDEFINED reason W reason field absent in response`,
`M include Accept header W building request`,
`M not fire second request W checkAsync called twice with same sessionId`,
`M fire new request W checkAsync called with different sessionId`,
`M fire new request W reset then checkAsync with same sessionId`,
`M return null W lastResult queried before any check` (no stubbing; unchanged),
`M expose last result W checkAsync completes`,
`M clear last result W reset called`.

Two need more than a mechanical swap. The request-shape tests
(`M use quota subdomain and session_id param W building request`,
`M include Accept header W building request`) assert on the `Request` captured from
`mockCallFactory.newCall(...)`, which still happens synchronously inside `checkAsync` — they need
no `respondWith` call at all. The three `M fire new request …` / `M not fire second request …`
tests assert on `newCall` invocation counts, which are also unaffected by the sync→async change;
they only need the `executor` field removed from scope.

Then add these new tests for behavior that only exists in the async form:

```kotlin
@Test
fun `M enqueue call W checkAsync()`() {
    // When
    testedChecker.checkAsync(fakeSessionId, fakeDatadogContext)

    // Then
    verify(mockCall).enqueue(any())
    verify(mockCall, never()).execute()
}

@Test
fun `M cancel in flight call W checkAsync() {new session}`(
    @StringForgery fakeOtherSessionId: String
) {
    // Given
    testedChecker.checkAsync(fakeSessionId, fakeDatadogContext)

    // When
    testedChecker.checkAsync(fakeOtherSessionId, fakeDatadogContext)

    // Then
    verify(mockCall).cancel()
}

@Test
fun `M cancel in flight call W reset()`() {
    // Given
    testedChecker.checkAsync(fakeSessionId, fakeDatadogContext)

    // When
    testedChecker.reset()

    // Then
    verify(mockCall).cancel()
    assertThat(testedChecker.lastResult).isNull()
}

@Test
fun `M not report result W onFailure() {call was cancelled}`() {
    // Given a cancelled call reports failure through onFailure; that is not a real error
    testedChecker.checkAsync(fakeSessionId, fakeDatadogContext)
    whenever(mockCall.isCanceled()) doReturn true

    // When
    failWith(IOException("Canceled"))

    // Then
    assertThat(testedChecker.lastResult).isNull()
    assertThat(capturedResults).isEmpty()
}

@Test
fun `M report API_ERROR W onFailure() {network error}`() {
    // Given
    testedChecker.checkAsync(fakeSessionId, fakeDatadogContext)
    whenever(mockCall.isCanceled()) doReturn false

    // When
    failWith(IOException(forge.anAlphabeticalString()))

    // Then
    assertThat(testedChecker.lastResult).isEqualTo(QuotaResult.API_ERROR)
    assertThat(capturedResults).containsExactly(QuotaResult.API_ERROR)
}
```

The last test needs a `Forge`; take it as a `forge: Forge` test parameter (`fr.xgouchet.elmyr.Forge`) as other tests in this module do, or inline a literal string.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :features:dd-sdk-android-profiling:testDebugUnitTest --tests "*QuotaCheckerTest*"`

Expected: FAIL — compilation error, `ProfilingQuotaChecker` still requires the `executor` argument, and `enqueue` is never called.

- [ ] **Step 3: Rewrite `ProfilingQuotaChecker`**

Replace the constructor, the `pendingFuture`/`checkAsync`/`reset`/`performCheck` block (lines 22-83) with:

```kotlin
internal class ProfilingQuotaChecker(
    private val callFactory: Call.Factory,
    private val internalLogger: InternalLogger,
    private val onResult: (QuotaResult) -> Unit = {}
) : QuotaChecker {

    private val pendingCall = AtomicReference<Call?>()
    private val lastSessionId = AtomicReference<String?>(null)

    @Volatile
    override var lastResult: QuotaResult? = null
        private set

    @Suppress("TooGenericExceptionCaught")
    override fun checkAsync(sessionId: String, datadogContext: DatadogContext) {
        val previousId = lastSessionId.getAndSet(sessionId)
        if (previousId == sessionId) return // same session: in-flight check (if any) is still valid
        pendingCall.getAndSet(null)?.cancel()
        val call = try {
            @Suppress("UnsafeThirdPartyFunctionCall") // wrapped in this try-catch
            callFactory.newCall(buildRequest(sessionId, datadogContext))
        } catch (e: Exception) {
            logErrorToMaintainer(e) { LOG_UNEXPECTED_ERROR.format(Locale.US, e.message) }
            lastSessionId.compareAndSet(sessionId, previousId)
            return
        }
        pendingCall.set(call)
        // The 5s budget comes from callTimeout() on the OkHttp client, so no executor of our own
        // is needed: OkHttp's dispatcher is already a shared, zero-idle, on-demand thread pool.
        @Suppress("UnsafeThirdPartyFunctionCall") // callback bodies never throw
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // A cancelled call (new session, or reset) surfaces here; that is not an error.
                if (call.isCanceled()) return
                logErrorToMaintainer(e) { LOG_NETWORK_ERROR.format(Locale.US, e.message) }
                deliver(call, sessionId, QuotaResult.API_ERROR)
            }

            override fun onResponse(call: Call, response: Response) {
                deliver(call, sessionId, readResult(response))
            }
        })
    }

    override fun reset() {
        lastSessionId.set(null)
        lastResult = null
        pendingCall.getAndSet(null)?.cancel()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun readResult(response: Response): QuotaResult {
        return try {
            @Suppress("UnsafeThirdPartyFunctionCall") // wrapped in this try-catch
            response.use {
                if (it.isSuccessful) {
                    parseQuotaResult(it.body?.string())
                } else {
                    handleHttpError(it.code)
                }
            }
        } catch (e: IOException) {
            logErrorToMaintainer(e) { LOG_NETWORK_ERROR.format(Locale.US, e.message) }
            QuotaResult.API_ERROR
        } catch (e: Exception) {
            logErrorToMaintainer(e) { LOG_UNEXPECTED_ERROR.format(Locale.US, e.message) }
            QuotaResult.API_ERROR
        }
    }

    private fun deliver(call: Call, sessionId: String, result: QuotaResult) {
        // compareAndSet, not set: a newer session's call must not be cleared by a late callback.
        pendingCall.compareAndSet(call, null)
        if (lastSessionId.get() == sessionId) {
            lastResult = result
            onResult(result)
        }
    }
```

Leave `buildRequest`, `parseQuotaResult`, `parseReason`, `handleHttpError` and the logging helpers exactly as they are. Then fix imports: **remove** `com.datadog.android.core.internal.utils.submitSafe`, `java.util.concurrent.ExecutorService`, `java.util.concurrent.Future`; **add** `okhttp3.Callback` and `okhttp3.Response`. Also remove the now-unused `private const val OPERATION_NAME_QUOTA` from the companion object.

- [ ] **Step 4: Delete the quota executor from `ProfilingFeature`**

Three deletions and one edit:

1. Remove the field (around line 73):

```kotlin
    @Volatile
    private var quotaExecutor: ExecutorService? = null
```

2. In `onInitialize`, replace the construction block:

```kotlin
        val quotaCallFactory = sdkCore.createOkHttpCallFactory {
            callTimeout(QUOTA_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }
        quotaChecker = ProfilingQuotaChecker(
            callFactory = quotaCallFactory,
            internalLogger = sdkCore.internalLogger,
            onResult = ::propagateQuotaResult
        )
```

3. In `onStop`, delete these two lines (around 167-168):

```kotlin
        quotaExecutor?.shutdownNow()
        quotaExecutor = null
```

4. Remove `private const val QUOTA_EXECUTOR_CONTEXT = "profiling-quota"` from the companion object (line 410), and drop the `java.util.concurrent.ExecutorService` import if nothing else in the file uses it.

Keep `quotaChecker.reset()` and `quotaChecker = NoOpQuotaChecker()` in `onStop` — `reset()` now cancels the in-flight call, which is what replaces the old `shutdownNow()`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :features:dd-sdk-android-profiling:testDebugUnitTest`

Expected: PASS. `ProfilingFeatureTest` may also reference `quotaExecutor` or assert `shutdownNow()` on it — if it does, delete those assertions and add one asserting `reset()` is called on stop.

- [ ] **Step 6: Lint and commit**

```bash
./gradlew :features:dd-sdk-android-profiling:ktlintFormat :features:dd-sdk-android-profiling:detekt
git add features/dd-sdk-android-profiling/
git commit -m "RUM-16893: Make profiling quota check async and drop its dedicated thread"
```

---

## Task 4: Named, on-demand scheduler for the process-wide profiler

**Files:**
- Modify: `features/dd-sdk-android-profiling/src/main/java/com/datadog/android/profiling/Profiling.kt:143-151`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: no signature change. `PerfettoProfiler`'s `scheduledExecutorService` constructor argument keeps its `ScheduledExecutorService` type.

**Why not route this through `sdkCore`** (verified during planning — do not "fix" this by calling `sdkCore.createScheduledExecutorService`):

1. `initializeProfiler()` is also reached from `Profiling.start()` via `DdProfilingContentProvider`, which runs at app startup **before** `Datadog.initialize()`. There is no SDK instance to ask.
2. The profiler is a process-wide singleton guarded by `isProfilerInitialized` and outlives any single SDK instance. A core-scoped executor would be shut down when that core is released, silently killing the profiler's scheduler.

`DatadogThreadFactory` is also Kotlin-`internal` to the core module, so it cannot be reused here (unlike `submitSafe`, which is public). Hence a local thread factory. This accepts the loss of core's `afterExecute` logging and backpressure — unavoidable for a pre-SDK, process-wide component — in exchange for a stable thread name and on-demand behavior.

- [ ] **Step 1: Write the failing test**

Create `features/dd-sdk-android-profiling/src/test/kotlin/com/datadog/android/profiling/ProfilingSchedulerTest.kt`:

```kotlin
/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal class ProfilingSchedulerTest {

    @Test
    fun `M create an on demand named scheduler W createProfilingScheduler()`() {
        // When
        val testedExecutor = createProfilingScheduler()

        // Then
        check(testedExecutor is ThreadPoolExecutor)
        assertThat(testedExecutor.allowsCoreThreadTimeOut()).isTrue()
        assertThat(testedExecutor.getKeepAliveTime(TimeUnit.MILLISECONDS))
            .isEqualTo(PROFILING_SCHEDULER_KEEP_ALIVE_MS)
        assertThat(testedExecutor.poolSize).isZero()
        testedExecutor.shutdownNow()
    }

    @Test
    fun `M name the thread W createProfilingScheduler() {task submitted}`() {
        // Given
        val testedExecutor = createProfilingScheduler()

        // When
        val threadName = testedExecutor.submit<String> { Thread.currentThread().name }
            .get(5, TimeUnit.SECONDS)

        // Then
        assertThat(threadName).isEqualTo(PROFILING_SCHEDULER_THREAD_NAME)
        testedExecutor.shutdownNow()
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :features:dd-sdk-android-profiling:testDebugUnitTest --tests "*ProfilingSchedulerTest*"`

Expected: FAIL — compilation error, `unresolved reference: createProfilingScheduler`.

- [ ] **Step 3: Write the minimal implementation**

Create `features/dd-sdk-android-profiling/src/main/java/com/datadog/android/profiling/ProfilingScheduler.kt`:

```kotlin
/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling

import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

internal const val PROFILING_SCHEDULER_THREAD_NAME = "datadog-profiling-scheduler"
internal val PROFILING_SCHEDULER_KEEP_ALIVE_MS = TimeUnit.SECONDS.toMillis(5)

/**
 * Scheduler for the process-wide profiler singleton.
 *
 * This cannot borrow an executor from the SDK core: the profiler is initialized from
 * [DdProfilingContentProvider] before `Datadog.initialize()` runs, and it outlives any single SDK
 * instance, so a core-scoped executor would be shut down underneath it. It therefore builds its
 * own pool, with a core thread that is reclaimed once idle so the profiler holds no thread between
 * profiling windows.
 */
internal fun createProfilingScheduler(): ScheduledExecutorService {
    @Suppress("UnsafeThirdPartyFunctionCall") // both arguments are safe
    val executor = ScheduledThreadPoolExecutor(
        1,
        ThreadFactory { runnable ->
            @Suppress("UnsafeThirdPartyFunctionCall") // both arguments are safe
            Thread(runnable, PROFILING_SCHEDULER_THREAD_NAME).apply {
                priority = Thread.NORM_PRIORITY
                isDaemon = false
            }
        }
    )
    // allowCoreThreadTimeOut rejects a non-positive keep-alive, and ScheduledThreadPoolExecutor
    // defaults it to 0, so the keep-alive must be set first. A pending task keeps the worker
    // alive regardless: ThreadPoolExecutor#getTask() will not let the last worker exit while the
    // queue is non-empty, including a delayed task that is not yet due.
    @Suppress("UnsafeThirdPartyFunctionCall") // keep-alive is a positive constant
    executor.setKeepAliveTime(PROFILING_SCHEDULER_KEEP_ALIVE_MS, TimeUnit.MILLISECONDS)
    @Suppress("UnsafeThirdPartyFunctionCall") // keep-alive was just set to a positive value
    executor.allowCoreThreadTimeOut(true)
    return executor
}
```

Then in `Profiling.kt`, change `initializeProfiler()`:

```kotlin
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun initializeProfiler() {
        if (!isProfilerInitialized.getAndSet(true)) {
            profiler = PerfettoProfiler(
                timeProvider = MutableTimeProvider.create(DefaultTimeProvider()),
                scheduledExecutorService = createProfilingScheduler()
            )
        }
    }
```

Remove the now-unused `java.util.concurrent.Executors` import from `Profiling.kt`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :features:dd-sdk-android-profiling:testDebugUnitTest`

Expected: PASS.

- [ ] **Step 5: Lint, verify API surface, commit**

```bash
./gradlew :features:dd-sdk-android-profiling:ktlintFormat :features:dd-sdk-android-profiling:detekt
./gradlew :features:dd-sdk-android-profiling:checkApiSurfaceChanges :features:dd-sdk-android-profiling:apiCheck
git add features/dd-sdk-android-profiling/
git commit -m "RUM-16893: Make the profiler scheduler named and on-demand"
```

Expected: API surface clean — everything added is `internal`.

---

## Task 5: Whole-repo verification

**Files:** none modified unless a check fails.

**Interfaces:**
- Consumes: all of Tasks 1-4.
- Produces: evidence that the acceptance criteria are met.

- [ ] **Step 1: Full unit test suite**

Run: `./gradlew unitTestDebug`

Expected: PASS. The modules most likely to surface a break are RUM, Flags, Trace and Session Replay — all consumers of the two factories whose pool policy changed in Task 2. A failure there most likely means a test asserted on `poolSize` or on thread liveness; fix the test, not the production behavior, unless the failure shows real task loss.

- [ ] **Step 2: Static analysis across all modules**

```bash
./gradlew detekt
./gradlew ktlintCheckAll || ./gradlew ktlintFormatAll
```

Expected: clean.

- [ ] **Step 3: Confirm the no-public-API-change claim repo-wide**

Run: `./gradlew checkApiSurfaceChangesAll`

Expected: PASS with no diff anywhere. **This is the plan's headline claim** — if any `api/apiSurface` or `api/<module>.api` file would change, something leaked out of an `internal` declaration. Fix the visibility rather than regenerating the surface files.

- [ ] **Step 4: Verify the thread reduction on a device**

Build and run the sample app, then count SDK threads while idle:

```bash
./gradlew :sample:kotlin:assembleUs1Debug
# install and launch, exercise a few screens so view tracking has fired at least once,
# then leave the app idle for >5s (longer than the keep-alive) and count:
adb shell ps -T $(adb shell pidof -s com.datadog.android.sample) | grep datadog
```

Expected: `datadog-rum-activity-tracking-thread-*`, `datadog-rum-fragmentx-lifecycle-thread-*` and `datadog-profiling-quota-thread-*` are **absent** while idle. `datadog-upload-thread-1`, `datadog-storage-thread-1` and `datadog-context-thread-1` are **present** — they are deliberately excluded. Record the before/after counts in the PR description.

Note `datadog-rum-activity-tracking-thread-N` reappearing with an incremented `N` after a view stop is expected and correct — `DatadogThreadFactory` increments per spawn.

- [ ] **Step 5: Push and open the PR**

```bash
git push -u origin yi.lu/rum-16893-idle-thread-reduction
```

Open the PR against `develop` using `.github/PULL_REQUEST_TEMPLATE.md`, titled `RUM-16893: Reduce idle/sparse-usage threads`. In the description, cover each acceptance criterion from the ticket, include the before/after thread counts from Step 4, and state explicitly that `createOnDemandExecutorService` was **not** added and `uploadExecutorService` was **not** reused — with the reasons from the spec, since the ticket proposed both.

---

## Deferred (not in this plan)

Recorded so a reviewer does not read these as oversights:

- **Consolidating the three RUM tracking schedulers** into one `rum-view-tracking` scheduler with proper shutdown. Task 2 already reclaims their threads when idle; consolidating also fixes the fact that they are never shut down. This was the Layer 2 explicitly deferred during brainstorming.
- **A shared scheduler for genuinely periodic work** (`rum-vital`, flags evaluation flush, `client-side-stats-aggregator`). These keep a pinned thread by design and see no change from this plan.
- **`rum-anr-detection`, `dd-trace-monitor`, `dd-trace-processor`** — parked-by-design workers, out of scope per the ticket.
- **Excluding hot feature-created executors** (`rum-pipeline`, Session Replay `snapshot`/`drawables`) from the timeout, should measurement show thread churn. They are busy enough not to churn in practice; revisit only with evidence.
