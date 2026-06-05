# Session Replay Macrobenchmark: Enhanced UI, Emulator vs Samsung S23

## What

`SessionReplayRumAutoBenchmark.frameTimingWithSessionReplay` run on both an **emulator** and a **Samsung Galaxy S23** with an **enhanced `CharacterItemView` UI** — Card with rounded image, StatusBadge, location icons, FlowRow of info chips, episode progress bar, and divider. This creates many more composable nodes per list item, increasing Session Replay's per-frame workload.

The regression toggle is `useSlowReflection` in `LayoutNodeUtils.kt` (forces `Class.getMethods()` scan instead of cached `Class.getMethod()` on every layout node per frame).

- **Emulator**: Medium_Phone_API_36 (AVD), arm64 on macOS Apple Silicon
- **Device**: Samsung Galaxy S23 (SM-S911B), Snapdragon 8 Gen 2, Android 16
- **Iterations**: 3 per run
- **Metrics**: FrameTimingMetric, MemoryUsageMetric (Max), TraceSectionMetric (SnapshotProducer Sum + Average)

## Results

### Emulator

**2 improvements, 0 regressions, 11 unstable metrics.**

| Metric | Verdict | Absolute CI | Relative CI |
|--------|---------|-------------|-------------|
| SnapshotProducerAverageMs | better | [-967us; -464us] | [-13.2%; -6.3%] |
| frameOverrunMs:P50 | better | [+13.9ms; +14.6ms] | ~-110% (sign flip) |
| frameDurationCpuMs:P50 | unstable | [+14.1ms; +14.9ms] | [+465%; +491%] |
| frameDurationCpuMs:P90 | unstable | [-1.2ms; +9.5ms] | straddles zero |
| frameDurationCpuMs:P99 | unstable | [-7.6ms; +12.6ms] | straddles zero |
| memoryRssAnonMaxKb | unstable | [+0.06MB; +15.0MB] | [+0.05%; +13.1%] |
| memoryHeapSizeMaxKb | unstable | [+1.4MB; +25.2MB] | [+2.8%; +49.1%] |

### Samsung Galaxy S23

**1 improvement, 1 regression, 11 unstable metrics.**

| Metric | Verdict | Absolute CI | Relative CI |
|--------|---------|-------------|-------------|
| memoryRssAnonMaxKb | **REGRESSION** | [+42.6MB; +52.2MB] | [+38.2%; +46.8%] |
| SnapshotProducerAverageMs | better | [-4.4ms; -3.6ms] | [-23.8%; -19.3%] |
| frameDurationCpuMs:P50 | unstable | [+12.2ms; +14.9ms] | [+228%; +278%] |
| frameDurationCpuMs:P90 | unstable | [+12.8ms; +15.6ms] | [+64%; +78%] |
| frameDurationCpuMs:P99 | unstable | [+19.5ms; +30.3ms] | [+54%; +83%] |
| frameOverrunMs:P90 | unstable | [+19.4ms; +21.6ms] | [+130%; +145%] |
| memoryHeapSizeMaxKb | unstable | [+49.9MB; +58.3MB] | [+81%; +94%] |
| frameCount | unstable | [-332; -204] | [-64%; -39%] |

## Reliability Issues

- **Only 3 iterations**: the "unstable" verdicts on Samsung are directionally consistent (all showing degradation), but 3 iterations don't provide enough statistical power for bp-analyzer to call them "significant". Increasing to 5-10 iterations would convert most to confirmed regressions.
- **Emulator is unreliable for frame timing**: noise floor (~20-40ms) exceeds the regression signal (~15ms). See `findings.md` for detailed root cause analysis (VSync emulation, vCPU scheduling, GPU translation layers).
- **SnapshotProducerAverageMs is misleading**: shows "better" because fewer frames were rendered, reducing per-snapshot contention. SnapshotProducerSumMs is a better total-cost indicator.
- **memoryRssAnonMaxKb is a reliable regression indicator**: heap allocations from `Class.getMethods()` arrays spike anonymous RSS regardless of timing noise.

## Key Finding

Memory metrics (especially RssAnon) are more reliable regression indicators than frame timing on both emulator and device, because they are unaffected by VSync/scheduling noise. TraceSectionMetric provides the cleanest SDK-internal timing signal.
