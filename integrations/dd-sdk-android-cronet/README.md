# Datadog SDK instrumentation for Cronet

This module provides Datadog instrumentation for [Cronet](https://developer.android.com/guide/topics/connectivity/cronet) HTTP requests. It supports:

- **RUM Resource tracking** — automatically reports HTTP requests as RUM Resources with timing information.
- **APM Tracing** — creates trace spans for HTTP requests and injects tracing headers into first-party host requests for distributed tracing.

## Setup

Add the `dd-sdk-android-cronet` and a Cronet engine implementation to your project:

```kotlin
dependencies {
    implementation("com.datadoghq:dd-sdk-android-cronet:<version>")
    implementation("com.google.android.gms:play-services-cronet:<version>")
}
```

**Note**: Datadog uses `cronet-api` version `141.7340.3` and has verified compatibility with the `play-services-cronet` implementation at version `17.0.1`.

## Configuration

Use the `CronetEngine.Builder.configureDatadogInstrumentation()` extension to enable
instrumentation. You can enable RUM, APM, or both depending on your needs.

Below are common configurations compared with their [OkHttp equivalents](https://github.com/DataDog/dd-sdk-android/tree/develop/integrations/dd-sdk-android-okhttp).


### 1. RUM + APM application-level tracing (recommended)

**OkHttp equivalent:** `addInterceptor(DatadogInterceptor)`

Tracks HTTP requests as RUM Resources and creates APM spans at the application level.
Redirects are not individually traced — a single span covers the entire request lifecycle.
This is the default behavior.

```kotlin
import com.datadog.android.cronet.configureDatadogInstrumentation
import com.datadog.android.rum.configuration.RumNetworkInstrumentationConfiguration
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.trace.ApmNetworkInstrumentationConfiguration
import com.datadog.android.trace.ExperimentalTraceApi

@OptIn(ExperimentalTraceApi::class, ExperimentalRumApi::class)
val cronetEngine = CronetEngine.Builder(context)
    .configureDatadogInstrumentation(
        apmInstrumentationConfiguration = ApmNetworkInstrumentationConfiguration(tracedHosts = listOf("api.example.com")),
        rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration()
    )
    .build()
```

**Note:** Both RUM and Trace features should be enabled: 
```kotlin
import com.datadog.android.rum.Rum
import com.datadog.android.trace.Trace

Trace.enable(...)
Rum.enable(...)
```

### 2. RUM + APM full tracing (including redirects)

**OkHttp equivalent:** `addInterceptor(DatadogInterceptor)` + `addNetworkInterceptor(TracingInterceptor)`

Tracks HTTP requests as RUM Resources and creates APM spans at both application and network levels.
Redirect hops are assigned individual spans, which provide full visibility of the request lifecycle.

```kotlin
@OptIn(ExperimentalTraceApi::class, ExperimentalRumApi::class)
val cronetEngine = CronetEngine.Builder(context)
    .configureDatadogInstrumentation(
        apmInstrumentationConfiguration = ApmNetworkInstrumentationConfiguration(tracedHosts = listOf("api.example.com"))
            .setTraceScope(ApmNetworkTracingScope.ALL),
        rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration()
    )
    .build()
```

**Note:** Both RUM and Trace features should be enabled:
```kotlin
Trace.enable(...)
Rum.enable(...)
```

### 3. RUM + distributed tracing (no client-side APM spans)

When `apmInstrumentationConfiguration` is provided, RUM-APM linking is enabled by default — the SDK injects `x-datadog-trace-id` and `x-datadog-parent-id` headers into outgoing requests, allowing you to navigate between RUM Resources and APM Spans in the Datadog UI and build end-to-end distributed traces across client, backend, and other components.

However, this also enables client-side APM spans. If you want RUM-APM linking **without** client-side network spans, call `setHeaderPropagationOnly)` on the APM configuration:

```kotlin
@OptIn(ExperimentalTraceApi::class, ExperimentalRumApi::class)
val cronetEngine = CronetEngine.Builder(context)
    .configureDatadogInstrumentation(
        rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration(),
        apmInstrumentationConfiguration = ApmNetworkInstrumentationConfiguration(
            tracedHosts = listOf("api.example.com")
        ).setHeaderPropagationOnly()
    )
    .build()
```

**Note:** Both RUM and Trace features should be enabled:
```kotlin
Trace.enable(...)
Rum.enable(...)
```

### 4. APM tracing only (no RUM)

**OkHttp equivalent:** `addNetworkInterceptor(TracingInterceptor)`

Creates APM spans at the network level — each redirect hop gets its own span. No RUM resource tracking.

```kotlin
@OptIn(ExperimentalTraceApi::class, ExperimentalRumApi::class)
val cronetEngine = CronetEngine.Builder(context)
    .configureDatadogInstrumentation(
        rumInstrumentationConfiguration = null,
        apmInstrumentationConfiguration = ApmNetworkInstrumentationConfiguration(tracedHosts = listOf("api.example.com"))
    )
    .build()
```

**Note:** Trace feature should be enabled: `Trace.enable(...)`

### 5. RUM only (no APM tracing)

**OkHttp equivalent:** `addInterceptor(DatadogInterceptor)` without `Trace.enable(tracesConfig)`

Tracks HTTP requests as RUM Resources only. No APM spans or tracing headers.

```kotlin
@OptIn(ExperimentalTraceApi::class, ExperimentalRumApi::class)
val cronetEngine = CronetEngine.Builder(context)
    .configureDatadogInstrumentation(
        rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration(),
        apmInstrumentationConfiguration = null
    )
    .build()
```
**Note:** RUM feature should be enabled: `Rum.enable(...)`


## Known Limitations

### Tracing redirects

When `ApmNetworkTracingScope.ALL` is used together with RUM, 
client spans will be created for both the original request and the redirect hops. 
However, tracing headers (`x-datadog-trace-id`, `x-datadog-parent-id`, etc.) will only be added for
the original request, as Cronet does not allow headers to be modified on redirect requests.

```kotlin
@OptIn(ExperimentalTraceApi::class, ExperimentalRumApi::class)
val cronetEngine = CronetEngine.Builder(context)
    .configureDatadogInstrumentation(
        apmInstrumentationConfiguration = ApmNetworkInstrumentationConfiguration(tracedHosts = listOf("api.example.com"))
            .setTraceScope(ApmNetworkTracingScope.ALL),
        rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration()
    )
    .build()
```

### Tracing retries

At the moment, Cronet does not allow retry requests to be intercepted, so retries cannot be instrumented directly.


