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

Use the `CronetEngine.Builder.configureDatadogInstrumentation()` extension to enable instrumentation. You can enable RUM, APM, or both depending on your needs.

Below are common configurations compared with their OkHttp equivalents.


### 1. RUM + APM full tracing (recommended)

**OkHttp equivalent:** `addInterceptor(DatadogInterceptor)` + `addNetworkInterceptor(TracingInterceptor)`

Tracks HTTP requests as RUM Resources and creates APM spans at both application and network levels. Redirect hops and retries get individual spans, providing full visibility into the request lifecycle.

```kotlin
@OptIn(ExperimentalTracingApi::class, ExperimentalRumApi::class)
val cronetEngine = CronetEngine.Builder(context)
    .configureDatadogInstrumentation(
        apmInstrumentationConfiguration = ApmNetworkInstrumentationConfiguration(tracedHosts = listOf("api.example.com")),
        rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration()
    )
    .build()
```


### 2. RUM + APM application-level tracing

**OkHttp equivalent:** `addInterceptor(DatadogInterceptor)`

Tracks HTTP requests as RUM Resources and creates APM spans at the application level. Redirects and retries are not individually traced — a single span covers the entire request lifecycle.

```kotlin
@OptIn(ExperimentalTracingApi::class, ExperimentalRumApi::class)
val cronetEngine = CronetEngine.Builder(context)
    .configureDatadogInstrumentation(
        apmInstrumentationConfiguration = ApmNetworkInstrumentationConfiguration(tracedHosts = listOf("api.example.com"))
            .setTraceScope(ApmNetworkTracingScope.EXCLUDE_INTERNAL_REDIRECTS),
        rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration()
    )
    .build()
```

### 3. APM network-level tracing only

**OkHttp equivalent:** `addNetworkInterceptor(TracingInterceptor)`

Creates APM spans at the network level — each redirect hop and retry gets its own span. No RUM Resource tracking.

```kotlin
@OptIn(ExperimentalTracingApi::class, ExperimentalRumApi::class)
val cronetEngine = CronetEngine.Builder(context)
    .configureDatadogInstrumentation(
        rumInstrumentationConfiguration = null,
        apmInstrumentationConfiguration = ApmNetworkInstrumentationConfiguration(tracedHosts = listOf("api.example.com"))
    )
    .build()
```



### 4. RUM only (no APM tracing)

Tracks HTTP requests as RUM Resources only. No APM spans or tracing headers.

```kotlin
@OptIn(ExperimentalTracingApi::class, ExperimentalRumApi::class)
val cronetEngine = CronetEngine.Builder(context)
    .configureDatadogInstrumentation(
        rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration(),
        apmInstrumentationConfiguration = null
    )
    .build()
```

### 5. RUM + distributed tracing headers only (no client-side APM spans)

Tracks HTTP requests as RUM Resources and injects distributed tracing headers for server-side correlation, without creating client-side APM spans.

```kotlin
@OptIn(ExperimentalTracingApi::class, ExperimentalRumApi::class)
val cronetEngine = CronetEngine.Builder(context)
    .configureDatadogInstrumentation(
        rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration(),
        apmInstrumentationConfiguration = null,
        distributedTracingConfiguration = ApmNetworkInstrumentationConfiguration(
            tracedHosts = listOf("api.example.com")
        )
    )
    .build()
```

## Distributed tracing headers and RUM–APM linking

When RUM is enabled, the SDK automatically injects distributed tracing headers (`x-datadog-trace-id`, `x-datadog-parent-id`, etc.) into outgoing requests. These headers allow the Datadog backend to link RUM Resources to the corresponding server-side APM spans, making it possible to trace a user action end-to-end from the mobile app through the backend services.

### How it works

The SDK uses a separate tracing pipeline for header injection, controlled by the `distributedTracingConfiguration` parameter. This pipeline:

- Creates a local trace span with `EXCLUDE_INTERNAL_REDIRECTS` scope (one trace ID per RUM resource, regardless of redirects).
- Injects the tracing headers into the request before it is sent to the network.
- Does **not** send the local span to the APM backend — it exists only to carry the trace context.

The modified request (with headers) is then passed to both RUM resource tracking and to the APM instrumentation (if configured), ensuring that the trace context propagates consistently.

### Default behavior

When `distributedTracingConfiguration` is not specified, the SDK reuses the `apmInstrumentationConfiguration` host list for header injection. Only requests to hosts listed in `apmInstrumentationConfiguration` receive tracing headers, and the same hosts get client-side APM spans.

### Customising with `distributedTracingConfiguration`

Provide `distributedTracingConfiguration` explicitly when the host list for header injection should differ from the host list for APM spans. Common reasons:

- **Broader header injection than APM spans.** You want RUM resources for CDN or third-party hosts to link to server-side traces, but you do not want the SDK to create client-side APM spans for those hosts.

  ```kotlin
  @OptIn(ExperimentalTracingApi::class, ExperimentalRumApi::class)
  val cronetEngine = CronetEngine.Builder(context)
      .configureDatadogInstrumentation(
          rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration(),
          apmInstrumentationConfiguration = ApmNetworkInstrumentationConfiguration(
              tracedHosts = listOf("api.example.com") // APM spans for backend only
          ),
          distributedTracingConfiguration = ApmNetworkInstrumentationConfiguration(
              tracedHosts = listOf("api.example.com", "cdn.example.com") // headers for both
          )
      )
      .build()
  ```

- **Header injection without APM spans.** Set `apmInstrumentationConfiguration = null` and provide `distributedTracingConfiguration` to inject tracing headers for RUM correlation without creating any client-side spans (see [configuration 5](#5-rum--distributed-tracing-headers-only-no-client-side-apm-spans) above).

- **Different sampling or header format.** Pass a configuration with a different sampler or `TracingHeaderType` to control how distributed tracing headers are formatted for the RUM pipeline independently from the APM pipeline.

> **Note:** `distributedTracingConfiguration` is only active when `rumInstrumentationConfiguration` is also provided. Without RUM enabled there is no RUM resource to link, so the SDK ignores this parameter.

## Known Limitations

### Tracing headers on redirects

Cronet's `UrlRequest.followRedirect()` does not allow modifying request headers. This has the following implications when using `ApmNetworkTracingScope.ALL`:

- Client-side spans for redirect hops are created correctly — the local trace tree is complete.
- However, tracing headers (`x-datadog-trace-id`, `x-datadog-parent-id`, etc.) are **not** updated on redirect — the server on the redirect target receives the headers from the original request.
- This breaks **server-side** distributed tracing correlation for redirect hops, since the redirect target sees a `parent-id` that belongs to the original request span rather than the redirect hop span.

This limitation does not apply when using `ApmNetworkTracingScope.EXCLUDE_INTERNAL_REDIRECTS`, since redirect hops are not individually traced and the original headers remain valid.

### RUM-to-APM linking with `ApmNetworkTracingScope.ALL`

When `ApmNetworkTracingScope.ALL` is used together with RUM, the SDK automatically switches the distributed tracing scope to `EXCLUDE_INTERNAL_REDIRECTS`. Because Cronet does not allow modifying headers on redirect, using `ALL` scope with RUM would create multiple APM spans per RUM resource (one for the initial request and one for each redirect hop), making a 1:1 RUM–APM link ambiguous.

In the following configuration, RUM Resources will **not** link directly to APM Spans in the Datadog UI:

```kotlin
@OptIn(ExperimentalTracingApi::class, ExperimentalRumApi::class)
val cronetEngine = CronetEngine.Builder(context)
    .configureDatadogInstrumentation(
        apmInstrumentationConfiguration = ApmNetworkInstrumentationConfiguration(tracedHosts = listOf("api.example.com"))
            .setTraceScope(ApmNetworkTracingScope.ALL),
        rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration()
    )
    .build()
```

If RUM-to-APM linking is important for your use case, use `ApmNetworkTracingScope.EXCLUDE_INTERNAL_REDIRECTS` instead (see [configuration 2](#2-rum--apm-application-level-tracing)). This creates a single span per RUM resource and preserves the link.
