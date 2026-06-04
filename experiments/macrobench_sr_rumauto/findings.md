# SessionReplayRumAutoBenchmark: Emulator vs Real Device Findings

## Experiment Setup

- **Benchmark**: `SessionReplayRumAutoBenchmark.frameTimingWithSessionReplay`
- **Regression toggle**: `useSlowReflection` in `LayoutNodeUtils.kt` — forces `Class.getMethods()` scan instead of cached `Class.getMethod()` lookup on every frame for every layout node processed by Session Replay
- **Iterations**: 3 per run
- **UI**: Enhanced `CharacterItemView` — Card with rounded image, StatusBadge, location icons, FlowRow of info chips, episode progress bar, divider (many more composable nodes per list item)

### Devices

| Device | Type | Details |
|---|---|---|
| Medium_Phone_API_36 (AVD) | Emulator | arm64 on macOS Apple Silicon, Hypervisor.framework |
| Samsung Galaxy S23 (SM-S911B) | Real device | Snapdragon 8 Gen 2, Adreno 740, Android 16 |

## Results

### Emulator

bp-analyzer: **2 improvements, 0 regressions**

| Metric | Δ Confidence Interval | Verdict |
|---|---|---|
| SnapshotProducerAverageMs | [-13.2%; -6.3%] | better |
| frameOverrunMs:P50 | [+13.9ms; +14.6ms] (from positive to negative overrun) | better |
| frameDurationCpuMs:P50 | [+14.1ms; +14.9ms] or [+465%; +491%] | unstable |
| frameDurationCpuMs:P90 | [-1.2ms; +9.5ms] | unstable (straddles zero) |
| frameDurationCpuMs:P99 | [-7.6ms; +12.6ms] | unstable (spans 20ms range) |
| frameOverrunMs:P90 | [-0.1ms; +10.6ms] | unstable (straddles zero) |
| frameCount | [-14; +21] | unstable (straddles zero) |
| memoryRssAnonMaxKb | [+0.06MB; +15.0MB] | unstable |
| memoryHeapSizeMaxKb | [+1.4MB; +25.2MB] | unstable |

The emulator **could not reliably detect the regression**. Confidence intervals span zero for most frame timing metrics.

### Samsung Galaxy S23

bp-analyzer: **1 improvement, 1 regression**

| Metric | Δ Confidence Interval | Verdict |
|---|---|---|
| SnapshotProducerAverageMs | [-23.8%; -19.3%] | better |
| memoryRssAnonMaxKb | [+42.6MB; +52.2MB] or [+38.2%; +46.8%] | **worse** |
| frameDurationCpuMs:P50 | [+12.2ms; +14.9ms] or [+228%; +278%] | unstable (but consistently positive) |
| frameDurationCpuMs:P90 | [+12.8ms; +15.6ms] or [+64%; +78%] | unstable (but consistently positive) |
| frameDurationCpuMs:P99 | [+19.5ms; +30.3ms] or [+54%; +83%] | unstable (but consistently positive) |
| frameOverrunMs:P90 | [+19.4ms; +21.6ms] or [+130%; +145%] | unstable (but consistently positive) |
| frameCount | [-332; -204] or [-64%; -39%] | unstable (but consistently negative = fewer frames) |
| memoryHeapSizeMaxKb | [+49.9MB; +58.3MB] or [+81%; +94%] | unstable (but consistently positive) |
| memoryRssFileMaxKb | same | same |

The device showed **clear, directionally consistent degradation** across all frame timing and memory metrics — they were only marked "unstable" because 3 iterations isn't enough for statistical significance, not because the direction was ambiguous.

### Why memoryRssAnonMaxKb regressed but memoryRssFileMaxKb didn't

These metrics measure disjoint portions of RSS (`RssAnon + RssFile ≈ total RSS`, from `/proc/<pid>/status`):

- **RssAnon** (anonymous pages): heap allocations, JIT code, stacks — no file backing. `Class.getMethods()` allocates fresh `Method[]` arrays on every call, creating a burst of short-lived heap objects that spike peak heap usage before GC can reclaim them.
- **RssFile** (file-backed pages): memory-mapped `.so` libraries, DEX/OAT files, resources. The same classes/methods are loaded regardless of which lookup path is used — no new files are mapped.

### Why SnapshotProducerAverageMs shows "better" (counter-intuitive)

This likely reflects **fewer frames being processed** in the regression case. With slower reflection, the app produces fewer frames overall (frameCount dropped 39-64% on device), so each individual snapshot has less contention and appears faster on average. The **total** snapshot time (`SnapshotProducerSumMs`) is a better indicator and showed -10% to -27% on device (marked unstable).

## Why Emulator Fails at Regression Detection

### The fundamental issue: noise floor exceeds signal

The `useSlowReflection` regression adds ~15-20ms of extra work per frame (visible as the ~15ms shift in device frameDurationCpuMs P50). On the device, the baseline noise floor is ~1-2ms, so the signal-to-noise ratio is high. On the emulator, the noise floor is ~20-40ms (visible in the wide confidence intervals), burying the signal.

### Root causes of emulator frame timing noise

1. **VSync is a software timer**: On a real device, VSync comes from the display panel's hardware refresh circuit (sub-microsecond precision). On the emulator, it's a software timer in the emulator process, delivered to the Android guest via virtual interrupt — timing depends on host thread scheduling.

2. **vCPUs are host threads**: On macOS with Hypervisor.framework, each emulator vCPU is a POSIX thread competing with Spotlight, WindowServer, the IDE, and other processes for CPU time. macOS provides **no CPU pinning** (unlike Linux `isolcpus`/`taskset`).

3. **GPU path adds translation layers**: Guest OpenGL/Vulkan calls are translated via MoltenVK to Metal, then composited by macOS WindowServer. Each layer adds independent jitter. The Galaxy S23's Adreno 740 has a dedicated hardware rendering pipeline with its own clock domain.

4. **VM exits on timer interrupts**: Every Android timer interrupt requires: exit guest → trap to macOS kernel → notify emulator user-space process → re-enter guest. Each VM exit costs 2-20μs depending on cache state, and these add up across the many timer interrupts per frame.

### Google's official position

The Jetpack Benchmark library throws a **runtime error** (`EMULATOR`) by default when run on an emulator. The `suppressErrors = "EMULATOR"` flag in our benchmark config is required to override this. Google's documentation states:

> "We discourage running the benchmarks on an emulator, as they don't produce performance numbers representative of the end-user experience."

> "You're basically measuring your host machine performance — if it's under heavy load, your benchmarks will appear slower and vice versa."

## Recommendations

### For reliable regression detection

Use **real devices**. Our Galaxy S23 detected clear degradation with just 3 iterations. Increasing to 5-10 iterations would convert the "unstable" metrics into statistically significant detections.

### If emulator is the only option

| Approach | Why |
|---|---|
| Increase iterations to 10-15 | Confidence intervals narrow proportionally to sqrt(n) |
| Use `TraceSectionMetric` instead of `FrameTimingMetric` | Measures SDK-internal CPU time directly, bypasses the display pipeline |
| Use `MemoryUsageMetric` | Less affected by virtualization noise than frame timing |
| Use microbenchmarks for hot-path validation | No rendering pipeline involvement |
| Use API 29-30 arm64 AOSP image | Most stable emulator configuration on Apple Silicon |
| Cold boot always, disable animations, fixed GPU mode | Reduces variance but doesn't eliminate it |

### Detection capability (approximate)

| Regression Magnitude | Device (3 iter) | Emulator (3 iter) | Emulator (15 iter) |
|---|---|---|---|
| >100% frame time | Detected | Maybe | Likely |
| 50-100% | Detected | Unlikely | Maybe |
| 20-50% | Likely | No | Unlikely |
| <20% | Needs more iter | No | No |
