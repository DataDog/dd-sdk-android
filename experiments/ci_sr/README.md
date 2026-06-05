# CI Session Replay Macrobenchmark (Emulator, Simple UI)

## What

`SessionReplayRumAutoBenchmark.frameTimingWithSessionReplay` run on a **CI emulator** (arm64 AVD on macOS Apple Silicon) with the original simple `CharacterItemView` UI (before complexity enhancements). The regression toggle is `useSlowReflection` in `LayoutNodeUtils.kt`, which forces `Class.getMethods()` instead of cached `Class.getMethod()` on every frame for every composable node processed by Session Replay.

- **Device**: Medium_Phone_API_36 (AVD), arm64, Hypervisor.framework
- **Iterations**: 10 per run
- **Metrics**: FrameTimingMetric only (frameDurationCpuMs, frameOverrunMs, frameCount)

## Results

**0 regressions detected, 0 improvements, 8 unstable metrics.**

| Metric | Verdict | Absolute CI | Relative CI |
|--------|---------|-------------|-------------|
| frameCount | same | -- | -- |
| frameDurationCpuMs:P50 | unstable | [-5.0ms; +2.5ms] | [-60.6%; +30.0%] |
| frameDurationCpuMs:P90 | unstable | [-4.1ms; +3.2ms] | [-16.5%; +12.9%] |
| frameDurationCpuMs:P95 | unstable | [-4.8ms; +4.3ms] | [-15.9%; +14.4%] |
| frameDurationCpuMs:P99 | unstable | [-2.8ms; +9.9ms] | [-7.0%; +24.9%] |
| frameOverrunMs:P50 | unstable | [-5.1ms; +3.8ms] | straddles zero |
| frameOverrunMs:P90 | unstable | [-4.3ms; +5.6ms] | [-30.5%; +40.1%] |
| frameOverrunMs:P95 | unstable | [-3.4ms; +7.2ms] | [-16.9%; +35.6%] |
| frameOverrunMs:P99 | unstable | [-4.4ms; +11.3ms] | [-13.1%; +33.8%] |

## Reliability Issues

- **Completely failed to detect the regression**: all frame timing confidence intervals straddle zero.
- **Noise floor exceeds signal**: emulator frame timing jitter is 20-40ms, burying the ~15ms regression.
- **No memory/trace metrics**: only FrameTimingMetric was used, so the memory signal (which was detectable on real devices) was not captured.
- **VSync is software-emulated**: on the emulator, VSync is a software timer in the host process, not a hardware signal.
- **vCPUs compete with host processes**: macOS provides no CPU pinning (unlike Linux `isolcpus`), so emulator threads compete with Spotlight, WindowServer, IDE, etc.

## Conclusion

Emulator macrobenchmarks with FrameTimingMetric alone are not reliable for detecting SDK-level regressions of this magnitude (~15ms per frame). Use a real device or supplement with TraceSectionMetric/MemoryUsageMetric.
