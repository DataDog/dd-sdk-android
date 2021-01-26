# Android RUM Collection

Datadog Real User Monitoring (RUM) enables you to visualize and analyze the real-time performance and user journeys of your application's individual users.

## Setup


1. Declare [dd-sdk-android][1] as a dependency in your `build.gradle` file:

    ```conf
    repositories {
        maven { url "https://dl.bintray.com/datadog/datadog-maven" }
    }

    dependencies {
        implementation "com.datadoghq:dd-sdk-android:x.x.x"
    }
    ```

2. [Specify application details in Datadog UI][2] to generate a unique Datadog Application ID, and Client Token.

{{< img src="docs/images/screenshot_rum.png" alt="RUM Event hierarchy" style="width:50%;border:none" >}}

To ensure safety of your data, you must use a client token: you cannot use [Datadog API keys][3] to configure the `dd-sdk-android` library as they would be exposed client-side in the Android application APK byte code. For more information about setting up a client token, see the [client token documentation][4]

3. Initialize the library with application context and start sending data:

    {{< tabs >}}
    {{% tab "US" %}}
```kotlin
class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val config = DatadogConfig.Builder("<CLIENT_TOKEN>", "<ENVIRONMENT_NAME>", "<APPLICATION_ID>")
                        .build()
        Datadog.initialize(this, trackingConsent, config)
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
                        .useEUEndpoints()
                        .build()
        Datadog.initialize(this, trackingConsent, config)
    }
}
```
    {{% /tab %}}
    {{< /tabs >}}
    

4. To automatically track your views, provide a tracking strategy at initialization. Depending on your application's architecture, you can choose one of the strategies:


| Strategy | Description                                                                                                                                                                                                                                                   |
|------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
|  `ActivityViewTrackingStrategy`   | Every activity in your application is considered a distinct view. |
| `FragmentViewTrackingStrategy`     | Every fragment in your application is considered a distinct view.    |
| `MixedViewTrackingStrategy` | Every activity or fragment in your application is considered a distinct view.  |
| `NavigationViewTrackingStrategy`| Recommended for Android Jetpack Navigation library users. Each Navigation destination is considered a distinct view.  |
   
   ```kotlin
   class SampleApplication : Application() {
       override fun onCreate() {
           super.onCreate()
           
          val configuration = Configuration.Builder()
                           .trackInteractions()
                           .useViewTrackingStrategy(Strategy)
                           .build()
          
          Datadog.initialize(this, credentials, configuration, trackingConsent)
       }
   }
   ```
   
   **Tip**: For `ActivityViewTrackingStrategy`, `FragmentViewTrackingStrategy`, or `MixedViewTrackingStrategy` you can filter which `Fragment` or `Activity` is tracked as a RUM View by providing a `ComponentPredicate` implementation in the constructor.
   
   **Note**: By default, the library won't track any view. If you decide not to provide a view tracking strategy you will have to manually send the views by calling the `startView` and `stopView` methods yourself.


4. Configure and register the RUM Monitor. You only need to do it once, usually in your application's `onCreate()` method:

    ```kotlin
    val monitor = RumMonitor.Builder()
            // Optionally set a sampling between 0.0 and 100.0%
            // Here 75% of the RUM Sessions will be sent to Datadog
            .sampleRumSessions(75.0f)
            .build()
    GlobalRum.registerIfAbsent(monitor)
    ```

5. To track your OkHttp requests as resources, add the provided [Interceptor][5]:

    ```kotlin
    val okHttpClient =  OkHttpClient.Builder()
        .addInterceptor(DatadogInterceptor())
        .build()
    ```

    This creates RUM Resource data around each request processed by the OkHttpClient, with all the relevant information automatically filled (URL, method, status code, error). Note that only network requests started when a view is active will be tracked. If you want to track requests when your application is in the background, you can [create a view manually][7].

    **Note**: If you also use multiple Interceptors, this one must be called first.

6. (Optional) To get timing information in Resources (such as time to first byte, DNS resolution, etc.),  add the [Event][6] listener factory:

    ```kotlin
    val okHttpClient =  OkHttpClient.Builder()
        .addInterceptor(DatadogInterceptor())
        .eventListenerFactory(DatadogEventListener.Factory())
        .build()
    ```


## Further Reading

{{< partial name="whats-next/whats-next.html" >}}

[1]: https://github.com/DataDog/dd-sdk-android
[2]: https://app.datadoghq.com/rum/create
[3]: https://docs.datadoghq.com/account_management/api-app-keys/#api-keys
[4]: https://docs.datadoghq.com/account_management/api-app-keys/#client-tokens
[5]: https://square.github.io/okhttp/interceptors/
[6]: https://square.github.io/okhttp/events/
