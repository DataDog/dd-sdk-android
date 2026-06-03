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

Found 0 performance improvements and 0 performance regressions! Performance is the same for 10 metrics, 8 unstable metrics.

<details><summary>See unchanged results</summary>

|scenario                                                    |Δ mean execution_time                                                  |Δ mean iterations|
|------------------------------------------------------------|-----------------------------------------------------------------------|-----------------|
|scenario:frameTimingWithSessionReplay:frameCount            |                                                                       |<b>same</b>      |
|scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P50|<b>unstable</b><br>[-5.046ms; +2.499ms] or [-60.567%; +29.996%]        |                 |
|scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P90|<b>unstable</b><br>[-4108.904µs; +3214.792µs] or [-16.549%; +12.948%]  |                 |
|scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P95|<b>unstable</b><br>[-4784.784µs; +4345.026µs] or [-15.893%; +14.433%]  |                 |
|scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P99|<b>unstable</b><br>[-2.758ms; +9.861ms] or [-6.967%; +24.913%]         |                 |
|scenario:frameTimingWithSessionReplay:frameOverrunMs:P50    |<b>unstable</b><br>[-5128.859µs; +3763.952µs] or [+572.928%; -420.459%]|                 |
|scenario:frameTimingWithSessionReplay:frameOverrunMs:P90    |<b>unstable</b><br>[-4262.538µs; +5599.507µs] or [-30.549%; +40.131%]  |                 |
|scenario:frameTimingWithSessionReplay:frameOverrunMs:P95    |<b>unstable</b><br>[-3.425ms; +7.229ms] or [-16.863%; +35.591%]        |                 |
|scenario:frameTimingWithSessionReplay:frameOverrunMs:P99    |<b>unstable</b><br>[-4.356ms; +11.268ms] or [-13.064%; +33.794%]       |                 |



</details>

