## Parameters

|             |Baseline          |Candidate      |
|-------------|------------------|---------------|
|<b>branch</b>|without-regression|with-regression|


<details><summary>See matching parameters</summary>

|                |Baseline                                                       |Candidate                                                      |
|----------------|---------------------------------------------------------------|---------------------------------------------------------------|
|<b>className</b>|com.datadog.android.core.internal.UpdateFeatureContextBenchmark|com.datadog.android.core.internal.UpdateFeatureContextBenchmark|



</details>

## Summary

Found 0 performance improvements and 1 performance regressions! Performance is the same for 2 metrics, 1 unstable metrics.

|scenario                                              |Δ mean allocations                                      |Δ mean execution_time|
|------------------------------------------------------|--------------------------------------------------------|---------------------|
|scenario:EMULATOR_updateFeatureContext:allocationCount|<b>worse</b><br>[+184; +184] or [+6133.247%; +6133.259%]|                     |


<details><summary>See unchanged results</summary>

|scenario                                     |Δ mean allocations|Δ mean execution_time                                              |
|---------------------------------------------|------------------|-------------------------------------------------------------------|
|scenario:EMULATOR_updateFeatureContext:timeNs|                  |<b>unstable</b><br>[+5.603µs; +5.712µs] or [+2879.006%; +2934.889%]|



</details>

