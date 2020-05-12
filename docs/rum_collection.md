# Android RUM Collection

<div class="alert alert-info">The Android RUM collection is in public beta, and is currently not supported by Datadog.</div>

Send [Real User Monitoring data][1] to Datadog from your Android applications with [Datadog's `dd-sdk-android` client-side RUM library][2] and leverage the following features:

* get a global idea about your app’s performance and demographics;
* understand which resources are the slowest;
* analyze errors by OS and device type.

**Note**: RUM on Android is still experimental, and will be available in the `dd-sdk-android` library version `1.5.0` or higher. The `dd-sdk-android` library supports all Android versions from API level 19 (Kit-Kat).

## Setup

1. Add the Gradle dependency by declaring the library as a dependency in your `build.gradle` file:

    ```conf
    repositories {
        maven { url "https://dl.bintray.com/datadog/datadog-maven" }
    }

    dependencies {
        implementation "com.datadoghq:dd-sdk-android:x.x.x"
    }
    ```

2. Initialize the library with your application context and your [Datadog client token][4]. For security reasons, you must use a client token: you cannot use [Datadog API keys][5] to configure the `dd-sdk-android` library as they would be exposed client-side in the Android application APK byte code. For more information about setting up a client token, see the [client token documentation][4]. You also need to provide an Application ID (see our [RUM Getting Started page][3]).

    {{< tabs >}}
    {{% tab "US" %}}

```kotlin
class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val config = DatadogConfig.Builder("<CLIENT_TOKEN>", "<ENVIRONMENT_NAME>", "<APPLICATION_ID>")
                        .trackGestures()
                        .useViewTrackingStrategy(strategy)
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

        val config = DatadogConfig.Builder("<CLIENT_TOKEN>", "<ENVIRONMENT_NAME>", "<APPLICATION_ID>")
                        .trackGestures()
                        .useViewTrackingStrategy(strategy)
                        .useEUEndpoints()
                        .build()
        Datadog.initialize(this, config)
    }
}
```

    {{% /tab %}}
    {{< /tabs >}}

Depending on your application's architecture, you can choose one of several implementations of `ViewTrackingStrategy`:

  - `ActivityViewTrackingStrategy`: Every activity in your application is considered a distinct view.
  - `FragmentViewTrackingStrategy`: Every fragment in your application is considered a distinct view.
  - `NavigationViewTrackingStrategy`: If you use the Android Jetpack Navigation library, this is the recommended strategy. It automatically tracks the navigation destination as a distinct view.
  - `MixedViewTrackingStrategy`: Every activity or fragment in your application is considered a distinct view. This strategy is a mix between the `ActivityViewTrackingStrategy` and `FragmentViewTrackingStrategy`.


  **Note**: For `ActivityViewTrackingStrategy`, `FragmentViewTrackingStrategy`, or `MixedViewTrackingStrategy` you can validate which `Fragment` or `Activity` is tracked as a RUM view event by providing a `ComponentPredicate` implementation in the constructor.

3. Configure and register the RUM Monitor. You only need to do it once, usually in your application's `onCreate()` method:

    ```kotlin
    val monitor = RumMonitor.Builder().build()
    GlobalRum.registerIfAbsent(monitor)
    ```

4. If you want to track your OkHttp requests as resources, you can add the provided [Interceptor][6] as follows:

    ```kotlin
    val okHttpClient =  OkHttpClient.Builder()
        .addInterceptor(DatadogInterceptor())
        .build()
    ```

    This creates RUM resource data around each request processed by the OkHttpClient, with all the relevant information automatically filled (URL, method, status code, error).

    **Note**: If you use multiple Interceptors, this one must be called first.
    
5. (Optionnal) If you want to get timing information in Resources (such as time to first byte, DNS resolution, …), you can add the provided [Event][6] listener as follows:

    ```kotlin
    val okHttpClient =  OkHttpClient.Builder()
        .addInterceptor(DatadogInterceptor())
        .eventListenerFactory(DatadogEventListener.Factory())
        .build()
    ```

## Further Reading

{{< partial name="whats-next/whats-next.html" >}}

[1]: https://docs.datadoghq.com/real_user_monitoring/data_collected/
[2]: https://github.com/DataDog/dd-sdk-android
[3]: https://docs.datadoghq.com/real_user_monitoring/installation/?tab=us
[4]: https://docs.datadoghq.com/account_management/api-app-keys/#client-tokens
[5]: https://docs.datadoghq.com/account_management/api-app-keys/#api-keys
[6]: https://square.github.io/okhttp/interceptors/
[7]: https://square.github.io/okhttp/events/
