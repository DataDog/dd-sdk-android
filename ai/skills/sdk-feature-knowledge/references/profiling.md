# Profiling — external references

Official Android docs on `ProfilingManager`, the platform API the Datadog SDK's Profiling
feature (`features/dd-sdk-android-profiling`) is built on — for developing, debugging, and
reviewing that module. The prompts below pair these links with SDK tasks. For how the SDK
uses the API, read the code.

## Links

- [Overview](https://developer.android.com/topic/performance/tracing/profiling-manager/overview) — what `ProfilingManager` is, supported profile types.
- [App-driven profiling](https://developer.android.com/topic/performance/tracing/profiling-manager/how-to-capture) — requesting stack-sampling and other profiles from app code.
- [Trigger-based profiling](https://developer.android.com/topic/performance/tracing/profiling-manager/trigger-based-capture) — system triggers such as `TRIGGER_TYPE_ANR`.
- [Retrieve and upload profiles](https://developer.android.com/topic/performance/tracing/profiling-manager/upload-worker) — receiving results and uploading them with WorkManager.
- [Rate limiting and collection behavior](https://developer.android.com/topic/performance/tracing/profiling-manager/will-my-profile-always-be-collected) — why a requested profile may not be collected.
- [Debug commands for local profiling](https://developer.android.com/topic/performance/tracing/profiling-manager/debug-mode) — `adb` commands to lift rate limits while testing.
- [Case study: Debug an ANR](https://developer.android.com/topic/performance/tracing/profiling-manager/debugging-anr-example) — reading an ANR profile end to end.
- [Analyze profiles with AI](https://developer.android.com/topic/performance/tracing/profiling-manager/analyze-profiles-with-ai) — using agents to analyze captured traces.
- [`ProfilingManager` API reference](https://developer.android.com/reference/android/os/ProfilingManager).

## Example prompts

- **Developing:** "Read the *Trigger-based profiling* and *Retrieve and upload profiles* pages above, then check how trigger registration and result handling in `features/dd-sdk-android-profiling` align with the documented API contract and lifecycle. Flag deviations."
- **Debugging:** "Read the *Rate limiting and collection behavior* and *Debug commands for local profiling* pages above, then explain why a profile the SDK requested was not collected (for example from `sample/kotlin`), and give the `adb` commands to lift rate limits for testing."
- **Reviewing:** "Read the *Overview*, *Trigger-based profiling*, and *Rate limiting and collection behavior* pages above and review this PR to the profiling module for misuse of `ProfilingManager`: callback registration timing, rate-limit assumptions, ANR trigger semantics."
- **Analysis:** "Read the *Case study: Debug an ANR* and *Analyze profiles with AI* pages above and analyze the Perfetto trace the SDK captured at </path/to/trace> for the ANR root cause."
