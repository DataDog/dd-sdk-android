## Parameters

|             |Baseline          |Candidate      |
|-------------|------------------|---------------|
|<b>branch</b>|without-regression|with-regression|


<details><summary>See matching parameters</summary>

|                |Baseline                                                              |Candidate                                                             |
|----------------|----------------------------------------------------------------------|----------------------------------------------------------------------|
|<b>className</b>|com.datadog.android.rum.internal.vitals.FrameStatesAggregatorBenchmark|com.datadog.android.rum.internal.vitals.FrameStatesAggregatorBenchmark|



</details>

## Summary

Found 0 performance improvements and 2 performance regressions! Performance is the same for 2 metrics, 0 unstable metrics.

|scenario                                 |Δ mean allocations                        |Δ mean execution_time                                           |
|-----------------------------------------|------------------------------------------|----------------------------------------------------------------|
|scenario:EMULATOR_onFrame:timeNs         |                                          |<b>worse</b><br>[+13.156ns; +13.483ns] or [+119.968%; +122.944%]|
|scenario:EMULATOR_onFrame:allocationCount|<b>worse</b><br>[+0; +0] or [+inf%; +inf%]|                                                                |


<details><summary>See unchanged results</summary>


</details>

