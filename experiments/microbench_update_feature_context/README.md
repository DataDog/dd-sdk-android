# UpdateFeatureContext Microbenchmark (Samsung Galaxy S23)

## What

Jetpack Microbenchmark for `DatadogCore.updateFeatureContext()` — the real SDK method that updates a feature's context map, acquires a lock, copies maps defensively, and notifies all registered `FeatureContextUpdateReceiver`s.

This benchmark uses **full SDK initialization** via `Datadog.initialize()`, registers a minimal no-op "rum" feature, populates its context with ~20 keys, and adds 5 no-op receivers. It calls the actual `DatadogCore.updateFeatureContext()` method, exercising the real lock acquisition, map copy, and receiver notification code path.

The regression toggle is `useSlowMapCopy` in `DatadogCore.kt`, which replaces optimized map construction with an artificially slow defensive copy pattern using intermediate collections (simulating the kind of overhead from excessive defensive copying reported in PANA-5027).

- **Device**: Samsung Galaxy S23 (SM-S911B), Snapdragon 8 Gen 2, Android 16, CPU locked
- **Metrics**: timeNs (execution time per call), allocationCount

## Results

**1 regression detected (allocationCount), 1 unstable (timeNs).**

| Metric | Baseline | Regression | Absolute CI | Relative CI | Verdict |
|--------|----------|------------|-------------|-------------|---------|
| timeNs | ~166 ns | ~6,830 ns | [+6.607us; +6.685us] | [+3,874%; +3,920%] | unstable |
| allocationCount | 3.0 | 187.0 | [+183; +184] | [+6,133%] | **REGRESSION** |

## Reliability Issues

- **timeNs marked "unstable" despite 41x difference**: bp-analyzer's statistical model flagged this as unstable rather than a confirmed regression. This is likely because the sub-microsecond measurement values produce distributions that don't satisfy bp-analyzer's normality/sample-size requirements (it warns about n<105). The raw data is unambiguous — baseline range [150, 267] ns vs regression range [6,526, 7,125] ns with zero overlap.
- **allocationCount is a perfect signal**: 3 vs 187 allocations with near-zero variance, giving a clean statistically significant result.
- **Warmup iterations differ dramatically**: baseline did 31.5M warmup iterations vs regression did 33K. This is expected — the JIT compiler needs more warmup iterations when the per-iteration cost is lower. Both reached steady state.

## Conclusion

The allocation count metric is the most reliable indicator for map-copy overhead regressions. The time metric, while showing an obvious 41x difference in raw data, was conservatively classified by bp-analyzer due to distribution characteristics. For SDK-internal benchmarks at sub-microsecond scale, the allocation count should be the primary decision metric.
