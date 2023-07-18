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