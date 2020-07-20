# Android Native Crash Collection

<div class="alert alert-info">The Android NDK module is in public alpha and not supported by Datadog.</div>

Send crash report for issues rising from the C/C++ code in your application.

**Note**: This package is an extension of the main package, so add both dependencies into your gradle file.

## Setup

```conf
repositories {
    maven { url "https://dl.bintray.com/datadog/datadog-maven" }
}

dependencies {
    implementation "com.datadoghq:dd-sdk-android:x.x.x"
    implementation "com.datadoghq:dd-sdk-android-ndk:x.x.x"
}
```

### Plugins

1. The NDKCrashReporterPlugin handles the crash signals at the native level and reports them as errors by adding more explicit information (for example: backtrace, signal name, signal relevant error message) in the Datadog logs dashboard:

```kotlin

val config = DatadogConfig.Builder("<CLIENT_TOKEN>", "<ENVIRONMENT_NAME>", "<APPLICATION_ID>")
                .addPlugin(NDKCrashReporterPlugin(), Feature.CRASH)
                .build()
Datadog.initialize(this, config)

```
