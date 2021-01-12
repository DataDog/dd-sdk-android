# Android Native Crash Collection

<div class="alert alert-info">The Android NDK module is in public alpha and not supported by Datadog.</div>

Send crash report for issues rising from the C/C++ code in your application.

**Note**: This package is an extension of the main package, so add both dependencies into your gradle file.

## Setup


1. Add the Gradle dependency by declaring the library as a dependency in your `build.gradle` file:

    ```conf
    repositories {
        maven { url "https://dl.bintray.com/datadog/datadog-maven" }
    }

    dependencies {
        implementation "com.datadoghq:dd-sdk-android:x.x.x"
        implementation "com.datadoghq:dd-sdk-android-ndk:x.x.x"
    }
    ```

2. Initialize the library with your application context, tracking consent, and the [Datadog client token][2] and Application ID generated when you create a new RUM application in the Datadog UI (see [Getting Started with Android RUM Collection][6] for more information). For security reasons, you must use a client token: you cannot use [Datadog API keys][3] to configure the `dd-sdk-android` library as they would be exposed client-side in the Android application APK byte code. For more information about setting up a client token, see the [client token documentation][2]:

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

   **Note**: Use the utility method `isInitialized` to check if the SDK is properly initialized:

    ```kotlin
    if (Datadog.isInitialized()) {
        // your code here
    }
    ```
    When writing your application, you can enable development logs by calling the `setVerbosity` method. All internal messages in the library with a priority equal to or higher than the provided level are then logged to Android's Logcat:

    ```kotlin
    Datadog.setVerbosity(Log.INFO)
    ```


[1]: https://docs.datadoghq.com/account_management/api-app-keys/#client-tokens
[2]: https://docs.datadoghq.com/account_management/api-app-keys/#api-keys
[3]: https://docs.datadoghq.com/real_user_monitoring/android/?tab=us
