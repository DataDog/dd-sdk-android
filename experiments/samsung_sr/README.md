# Samsung Galaxy S23 Session Replay Macrobenchmark (Simple UI)

## What

`SessionReplayRumAutoBenchmark.frameTimingWithSessionReplay` run on a **Samsung Galaxy S23 (SM-S911B)** with the original simple `CharacterItemView` UI. The regression toggle is `useSlowReflection` in `LayoutNodeUtils.kt`.

- **Device**: Samsung Galaxy S23 (SM-S911B), Snapdragon 8 Gen 2, Adreno 740, Android 16
- **Iterations**: 20 per run
- **Metrics**: FrameTimingMetric only (frameDurationCpuMs, frameOverrunMs, frameCount)

## Results

The real device detected the regression clearly across most metrics:

| Metric | Verdict | Absolute CI | Relative CI |
|--------|---------|-------------|-------------|
| frameCount | **better** (fewer frames) | [-383; -246] | [-63.1%; -40.5%] |
| frameDurationCpuMs:P50 | **worse** | [+10.4ms; +10.9ms] | [+324.6%; +342.0%] |
| frameDurationCpuMs:P90 | **worse** | [+9.0ms; +10.9ms] | [+51.6%; +62.8%] |
| frameDurationCpuMs:P95 | **worse** | [+8.5ms; +11.5ms] | [+40.9%; +55.6%] |
| frameDurationCpuMs:P99 | **worse** | [+5.1ms; +11.1ms] | [+15.4%; +33.9%] |
| frameOverrunMs:P50 | **worse** | [+3.7ms; +5.5ms] | significant |
| frameOverrunMs:P90 | **worse** | [+1.6ms; +4.1ms] | [+14.5%; +35.8%] |
| frameOverrunMs:P95 | no difference | [-0.2ms; +3.8ms] | straddles zero |
| frameOverrunMs:P99 | no difference | [-1.9ms; +10.0ms] | straddles zero |

## Reliability

- **Highly reliable**: all major frame timing metrics showed statistically significant differences with 20 iterations.
- **Tight confidence intervals**: baseline variance was low (e.g. frameDurationCpuMs:P50 range 2.7-3.7ms vs regression 13.5-14.0ms), giving excellent signal-to-noise ratio.
- **frameOverrunMs:P95/P99 were inconclusive**: higher percentiles showed no significant difference due to natural variance in tail latencies.
- **Note**: frameCount showing "better" is actually evidence of regression — the app produced fewer frames because each frame took longer.

## Conclusion

Real device macrobenchmarks reliably detect regressions of this magnitude (~10ms per frame) with 20 iterations. The Samsung S23 is an excellent benchmark device with stable, low-noise measurements.
