# Android Log Collection

Send logs to Datadog from your Android applications with [Datadog's `dd-sdk-android` client-side logging library][1] and leverage the following features:

* Log to Datadog in JSON format natively.
* Add `context` and extra custom attributes to each log sent.
* Forward Java/Kotlin caught exceptions.
* Record real client IP addresses and User-Agents.
* Optimized network usage with automatic bulk posts.

## Setup

1. Add the Gradle dependency by declaring the library as a dependency in the module-level `build.gradle` file:

    ```conf
    dependencies {
        implementation "com.datadoghq:dd-sdk-android:x.x.x"
    }
    ```

2. Initialize the library with your application context, tracking consent, and the [Datadog client token][2] and Application ID generated when you create a new RUM application in the Datadog UI (see [Getting Started with Android RUM Collection][6] for more information). For security reasons, you must use a client token: you cannot use [Datadog API keys][3] to configure the `dd-sdk-android` library as they would be exposed client-side in the Android application APK byte code. For more information about setting up a client token, see the [client token documentation][2]:

   {{< tabs >}}
   {{% tab "US" %}}
   ```kotlin
    class SampleApplication : Application() {
        override fun onCreate() {
            super.onCreate()
            val configuration = Configuration.Builder(logsEnabled = true, ...).build()
            val credentials = Credentials(<CLIENT_TOKEN>, <ENV_NAME>, <APP_VARIANT_NAME>, <APPLICATION_ID>)
            Datadog.initialize(this, credentials, configuration, trackingConsent)
        }
    }
   ```
   {{% /tab %}}
   {{% tab "EU" %}}
   ```kotlin
   class SampleApplication : Application() {
       override fun onCreate() {
          super.onCreate()
          val configuration = Configuration.Builder(logsEnabled = true, ...)
             .useEUEndpoints()
             .build()
          val credentials = Credentials(<CLIENT_TOKEN>, <ENV_NAME>, <APP_VARIANT_NAME>, <APPLICATION_ID>)
          Datadog.initialize(this, credentials, configuration, trackingConsent)
       }
   }
   ```
   {{% /tab %}}
   {{< /tabs >}}
   
   To be compliant with the GDPR regulation, the SDK requires the tracking consent value at initialization.
   The tracking consent can be one of the following values:
   * `TrackingConsent.PENDING`: The SDK starts collecting and batching the data but does not send it to the data
     collection endpoint. The SDK waits for the new tracking consent value to decide what to do with the batched data.
   * `TrackingConsent.GRANTED`: The SDK starts collecting the data and sends it to the data collection endpoint.
   * `TrackingConsent.NOT_GRANTED`: The SDK does not collect any data. You will not be able to manually send any logs, traces, or
     RUM events.

   To update the tracking consent after the SDK is initialized, call: `Datadog.setTrackingConsent(<NEW CONSENT>)`.
   The SDK changes its behavior according to the new consent. For example, if the current tracking consent is `TrackingConsent.PENDING` and you update it to:
   * `TrackingConsent.GRANTED`: The SDK sends all current batched data and future data directly to the data collection endpoint.
   * `TrackingConsent.NOT_GRANTED`: The SDK wipes all batched data and does not collect any future data.

   Note that in the credentials required for initialization, your application variant name is also required. This is important because it enables  the right proguard `mapping.txt` file to be automatically uploaded at build time. This allows a Datadog dashboard to de-obfuscate the stack traces.

   Use the utility method `isInitialized` to check if the SDK is properly initialized:

   ```kotlin
    if (Datadog.isInitialized()) {
        // your code here
    }
   ```
   When writing your application, you can enable development logs by calling the `setVerbosity` method. All internal messages in the library with a priority equal to or higher than the provided level are then logged to Android's Logcat:
   ```kotlin
   Datadog.setVerbosity(Log.INFO)
   ```
   
3. Configure the Android Logger:

    ```kotlin
    val logger = Logger.Builder()
        .setNetworkInfoEnabled(true)
        .setLogcatLogsEnabled(true)
        .setDatadogLogsEnabled(true)
        .setBundleWithTraceEnabled(true)
        .setLoggerName("<LOGGER_NAME>")
        .build();
    ```

4. Send a custom log entry directly to Datadog with one of the following functions:

    ```kotlin
    logger.d("A debug message.")
    logger.i("Some relevant information ?")
    logger.w("An important warning…")
    logger.e("An error was met!")
    logger.wtf("What a Terrible Failure!")
    ```

    Exceptions caught can be sent with a message:

    ```kotlin
    try {
        doSomething()
    } catch (e: IOException) {
        logger.e("Error while doing something", e)
    }
    ```

    **Note**: All logging methods can have a throwable attached to them.

5. (Optional) - Provide a map alongside your log message to add attributes to the emitted log. Each entry of the map is added as an attribute.

    ```kotlin
    logger.i("onPageStarted", attributes = mapOf("http.url" to url))
    ```

    In Java you would have:

    ```java
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("http.url", url);
    Logger.d("onPageStarted", null, attributes);
    ```
   
6. If you need to modify some attributes in your Log events before batching you can do so by providing an implementation of `EventMapper<LogEvent>` when initializing the SDK:
   ```kotlin
      val config = Configuration.Builder(logsEnabled = true, ...)
                        // ...
                        .setLogEventMapper(logEventMapper)
                        .build()
   ```
   **Note**: If you return null or a different instance from the `EventMapper<LogEvent>` implementation the event will be dropped.

## Advanced logging

### Library initialization

The following methods in `Configuration.Builder` can be used when creating the Datadog Configuration to initialize the library:

| Method                           | Description                                                                                                                                                                                                                                                             |
|----------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `constructor(logsEnabled = true)`     | Set to `true` to enable sending logs to Datadog.                                                                                                                                                                                                                                  |
| `addPlugin(DatadogPlugin, Feature)`   | Adds a plugin implementation for a specific feature (CRASH, LOG, TRACE, RUM). The plugin will be registered once the feature is initialized and unregistered when the feature is stopped. |

### Logger initialization

The following methods in `Logger.Builder` can be used when initializing the logger to send logs to Datadog:

| Method                           | Description                                                                                                                                                                                                                                                             |
|----------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `setNetworkInfoEnabled(true)`    | Add the `network.client.connectivity` attribute to all logs. The data logged by default is `connectivity` (`Wifi`, `3G`, `4G`...) and `carrier_name` (`AT&T - US`). `carrier_name` is only available for Android API level 28+.                                     |
| `setServiceName(<SERVICE_NAME>)` | Set `<SERVICE_NAME>` as value for the `service` [standard attribute][4] attached to all logs sent to Datadog.                                                                                                                                                           |
| `setLogcatLogsEnabled(true)`     | Set to `true` to use Logcat as a logger.                                                                                                                                                                                                                                  |
| `setDatadogLogsEnabled(true)`    | Set to `true` to send logs to Datadog.                                                                                                                                                                                                                                  |
| `setBundleWithTraceEnabled(true)`| Set to `true` (default) to bundle the logs with the active trace in your application. This parameter lets you display all the logs sent during a specific trace by using the Datadog dashboard.                                                        |
| `setBundleWithRumEnabled(true)`| Set to `true` (default) to bundle the logs with the current RUM context in your application. This parameter lets you display all the logs sent while a specific View is active by using the Datadog RUM Explorer.                                                        |
| `setLoggerName(<LOGGER_NAME>)`   | Set `<LOGGER_NAME>` as the value for the `logger.name` attribute attached to all logs sent to Datadog.                                                                                                                                                                  |
| `setSampleRate(<SAMPLE_RATE>)`   | Set the sampling rate for this logger. All the logs produced by the logger instance are randomly sampled according to the provided sample rate (default 1.0 = all logs). **Note**: The Logcat logs are not sampled.            |
| `build()`                        | Build a new logger instance with all options set.                                                                                                                                                                                                                       |

### Global configuration

Find below functions to add/remove tags and attributes to all logs sent by a given logger.

#### Global tags

##### Add tags

Use the `addTag("<TAG_KEY>", "<TAG_VALUE>")` function to add tags to all logs sent by a specific logger:

```kotlin
// This adds a tag "build_type:debug" or "build_type:release" accordingly
logger.addTag("build_type", BuildConfig.BUILD_TYPE)

// This adds a tag "device:android"
logger.addTag("device", "android")
```

**Note**: `<TAG_VALUE>` must be a String.

##### Remove tags

Use the `removeTagsWithKey("<TAG_KEY>")` function to remove tags from all logs sent by a specific logger:

```kotlin
// This removes any tag starting with "build_type"
logger.removeTagsWithKey("build_type")
```

[Learn more about Datadog tags][5].

#### Global attributes

##### Add attributes

By default, the following attributes are added to all logs sent by a logger:

* `http.useragent` and its extracted `device` and `OS` properties
* `network.client.ip` and its extracted geographical properties (`country`, `city`)

Use the `addAttribute("<ATTRIBUTE_KEY>", "<ATTRIBUTE_VALUE>")` function to add a custom attribute to all logs sent by a specific logger:

```kotlin
// This adds an attribute "version_code" with an integer value
logger.addAttribute("version_code", BuildConfig.VERSION_CODE)

// This adds an attribute "version_name" with a String value
logger.addAttribute("version_name", BuildConfig.VERSION_NAME)
```

**Note**: `<ATTRIBUTE_VALUE>` can be any primitive, String, or Date.

##### Remove attributes

Use the `removeAttribute("<ATTRIBUTE_KEY>", "<ATTRIBUTE_VALUE>")` function to remove a custom attribute from all logs sent by a specific logger:

```kotlin
// This removes the attribute "version_code" from all further log send.
logger.removeAttribute("version_code")

// This removes the attribute "version_name" from all further log send.
logger.removeAttribute("version_name")
```

## Batch collection

All the logs are first stored on the local device in batches. Each batch follows the intake specification. They are sent as soon as network is available, and the battery is high enough to ensure the Datadog SDK does not impact the end user's experience. If the network is not available while your application is in the foreground, or if an upload of data fails, the batch is kept until it can be sent successfully.

This means that even if users open your application while being offline, no data will be lost.

The data on disk will automatically be discarded if it gets too old to ensure the SDK doesn't use too much disk space.

## Extensions

### Timber

If your existing codebase is using Timber, you can forward all those logs to  Datadog automatically by using the [dedicated library](https://github.com/DataDog/dd-sdk-android/tree/master/dd-sdk-android-timber).

## Further Reading

{{< partial name="whats-next/whats-next.html" >}}

[1]: https://github.com/DataDog/dd-sdk-android
[2]: https://docs.datadoghq.com/account_management/api-app-keys/#client-tokens
[3]: https://docs.datadoghq.com/account_management/api-app-keys/#api-keys
[4]: https://docs.datadoghq.com/logs/processing/attributes_naming_convention/
[5]: https://docs.datadoghq.com/tagging/
[6]: https://docs.datadoghq.com/real_user_monitoring/android/?tab=us
