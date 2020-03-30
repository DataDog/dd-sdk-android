# Troubleshooting

## Compilation issues

### Manifest merger failed

> Manifest merger failed : Attribute application@appComponentFactory value=(android.support.v4.app.CoreComponentFactory) from [com.android.support:support-compat:28.0.0] AndroidManifest.xml:22:18-91 is also present at [androidx.core:core:1.0.0] AndroidManifest.xml:22:18-86 value=(androidx.core.app.CoreComponentFactory).
> Suggestion: add 'tools:replace="android:appComponentFactory"' to <application> element at AndroidManifest.xml:7:5-14:19 to override.

The Datadog SDK for Android uses some AndroidX artifacts. Make sure you’re using AndroidX dependencies too (you can easily migrate your code to AndroidX using the `Refactor > Migrate to AndroidX` menu in Android Studio).

You also need to set the `android.useAndroidX` and `android.enableJetifier` properties to `true` in your project’s `gradle.properties` file.

### Duplicate class com.google.common.util.concurrent.ListenableFuture


> Duplicate class com.google.common.util.concurrent.ListenableFuture found in modules jetified-guava-jdk5-17.0.jar (com.google.guava:guava-jdk5:17.0) and jetified-listenablefuture-1.0.jar (com.google.guava:listenablefuture:1.0)

The Datadog SDK for Android uses AndroidX's WorkManager, which depends on a specific Guava dependency. If your own dependencies rely on different Guava artifacts, this issue can occur. It can easily be solved by excluding the conflicting module from your dependency, eg: 

```
implementation ("com.datadoghq:dd-sdk-android:1.3.0") {
    exclude group: 'com.google.guava', module: 'listenablefuture'
}
```

### Other

If you encounter another issue when building your application with the Datadog SDK for Android, you can [open an issue](https://github.com/DataDog/dd-sdk-android/issues/new?assignees=&labels=bug&template=bug_report.md&title=), and give us as many details as you can.


## Feature issues

### Foreword

If you think the SDK does not behave as it should, make sure you set the library's verbosity before running your application, as follow. It'll print relevant error messages in the Logcat that can help you locate the source of the problem.

```kotlin
    Datadog.setVerbosity(Log.VERBOSE)
```

### Logs/Traces are not appearing in your dashboard.

Make sure that you initialize the SDK using a valid Client Token. 
When the library's logs are enabled, you'll see the following message in the logcat :
 
> Unable to send batch because your token is invalid. Make sure that the provided token still exists.
