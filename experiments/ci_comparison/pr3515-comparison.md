## Parameters

|                     |Baseline |Candidate|
|---------------------|---------|---------|
|<b>ci_pipeline_id</b>|117359250|-        |
|<b>git_branch</b>    |develop  |pr3515   |
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

|scenario                                                    |Δ mean allocations|Δ mean execution_time                                                  |Δ mean iterations|Δ mean rss |
|------------------------------------------------------------|------------------|-----------------------------------------------------------------------|-----------------|-----------|
|scenario:frameTimingWithSessionReplay:frameCount            |                  |                                                                       |<b>same</b>      |           |
|scenario:frameTimingWithSessionReplay:memoryHeapSizeMaxKb   |                  |                                                                       |                 |<b>same</b>|
|scenario:frameTimingWithSessionReplay:memoryRssAnonMaxKb    |                  |                                                                       |                 |<b>same</b>|
|scenario:frameTimingWithSessionReplay:memoryRssFileMaxKb    |                  |                                                                       |                 |<b>same</b>|
|scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P50|                  |<b>unstable</b><br>[-2055.275µs; +882.412µs] or [-25.561%; +10.975%]   |                 |           |
|scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P90|                  |<b>unstable</b><br>[-1589.123µs; +2999.067µs] or [-6.994%; +13.200%]   |                 |           |
|scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P95|                  |<b>unstable</b><br>[-1627.195µs; +3550.018µs] or [-6.139%; +13.393%]   |                 |           |
|scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P99|                  |<b>unstable</b><br>[-1.711ms; +3.997ms] or [-4.634%; +10.825%]         |                 |           |
|scenario:frameTimingWithSessionReplay:frameOverrunMs:P50    |                  |<b>unstable</b><br>[-3035.234µs; +3251.970µs] or [+613.158%; -656.942%]|                 |           |
|scenario:frameTimingWithSessionReplay:frameOverrunMs:P90    |                  |<b>unstable</b><br>[-1.536ms; +4.299ms] or [-13.703%; +38.352%]        |                 |           |
|scenario:frameTimingWithSessionReplay:frameOverrunMs:P95    |                  |<b>unstable</b><br>[-2502.627µs; +3831.887µs] or [-15.127%; +23.161%]  |                 |           |
|scenario:frameTimingWithSessionReplay:frameOverrunMs:P99    |                  |<b>unstable</b><br>[-11.326ms; +4.942ms] or [-33.040%; +14.415%]       |                 |           |
|scenario:EMULATOR_updateFeatureContext:timeNs               |                  |<b>unstable</b><br>[+106.302ns; +143.736ns] or [+45.174%; +61.082%]    |                 |           |
|scenario:EMULATOR_updateFeatureContext:allocationCount      |<b>same</b>       |                                                                       |                 |           |
|scenario:EMULATOR_onFrame:timeNs                            |                  |<b>unsure</b><br>[+0.083ns; +0.131ns] or [+0.761%; +1.198%]            |                 |           |
|scenario:EMULATOR_onFrame:allocationCount                   |<b>same</b>       |                                                                       |                 |           |



</details>

