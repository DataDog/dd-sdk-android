## Parameters

|                     |Baseline |Candidate|
|---------------------|---------|---------|
|<b>ci_pipeline_id</b>|117359250|-        |
|<b>git_branch</b>    |develop  |pr3514   |
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

|scenario                                                    |Δ mean allocations|Δ mean execution_time                                                  |Δ mean iterations|Δ mean rss                                                 |
|------------------------------------------------------------|------------------|-----------------------------------------------------------------------|-----------------|-----------------------------------------------------------|
|scenario:frameTimingWithSessionReplay:frameCount            |                  |                                                                       |<b>same</b>      |                                                           |
|scenario:frameTimingWithSessionReplay:memoryHeapSizeMaxKb   |                  |                                                                       |                 |<b>same</b>                                                |
|scenario:frameTimingWithSessionReplay:memoryRssAnonMaxKb    |                  |                                                                       |                 |<b>unsure</b><br>[+0.257MB; +3.869MB] or [+0.336%; +5.063%]|
|scenario:frameTimingWithSessionReplay:memoryRssFileMaxKb    |                  |                                                                       |                 |<b>same</b>                                                |
|scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P50|                  |<b>unstable</b><br>[-2125.548µs; +3112.760µs] or [-26.435%; +38.713%]  |                 |                                                           |
|scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P90|                  |<b>unstable</b><br>[-2.302ms; +0.197ms] or [-10.130%; +0.867%]         |                 |                                                           |
|scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P95|                  |<b>unstable</b><br>[-2643.508µs; +1171.068µs] or [-9.973%; +4.418%]    |                 |                                                           |
|scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P99|                  |<b>unstable</b><br>[-3140.612µs; +1902.736µs] or [-8.506%; +5.153%]    |                 |                                                           |
|scenario:frameTimingWithSessionReplay:frameOverrunMs:P50    |                  |<b>unstable</b><br>[-4478.558µs; +2851.894µs] or [+904.729%; -576.121%]|                 |                                                           |
|scenario:frameTimingWithSessionReplay:frameOverrunMs:P90    |                  |<b>unstable</b><br>[-2180.690µs; +1048.200µs] or [-19.454%; +9.351%]   |                 |                                                           |
|scenario:frameTimingWithSessionReplay:frameOverrunMs:P95    |                  |<b>unstable</b><br>[-2797.524µs; +3160.870µs] or [-16.909%; +19.105%]  |                 |                                                           |
|scenario:frameTimingWithSessionReplay:frameOverrunMs:P99    |                  |<b>unstable</b><br>[-7.788ms; +10.962ms] or [-22.720%; +31.979%]       |                 |                                                           |
|scenario:EMULATOR_updateFeatureContext:timeNs               |                  |<b>unstable</b><br>[+17.200ns; +49.147ns] or [+7.310%; +20.885%]       |                 |                                                           |
|scenario:EMULATOR_updateFeatureContext:allocationCount      |<b>same</b>       |                                                                       |                 |                                                           |
|scenario:EMULATOR_onFrame:timeNs                            |                  |<b>unsure</b><br>[+0.060ns; +0.222ns] or [+0.548%; +2.027%]            |                 |                                                           |
|scenario:EMULATOR_onFrame:allocationCount                   |<b>same</b>       |                                                                       |                 |                                                           |



</details>

