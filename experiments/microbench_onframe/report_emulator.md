## Parameters

|              |Baseline     |Candidate      |
|--------------|-------------|---------------|
|<b>variant</b>|no_regression|with_regression|


<details><summary>See matching parameters</summary>

|                |Baseline                                                              |Candidate                                                             |
|----------------|----------------------------------------------------------------------|----------------------------------------------------------------------|
|<b>className</b>|com.datadog.android.rum.internal.vitals.FrameStatesAggregatorBenchmark|com.datadog.android.rum.internal.vitals.FrameStatesAggregatorBenchmark|



</details>

## Summary

Found 0 performance improvements and 2 performance regressions! Performance is the same for 2 metrics, 0 unstable metrics.

|scenario                                 |Δ mean allocations                        |Δ mean execution_time                                           |
|-----------------------------------------|------------------------------------------|----------------------------------------------------------------|
|scenario:EMULATOR_onFrame:timeNs         |                                          |<b>worse</b><br>[+11.311ns; +11.596ns] or [+194.129%; +199.010%]|
|scenario:EMULATOR_onFrame:allocationCount|<b>worse</b><br>[+1; +1] or [+inf%; +inf%]|                                                                |


<details><summary>See unchanged results</summary>


</details>

