# RUM-16893 — Reduce idle/sparse-usage threads

Status: approved design, pending implementation
Ticket: https://datadoghq.atlassian.net/browse/RUM-16893

## Problem

The SDK creates ~19–24 threads per instance with all features enabled. Several are
dedicated threads that run short, sparse tasks and idle the rest of the process
lifetime. They multiply per SDK instance.

## Audit (re-verified against `develop` @ e306291f4)

The ticket's audit is stale. Current state — 15 `createXExecutorService` call sites
plus 2 raw `Executors.new…`:

### Scheduled (8)

| Site | Task shape | Idle? |
| --- | --- | --- |
| `rum-vital` (`RumFeature.kt:630`) | periodic | pinned — justified |
| `rum-fragmentx-lifecycle` (`AndroidXFragmentLifecycleCallbacks.kt:37`) | one-shot 200 ms | idle ~100%, never shut down |
| `rum-fragment-lifecycle` (`OreoFragmentLifecycleCallbacks.kt:43`) | one-shot 200 ms | idle ~100%, never shut down |
| `rum-activity-tracking` (`ActivityViewTrackingStrategy.kt:43`) | one-shot 200 ms | idle ~100%, never shut down |
| `client-side-stats-aggregator` (`ClientStatsFeature.kt:60`) | periodic | pinned — not in ticket |
| flags init-timeout (`FlagsFeature.kt:85`) | one-shot, self-shutdown | already correct — reference model |
| flags evaluations (`EvaluationsFeature.kt:78`) | periodic flush | pinned |
| `PerfettoProfiler` (`Profiling.kt:148`) | raw `Executors.newSingleThreadScheduledExecutor()` | bypasses core: unnamed, no logging, no backpressure |

### Single-thread (7)

`rum-pipeline` (busy), `rum-anr-detection` (parked by design — out of scope),
**profiling quota** (`ProfilingFeature.kt:119`, one-shot per session, still a blocking
`execute()` at `ProfilingQuotaChecker.kt:69`), `FlagsClient` network
(`FlagsClient.kt:398`), Session Replay `embeddedContent` / `snapshot` / `drawables`.

## Decision

Reclaim idle threads by enabling **core-thread timeout** on the two executor
implementations the factories already return. No new public API, no call-site
migration.

### Why not the ticket's proposed options

- **Option 1 (`createOnDemandExecutorService`, corePoolSize=0 + `SynchronousQueue`)**
  cannot serve most candidates: a `SynchronousQueue` pool cannot accept `schedule()`
  at all, since `ScheduledThreadPoolExecutor` mandates its own `DelayedWorkQueue`.
  4 of 6 named candidates need scheduled execution. It would also add surface to the
  public `FeatureSdkCore` interface (`apiSurface` + `.api` dump + `NoOpInternalSdkCore`
  + `StubSDKCore`) for a strict subset of the benefit.
- **Option 2 (reuse `uploadExecutorService`)** — rejected, see ticket comment:
  one SDK-wide thread, a 5 s blocking call would stall all uploads, and the existing
  `ProfilingFeature.onStop()` `shutdownNow()` would kill core's executor SDK-wide.

### Why core-thread timeout works

`BackPressureExecutorService` is `ThreadPoolExecutor(1, 1, keepAlive = 5s, …)`, but
`allowCoreThreadTimeOut` is called **nowhere in the repo** — that keep-alive is dead
code and the core thread never times out.

The safety of enabling it rests on `ThreadPoolExecutor.getTask()`, whose worker-exit
guard is `(wc > 1 || workQueue.isEmpty())`. The last worker therefore **cannot** exit
while the queue holds a pending task — including a delayed task that is not yet due.
This yields exactly the split we want, for free:

- one-shot consumers → thread reclaimed ~5 s after the task runs
- periodic consumers (`rum-vital`, flags flush, client stats) → thread retained,
  **zero** behavior change
- parked workers (`rum-anr-detection`) → unaffected; they block inside a running task,
  not on the queue

## Design

### Layer 0 — remove the need for two executors

**L0.1 — Async quota check.** Convert `ProfilingQuotaChecker.performCheck` from
`callFactory.newCall(request).execute()` to `enqueue()` with a callback. OkHttp's
dispatcher is already a shared, zero-idle, on-demand pool, so the check needs no
executor of its own. Then delete from `ProfilingFeature`: the `quotaExecutor` field,
its creation at line 119, and the `shutdownNow()` at line 167.

The 5 s budget is unchanged: it comes from `callTimeout()` on the OkHttp client built in
`ProfilingFeature.onInitialize`, which applies identically to `enqueue()`. Result
mapping must stay byte-for-byte identical to today's (`API_ERROR` on `IOException` and
on unexpected exceptions). Note `QuotaResult.FAIL_OPEN` is declared but referenced
nowhere in main source — it is not a live code path and must not be introduced as one.

**L0.2 — Make the profiler scheduler named and on-demand.** The raw
`Executors.newSingleThreadScheduledExecutor()` in `Profiling.initializeProfiler()`
cannot be routed through `sdkCore`, for two independent reasons found during planning:

1. `initializeProfiler()` is also reached from `Profiling.start()` via
   `DdProfilingContentProvider`, which runs at app startup **before**
   `Datadog.initialize()` — there is no SDK instance to ask.
2. The profiler is a process-wide singleton (`isProfilerInitialized`) that outlives any
   single SDK instance. A core-scoped executor would be shut down when that core is
   released, silently killing the profiler's scheduler.

Additionally `DatadogThreadFactory` is Kotlin-`internal` to the core module, so the
profiling module cannot reuse it (unlike `submitSafe`, which is public).

So instead: construct a self-contained `ScheduledThreadPoolExecutor(1, …)` in the
profiling module with a local thread factory naming the thread
`datadog-profiling-scheduler`, plus `setKeepAliveTime` + `allowCoreThreadTimeOut(true)`
so the thread is reclaimed between profiling windows. This accepts the loss of core's
`afterExecute` logging and backpressure — unavoidable for a pre-SDK, process-wide
component — in exchange for a stable name and on-demand behavior.

### Layer 1 — idle-thread timeout for feature-created executors

Apply the timeout at the `CoreFeature` seam, not in the factories:

```kotlin
// CoreFeature.kt
fun createExecutorService(executorContext: String): ExecutorService =
    executorServiceFactory.create(...).also { it.enableIdleThreadTimeout() }

fun createScheduledExecutorService(executorContext: String): ScheduledExecutorService =
    scheduledExecutorServiceFactory.create(...).also { it.enableIdleThreadTimeout() }
```

`enableIdleThreadTimeout()` is a new internal extension in
`core/internal/thread/`. It applies only to our own concrete types —
`BackPressureExecutorService` and `LoggingScheduledThreadPoolExecutor` — and no-ops
otherwise. This matters: `FlushableExecutorService.Factory` is **public** and
user-supplied, and we must not mutate a third-party executor's pool policy.
`ScheduledExecutorServiceFactory` is internal.

For the scheduled case, `setKeepAliveTime(5, SECONDS)` must be called **before**
`allowCoreThreadTimeOut(true)`: `ScheduledThreadPoolExecutor` defaults keep-alive to
0, and `allowCoreThreadTimeOut(true)` throws `IllegalArgumentException` when
keep-alive is not > 0. This mirrors the existing type-check pattern at
`FlagsFeature.kt:87`.

`setupExecutors()` is deliberately **not** changed, so the core-owned hot executors
(`upload`, `storage`, `context`) keep permanently-alive, stably-named threads.

### Scope boundary

| Decision | Rationale |
| --- | --- |
| Core-owned `upload`/`storage`/`context` excluded | Hot paths; stable thread names feed `BatchMetricsDispatcher`'s `THREAD_NAME` telemetry |
| `rum-anr-detection`, trace's `CommonTaskExecutor` / `AgentTaskScheduler` untouched | Parked-by-design workers with their own thread factories |
| 3 RUM tracking schedulers not consolidated | Deferred Layer 2; the timeout already reclaims them |
| `trace-otel` thread-name context keying untouched | Runs on caller threads, not SDK executors |
| No `createOnDemandExecutorService` | See "Why not" above |

## Expected outcome

| Change | Threads |
| --- | --- |
| `dd-profiling-quota` deleted outright | −1 |
| 3 RUM tracking schedulers reclaimed when idle | −3 |
| `FlagsClient` network reclaimed when idle | −1 |
| `PerfettoProfiler` scheduler reclaimed between windows | −1 while idle |

≈5 fewer live threads at steady state, per SDK instance.

## Testing

- `enableIdleThreadTimeout` sets `allowsCoreThreadTimeOut()` on factory-created
  executors and **not** on core-owned ones.
- **No task stranding:** schedule a delayed task, let the pool idle past keep-alive,
  assert the task still runs. Locks in the `getTask()` guarantee this design rests on.
- A periodic task retains its worker across more than one keep-alive window.
- The extension no-ops on a non-Datadog `ExecutorService` from a custom factory.
- Quota check: async success, denial, timeout, and fail-open paths.
- `./gradlew checkApiSurfaceChangesAll` stays clean — the proof that no public API
  changed.

## Risks

1. **Thread churn on feature hot paths.** `rum-pipeline` and SR `snapshot`/`drawables`
   get the timeout too. They are busy enough not to churn in practice; if measurement
   shows otherwise, exclude them by context.
2. **Thread-name index growth.** `DatadogThreadFactory` increments per spawn, so a
   respawned sparse worker appears as `-thread-2`, `-thread-3`, … Cosmetic, and a
   useful churn signal. No telemetry cardinality impact, because the only
   thread-name-reporting path runs on the excluded core-owned executors.
3. **ANR/crash trace readability** — a sparse worker may be absent from a trace where
   it previously always appeared.
