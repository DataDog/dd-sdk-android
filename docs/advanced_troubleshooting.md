# Advanced Troubleshooting

## Compilation issues

### Manifest merger failed

> Manifest merger failed : Attribute application@appComponentFactory value=(android.support.v4.app.CoreComponentFactory) from [com.android.support:support-compat:28.0.0] AndroidManifest.xml:22:18-91 is also present at [androidx.core:core:1.0.0] AndroidManifest.xml:22:18-86 value=(androidx.core.app.CoreComponentFactory).
> Suggestion: add 'tools:replace="android:appComponentFactory"' to <application> element at AndroidManifest.xml:7:5-14:19 to override.

The Datadog SDK for Android uses some AndroidX artifacts. Make sure you’re using AndroidX dependencies too (you can migrate your code to AndroidX using the `Refactor > Migrate to AndroidX` menu in Android Studio).

You also need to set the `android.useAndroidX` and `android.enableJetifier` properties to `true` in your project’s `gradle.properties` file.

### Duplicate class com.google.common.util.concurrent.ListenableFuture

> Duplicate class com.google.common.util.concurrent.ListenableFuture found in modules jetified-guava-jdk5-17.0.jar (com.google.guava:guava-jdk5:17.0) and jetified-listenablefuture-1.0.jar (com.google.guava:listenablefuture:1.0)

This issue can occur if your dependencies rely on different Guava artifacts as the Datadog SDK for Android. The SDK uses AndroidX's WorkManager, which depends on a specific Guava dependency.

Solve this issue by excluding the conflicting module from your dependency:

```
implementation ("com.datadoghq:dd-sdk-android:1.3.0") {
    exclude group: 'com.google.guava', module: 'listenablefuture'
}
```

### Duplicate class kotlin.collections.jdk8.CollectionsJDK8Kt found

> Duplicate class kotlin.collections.jdk8.CollectionsJDK8Kt found in modules kotlin-stdlib-1.8.10 (org.jetbrains.kotlin:kotlin-stdlib:1.8.10) and kotlin-stdlib-jdk8-1.7.20 (org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.20)

You need to add the following rules to your buildscript (more details [here](https://stackoverflow.com/a/75298544)):

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

### Other

If you encounter another issue when building your application with the Datadog SDK for Android, open an issue on the [DataDog/dd-sdk-android Github repository](https://github.com/DataDog/dd-sdk-android/issues/new?assignees=&labels=bug&template=bug_report.md&title=) with as many details as you can.

## Runtime Issues

### Crash with `java.lang.NoSuchMethodError: No virtual method setInitialDelay`

If your application crashes with a similar message, this is most likely due to a conflict in the Android WorkManager dependency. The `dd-sdk-android` library uses the AndroidX `androidx.work:work-runtime` artifact with a version above `2.2.0`. There was a change in the signature of the `WorkRequest.Builder` class, that make modules built with a previous version incompatible. Make sure your modules and dependency target the same version of the `androidx.work:work-runtime` library.

You can check which version your dependencies are using by typing the following command in a shell prompt:

```shell script
./gradlew :app:dependencies
```

### Crash with `java.lang.NoSuchMethodError: No static method metafactory(…)` or `java.lang.BootstrapMethodError: Exception from call site #1 bootstrap method`

If you're using OkHttp version `3.13.0` or above, you may run into this crash when initializing the SDK. This happens because from this version on, OkHttp uses Java 1.8, including the Java lambda features, which are not available in Android.

You can fix this issue by forcing the Android compiler to use Java 8 compatibility when building your application. To do so, add the following code to your application's `build.gradle` script.

```groovy
android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

// optionally if you're using Kotlin  
kotlinOptions {
    jvmTarget = "1.8"
}
```

## SDK behavior issues

### Foreword

If you think the SDK does not behave as it should, make sure you set the library's verbosity to `VERBOSE` before running your application, as follows. It prints relevant error messages in the Logcat that can help you locate the source of the problem.

```kotlin
    Datadog.setVerbosity(Log.VERBOSE)
```

### Logs/Traces are not appearing in your dashboard.

Make sure that you initialized the SDK using a valid [Client Token](https://docs.datadoghq.com/account_management/api-app-keys/#client-tokens) and you are targeting the correct Datadog site.
Otherwise, if the library's logs are enabled, you should see the following message in the Logcat :

> "$batchInfo failed because your token is invalid; the batch was dropped. Make sure that the provided token still exists and you're targeting the relevant Datadog site."

### RUM `FragmentViewTrackingStrategy` is not working correctly with the `ViewPager`

If you have a `FragmentViewPager` somewhere in your activities this can produce wrong View events if you are using the RUM `FragmentViewTrackingStrategy`.
The reason for this is the way the `FragmentPagerAdapter` used to work by resuming **current** and **next** fragment to be able to resolve the
animated transition from one page to another. Please note that this behaviour is deprecated in the `FragmentPagerAdapter`. To avoid these issues please
makes sure you switch to the new way of instantiating your `FragmentPagerAdapter` :
```kotlin
  FragmentPagerAdapter(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT)
```

### RUM view debugging

You can use `RumMonitor#debug` property, which once enable will show you the overlay in the application with the actual RUM view name.
