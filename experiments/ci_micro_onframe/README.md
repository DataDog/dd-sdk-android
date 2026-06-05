# FrameStatesAggregator onFrame Microbenchmark (CI Emulator)

## What

Measures the performance of `FrameStatesAggregatorBenchmark` from `dd-sdk-android-rum`, which benchmarks the `onFrame` callback in the RUM vitals frame-states aggregator.

The **regression toggle** (`useForEachIteration=true`) forces the aggregator to use a `forEach` iteration pattern instead of the optimized indexed-loop path, introducing 1 extra allocation and ~121% slower execution per call.

- **Device**: Android emulator (API 36, arm64-v8a, `sdk_gphone64_arm64`)
- **Host**: macOS Sonoma, Apple Silicon (CI runner: `macos:sonoma`)
- **Iterations**: 50 (execution_time), 5 (allocationCount)
- **Metrics**: `timeNs` (execution time per iteration), `allocationCount` (heap allocations per iteration)
- **Compilation mode**: `verify` (no AOT)

## Results

**2 regressions detected, 0 improvements, 0 unstable**

| Metric | Baseline (mean ± σ) | Regression (mean ± σ) | Absolute Δ | Relative Δ | bp-analyzer Verdict |
|--------|---------------------|-----------------------|------------|------------|---------------------|
| timeNs | 10.97 ± 0.10 ns | 24.29 ± 0.58 ns | +13.32 ns | +121.4% | **worse** (regression) |
| allocationCount | 0.00 ± 0.00 | 1.00 ± 0.00 | +1.00 | +∞% | **worse** (regression) |

### Confidence Intervals (from bp-analyzer)

| Metric | Absolute CI | Relative CI |
|--------|-------------|-------------|
| timeNs | [+13.156 ns, +13.483 ns] | [+119.97%, +122.94%] |
| allocationCount | [+0, +0] → [+∞%, +∞%] | (baseline is 0, so relative is infinite) |

## Reliability Issues

- Both metrics cleanly detected as regressions with tight confidence intervals.
- The `timeNs` regression (+121%) has very narrow CIs (~3% band), indicating low noise for this benchmark on the CI emulator.
- The `allocationCount` metric is perfectly deterministic (0 → 1 allocation), making it trivial to detect.
- The absolute timing values are very small (~11 ns baseline), yet the 2.2x regression is still clearly detected — this benchmark has excellent signal-to-noise ratio.

## Conclusion

The CI emulator **reliably detects both regressions** in this benchmark. The ~121% timing regression and the +1 allocation regression are both detected with high confidence. The `onFrame` microbenchmark has excellent noise characteristics on CI emulators, making it suitable for automated regression detection even for moderate (~2x) performance changes.
