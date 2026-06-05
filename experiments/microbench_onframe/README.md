# FrameStatesAggregator.onFrame Microbenchmark

## What

Jetpack Microbenchmark for `FrameStatesAggregator.onFrame()` — the method called on every frame to aggregate frame state information for RUM vitals. Run on both **emulator** and **Samsung Galaxy S23**.

The regression toggle is `useForEachIteration` in `FrameStatesAggregator.kt`, which forces iteration via `forEach` with lambda allocation instead of direct indexed access, causing GC pressure on the hot path.

- **Emulator**: Medium_Phone_API_36 (AVD), arm64
- **Device**: Samsung Galaxy S23 (SM-S911B), Android 16
- **Metrics**: timeNs (execution time per call), allocationCount

## Results

### Samsung Galaxy S23

**2 regressions detected, 0 unstable.**

| Metric | Baseline | Regression | Absolute CI | Relative CI | Verdict |
|--------|----------|------------|-------------|-------------|---------|
| timeNs | ~9.1 ns | ~29.3 ns | [+19.8ns; +20.4ns] | [+217.9%; +224.2%] | **worse** |
| allocationCount | 0 | 1 | [+0; +1] | [+inf%] | **worse** |

### Emulator

**2 regressions detected, 0 unstable.**

| Metric | Baseline | Regression | Absolute CI | Relative CI | Verdict |
|--------|----------|------------|-------------|-------------|---------|
| timeNs | ~5.8 ns | ~17.4 ns | [+11.3ns; +11.6ns] | [+194.1%; +199.0%] | **worse** |
| allocationCount | 0 | 1 | [+1; +1] | [+inf%] | **worse** |

## Reliability

- **Excellent**: both device and emulator cleanly detected the regression with high statistical confidence. Zero metrics were unstable.
- **Microbenchmarks bypass emulator noise**: because there is no rendering pipeline or VSync involvement, the emulator performs nearly as well as the real device for pure CPU microbenchmarks.
- **The regression is massive (~3x)**: a 200%+ increase in execution time is easy to detect regardless of platform noise.
- **Allocation count is a perfect signal**: went from 0 to 1, providing a binary, noise-free indicator.

## Conclusion

Microbenchmarks are ideal for validating hot-path regressions. Both emulator and real device produced clean, statistically significant results. This validates that the emulator is suitable for microbenchmark-level (non-rendering) regression detection.
