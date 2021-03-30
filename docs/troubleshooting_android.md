# Troubleshooting FAQs
 

## Check if Datadog RUM is initialized
Use the utility method `isInitialized` to check if the SDK is properly initialized:

```kotlin
    if (Datadog.isInitialized()) {
        // your code here
    }
```

## Debugging
When writing your application, you can enable development logs by calling the `setVerbosity` method. All internal messages in the library with a priority equal to or higher than the provided level are then logged to Android's Logcat:

```kotlin
    Datadog.setVerbosity(Log.INFO)
```

## Set tracking consent (GDPR compliance)

To be compliant with the GDPR regulation, the SDK requires the tracking consent value at initialization.
    Tracking consent can be one of the following values:
    * `TrackingConsent.PENDING`: (Default) The SDK starts collecting and batching the data but does not send it to the data
     collection endpoint. The SDK waits for the new tracking consent value to decide what to do with the batched data.
    * `TrackingConsent.GRANTED`: The SDK starts collecting the data and sends it to the data collection endpoint.
    * `TrackingConsent.NOT_GRANTED`: The SDK does not collect any data. You are not able to manually send any logs, traces, or
     RUM events.

    To update the tracking consent after the SDK is initialized, call: `Datadog.setTrackingConsent(<NEW CONSENT>)`.
    The SDK changes its behavior according to the new consent. For example, if the current tracking consent is `TrackingConsent.PENDING` and you update it to:
    * `TrackingConsent.GRANTED`: The SDK sends all current batched data and future data directly to the data collection endpoint.
    * `TrackingConsent.NOT_GRANTED`: The SDK wipes all batched data and does not collect any future data.

## Sample RUM sessions

To control the data your application sends to Datadog RUM, you can specify a sampling rate for RUM sessions while [initializing the RumMonitor][1] as a percentage between 0 and 100.

```kotlin
    val monitor = RumMonitor.Builder()
            // Here 75% of the RUM sessions are sent to Datadog
            .sampleRumSessions(75.0f)
            .build()
    GlobalRum.registerIfAbsent(monitor)
```

[1]:/real_user_monitoring/android/troubleshooting_android/#setup

## Sending data when device is offline

RUM ensures availability of data when your user device is offline. In cases of low-network areas, or when the device battery is too low, all the RUM events are first stored on the local device in batches. Each batch follows the intake specification. They are sent as soon as the network is available, and the battery is high enough to ensure the Datadog SDK does not impact the end user's experience. If the network is not available while your application is in the foreground, or if an upload of data fails, the batch is kept until it can be sent successfully.
 
This means that even if users open your application while offline, no data is lost.
 
**Note**: The data on the disk is automatically discarded if it gets too old to ensure the SDK doesn't use too much disk space.

## Migrating to 1.0.0

If you've been using the former SDK (version `0.1.x` or `0.2.x`), there are some breaking changes introduced in version `1.0.0`, namely:

### Logger.Builder

{{< tabs >}}
    {{% tab "After" %}}


```java
    Datadog.initialize(context, "my-api-key");

    // …

    logger = new Logger.Builder()
            .setNetworkInfoEnabled(true)
            .setServiceName("android-sample-java") // Sets the service name
            .setLoggerName("my_logger") // Sets the logger name (within the service)
            .setLogcatLogsEnabled(true)
            .build();
```

{{% /tab %}}
    {{% tab "Before" %}}


```java
    logger = new LoggerBuilder()
        .withName("my-application-name") // This would set the service name
        .withNetworkInfoLogging(this)
        .build("my-api-key");
```
    {{% /tab %}}
    {{< /tabs >}}


### Attributes

In earlier versions, attributes were created or removed with the `Logger.addField()` or `Logger.removeField()`
methods. These methods were renamed for consistency purposes, and are now `Logger.addAttribute()`
 and `Logger.removeAttribute()`. Their behavior remains the same.


## Further Reading
{{< partial name="whats-next/whats-next.html" >}}

[1]: https://github.com/DataDog/dd-sdk-android/blob/master/docs/TROUBLESHOOTING.md


 
