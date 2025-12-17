# Datadog SDK instrumentation for Cronet

This module is responsible for Cronet library instrumentation.
At the current stage, only RUM Resource reporting is supported.

## Setup

1. Add the `dd-sdk-android-cronet` and Cronet engine implementation to your project:

```kotlin
dependencies {
    implementation("com.datadoghq:dd-sdk-android-cronet:<version>")
    implementation("com.google.android.gms:play-services-cronet:<version>")
}
```

**Note**: Datadog uses `cronet-api` version `141.7340.3` and has verified compatibility with the `play-services-cronet` implementation at version `17.0.1`.

2. Replace `CronetEngine.Builder` with `DatadogCronetEngine.Builder`:

```kotlin
@OptIn(ExperimentalRumApi::class)
private val cronetEngine: CronetEngine = DatadogCronetEngine.Builder(application)
    //...
    .build()
```


