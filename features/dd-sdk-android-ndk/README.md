# Datadog Android Native Crash Collection

Send crash report for issues rising from the C/C++ code in your application.

**Note**: Native crash reports can be sent to both RUM and Logs, so you need to add these modules as well.

## Setup

```groovy
dependencies {
    // if you want to send native crash reports to RUM product
    implementation "com.datadoghq:dd-sdk-android-rum:x.x.x"
    // if you want to send native crash reports to Logs product
    implementation "com.datadoghq:dd-sdk-android-logs:x.x.x"

    implementation "com.datadoghq:dd-sdk-android-ndk:x.x.x"
}
```

1. If you want to send native crash reports to RUM, [add and initialize RUM product in your application][1].
2. If you want to sent native crash reports to Logs, [add and initialize Logs product in your application][2].
3. Initialize NDK Crash Reporting feature in your application:

```kotlin
class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // RUM and/or Logs were initialized before
        NdkCrashReports.enable()
    }
}
```

```java
public class SampleApplication extends Application {
    @Override
    public void onCreate() {
      super.onCreate();
      
      // RUM and/or Logs were initialized before
      NdkCrashReports.enable();
    }
}
```

[1]: https://docs.datadoghq.com/real_user_monitoring/android/?tab=kotlin
[2]: https://docs.datadoghq.com/logs/log_collection/android/?tab=kotlin
