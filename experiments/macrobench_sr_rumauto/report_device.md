## Parameters

|              |Baseline     |Candidate      |
|--------------|-------------|---------------|
|<b>variant</b>|no_regression|with_regression|


<details><summary>See matching parameters</summary>

|                |Baseline                                                          |Candidate                                                         |
|----------------|------------------------------------------------------------------|------------------------------------------------------------------|
|<b>className</b>|com.datadog.benchmark.macrobenchmark.SessionReplayRumAutoBenchmark|com.datadog.benchmark.macrobenchmark.SessionReplayRumAutoBenchmark|



</details>

## Summary

Found 1 performance improvements and 1 performance regressions! Performance is the same for 29 metrics, 11 unstable metrics.

|scenario                                                       |Δ mean execution_time                                        |Δ mean iterations|Δ mean rss                                                    |
|---------------------------------------------------------------|-------------------------------------------------------------|-----------------|--------------------------------------------------------------|
|scenario:frameTimingWithSessionReplay:SnapshotProducerAverageMs|<b>better</b><br>[-4.439ms; -3.605ms] or [-23.762%; -19.295%]|                 |                                                              |
|scenario:frameTimingWithSessionReplay:memoryRssAnonMaxKb       |                                                             |                 |<b>worse</b><br>[+42.646MB; +52.236MB] or [+38.243%; +46.843%]|


<details><summary>See unchanged results</summary>

|scenario                                                    |Δ mean execution_time                                                |Δ mean iterations                                      |Δ mean rss                                                       |
|------------------------------------------------------------|---------------------------------------------------------------------|-------------------------------------------------------|-----------------------------------------------------------------|
|scenario:frameTimingWithSessionReplay:SnapshotProducerSumMs |<b>unstable</b><br>[-401.426ms; -148.296ms] or [-26.640%; -9.841%]   |                                                       |                                                                 |
|scenario:frameTimingWithSessionReplay:frameCount            |                                                                     |<b>unstable</b><br>[-332; -204] or [-64.257%; -39.475%]|                                                                 |
|scenario:frameTimingWithSessionReplay:memoryHeapSizeMaxKb   |                                                                     |                                                       |<b>unstable</b><br>[+49.908MB; +58.252MB] or [+80.756%; +94.258%]|
|scenario:frameTimingWithSessionReplay:memoryRssFileMaxKb    |                                                                     |                                                       |<b>same</b>                                                      |
|scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P50|<b>unstable</b><br>[+12.198ms; +14.864ms] or [+228.316%; +278.226%]  |                                                       |                                                                 |
|scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P90|<b>unstable</b><br>[+12.795ms; +15.634ms] or [+63.671%; +77.796%]    |                                                       |                                                                 |
|scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P95|<b>unstable</b><br>[+13.473ms; +17.898ms] or [+54.064%; +71.818%]    |                                                       |                                                                 |
|scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P99|<b>unstable</b><br>[+19.547ms; +30.281ms] or [+53.761%; +83.283%]    |                                                       |                                                                 |
|scenario:frameTimingWithSessionReplay:frameOverrunMs:P50    |<b>unstable</b><br>[+15.566ms; +19.466ms] or [-2580.497%; -3227.049%]|                                                       |                                                                 |
|scenario:frameTimingWithSessionReplay:frameOverrunMs:P90    |<b>unstable</b><br>[+19.409ms; +21.589ms] or [+129.955%; +144.555%]  |                                                       |                                                                 |
|scenario:frameTimingWithSessionReplay:frameOverrunMs:P95    |<b>unstable</b><br>[+20.245ms; +27.594ms] or [+102.158%; +139.242%]  |                                                       |                                                                 |
|scenario:frameTimingWithSessionReplay:frameOverrunMs:P99    |<b>unstable</b><br>[+22.108ms; +38.473ms] or [+67.293%; +117.105%]   |                                                       |                                                                 |



</details>

