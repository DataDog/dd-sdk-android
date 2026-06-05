# SessionReplay Macrobenchmark (CI Emulator) — BLOCKED

## What

Intended to measure the performance of `SessionReplayRumAutoBenchmark.frameTimingWithSessionReplay` from `benchmarks:macro`, which measures frame timing with and without Session Replay enabled.

The **regression toggle** was `useSlowReflection=true` in `LayoutNodeUtils.kt`, which forces the slow `Class.getMethods()` scan path instead of the cached `Class.getMethod()` lookup — reproducing the RUM-15813 regression.

- **Device**: Android emulator (API 36, arm64-v8a, `sdk_gphone64_arm64`)
- **Host**: macOS Sonoma, Apple Silicon (CI runner: `macos:sonoma`)

## Results

**BLOCKED — Both macro jobs failed on both branches (2 attempts each)**

The test `SessionReplayRumAutoBenchmark.frameTimingWithSessionReplay` crashes with:
```
java.lang.IndexOutOfBoundsException: Index -1 out of bounds for length 10
at jdk.internal.util.Preconditions.outOfBounds(Preconditions.java:64)
```

This is a deterministic bug in the macrobenchmark test code (not a flaky issue), affecting both the baseline and regression branches identically. No benchmark artifacts were produced.

### CI Job Links

- `benchmark/without-regression`: jobs [1746275129](https://gitlab.ddbuild.io/DataDog/dd-sdk-android/-/jobs/1746275129) (failed), [1746319755](https://gitlab.ddbuild.io/DataDog/dd-sdk-android/-/jobs/1746319755) (retry, failed)
- `benchmark/with-regression`: jobs [1746276622](https://gitlab.ddbuild.io/DataDog/dd-sdk-android/-/jobs/1746276622) (failed), [1746319761](https://gitlab.ddbuild.io/DataDog/dd-sdk-android/-/jobs/1746319761) (retry, failed)

## Next Steps

1. Fix the `IndexOutOfBoundsException` in `SessionReplayRumAutoBenchmark`
2. Re-run macro benchmark comparison once the test is stable
