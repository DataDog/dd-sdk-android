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

|scenario                        |Δ mean allocations                        |Δ mean execution_time                                           |
|--------------------------------|------------------------------------------|----------------------------------------------------------------|
|scenario:onFrame:timeNs         |                                          |<b>worse</b><br>[+19.788ns; +20.356ns] or [+217.921%; +224.173%]|
|scenario:onFrame:allocationCount|<b>worse</b><br>[+0; +1] or [+inf%; +inf%]|                                                                |


<details><summary>See unchanged results</summary>


</details>

