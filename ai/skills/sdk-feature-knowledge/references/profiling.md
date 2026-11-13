# Profiling (`features/dd-sdk-android-profiling`)

Paths below are relative to `features/dd-sdk-android-profiling/src/main/java/com/datadog/android/profiling/`.

## 1. Purpose and public surface

Collects Perfetto stack-sampling traces through Android `ProfilingManager` and uploads
them with RUM metadata so profiles can be linked to RUM sessions, long tasks, ANRs, and vitals.
Requires API 35 (`VANILLA_ICE_CREAM`); ANR-triggered profiles require API 36 (`BAKLAVA`).

Public API (see `api/apiSurface`):

- `Profiling.enable(configuration, sdkCore)` — registers `ProfilingFeature`. Only one SDK core
  can own profiling; a second call while the first core is active logs an error and is ignored.
- `ProfilingConfiguration.Builder`: `setApplicationLaunchSampleRate` (default 15),
  `setContinuousSampleRate` (default 15), `useCustomEndpoint`, `enableAnrTrigger` (default `true`).
- `DdProfilingContentProvider` — public because it is declared in the manifest.
- `ExperimentalProfilingApi` annotation.
- Generated models `model.ProfileEvent` and `model.RumMetadataEvent` are public in the API surface.

## 2. Architecture and data flow

Start reasons (`internal/ProfilingStartReason.kt`): `APPLICATION_LAUNCH`, `CONTINUOUS`, `ANR`,
`RUM_OPERATION`, `UNKNOWN`.

**Application launch**

1. `ProfilingFeature.onInitialize` stores the launch sample rate and an "enabled" flag in
   SharedPreferences (`internal/ProfilingStorage.kt`) for the *next* launch.
2. Next process start: `DdProfilingContentProvider.onCreate` (manifest `initOrder="1000"`) checks the
   flag, requires an eligible `ApplicationStartInfo` reason (launcher, start activity, recents),
   samples with `RateBasedSampler`, then calls `Profiling.start(APPLICATION_LAUNCH)`.
3. Launch profiling stops at the first RUM TTID vital (or `TTIDNotTracked`) unless the session is
   sampled for continuous profiling.
4. `tryWriteLaunchProfile` writes only after TTID **and** a quota decision for the launch's own RUM
   session; it drops the trace if that session is untracked, quota-denied, or ends first.

**Continuous**: `internal/ContinuousProfilingScheduler.kt` alternates ~60 s active windows and
~60 s cooldowns (±20 % jitter, random initial cooldown), waits for launch profiling to finish,
samples per RUM session with `DeterministicSampler`, and reacts to background
(`ProfilingLifecycleCallback`): an active window keeps running for a 10 s grace period before it
stops; a cooldown pauses immediately and resumes with its remaining duration on foreground. A window result is written with the RUM events buffered
during it (`PendingRumEventsBuffer`).

**ANR trigger**: `internal/trigger/ProfilingManagerTriggerRegistrar.kt` registers a `ProfilingManager`
ANR trigger. The result and the RUM ANR event are paired in `PendingTriggerProfileStorage`
(30 s expiry), then written via `ProfilingWriter.writeTriggerProfile` if quota allows.

**Recording**: `internal/perfetto/PerfettoProfiler.kt` builds a stack-sampling
`ProfilingRequest` with `StackSamplingRequestBuilder` (201 Hz launch, 101 Hz continuous, 5 MB buffer)
and reports results through `ProfilerCallback`. Launch and continuous traces without stack samples
are deleted and reported as failures (`PerfettoStackSampleDetector`), but only when the
`com.google.android.profiling` package version is known and below 373040100
(`internal/utils/ProfilingModuleVersion.kt`); unknown (0) or fixed versions skip the check.

**Quota**: `internal/quota/ProfilingQuotaChecker.kt` runs an HTTP quota check (5 s timeout).
`ProfilingFeature` requests it at session renewal only for tracked sessions that are sampled for
continuous profiling or own the launch profile; otherwise a trigger profile matched in the current
session requests it when no decision exists yet. Checks are deduplicated per current session
(same session id is a no-op, a new id cancels the in-flight check), and results for past sessions
are discarded.

**Write and upload**: `internal/ProfilingDataWriter.kt` builds a batch event with the
`perfetto.proto` and `rum-mobile-events.json` attachments; `internal/ProfilingRequestFactory.kt`
sends it as multipart (`event.json` + attachments). Storage uses `maxItemsPerBatch = 1`.

Remote configuration overrides the three sample-rate/ANR settings at `enable` time
(`internal/ProfilingConfigurationExt.kt`).

## 3. Threading

- `PerfettoProfiler` gets its own `Executors.newSingleThreadScheduledExecutor()`, created once in
  `Profiling.initializeProfiler`. The scheduler and `PendingTriggerProfileStorage` reuse it.
- Quota checks run on `sdkCore.createSingleThreadExecutorService("profiling-quota")`.
- `ProfilingFeature` guards session id, launch-session state, quota result, and queued trigger
  profiles with one lock, `quotaSessionLock`. Keep session renewal, quota propagation, and trigger
  queueing atomic under it.
- `Profiling.enable` is `synchronized` so concurrent calls can't register two features.

## 4. Core integration

- Receives `ProfilerEvent` (from `dd-sdk-android-internal`, sent by RUM) via `FeatureEventReceiver`:
  `RumVitalEvent`, `RumLongTaskEvent`, `RumAnrEvent`, `TTIDNotTracked`.
- Reads the RUM context through `FeatureContextUpdateReceiver.onContextUpdate` (session id,
  sample rate, tracked state).
- Writes to its feature context (`FeatureContextKeys`): `PROFILING_SAMPLE_RATE`,
  `PROFILING_APPLICATION_LAUNCH_SAMPLE_RATE`, `PROFILING_ANR_ENABLED`, `PROFILER_IS_RUNNING`,
  and `PROFILING_QUOTA_REASON` / `PROFILING_QUOTA_SESSION_ID` when quota is denied.
- RUM readers: `rum/internal/profiling/ProfilingFeatureContext.kt` reads `PROFILER_IS_RUNNING` and the
  quota reason/session pair; `telemetry/internal/TelemetryEventHandler.kt` reads the three
  configuration keys for configuration telemetry.
- Sends `ProfilingAnrDetectedEvent` to the RUM feature on ANR.

## 5. Generated models

Schemas in `src/main/json/profiling` are cloned from `rum-events-format` (`cloneProfilingSchema`);
change them upstream first. `generateProfilingModelsFromJson` generates `ProfileEvent` and
`RumMetadataEvent` into `com.datadog.android.profiling.model`.

## 6. Invariants and gotchas

- Launch sampling applies on the **next** launch; the content provider runs before `Datadog.initialize`.
- The launch profile is judged only by its own (first-seen) RUM session, not later ones.
- A trigger profile from a past session is dropped; never check quota for a past session (it
  cancels the current session's check).
- Continuous results with no buffered RUM events are not uploaded.
- Dropped profiles must delete their trace file (`fileDeleteSafe`).
- `onStop` must unregister callbacks, shut down the quota executor, and reset session state.

## 7. Tests

- Unit: `src/test/kotlin/com/datadog/android/profiling/` (`ProfilingFeatureTest`,
  `ContinuousProfilingSchedulerTest`, `PerfettoProfilerTest`, `QuotaCheckerTest`,
  `PendingTriggerProfilesTest`, …). Perfetto fixtures live in `src/test/resources/perfetto/`.
- Test fixtures: `src/testFixtures/.../fixtures/ProfilingFeatureTestHandle.kt`, `ProfilingStorageFixture.kt`.
- E2E: `reliability/single-fit/profiling`.
- Run: `./gradlew :features:dd-sdk-android-profiling:testDebugUnitTest`.

## 8. Cross-platform parity

Android-only implementation on `ProfilingManager`; no iOS parity was verified for this file.
Treat public API changes as needing an iOS parity check.

## 9. Do not

- Don't add a second profiler or support multiple SDK cores without redesigning `Profiling`'s
  shared state.
- Don't create new executors; reuse the profiler's scheduled executor.
- Don't edit `src/main/json/profiling` locally or the generated models.
- Don't call `ProfilingManager` APIs without the API-level guards (`BuildSdkVersionProvider`).
