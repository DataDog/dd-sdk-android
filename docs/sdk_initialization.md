#SDK Initialization

1. Add the Gradle dependency by declaring the library as a dependency in your `build.gradle` file:

    ```conf
    repositories {
        maven { url "https://dl.bintray.com/datadog/datadog-maven" }
    }

    dependencies {
        implementation "com.datadoghq:dd-sdk-android:x.x.x"
    }
    ```

2. Initialize the library with your application context, [tracking consent][3], and the [Datadog client token][1] and Application ID generated when you create a new RUM application in the Datadog UI (see [Getting Started with Android RUM Collection][4] for more information). For security reasons, you must use a client token: you cannot use [Datadog API keys][2] to configure the `dd-sdk-android` library as they would be exposed client-side in the Android application APK byte code. For more information about setting up a client token, see the [client token documentation][1]:
    
    {{< tabs >}}
    {{% tab "US" %}} 

```kotlin
class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val configuration = Configuration.Builder().build()
        val credentials = Credentials(
           <CLIENT_TOKEN>,
           <ENV_NAME>,
           <APP_VARIANT_NAME>,
           <APPLICATION_ID>
        )
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
      
      val configuration = Configuration.Builder()
         .useEUEndpoints()
         .build()
      val credentials = Credentials(
         <CLIENT_TOKEN>,
      <ENV_NAME>,
      <APP_VARIANT_NAME>,
      <APPLICATION_ID>
      )
      Datadog.initialize(this, credentials, configuration, trackingConsent)
   }
```

    {{% /tab %}}
    {{< /tabs >}}

Note that in the credentials required for initialization, your application variant name is also required. This is important because it enables  the right proguard `mapping.txt` file to be automatically uploaded at build time. This allows a Datadog dashboard to de-obfuscate the stack traces.

   Use the utility method `isInitialized` to check if the SDK is properly initialized:

   ```kotlin
    if(Datadog.isInitialized()){
        // your code here
    }
   ```
   When writing your application, you can enable development logs by calling the `setVerbosity` method. All internal messages in the library with a priority equal to or higher than the provided level are then logged to Android's Logcat:
   ```kotlin
   Datadog.setVerbosity(Log.INFO)
   ```
**Note**: The `dd-sdk-android` library supports all Android versions from API level 19 (KitKat).

[1]: https://docs.datadoghq.com/account_management/api-app-keys/#client-tokens
[2]: https://docs.datadoghq.com/account_management/api-app-keys/#api-keys
[3]: gdpr.md
[4]: https://docs.datadoghq.com/real_user_monitoring/android/?tab=us
