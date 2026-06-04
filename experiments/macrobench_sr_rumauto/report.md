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

Found 2 performance improvements and 0 performance regressions! Performance is the same for 29 metrics, 11 unstable metrics.

|scenario                                                       |Δ mean execution_time                                            |Δ mean iterations|Δ mean rss|
|---------------------------------------------------------------|-----------------------------------------------------------------|-----------------|----------|
|scenario:frameTimingWithSessionReplay:SnapshotProducerAverageMs|<b>better</b><br>[-966.882µs; -463.967µs] or [-13.201%; -6.335%] |                 |          |
|scenario:frameTimingWithSessionReplay:frameOverrunMs:P50       |<b>better</b><br>[+13.865ms; +14.621ms] or [-108.722%; -114.649%]|                 |          |


<details><summary>See unchanged results</summary>

|scenario                                                    |Δ mean execution_time                                               |Δ mean iterations                                  |Δ mean rss                                                     |
|------------------------------------------------------------|--------------------------------------------------------------------|---------------------------------------------------|---------------------------------------------------------------|
|scenario:frameTimingWithSessionReplay:SnapshotProducerSumMs |<b>unstable</b><br>[-80.880ms; -6.163ms] or [-12.889%; -0.982%]     |                                                   |                                                               |
|scenario:frameTimingWithSessionReplay:frameCount            |                                                                    |<b>unstable</b><br>[-14; +21] or [-4.690%; +6.799%]|                                                               |
|scenario:frameTimingWithSessionReplay:memoryHeapSizeMaxKb   |                                                                    |                                                   |<b>unstable</b><br>[+1.419MB; +25.189MB] or [+2.767%; +49.114%]|
|scenario:frameTimingWithSessionReplay:memoryRssAnonMaxKb    |                                                                    |                                                   |<b>unstable</b><br>[+0.057MB; +14.970MB] or [+0.049%; +13.064%]|
|scenario:frameTimingWithSessionReplay:memoryRssFileMaxKb    |                                                                    |                                                   |<b>unsure</b><br>[+0.821MB; +2.702MB] or [+0.735%; +2.421%]    |
|scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P50|<b>unstable</b><br>[+14.131ms; +14.910ms] or [+464.996%; +490.657%] |                                                   |                                                               |
|scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P90|<b>unstable</b><br>[-1.157ms; +9.534ms] or [-7.905%; +65.134%]      |                                                   |                                                               |
|scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P95|<b>unstable</b><br>[+0.639ms; +9.671ms] or [+4.096%; +61.953%]      |                                                   |                                                               |
|scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P99|<b>unstable</b><br>[-7.578ms; +12.636ms] or [-25.983%; +43.324%]    |                                                   |                                                               |
|scenario:frameTimingWithSessionReplay:frameOverrunMs:P90    |<b>unstable</b><br>[-0.124ms; +10.587ms] or [+10.541%; -900.980%]   |                                                   |                                                               |
|scenario:frameTimingWithSessionReplay:frameOverrunMs:P95    |<b>unstable</b><br>[+4.425ms; +12.711ms] or [-1634.296%; -4694.837%]|                                                   |                                                               |
|scenario:frameTimingWithSessionReplay:frameOverrunMs:P99    |<b>unstable</b><br>[-0.793ms; +37.674ms] or [-2.636%; +125.275%]    |                                                   |                                                               |



</details>

