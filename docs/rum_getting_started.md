# Getting Started with Android RUM Collection

Datadog Real User Monitoring (RUM) enables you to visualize and analyze the real-time performance and user journeys of your application's individual users.

## Setup

1. Declare SDK as a dependency.
2. Specify application details in UI.
3. Initialize the library with application context.
4. Initialize RUM Monitor, Interceptor and start sending data.

**Minimum Android OS version**: The Datadog Android SDK supports Android 4.4 (API level 19)+.


### Declare SDK as dependency

Declare [dd-sdk-android][1] and the [gradle plugin][13] as a dependency in your `build.gradle` file:

```
plugins {
    id("dd-sdk-android-gradle-plugin")
}
dependencies {
    implementation "com.datadoghq:dd-sdk-android:x.x.x" 
}
buildscript {
    dependencies {
        classpath("com.datadoghq:dd-sdk-android-gradle-plugin:x.x.x")
    }
}
```

### Specify application details in UI

1. Select UX Monitoring -> RUM Applications -> New Application
2. Choose `android` as your Application Type in [Datadog UI][2] and provide a new application name to generate a unique Datadog application ID and client token.

![image][12]

To ensure the safety of your data, you must use a client token. You cannot use only [Datadog API keys][3] to configure the `dd-sdk-android` library, as they would be exposed client-side in the Android application APK byte code. For more information about setting up a client token, see the [Client Token documentation][4].

### Initialize the library with application context

{{< site-region region="us" >}}
{{< tabs >}}
{{% tab "Kotlin" %}}
```kotlin
    class SampleApplication : Application() {
        override fun onCreate() {
            super.onCreate()
            val configuration = Configuration.Builder(
                    logsEnabled = true,
                    tracesEnabled = true,
                    crashReportsEnabled = true,
                    rumEnabled = true
                )
                .useSite(DatadogSite.US3)
                .trackInteractions()
                .trackLongTasks(durationThreshold)
                .useViewTrackingStrategy(strategy)
                .build()
            val credentials = Credentials(<CLIENT_TOKEN>, <ENV_NAME>, <APP_VARIANT_NAME>, <APPLICATION_ID>)
            Datadog.initialize(this, credentials, configuration, trackingConsent)
        }
    }
```
{{% /tab %}}
{{% tab "Java" %}}
```java
    public class SampleApplication extends Application { 
        @Override 
        public void onCreate() { 
            super.onCreate();
            final Configuration configuration = 
                    new Configuration.Builder(true, true, true, true)
                            .trackInteractions()
                            .trackLongTasks(durationThreshold)
                            .useViewTrackingStrategy(strategy)
                            .build();
               final Credentials credentials = new Credentials(<CLIENT_TOKEN>, <ENV_NAME>, <APP_VARIANT_NAME>, <APPLICATION_ID>);
               Datadog.initialize(this, credentials, configuration, trackingConsent); 
        }
    }
```
{{% /tab %}}
{{< /tabs >}}
{{< /site-region >}}

{{< site-region region="eu" >}}
{{< tabs >}}
{{% tab "Kotlin" %}}
```kotlin
    class SampleApplication : Application() {
        override fun onCreate() {
            super.onCreate()
            val configuration = Configuration.Builder(
                    logsEnabled = true,
                    tracesEnabled = true,
                    crashReportsEnabled = true,
                    rumEnabled = true
                )
                .useSite(DatadogSite.EU1)
                .trackInteractions()
                .trackLongTasks(durationThreshold)
                .useViewTrackingStrategy(strategy)
                .build()
            val credentials = Credentials(<CLIENT_TOKEN>, <ENV_NAME>, <APP_VARIANT_NAME>, <APPLICATION_ID>)
            Datadog.initialize(this, credentials, configuration, trackingConsent)
        }
    }
```
{{% /tab %}}
{{% tab "Java" %}}
```java
    public class SampleApplication extends Application { 
        @Override 
        public void onCreate() { 
            super.onCreate();
            final Configuration configuration = 
                    new Configuration.Builder(true, true, true, true)
                            .trackInteractions()
                            .trackLongTasks(durationThreshold)
                            .useViewTrackingStrategy(strategy)
                            .useSite(DatadogSite.EU1)
                            .build();
            Credentials credentials = new Credentials(<CLIENT_TOKEN>, <ENV_NAME>, <APP_VARIANT_NAME>, <APPLICATION_ID>);
            Datadog.initialize(this, credentials, configuration, trackingConsent); 
        }
    }
```
{{% /tab %}}
{{< /tabs >}}
{{< /site-region >}}

{{< site-region region="us3" >}}
{{< tabs >}}
{{% tab "Kotlin" %}}
```kotlin
    class SampleApplication : Application() {
        override fun onCreate() {
            super.onCreate()
            val configuration = Configuration.Builder(
                    logsEnabled = true,
                    tracesEnabled = true,
                    crashReportsEnabled = true,
                    rumEnabled = true
                )
                .useSite(DatadogSite.US3)
                .trackInteractions()
                .trackLongTasks(durationThreshold)
                .useViewTrackingStrategy(strategy)
                .build()
            val credentials = Credentials(<CLIENT_TOKEN>, <ENV_NAME>, <APP_VARIANT_NAME>, <APPLICATION_ID>)
            Datadog.initialize(this, credentials, configuration, trackingConsent)
        }
    }
```
{{% /tab %}}
{{% tab "Java" %}}
```java
    public class SampleApplication extends Application { 
        @Override 
        public void onCreate() { 
            super.onCreate();
            final Configuration configuration = 
                    new Configuration.Builder(true, true, true, true)
                            .trackInteractions()
                            .trackLongTasks(durationThreshold)
                            .useViewTrackingStrategy(strategy)
                            .useSite(DatadogSite.US3)
                            .build();
            Credentials credentials = new Credentials(<CLIENT_TOKEN>, <ENV_NAME>, <APP_VARIANT_NAME>, <APPLICATION_ID>);
            Datadog.initialize(this, credentials, configuration, trackingConsent); 
        }
    }
```
{{% /tab %}}
{{< /tabs >}}
{{< /site-region >}}

{{< site-region region="us5" >}}
{{< tabs >}}
{{% tab "Kotlin" %}}
```kotlin
    class SampleApplication : Application() {
        override fun onCreate() {
            super.onCreate()
            val configuration = Configuration.Builder(
                    logsEnabled = true,
                    tracesEnabled = true,
                    crashReportsEnabled = true,
                    rumEnabled = true
                )
                .useSite(DatadogSite.US5)
                .trackInteractions()
                .trackLongTasks(durationThreshold)
                .useViewTrackingStrategy(strategy)
                .build()
            val credentials = Credentials(<CLIENT_TOKEN>, <ENV_NAME>, <APP_VARIANT_NAME>, <APPLICATION_ID>)
            Datadog.initialize(this, credentials, configuration, trackingConsent)
        }
    }
```
{{% /tab %}}
{{% tab "Java" %}}
```java
    public class SampleApplication extends Application { 
        @Override 
        public void onCreate() { 
            super.onCreate();
            final Configuration configuration = 
                    new Configuration.Builder(true, true, true, true)
                            .trackInteractions()
                            .trackLongTasks(durationThreshold)
                            .useViewTrackingStrategy(strategy)
                            .useSite(DatadogSite.US5)
                            .build();
            Credentials credentials = new Credentials(<CLIENT_TOKEN>, <ENV_NAME>, <APP_VARIANT_NAME>, <APPLICATION_ID>);
            Datadog.initialize(this, credentials, configuration, trackingConsent); 
        }
    }
```
{{% /tab %}}
{{< /tabs >}}
{{< /site-region >}}

{{< site-region region="gov" >}}
{{< tabs >}}
{{% tab "Kotlin" %}}
```kotlin
    class SampleApplication : Application() {
        override fun onCreate() {
            super.onCreate()
            val configuration = Configuration.Builder(
                    logsEnabled = true,
                    tracesEnabled = true,
                    crashReportsEnabled = true,
                    rumEnabled = true
                )
                .useSite(DatadogSite.US1_FED)
                .trackInteractions()
                .trackLongTasks(durationThreshold)
                .useViewTrackingStrategy(strategy)
                .build()
            val credentials = Credentials(<CLIENT_TOKEN>, <ENV_NAME>, <APP_VARIANT_NAME>, <APPLICATION_ID>)
            Datadog.initialize(this, credentials, configuration, trackingConsent)
        }
    }
```
{{% /tab %}}
{{% tab "Java" %}}
```java
    public class SampleApplication extends Application { 
        @Override 
        public void onCreate() { 
            super.onCreate();
            final Configuration configuration = 
                    new Configuration.Builder(true, true, true, true)
                            .trackInteractions()
                            .trackLongTasks(durationThreshold)
                            .useViewTrackingStrategy(strategy)
                            .useSite(DatadogSite.US1_FED)
                            .build();
            Credentials credentials = new Credentials(<CLIENT_TOKEN>, <ENV_NAME>, <APP_VARIANT_NAME>, <APPLICATION_ID>);
            Datadog.initialize(this, credentials, configuration, trackingConsent); 
        }
    }
```
{{% /tab %}}
{{< /tabs >}}
{{< /site-region >}}

Learn more about [`ViewTrackingStrategy`][5] to enable automatic tracking of all your views (activities, fragments, etc.), [`trackingConsent`][6] to add GDPR compliance for your EU users, and [other configuration options][7] to initialize the library.

**Note**: In the credentials required for initialization, your application variant name is also required, and should use your `BuildConfig.FLAVOR` value (or an empty string if you don't have variants). This is important because it enables the right ProGuard `mapping.txt` file to be automatically uploaded at build time to be able to view de-obfuscated RUM error stack traces. For more information see the [guide to uploading Android source mapping files][8].

### Initialize RUM Monitor and Interceptor

Configure and register the RUM Monitor. You only need to do it once, usually in your application's `onCreate()` method:

{{< tabs >}}
{{% tab "Kotlin" %}}
   ```kotlin
        val monitor = RumMonitor.Builder().build()
        GlobalRum.registerIfAbsent(monitor)
   ```
{{% /tab %}}
{{% tab "Java" %}}
   ```java
        final RumMonitor monitor = new RumMonitor.Builder().build();
        GlobalRum.registerIfAbsent(monitor);
   ```
{{% /tab %}}
{{< /tabs >}}

To track your OkHttp requests as resources, add the provided [Interceptor][9]:

{{< tabs >}}
{{% tab "Kotlin" %}}
   ```kotlin
        val okHttpClient =  OkHttpClient.Builder()
            .addInterceptor(DatadogInterceptor())
            .build()
   ```
{{% /tab %}}
{{% tab "Java" %}}
   ```java
        final OkHttpClient okHttpClient =  new OkHttpClient.Builder()
            .addInterceptor(new DatadogInterceptor())
            .build();
   ```
{{% /tab %}}
{{< /tabs >}}

This records each request processed by the `OkHttpClient` as a resource in RUM, with all the relevant information automatically filled (URL, method, status code, error). Note that only network requests started when a view is active are tracked. If you want to track requests when your application is in the background, you can [create a view manually][10].

**Note**: If you also use multiple Interceptors, `DatadogInterceptor` must be called first.

You can further add an `EventListener` for the `OkHttpClient` to [automatically track resource timing][11] (third-party providers, network requests). 


## Further Reading

{{< partial name="whats-next/whats-next.html" >}}

[1]: https://github.com/DataDog/dd-sdk-android
[2]: https://app.datadoghq.com/rum/application/create
[3]: https://docs.datadoghq.com/account_management/api-app-keys/#api-keys
[4]: https://docs.datadoghq.com/account_management/api-app-keys/#client-tokens
[5]: /real_user_monitoring/android/advanced_configuration/#automatically-track-views
[6]: /real_user_monitoring/android/troubleshooting/#set-tracking-consent-gdpr-compliance
[7]: /real_user_monitoring/android/advanced_configuration/#initialization-parameters
[8]: /real_user_monitoring/error_tracking/android/#upload-your-mapping-file
[9]: https://square.github.io/okhttp/interceptors/
[10]: /real_user_monitoring/android/advanced_configuration/#custom-views
[11]: /real_user_monitoring/android/advanced_configuration/#automatically-track-network-requests
[12]: https://raw.githubusercontent.com/DataDog/dd-sdk-android/master/docs/images/create_rum_application.png
[13]: https://github.com/DataDog/dd-sdk-android-gradle-plugin
