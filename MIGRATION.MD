# Migration from 1.x to 2.0

The main changes introduced in SDK 2.0 compared to 1.x are:

1. All relevant products (RUM, Trace, Logs, etc.) are now extracted into different modules. That allows you to integrate only what is needed into your application.

Whereas all products in version 1.x were contained in the single artifact `com.datadoghq:dd-sdk-android:x.x.x`, you now need to adopt the following artifacts:

* RUM: `com.datadoghq:dd-sdk-android-rum:x.x.x`
* Logs: `com.datadoghq:dd-sdk-android-logs:x.x.x`
* Trace: `com.datadoghq:dd-sdk-android-trace:x.x.x`
* WebView Tracking: `com.datadoghq:dd-sdk-android-webview:x.x.x`
* OkHttp instrumentation: `com.datadoghq:dd-sdk-android-okhttp:x.x.x`

**Note**: If you utilize NDK Crash Reporting and WebView Tracking, you also need to add RUM and/or Logs artifacts to be able to report events to RUM and/or Logs respectively.

Reference to the `com.datadoghq:dd-sdk-android` artifact should be removed from your Gradle buildscript, this artifact doesn't exist anymore.

**Note**: The Maven coordinates of all the other artifacts stay the same.

2. Support for multiple SDK instances (see below).
3. Unification of the API layout, as well as naming between iOS and Android SDKs with other Datadog products. Datadog SDK v2 is not binary compatible with Datadog SDK v1.
4. Support of Android API 19 (KitKat) was dropped. The minimum SDK supported is now API 21 (Lollipop).
5. Kotlin 1.7 is required in order to integrate the SDK. SDK itself is compiled with Kotlin 1.8, so compiler of Kotlin 1.6 and below cannot read SDK classes metadata.

If you have an error like the following:

```
A failure occurred while executing com.android.build.gradle.internal.tasks.CheckDuplicatesRunnable
Duplicate class kotlin.collections.jdk8.CollectionsJDK8Kt found in modules kotlin-stdlib-1.8.10 (org.jetbrains.kotlin:kotlin-stdlib:1.8.10) and kotlin-stdlib-jdk8-1.7.20 (org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.20)
```

you need to add the following rules to your buildscript (more details [here](https://stackoverflow.com/a/75298544)):

```kotlin
dependencies {
    constraints {
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.10") {
            because("kotlin-stdlib-jdk7 is now a part of kotlin-stdlib")
        }
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.10") {
            because("kotlin-stdlib-jdk8 is now a part of kotlin-stdlib")
        }
    }
}
```

You can always refer to this [sample application](https://github.com/DataDog/dd-sdk-android/blob/9c2d460b6b66161efb1252039a82784792958042/sample/kotlin/src/main/kotlin/com/datadog/android/sample/SampleApplication.kt) for an example on how to setup the SDK.

## SDK configuration changes

Better SDK granularity is achieved with the extraction of different products into independent modules. This changes the way SDK is configured.

`com.datadog.android.core.configuration.Configuration.Builder` class has the following changes:

* Client token, env name, variant name (default value is empty string), service name (default value is application ID taken from the manifest) should be provided in the constructor.
* `com.datadog.android.core.configuration.Credentials` class which was containing parameters mentioned above is removed.
* `logsEnabled`, `tracesEnabled`, `rumEnabled` are removed from the constructor in favour of the individual products configuration (see below).
* `crashReportsEnabled` constructor argument is removed. You can enable/disable JVM crash reporting by using `Configuration.Builder.setCrashReportsEnabled` method, by default JVM crash reporting is enabled.
* RUM, Logs, Trace products configuration methods are removed from `Configuration.Builder` in favour of the individual products configuration (see below).

`Datadog.initialize` method has `Credentials` class removed from the list of the arguments.

`com.datadog.android.plugin` package and all related classes/methods are removed.

### Logs product changes

All the classes related to the Logs product are now strictly contained in the `com.datadog.android.log` package.

To use Logs product, import the following artifact:

```kotlin
implementation("com.datadoghq:dd-sdk-android-logs:x.x.x")
```

You can enable the Logs product with the following snippet:

```kotlin
val logsConfig = LogsConfiguration.Builder()
    ...
    .build()

Logs.enable(logsConfig)

val logger = Logger.Builder()
    ...
    .build()
```

API changes:

|`1.x`|`2.0`|
|---|---|
|`com.datadog.android.core.configuration.Configuration.Builder.setLogEventMapper`|`com.datadog.android.log.LogsConfiguration.Builder.setEventMapper`|
|`com.datadog.android.core.configuration.Configuration.Builder.useCustomLogsEndpoint`|`com.datadog.android.log.LogsConfiguration.Builder.useCustomEndpoint`|
|`com.datadog.android.log.Logger.Builder.setLoggerName`|`com.datadog.android.log.Logger.Builder.setName`|
|`com.datadog.android.log.Logger.Builder.setSampleRate`|`com.datadog.android.log.Logger.Builder.setRemoteSampleRate`|
|`com.datadog.android.log.Logger.Builder.setDatadogLogsEnabled`|This method has been removed. Use `com.datadog.android.log.Logger.Builder.setRemoteSampleRate(0f)` instead to disable sending logs to Datadog.|
|`com.datadog.android.log.Logger.Builder.setServiceName`|`com.datadog.android.log.Logger.Builder.setService`|
|`com.datadog.android.log.Logger.Builder.setDatadogLogsMinPriority`|`com.datadog.android.log.Logger.Builder.setRemoteLogThreshold`|

### Trace product changes

All the classes related to the Trace product are now strictly contained in the `com.datadog.android.trace` package (this means that all classes residing in `com.datadog.android.tracing` before have moved).

To use the Trace product, import the following artifact:

```kotlin
implementation("com.datadoghq:dd-sdk-android-trace:x.x.x")
```

Enable the Trace product with the following snippet:

```kotlin
val traceConfig = TraceConfiguration.Builder()
    ...
    .build()

Trace.enable(traceConfig)

val tracer = AndroidTracer.Builder()
    ...
    .build()

GlobalTracer.registerIfAbsent(tracer)
```

API changes:

|`1.x`|`2.0`|
|---|---|
|`com.datadog.android.core.configuration.Configuration.Builder.setSpanEventMapper`|`com.datadog.android.trace.TraceConfiguration.Builder.setEventMapper`|
|`com.datadog.android.core.configuration.Configuration.Builder.useCustomTracesEndpoint`|`com.datadog.android.trace.TraceConfiguration.Builder.useCustomEndpoint`|
|`com.datadog.android.tracing.AndroidTracer.Builder.setSamplingRate`|`com.datadog.android.trace.AndroidTracer.Builder.setSampleRate`|
|`com.datadog.android.tracing.AndroidTracer.Builder.setServiceName`|`com.datadog.android.trace.AndroidTracer.Builder.setService`|

### RUM product changes

All classes related to the RUM product are now strictly contained in the `com.datadog.android.rum` package.

To use the RUM product, import the following artifact:

```kotlin
implementation("com.datadoghq:dd-sdk-android-rum:x.x.x")
```

The RUM product can be enabled with the following snippet:

```kotlin
val rumConfig = RumConfiguration.Builder(rumApplicationId)
    ...
    .build()

Rum.enable(rumConfig)
```

API changes:

|`1.x`|`2.0`|
|---|---|
|`com.datadog.android.core.configuration.Configuration.Builder.setRumViewEventMapper`|`com.datadog.android.rum.RumConfiguration.Builder.setViewEventMapper`|
|`com.datadog.android.core.configuration.Configuration.Builder.setRumResourceEventMapper`|`com.datadog.android.rum.RumConfiguration.Builder.setResourceEventMapper`|
|`com.datadog.android.core.configuration.Configuration.Builder.setRumActionEventMapper`|`com.datadog.android.rum.RumConfiguration.Builder.setActionEventMapper`|
|`com.datadog.android.core.configuration.Configuration.Builder.setRumErrorEventMapper`|`com.datadog.android.rum.RumConfiguration.Builder.setErrorEventMapper`|
|`com.datadog.android.core.configuration.Configuration.Builder.setRumLongTaskEventMapper`|`com.datadog.android.rum.RumConfiguration.Builder.setLongTaskEventMapper`|
|`com.datadog.android.core.configuration.Configuration.Builder.useCustomRumEndpoint`|`com.datadog.android.rum.RumConfiguration.Builder.useCustomEndpoint`|
|`com.datadog.android.event.ViewEventMapper`|`com.datadog.android.rum.event.ViewEventMapper`|
|`com.datadog.android.core.configuration.VitalsUpdateFrequency`|`com.datadog.android.rum.configuration.VitalsUpdateFrequency`|
|`com.datadog.android.core.configuration.Configuration.Builder.trackInteractions`|`com.datadog.android.rum.RumConfiguration.Builder.trackUserInteractions`|
|`com.datadog.android.core.configuration.Configuration.Builder.disableInteractionTracking`|`com.datadog.android.rum.RumConfiguration.Builder.disableUserInteractionTracking`|
|`com.datadog.android.core.configuration.Configuration.Builder.sampleRumSessions`|`com.datadog.android.rum.RumConfiguration.Builder.setSessionSampleRate`|
|`com.datadog.android.core.configuration.Configuration.Builder.sampleTelemetry`|`com.datadog.android.rum.RumConfiguration.Builder.setTelemetrySampleRate`|
|`com.datadog.android.rum.RumMonitor.Builder`|This class has been removed. The RUM monitor is created and registered during the `Rum.enable` call.|
|`com.datadog.android.rum.RumMonitor.Builder.sampleRumSessions`|`com.datadog.android.rum.RumConfiguration.Builder.setSessionSampleRate`|
|`com.datadog.android.rum.RumMonitor.Builder.setSessionListener`|`com.datadog.android.rum.RumConfiguration.Builder.setSessionListener`|
|`com.datadog.android.rum.RumMonitor.addUserAction`|`com.datadog.android.rum.RumMonitor.addAction`|
|`com.datadog.android.rum.RumMonitor.startUserAction`|`com.datadog.android.rum.RumMonitor.startAction`|
|`com.datadog.android.rum.RumMonitor.stopUserAction`|`com.datadog.android.rum.RumMonitor.stopAction`|
|`com.datadog.android.rum.GlobalRum.registerIfAbsent`|This method has been removed. The RUM monitor is created and registered during the `Rum.enable` call.|
|`com.datadog.android.rum.GlobalRum`|`com.datadog.android.rum.GlobalRumMonitor`|
|`com.datadog.android.rum.GlobalRum.addAttribute`|`com.datadog.android.rum.RumMonitor.addAttribute`|
|`com.datadog.android.rum.GlobalRum.removeAttribute`|`com.datadog.android.rum.RumMonitor.removeAttribute`|

### NDK Crash Reporting changes

The artifact name stays the same as before: `com.datadoghq:dd-sdk-android-ndk:x.x.x`

NDK Crash Reporting can be enabled using the following snippet:

```kotlin
NdkCrashReports.enable()
```

This configuration replaces the `com.datadog.android.core.configuration.Configuration.Builder.addPlugin` call used before.

**Note**: You should have RUM and/or Logs products enabled in order to receive NDK crash reports in RUM and/or Logs.

### WebView Tracking changes

The artifact name stays the same as before: `com.datadoghq:dd-sdk-android-webview:x.x.x`

You can enable WebView Tracking with the following snippet:

```kotlin
WebViewTracking.enable(webView, allowedHosts)
```

**Note**: You should have RUM and/or Logs products enabled in order to receive events coming from WebView in RUM and/or Logs.

API changes:

|`1.x`|`2.0`|
|---|---|
|`com.datadog.android.webview.DatadogEventBridge`|This method became an `internal` class. Use `WebViewTracking` instead.|
|`com.datadog.android.rum.webview.RumWebChromeClient`|This class was removed. Use `WebViewTracking` instead.|
|`com.datadog.android.rum.webview.RumWebViewClient`|This class was removed. Use `WebViewTracking` instead.|

### OkHttp Tracking changes

In order to be able to use OkHttp Tracking you need to import the following artifact:

```kotlin
implementation("com.datadoghq:dd-sdk-android-okhttp:x.x.x")
```

OkHttp instrumentation now supports the case when Datadog SDK is initialized after the OkHttp client, allowing you to create `com.datadog.android.okhttp.DatadogEventListener`, `com.datadog.android.okhttp.DatadogInterceptor`, and `com.datadog.android.okhttp.trace.TracingInterceptor` before Datadog SDK. OkHttp instrumentation starts reporting events to Datadog once Datadog SDK is initialized.

Also, both `com.datadog.android.okhttp.DatadogInterceptor` and `com.datadog.android.okhttp.trace.TracingInterceptor` improve the integration with remote configuration, allowing you to control sampling dynamically.
In order to do that, you need to provide your own implementation of the `com.datadog.android.core.sampling.Sampler` interface in the `com.datadog.android.okhttp.DatadogInterceptor`/`com.datadog.android.okhttp.trace.TracingInterceptor` constructor. It is queried for each request to make the sampling decision.

### `dd-sdk-android-ktx` module removal

In order to provide the better granularity for the Datadog SDK libraries used, `dd-sdk-android-ktx` module which was containing extension methods for both RUM and Trace features is removed and the code was re-arranged between the other modules:

| `1.x`                                                                                     | '2.0'                                                                                       | Module name                       |
|-------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|-----------------------------------|
| `com.datadog.android.ktx.coroutine#kotlinx.coroutines.CoroutineScope.launchTraced`        | `com.datadog.android.trace.coroutines#kotlinx.coroutines.CoroutineScope.launchTraced`       | `dd-sdk-android-trace-coroutines` |
| `com.datadog.android.ktx.coroutine#runBlockingTraced`                                     | `com.datadog.android.trace.coroutines#runBlockingTraced`                                    | `dd-sdk-android-trace-coroutines` |
| `com.datadog.android.ktx.coroutine#kotlinx.coroutines.CoroutineScope.asyncTraced`         | `com.datadog.android.trace.coroutines#kotlinx.coroutines.CoroutineScope.asyncTraced`        | `dd-sdk-android-trace-coroutines` |
| `com.datadog.android.ktx.coroutine#kotlinx.coroutines.Deferred<T>.awaitTraced`            | `com.datadog.android.trace.coroutines#kotlinx.coroutines.Deferred<T>.awaitTraced`           | `dd-sdk-android-trace-coroutines` |
| `com.datadog.android.ktx.coroutine#withContextTraced`                                     | `com.datadog.android.trace.coroutines#withContextTraced`                                    | `dd-sdk-android-trace-coroutines` |
| `com.datadog.android.ktx.coroutine.CoroutineScopeSpan`                                    | `com.datadog.android.trace.coroutines.CoroutineScopeSpan`                                   | `dd-sdk-android-trace-coroutines` |
| `com.datadog.android.ktx.sqlite#android.database.sqlite.SQLiteDatabase.transactionTraced` | `com.datadog.android.trace.sqlite#android.database.sqlite.SQLiteDatabase.transactionTraced` | `dd-sdk-android-trace`            |
| `com.datadog.android.ktx.tracing#io.opentracing.Span.setError`                            | `com.datadog.android.trace#io.opentracing.Span.setError`                                    | `dd-sdk-android-trace`            |
| `com.datadog.android.ktx.tracing#withinSpan`                                              | `com.datadog.android.trace#withinSpan`                                                      | `dd-sdk-android-trace`            |
| `com.datadog.android.ktx.coroutine#sendErrorToDatadog`                                    | `com.datadog.android.rum.coroutines#sendErrorToDatadog`                                     | `dd-sdk-android-rum-coroutines`   |
| `com.datadog.android.ktx.rum#java.io.Closeable.useMonitored`                              | `com.datadog.android.rum#java.io.Closeable.useMonitored`                                    | `dd-sdk-android-rum`              |
| `com.datadog.android.ktx.rum#android.content.Context.getAssetAsRumResource`               | `com.datadog.android.rum.resource#android.content.Context.getAssetAsRumResource`            | `dd-sdk-android-rum`              |
| `com.datadog.android.ktx.rum#android.content.Context.getRawResAsRumResource`              | `com.datadog.android.rum.resource#android.content.Context.getRawResAsRumResource`           | `dd-sdk-android-rum`              |
| `com.datadog.android.ktx.rum#java.io.InputStream.asRumResource`                           | `com.datadog.android.rum.resource#java.io.InputStream.asRumResource`                        | `dd-sdk-android-rum`              |
| `com.datadog.android.ktx.tracing#okhttp3.Request.Builder.parentSpan`                      | `com.datadog.android.okhttp.trace#okhttp3.Request.Builder.parentSpan`                       | `dd-sdk-android-okhttp`           |

## Using a Secondary Instance of the SDK

Previously, the Datadog SDK implemented a singleton and only one SDK instance could exist in the application process. This created obstacles for use-cases like the usage of the SDK by 3rd party libraries.

With version 2.0 we addressed this limitation:

* It is now possible to initialize multiple instances of the SDK by associating them with a name.
* Many methods of the SDK can optionally take an SDK instance as an argument. If not provided, the call is associated with the default (nameless) SDK instance.

Here is an example illustrating how to initialize a secondary core instance and enable products:

```kotlin
val namedSdkInstance = Datadog.initialize("myInstance", context, configuration, trackingConsent)
val userInfo = UserInfo(...)
Datadog.setUserInfo(userInfo, sdkCore = namedSdkInstance)

Logs.enable(logsConfig, namedSdkInstance)
val logger = Logger.Builder(namedSdkInstance)
    ...
    .build()

Trace.enable(traceConfig, namedSdkInstance)
val tracer = AndroidTracer.Builder(namedSdkInstance)
    ...
    .build()

Rum.enable(rumConfig, namedSdkInstance)
GlobalRumMonitor.get(namedSdkInstance)

NdkCrashReports.enable(namedSdkInstance)

WebViewTracking.enable(webView, allowedHosts, namedSdkInstance)
```

**Note**: The SDK instance name should have the same value between application runs. Storage paths for SDK events are associated with it.

You can retrieve the named SDK instance by calling `Datadog.getInstance(<name>)` and use the `Datadog.isInitialized(<name>)` method to check if the particular SDK instance is initialized.