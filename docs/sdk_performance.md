# SDK Performance and impact on the host application

## Methodology

To simulate the typical usage of Datadog SDK it was added to the [Docile-Alligator/Infinity-For-Reddit][1] application and typical user behavior (scrolling the feed, browsing reddits) was simulated for 2 minutes 30 seconds.

Application used: [Docile-Alligator/Infinity-For-Reddit][1] (revision [e8c9915a](https://github.com/Docile-Alligator/Infinity-For-Reddit/tree/e8c9915a))
Device used: Google Pixel 6
Android OS: Android 13 (Build Number TQ2A.230505.002)
Datadog SDK: revision [7f842d343](https://github.com/DataDog/dd-sdk-android/tree/7f842d343)

Network profiling was done using Charles Proxy 4.6.4.
CPU, Memory, Energy profiling was done using Android Studio Flamingo | 2022.2.1 Patch 1.
Device had 1.6 GB memory free on average (out of 7.6 GB), 115 apps installed and 25 GB of storage was free.
Device was connected to the 4G network, WiFi interface was disabled.

CPU and Memory profiling was done for the minified `release` build type with `profileable` attribute. CPU profiling was done using the `System Traces` option.

SDK modules which were added to the application:

* `dd-sdk-android-logs`
* `dd-sdk-android-trace`
* `dd-sdk-android-rum`
* `dd-sdk-android-okhttp`
* `dd-sdk-android-glide`

SDK was set up with default settings.

## Performance profiling

### Network traffic

| Measurement | Traffic                                                              |
|-------------|----------------------------------------------------------------------|
| #1          | 23 requests: 62.2 KB sent, 22.2 KB received.                         |
| #2          | 29 requests: 72.5 KB sent, 23.2 KB received.                         |
| #3          | 28 requests: 86.1 KB sent, 23.2 KB received.                         |
| #4          | 27 requests: 71.9 KB sent, 23.1 KB received.                         |
| #5          | 25 requests: 69.8 KB sent, 22.7 KB received.                         |
| average     | 26 requests: 72.5 KB (σ=7.73 KB) sent, 22.9 KB (σ=0.39 KB) received. |

All requests were sent using `gzip` encoding, compression was around 90%.

For each measurement round the first request had an initial TLS handshake with certificate chain download, which had a size of 17.5 KB. Removing this single server certificate chain download from calculation leads to the following results.

| Measurement | Traffic                                                             |
|-------------|---------------------------------------------------------------------|
| #1          | 23 requests: 62.2 KB sent, 4.7 KB received.                         |
| #2          | 29 requests: 72.5 KB sent, 5.7 KB received.                         |
| #3          | 28 requests: 86.1 KB sent, 5.7 KB received.                         |
| #4          | 27 requests: 71.9 KB sent, 5.6 KB received.                         |
| #5          | 25 requests: 69.8 KB sent, 5.2 KB received.                         |
| average     | 26 requests: 72.5 KB sent (σ=7.73 KB), 5.4 KB (σ=0.39 KB) received. |

### Peak CPU and Memory usage

**Note**: Measurement without SDK means the measurement of the original application without Datadog SDK added as a dependency.

| Measurement | CPU with SDK    | CPU w/o SDK     | Memory with SDK       | Memory w/o SDK     |
|-------------|-----------------|-----------------|-----------------------|--------------------|
| #1          | 19%             | 23%             | 432 MB                | 413 MB             |
| #2          | 32%             | 23%             | 441 MB                | 470 MB             |
| #3          | 25%             | 27%             | 400 MB                | 430 MB             |
| #4          | 32%             | 29%             | 453 MB                | 432 MB             |
| #5          | 26%             | 24%             | 437 MB                | 440 MB             |
| average     | 26.8% (σ=4.87%) | 25.2% (σ=2.4%)  | 432.6 MB (σ=17.72 MB) | 437 MB (σ=18.7 MB) |

CPU usage pattern was the same, with the majority of the CPU usage below 10% during user interactions with the app and around 1-2% when application was in idle (no interactions performed).
Memory allocation/de-allocation pattern stays the same with and without SDK.

### Janky frames

Janky frames are described in the [official Android documentation][2]

| Measurement | With SDK        | Without SDK     |
|-------------|-----------------|-----------------|
| #1          | 57/5883 (0.9%)  | 69/7781 (0.9%)  |
| #2          | 73/6580 (1.1%)  | 43/6247 (0.7%)  |
| #3          | 57/6323 (0.9%)  | 81/6607 (1.2%)  |
| #4          | 59/5628 (1.0%)  | 81/7688 (1.0%)  |
| #5          | 62/6256 (0.9%)  | 62/6577 (0.9%)  |
| average     | 0.96% (σ=0.08%) | 0.94% (σ=0.16%) |

Datadog SDK doesn't have any meaningful impact on the amount of janky frames in the app.

### Energy consumption

Documentation about this metric can be found [here][3].

| Measurement | With SDK | Without SDK |
|-------------|----------|-------------|
| #1          | LIGHT    | LIGHT       |
| #2          | LIGHT    | LIGHT       |
| #3          | LIGHT    | LIGHT       |
| #4          | LIGHT    | LIGHT       |
| #5          | LIGHT    | LIGHT       |

### Application Startup Time

As a reference we measured [Time To Initial Display][4].

Cold start was simulated by running the following command (application was killed before run): 

```shell
adb shell am start -S -W ml.docilealligator.infinityforreddit/.activities.MainActivity -c android.intent.category.LAUNCHER -a android.intent.action.MAIN
```

| Measurement | With SDK          | Without SDK         |
|-------------|-------------------|---------------------|
| #1          | 246 ms            | 228 ms              |
| #2          | 239 ms            | 222 ms              |
| #3          | 242 ms            | 233 ms              |
| #4          | 241 ms            | 233 ms              |
| #5          | 247 ms            | 228 ms              |
| average     | 243 ms (σ=3.4 ms) | 228.8 ms (σ=4.5 ms) |

SDK and its initialization has no significant impact on the `Time To Initial Display` metric.

### Application size impact

The measurement was done for the `2.0.0-beta2` version of Datadog SDK for the `minifiedRelease` application variant.

`apk` size without Datadog SDK: 11044045 bytes
`apk` size with Datadog SDK: 11566506 bytes

Datadog SDK added 552 KB to the `apk` size.

## SDK behavior in the host application

### Background behavior 
When the application goes in background:
    -   the auto-instrumentation stops and
detaches itself from any UI callbacks (lifecycle events, gesture detection). No RUM event will be automatically tracked
but you can still manually send them with the `RumMonitor`.
   -    The endpoints accept RUM events, logs and traces as usual.
   -    The endpoints collect and send new batches of data.

### How batches are created and sent
When a new event is ready to be serialized and batched, the persistence layer asks for 
the last known batch file to store the serialized event. A batch file is 
valid for appending new data when all the following conditions are met:
   
   -   last time the file was accessed was less than 5 seconds ago 
   -   the file size is less than or equal to 4 MB
   -   the number of events in the file is less than or equal to 500   
    
   Once one of those criteria is not met, the batch is marked as `full` and is sent to the
   endpoint in one of the next upload cycles. The frequency at which the batches are sent starts at 5 seconds and goes up 
   linearly with every batch sent. The maximum frequency is one batch per second. If there's no batch or network available or the 
   battery level is too low, the upload frequency is linearly decreased to the minimum default value of one batch every 20 seconds.

### Battery consumption
The SDK does not perform network activity if the device battery level is less than 10% or if the 
device is in power saving mode.

### Lifespan of the persisted data
The SDK stores data in batches and tries to send those whenever the network is 
available. A batch will not be stored more than 18 hours in an application. Every time the SDK
reads a new batch for sending, will first remove batches that are older than 18 hours.

### Low available storage
The SDK checks the storage space used every time it creates a new batch. If this value is greater than
512 MB (the maximum amount of storage space that the SDK will use), it first tries to make more space available 
by removing the older files.

[1]: https://github.com/Docile-Alligator/Infinity-For-Reddit
[2]: https://developer.android.com/studio/profile/jank-detection
[3]: https://developer.android.com/studio/profile/energy-profiler
[4]: https://developer.android.com/topic/performance/vitals/launch-time#time-initial

## Session Relay Performance Measurement

The Session Replay feature is expected to have a larger impact on both CPU and memory usage
within the application.

This section provides detailed performance measurements across different scenarios.

### Methodology

To simulate the typical usage of the Datadog SDK with Session Replay enabled, measurements were
performed using two applications:

Application used: [Docile-Alligator/Infinity-For-Reddit][1] (
revision [cfe1781](https://github.com/Docile-Alligator/Infinity-For-Reddit/tree/cfe1781)) for
Android View
[NowInAndroid](https://github.com/android/nowinandroid) (revision [
904e6fccee809556b242898fb624f5f38200298c)) for Jetpack Compose

Device used: Samsung Galaxy S23
Android OS: Android 14 (Build Number UP1A.231005.007)
Datadog SDK: revision [2.17.0](https://github.com/DataDog/dd-sdk-android/tree/2.17.0)

CPU, Memory profiling was done using Android Studio Ladybug | 2024.2.1 Patch 2.
Device had 3.7 GB memory free on average (out of 8 GB), 110 apps installed and 41.7 GB of storage
was free.
Device was connected to WiFi interface.

CPU and Memory profiling was done for the debug build type. CPU profiling was done using the System
Traces option.

Each measurement lasted for 1 minute, during which typical user behavior was simulated (scrolling
the feed, browsing content).

### Scenario configuration

In the following measurements, "Baseline" refers to the scenario where the application integrates
the Datadog SDK without enabling the Session Replay feature.

The table below outlines the detailed masking configurations used in each measurement scenario:

| Measurement       | Touch Privacy | Text & Input          | Image     |
|-------------------|---------------|-----------------------|-----------|
| Minimum recording | HIDE          | MASK_ALL              | MASK_ALL  |
| Touch  Only       | SHOW          | MASK_ALL              | MASK_ALL  | 
| Text & Input Only | HIDE          | MASK_SENSITIVE_INPUTS | MASK_ALL  | 
| Image Only        | HIDE          | MASK_ALL              | MASK_NONE | 
| All               | SHOW          | MASK_SENSITIVE_INPUTS | MASK_NONE |

### CPU Peak

#### Android View

| Measurement | Baseline         | Minimum recording | Touch  Only      | Text & Input Only | Image Only       | All              |
|-------------|------------------|-------------------|------------------|-------------------|------------------|------------------|
| #1          | 27.9%            | 26%               | 25.5%            | 25.4%             | 32.3%            | 23.8%            |
| #2          | 21.7%            | 24.2%             | 30.3%            | 32.9%             | 29.2%            | 34.6%            |
| #3          | 27.8%            | 27.3%             | 22.7%            | 28.8%             | 36.1%            | 28.6%            |
| #4          | 30.8%            | 21.4%             | 29.4%            | 23.2%             | 29.4%            | 25.8%            |
| #5          | 24.2%            | 29.7%             | 24.4%            | 26.2%             | 41%              | 32.3%            |
| Average     | 26.48%<(σ=3.18%) | 25.72% (σ=2.81%)  | 26.46% (σ=2.92%) | 27.30% (σ=3.32%)  | 33.60% (σ=4.47%) | 29.02% (σ=3.99%) |

#### Jetpack Compose

| Measurement | Baseline         | Minimum recording | Touch  Only      | Text & Input Only | Image Only       | All              |
|-------------|------------------|-------------------|------------------|-------------------|------------------|------------------|
| #1          | 30.1%            | 29.6%             | 36.7%            | 34%               | 40.4%            | 42.2%            |
| #2          | 32%              | 29.8%             | 30.9%            | 27.5%             | 44.9%            | 31.7%            |
| #3          | 30%              | 38.2%             | 31.8%            | 35%               | 43.7%            | 42.1%            |
| #4          | 37.1%            | 31.4%             | 34.8%            | 39.4%             | 40.1%            | 45.4%            |
| #5          | 26.3%            | 42.2%             | 33.6%            | 33.4%             | 44.9%            | 43.8%            |
| Average     | 31.10% (σ=3.52%) | 34.24% (σ=5.07%)  | 33.56% (σ=2.08%) | 33.86% (σ=3.81%)  | 42.80% (σ=2.13%) | 41.04% (σ=4.82%) |

### Memory Peak

#### Android View

| Measurement | Baseline            | Minimum recording   | Touch  Only         | Text & Input Only   | Image Only          | All               |
|-------------|---------------------|---------------------|---------------------|---------------------|---------------------|-------------------|
| #1          | 265MB               | 265MB               | 291MB               | 261MB               | 317MB               | 334MB             |
| #2          | 248MB               | 248MB               | 319MB               | 284MB               | 287MB               | 285MB             |
| #3          | 250MB               | 250MB               | 156MB               | 259MB               | 311MB               | 284MB             |
| #4          | 221MB               | 221MB               | 318MB               | 195MB               | 341MB               | 315MB             |
| #5          | 275MB               | 275MB               | 301MB               | 252MB               | 274MB               | 317MB             |
| Average     | 251.8MB (σ=18.32MB) | 251.8MB (σ=18.32MB) | 277.0MB (σ=61.41MB) | 250.2MB (σ=29.62MB) | 306.0MB (σ=23.48MB) | 307MB (σ=19.52MB) |

#### Jetpack Compose

| Measurement | Baseline            | Minimum recording   | Touch  Only         | Text & Input Only   | Image Only          | All                 |
|-------------|---------------------|---------------------|---------------------|---------------------|---------------------|---------------------|
| #1          | 265MB               | 202MB               | 291MB               | 261MB               | 317MB               | 334MB               |
| #2          | 248MB               | 417MB               | 319MB               | 284MB               | 287MB               | 285MB               |
| #3          | 250MB               | 271MB               | 156MB               | 259MB               | 311MB               | 284MB               |
| #4          | 221MB               | 313MB               | 318MB               | 195MB               | 341MB               | 315MB               |
| #5          | 275MB               | 301MB               | 301MB               | 252MB               | 274MB               | 317MB               |
| Average     | 251.8MB (σ=18.32MB) | 300.8MB (σ=69.71MB) | 277.0MB (σ=61.41MB) | 250.2MB (σ=29.62MB) | 306.0MB (σ=23.48MB) | 307.0MB (σ=19.52MB) |

### Janky Frames

#### Android View

| Measurement | Baseline        | Minimum recording | Touch  Only     | Text & Input Only | Image Only      | All             |
|-------------|-----------------|-------------------|-----------------|-------------------|-----------------|-----------------|
| #1          | 139/3558        | 189/4139          | 298/5070        | 209/4381          | 161/3829        | 201/3079        |
| #2          | 156/5031        | 192/4963          | 225/4776        | 209/3524          | 209/3073        | 240/4235        |
| #3          | 69/2485         | 128/3173          | 259/4651        | 228/4281          | 240/3547        | 171/4008        |
| #4          | 114/4057        | 118/3569          | 122/4331        | 126/3423          | 194/4268        | 228/3886        |
| #5          | 132/4112        | 159/4369          | 201/4094        | 225/4430          | 167/3268        | 259/4491        |
| Average     | 3.16% (σ=0.41%) | 3.88% (σ=0.42%)   | 4.78% (σ=1.07%) | 4.96% (σ=0.74%)   | 5.49% (σ=1.10%) | 5.62% (σ=0.74%) |

#### Jetpack Compose

| Measurement | Baseline        | Minimum recording | Touch  Only     | Text & Input Only | Image Only      | All             |
|-------------|-----------------|-------------------|-----------------|-------------------|-----------------|-----------------|
| #1          | 157/5919        | 380/5191          | 368/5581        | 348/5227          | 393/5868        | 247/3644        |
| #2          | 120/4788        | 331/5115          | 427/4758        | 316/5393          | 410/5313        | 267/3514        |
| #3          | 131/4280        | 287/4572          | 345/5306        | 458/6743          | 336/5257        | 271/4392        |
| #4          | 198/6188        | 298/5057          | 354/5859        | 323/5054          | 323/5042        | 284/4678        |
| #5          | 159/4825        | 319/5051          | 346/5281        | 359/6693          | 411/5673        | 256/4096        |
| Average     | 2.94% (σ=0.31%) | 6.46% (σ=0.47%)   | 6.93% (σ=1.04%) | 6.21% (σ=0.53%)   | 6.89% (σ=0.52%) | 6.57% (σ=0.57%) |