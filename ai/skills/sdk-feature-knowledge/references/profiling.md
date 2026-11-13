# Profiling — external references

Official Android docs on `ProfilingManager`, which the Profiling feature builds on.
For how the SDK uses these, read the code in `features/dd-sdk-android-profiling`.

## Links

- [Overview](https://developer.android.com/topic/performance/tracing/profiling-manager/overview) — what `ProfilingManager` is, supported profile types, and [AI integration](https://developer.android.com/topic/performance/tracing/profiling-manager/overview#integrate-with-ai).
- [App-driven profiling](https://developer.android.com/topic/performance/tracing/profiling-manager/how-to-capture) — requesting stack-sampling and other profiles from app code.
- [Trigger-based profiling](https://developer.android.com/topic/performance/tracing/profiling-manager/trigger-based-capture) — system triggers such as `TRIGGER_TYPE_ANR`.
- [Retrieve and upload profiles](https://developer.android.com/topic/performance/tracing/profiling-manager/upload-worker) — receiving results and uploading them with WorkManager.
- [Rate limiting and collection behavior](https://developer.android.com/topic/performance/tracing/profiling-manager/will-my-profile-always-be-collected) — why a requested profile may not be collected.
- [Debug commands for local profiling](https://developer.android.com/topic/performance/tracing/profiling-manager/debug-mode) — `adb` commands to lift rate limits while testing.
- [Case study: Debug an ANR](https://developer.android.com/topic/performance/tracing/profiling-manager/debugging-anr-example) — reading an ANR profile end to end.
- [Analyze profiles with AI](https://developer.android.com/topic/performance/tracing/profiling-manager/analyze-profiles-with-ai) — using agents to analyze captured traces.
- [`ProfilingManager` API reference](https://developer.android.com/reference/android/os/ProfilingManager).

## Example prompts

- "Read https://developer.android.com/topic/performance/tracing/profiling-manager/trigger-based-capture and https://developer.android.com/topic/performance/tracing/profiling-manager/upload-worker and integrate ProfilingManager ANR trigger collection (`TRIGGER_TYPE_ANR`) into my app at </path/to/codebase>, including registering the result listener after app startup completes and scheduling a WorkManager upload job when a profile is received."
- "Read https://developer.android.com/topic/performance/tracing/profiling-manager/will-my-profile-always-be-collected and https://developer.android.com/topic/performance/tracing/profiling-manager/debug-mode, then explain why a launch profile from `sample/kotlin` may not be collected and give the `adb` commands to lift rate limiting."
- "Read https://developer.android.com/topic/performance/tracing/profiling-manager/debugging-anr-example and https://developer.android.com/topic/performance/tracing/profiling-manager/analyze-profiles-with-ai and analyze the Perfetto trace at </path/to/trace> for the ANR root cause."
