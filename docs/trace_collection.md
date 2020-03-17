# Android Trace Collection

<div class="alert alert-info">The Android trace collection is in public beta, and is currently not supported by Datadog.</div>

Send [traces][1] to Datadog from your Android applications with [Datadog's `dd-sdk-android` client-side tracing library][2] and leverage the following features:

* Create custom [spans][3] for operations in your application.
* Add `context` and extra custom attributes to each span sent.
* Optimized network usage with automatic bulk posts.

**Note**: Traces on Android are still experimental, and will be available in the `dd-sdk-android` library version `1.4.0` or higher. The `dd-sdk-android` library supports all Android versions from API level 19 (Kit-Kat).

## Setup

1. Add the Gradle dependency by declaring the library as a dependency in your `build.gradle` file:

    ```conf
    repositories {
        maven { url "https://dl.bintray.com/datadog/datadog-maven" }
    }

    dependencies {
        implementation "com.datadoghq:dd-sdk-android:x.x.x" {
            exclude group: "com.google.guava", module: "listenablefuture"
            exclude group: "com.lmax", module: "disruptor"
        }
    }
    ```

2. Initialize the library with your application context and your [Datadog client token][4]. For security reasons, you must use a client token: you cannot use [Datadog API keys][5] to configure the `dd-sdk-android` library as they would be exposed client-side in the Android application APK byte code. For more information about setting up a client token, see the [client token documentation][4]:

    {{< tabs >}}
    {{% tab "US" %}}

```kotlin
class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val config = DatadogConfig.Builder(BuildConfig.DD_CLIENT_TOKEN)
                        .build()
        Datadog.initialize(this, config)
    }
}
```

    {{% /tab %}}
    {{% tab "EU" %}}

```kotlin
class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val config = DatadogConfig.Builder(BuildConfig.DD_CLIENT_TOKEN)
                        .setServiceName("<SERVICE_NAME>")
                        .useEUEndpoints()
                        .build()
        Datadog.initialize(this, config)
    }
}
```

    {{% /tab %}}
    {{< /tabs >}}

3. Configure and register the Android Tracer. You only need to do it once, usually in your application's `onCreate()` method:

    ```kotlin
    val tracer = AndroidTracer.Builder().build()
    io.opentracing.util.GlobalTracer.registerIfAbsent(tracer)
    ```

4. (Optional) - Set the partial flush threshold. You can optimize the workload of the SDK if you create a lot of spans in your application, or on the contrary very few of them. The library waits until the number of finished spans gets above the threshold to write them on disk. A value of `1` writes each span as soon as its finished.

    ```kotlin
    val tracer = AndroidTracer.Builder()
        .setPartialFlushThreshold(10)
        .build()
    ```

5. Start a custom span using the following method:

    ```kotlin
    val tracer = GlobalTracer.get()
    val span = tracer.buildSpan("<SPAN_NAME>").start()
    // Do something ...
    // ...
    // Then when the span should be closed
    span.finish()
    ```

6. (Optional) - Provide additional tags alongside your span.

    ```kotlin
    span.setTag("http.url", url)
    ```

## Integrations

In addition to manual tracing, the `dd-sdk-android` library provides the following integration.

### OkHttp

If you want to trace your OkHttp requests, you can add the provided [Interceptor][6] as follow:

```kotlin
val okHttpClient =  OkHttpClient.Builder()
    .addInterceptor(TracingInterceptor())
    .build()
```

This creates a span around each request processed by the OkHttpClient, with all the relevant information automatically filled (url, method, status code, error), and propagates the tracing information to your backend to get a unified trace within Datadog

**Note**: If you use multiple Interceptors, this one must be called first.

## Further Reading

{{< partial name="whats-next/whats-next.html" >}}

[1]: https://docs.datadoghq.com/tracing/visualization/#trace
[2]: https://github.com/DataDog/dd-sdk-android
[3]: https://docs.datadoghq.com/tracing/visualization/#spans
[4]: https://docs.datadoghq.com/account_management/api-app-keys/#client-tokens
[5]: https://docs.datadoghq.com/account_management/api-app-keys/#api-keys
[6]: https://square.github.io/okhttp/interceptors/
