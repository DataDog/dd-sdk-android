## Parameters

|                 |Baseline|Candidate |
|-----------------|--------|----------|
|<b>git_branch</b>|develop |regression|
|<b>version</b>   |baseline|candidate |


<details><summary>See matching parameters</summary>

|                     |Baseline                                                          |Candidate                                                         |
|---------------------|------------------------------------------------------------------|------------------------------------------------------------------|
|<b>className</b>     |com.datadog.benchmark.macrobenchmark.SessionReplayRumAutoBenchmark|com.datadog.benchmark.macrobenchmark.SessionReplayRumAutoBenchmark|
|<b>cpu_model</b>     |redfin                                                            |redfin                                                            |
|<b>git_commit_sha</b>|979d9fb2                                                          |979d9fb2                                                          |



</details>

## Summary

Found 1 performance improvements and 2 performance regressions! Performance is the same for 56 metrics, 13 unstable metrics.

|scenario                                     |Δ mean allocations                                      |Δ mean execution_time                                      |Δ mean iterations|Δ mean rss|
|---------------------------------------------|--------------------------------------------------------|-----------------------------------------------------------|-----------------|----------|
|scenario:listSort:timeNs                     |                                                        |<b>better</b><br>[-1.603µs; -0.464µs] or [-4.556%; -1.319%]|                 |          |
|scenario:updateFeatureContext:allocationCount|<b>worse</b><br>[+184; +184] or [+6133.314%; +6133.329%]|                                                           |                 |          |
|scenario:onFrame:allocationCount             |<b>worse</b><br>[+0; +1] or [+inf%; +inf%]              |                                                           |                 |          |


<details><summary>See unchanged results</summary>

|scenario                                                    |Δ mean allocations|Δ mean execution_time                                                    |Δ mean iterations                                      |Δ mean rss                                                           |
|------------------------------------------------------------|------------------|-------------------------------------------------------------------------|-------------------------------------------------------|---------------------------------------------------------------------|
|scenario:frameTimingWithSessionReplay:frameCount            |                  |                                                                         |<b>unstable</b><br>[-105; +140] or [-26.821%; +35.738%]|                                                                     |
|scenario:frameTimingWithSessionReplay:memoryHeapSizeMaxKb   |                  |                                                                         |                                                       |<b>unstable</b><br>[-4561.920KB; +4739.720KB] or [-16.656%; +17.306%]|
|scenario:frameTimingWithSessionReplay:memoryRssAnonMaxKb    |                  |                                                                         |                                                       |<b>unstable</b><br>[-5022.071KB; +5274.071KB] or [-5.980%; +6.280%]  |
|scenario:frameTimingWithSessionReplay:memoryRssFileMaxKb    |                  |                                                                         |                                                       |<b>same</b>                                                          |
|scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P50|                  |<b>unstable</b><br>[-7826.330µs; +9120.252µs] or [-100.161%; +116.720%]  |                                                       |                                                                     |
|scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P90|                  |<b>unstable</b><br>[-18.685ms; +21.481ms] or [-108.958%; +125.259%]      |                                                       |                                                                     |
|scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P95|                  |<b>unstable</b><br>[-18.675ms; +23.014ms] or [-98.072%; +120.855%]       |                                                       |                                                                     |
|scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P99|                  |<b>unstable</b><br>[-16.873ms; +24.401ms] or [-64.632%; +93.466%]        |                                                       |                                                                     |
|scenario:frameTimingWithSessionReplay:frameOverrunMs:P50    |                  |<b>unstable</b><br>[-30.076ms; +24.179ms] or [-14554.625%; +11701.149%]  |                                                       |                                                                     |
|scenario:frameTimingWithSessionReplay:frameOverrunMs:P90    |                  |<b>unstable</b><br>[-30193.307µs; +32179.283µs] or [-654.001%; +697.018%]|                                                       |                                                                     |
|scenario:frameTimingWithSessionReplay:frameOverrunMs:P95    |                  |<b>unstable</b><br>[-27.867ms; +34.145ms] or [-445.091%; +545.365%]      |                                                       |                                                                     |
|scenario:frameTimingWithSessionReplay:frameOverrunMs:P99    |                  |<b>unstable</b><br>[-26577.147µs; +26286.873µs] or [-100.826%; +99.725%] |                                                       |                                                                     |
|scenario:listSort:allocationCount                           |<b>same</b>       |                                                                         |                                                       |                                                                     |
|scenario:updateFeatureContext:timeNs                        |                  |<b>unstable</b><br>[+15.223µs; +15.689µs] or [+3407.852%; +3512.078%]    |                                                       |                                                                     |
|scenario:onFrame:timeNs                                     |                  |<b>unstable</b><br>[+37.826ns; +49.946ns] or [+126.362%; +166.851%]      |                                                       |                                                                     |



</details>

