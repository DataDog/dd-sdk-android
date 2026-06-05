# UpdateFeatureContext Microbenchmark (Emulator)

## What

Same benchmark as `microbench_update_feature_context/` but run on an **emulator** instead of the Samsung Galaxy S23. Exercises the real `DatadogCore.updateFeatureContext()` with full SDK initialization.

The regression toggle is `useSlowMapCopy` in `DatadogCore.kt`.

- **Device**: Medium_Phone_API_36 (AVD), arm64 on macOS Apple Silicon, CPU not locked
- **Metrics**: timeNs (execution time per call), allocationCount

## Results

**1 regression detected (allocationCount), 1 unstable (timeNs).**

| Metric | Baseline | Regression | Absolute CI | Relative CI | Verdict |
|--------|----------|------------|-------------|-------------|---------|
| timeNs | ~103 ns | ~4,058 ns | [+3.945us; +4.019us] | [+3,842%; +3,914%] | unstable |
| allocationCount | 3.0 | 187.0 | [+184; +184] | [+6,133%] | **REGRESSION** |

## Comparison with Samsung

| Metric | Samsung | Emulator |
|--------|---------|----------|
| Baseline timeNs | ~166 ns | ~103 ns |
| Regression timeNs | ~6,830 ns | ~4,058 ns |
| Slowdown factor | ~41x | ~40x |
| allocationCount delta | +184 | +184 |
| bp-analyzer verdict (timeNs) | unstable | unstable |
| bp-analyzer verdict (allocations) | regression | regression |

The emulator was actually *faster* in absolute terms (103 ns vs 166 ns baseline) but the proportional regression was nearly identical (~40x on both). Both platforms agreed on the allocation count regression.

## Reliability Issues

- **Same timeNs "unstable" issue as Samsung**: bp-analyzer's statistical model is conservative with sub-microsecond distributions. The raw data has zero overlap between baseline and regression.
- **CPU not locked on emulator**: unlike the Samsung (cpuLocked: true), the emulator cannot lock CPU frequency, which could add variance in more marginal regressions.
- **Faster absolute times may be misleading**: the emulator's lower absolute numbers likely reflect different CPU architecture characteristics (Apple Silicon host vs Snapdragon 8 Gen 2), not necessarily a performance advantage.

## Conclusion

For pure CPU microbenchmarks like `updateFeatureContext`, the emulator produces results comparable to a real device. Both platforms detected the same regression with the same statistical profile. The emulator is a viable alternative for microbenchmark-level validation when a real device is unavailable.
