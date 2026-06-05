# Remote Session Replay Macrobenchmark (Sauce Labs)

## What

`SessionReplayRumAutoBenchmark.frameTimingWithSessionReplay` run on a **Samsung Galaxy S23 (SM-S911B) via Sauce Labs** real device cloud. The regression toggle is `useSlowReflection` in `LayoutNodeUtils.kt`.

- **Device**: Samsung Galaxy S23 (SM-S911B), Sauce Labs real device cloud
- **Iterations**: 10 per run
- **Metrics**: FrameTimingMetric only (frameDurationCpuMs, frameOverrunMs, frameCount)

## Results

| Metric | Verdict | Absolute CI | Relative CI |
|--------|---------|-------------|-------------|
| frameCount | significantly better | [-383; -246] | [-63.1%; -40.5%] |
| frameDurationCpuMs:P50 | **worse** | [+10.2ms; +11.3ms] | [+325.6%; +360.5%] |
| frameDurationCpuMs:P90 | **worse** | [+8.4ms; +11.6ms] | [+48.7%; +67.5%] |
| frameDurationCpuMs:P95 | **worse** | [+7.9ms; +13.1ms] | [+38.4%; +64.1%] |
| frameDurationCpuMs:P99 | **worse** | [+1.7ms; +13.0ms] | [+5.1%; +38.4%] |
| frameOverrunMs:P50 | **worse** | [+4.2ms; +5.8ms] | significant |
| frameOverrunMs:P90 | **worse** | [+1.4ms; +4.6ms] | [+12.6%; +41.6%] |
| frameOverrunMs:P95 | no difference | [-2.3ms; +5.0ms] | straddles zero |
| frameOverrunMs:P99 | no difference | [-5.9ms; +13.4ms] | straddles zero |

## Reliability Issues

- **P99 metrics inconclusive**: high variance in tail latencies prevented significance at the 99th percentile.
- **Sample size warning**: bp-analyzer flagged all metrics with "sample size is 10, which is lower than 105" — meaning the statistical conclusions may be imprecise.
- **Baseline had high variance in frameCount**: range 449-736 (SD=110), compared to regression range 282-309 (SD=8). This suggests some iterations had anomalous behavior.

## Conclusion

The remote Samsung S23 with 10 iterations detected regressions at P50-P95 for frame duration and P50-P90 for frame overrun. This validates that even with a remote device connection, a real device produces cleaner signals than any emulator configuration.
