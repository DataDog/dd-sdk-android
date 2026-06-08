## Parameters

|                     |Baseline |Candidate|
|---------------------|---------|---------|
|<b>ci_pipeline_id</b>|117359250|-        |
|<b>git_branch</b>    |develop  |pr3513   |
|<b>git_commit_sha</b>|979d9fb2 |unknown  |
|<b>version</b>       |baseline |candidate|


<details><summary>See matching parameters</summary>

|                |Baseline                                                          |Candidate                                                         |
|----------------|------------------------------------------------------------------|------------------------------------------------------------------|
|<b>className</b>|com.datadog.benchmark.macrobenchmark.SessionReplayRumAutoBenchmark|com.datadog.benchmark.macrobenchmark.SessionReplayRumAutoBenchmark|
|<b>cpu_model</b>|x86_64                                                            |x86_64                                                            |



</details>

## Summary

Found 0 performance improvements and 0 performance regressions! Performance is the same for 55 metrics, 9 unstable metrics.

<details><summary>See unchanged results</summary>

|scenario                                                    |Δ mean allocations                             |Δ mean execution_time                                                  |Δ mean iterations                               |Δ mean rss                                                     |
|------------------------------------------------------------|-----------------------------------------------|-----------------------------------------------------------------------|------------------------------------------------|---------------------------------------------------------------|
|scenario:frameTimingWithSessionReplay:frameCount            |                                               |                                                                       |<b>unsure</b><br>[-22; +0] or [-7.283%; -0.270%]|                                                               |
|scenario:frameTimingWithSessionReplay:memoryHeapSizeMaxKb   |                                               |                                                                       |                                                |<b>same</b>                                                    |
|scenario:frameTimingWithSessionReplay:memoryRssAnonMaxKb    |                                               |                                                                       |                                                |<b>same</b>                                                    |
|scenario:frameTimingWithSessionReplay:memoryRssFileMaxKb    |                                               |                                                                       |                                                |<b>unsure</b><br>[+194.301KB; +533.699KB] or [+0.174%; +0.478%]|
|scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P50|                                               |<b>unstable</b><br>[-1832.043µs; +3275.697µs] or [-22.785%; +40.740%]  |                                                |                                                               |
|scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P90|                                               |<b>unstable</b><br>[-1627.978µs; +1258.852µs] or [-7.165%; +5.541%]    |                                                |                                                               |
|scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P95|                                               |<b>unstable</b><br>[-1919.517µs; +1066.701µs] or [-7.241%; +4.024%]    |                                                |                                                               |
|scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P99|                                               |<b>unstable</b><br>[-3.766ms; +0.519ms] or [-10.199%; +1.404%]         |                                                |                                                               |
|scenario:frameTimingWithSessionReplay:frameOverrunMs:P50    |                                               |<b>unstable</b><br>[-3450.368µs; +3422.390µs] or [+697.021%; -691.369%]|                                                |                                                               |
|scenario:frameTimingWithSessionReplay:frameOverrunMs:P90    |                                               |<b>unstable</b><br>[-2.448ms; +0.405ms] or [-21.838%; +3.616%]         |                                                |                                                               |
|scenario:frameTimingWithSessionReplay:frameOverrunMs:P95    |                                               |<b>unstable</b><br>[-2704.968µs; +1604.615µs] or [-16.350%; +9.699%]   |                                                |                                                               |
|scenario:frameTimingWithSessionReplay:frameOverrunMs:P99    |                                               |<b>unstable</b><br>[-14.006ms; +2.433ms] or [-40.859%; +7.096%]        |                                                |                                                               |
|scenario:EMULATOR_updateFeatureContext:timeNs               |                                               |<b>unstable</b><br>[+52.958ns; +503.778ns] or [+22.505%; +214.087%]    |                                                |                                                               |
|scenario:EMULATOR_updateFeatureContext:allocationCount      |<b>unsure</b><br>[+0; +0] or [-0.002%; -0.001%]|                                                                       |                                                |                                                               |
|scenario:EMULATOR_onFrame:timeNs                            |                                               |<b>same</b>                                                            |                                                |                                                               |
|scenario:EMULATOR_onFrame:allocationCount                   |<b>same</b>                                    |                                                                       |                                                |                                                               |



</details>

