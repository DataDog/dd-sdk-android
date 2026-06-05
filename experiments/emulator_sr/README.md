# Emulator Session Replay Macrobenchmark (Local Emulator, Simple UI)

## What

`SessionReplayRumAutoBenchmark.frameTimingWithSessionReplay` run on a **local emulator** (arm64 AVD on macOS Apple Silicon) with the original simple `CharacterItemView` UI. The regression toggle is `useSlowReflection` in `LayoutNodeUtils.kt`.

- **Device**: Medium_Phone_API_36 (AVD), arm64, Hypervisor.framework
- **Iterations**: 10 per run
- **Metrics**: FrameTimingMetric only (frameDurationCpuMs, frameOverrunMs, frameCount)

## Results

| Metric | Verdict | Absolute CI | Relative CI |
|--------|---------|-------------|-------------|
| frameCount | no difference | [-11; +6] | [-3.0%; +1.8%] |
| frameDurationCpuMs:P50 | no difference | [-109us; +377us] | [-3.2%; +11.0%] |
| frameDurationCpuMs:P90 | no difference | [-3.7ms; +1.5ms] | [-61.4%; +24.9%] |
| frameDurationCpuMs:P95 | no difference | [-3.5ms; +1.5ms] | [-48.6%; +21.1%] |
| frameDurationCpuMs:P99 | no difference | [-5.9ms; +1.1ms] | [-27.4%; +5.1%] |
| frameOverrunMs:P50 | significant | [-147us; +450us] | ~3% |
| frameOverrunMs:P90 | no difference | [-3.6ms; +1.4ms] | straddles zero |
| frameOverrunMs:P95 | no difference | [-3.6ms; +1.4ms] | straddles zero |
| frameOverrunMs:P99 | no difference | [-9.8ms; +0.6ms] | straddles zero |

## Reliability Issues

- **Failed to detect the regression**: all confidence intervals straddle zero. The emulator noise completely masks the signal.
- **Baseline had outliers**: baseline frameDurationCpuMs:P99 ranged 16.7-30.3ms (a wide range from host interference), inflating variance.
- **Software VSync, no CPU pinning, GPU translation layers**: same fundamental emulator noise issues as other emulator experiments.

## Conclusion

The local emulator with 10 iterations and FrameTimingMetric-only could not detect the `useSlowReflection` regression at all. All CIs straddle zero.
