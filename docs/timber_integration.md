# Android Timber Integration

Send automatically Timber logs to Datadog from your Android applications.

**Note**: This package is an extension of the main package, so add both dependencies into your gradle file.

## Setup

```configure
repositories {
    maven { url "https://dl.bintray.com/datadog/datadog-maven" }
}

dependencies {
    implementation "com.datadoghq:dd-sdk-android:x.x.x"
    implementation "com.datadoghq:dd-sdk-android-timber:x.x.x"
}
```

### Timber integration setup

```kotlin
val  logger = Logger.Builder()
        .setNetworkInfoEnabled(true)
        .setLogcatLogsEnabled(true)
        .setDatadogLogsEnabled(true)
        .build();

Timber.plant(DatadogTree(logger))
```
