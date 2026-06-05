# UpdateFeatureContext Microbenchmark (CI Emulator)

## What

Measures the performance of `UpdateFeatureContextBenchmark` from `dd-sdk-android-core`, which benchmarks the `updateFeatureContext` code path that copies and merges feature context maps.

The **regression toggle** (`useSlowMapCopy=true`) forces a slow `HashMap` copy implementation instead of the optimized path, introducing ~184 extra allocations and ~30x slower execution per iteration.

- **Device**: Android emulator (API 36, arm64-v8a, `sdk_gphone64_arm64`)
- **Host**: macOS Sonoma, Apple Silicon (CI runner: `macos:sonoma`)
- **Iterations**: 50 (execution_time), 5 (allocationCount)
- **Metrics**: `timeNs` (execution time per iteration), `allocationCount` (heap allocations per iteration)
- **Compilation mode**: `verify` (no AOT)

## Results

**1 regression detected, 0 improvements, 1 unstable metric**

| Metric | Baseline (mean ± σ) | Regression (mean ± σ) | Absolute Δ | Relative Δ | bp-analyzer Verdict |
|--------|---------------------|-----------------------|------------|------------|---------------------|
| timeNs | 194.62 ± 1.76 ns | 5851.98 ± 196.18 ns | +5657.37 ns | +2908% | **unstable** |
| allocationCount | 3.00 ± 0.00 | 187.00 ± 0.00 | +184.00 | +6133% | **worse** (regression) |

### Notes on Verdicts

- **allocationCount**: Clear regression detected with absolute CI [+184, +184]. The allocations metric is essentially deterministic (zero variance in both runs), making detection trivial.
- **timeNs**: bp-analyzer classified this as "unstable" despite a ~30x regression. This is because the metric values in CBMF are per-iteration averages from a nanosecond-precision timer, and the variance between individual iterations (~1%) interacts with bp-analyzer's statistical thresholds. The absolute CI [+5.603 µs, +5.712 µs] still clearly shows the regression.

## Reliability Issues

- The `timeNs` metric being marked "unstable" despite a massive regression suggests bp-analyzer's confidence interval thresholds may be overly conservative for this data shape (many runs, low absolute values, small relative noise).
- allocationCount is an ideal metric for regression detection: zero noise, purely deterministic.
- The 50-iteration count provides solid statistical power for timeNs, but per-iteration values are very small (~195 ns), so even tiny absolute noise can widen relative CIs.

## Conclusion

The CI emulator **reliably detects the allocation regression** (+6133%, from 3 to 187 allocations). The timing regression (+2908%) is visible in the raw data but bp-analyzer classified it as "unstable" rather than "worse" — the regression is still unambiguous from the confidence intervals. For this benchmark, allocationCount is the more reliable indicator on CI emulators.
