# Datadog benchmark module

## Overview

This is a module for Datadog internal use to benchmark the overhead of SDK features.
It measures gauges that include CPU ticks, memory usage, and FPS of the application.

## Adding the dependency

The module is published as `com.datadoghq:dd-sdk-android-benchmark-internal`. It isn't on Maven Central: it is
published to `https://binaries.ddbuild.io/dd-sdk-android/benchmark/`, which is only reachable from Datadog's
network (VPN on laptops).

```kotlin
repositories {
    mavenCentral()
    maven("https://binaries.ddbuild.io/dd-sdk-android/benchmark/") {
        content { includeModule("com.datadoghq", "dd-sdk-android-benchmark-internal") }
    }
}

dependencies {
    implementation("com.datadoghq:dd-sdk-android-benchmark-internal:<version>")
}
```

* Keep `mavenCentral()`: the module's dependencies, including `dd-sdk-android-core` and `dd-sdk-android-internal`,
  still come from there.
* Every SDK release is published there with the same version as the SDK, and so is every `develop` build as
  `<version>-SNAPSHOT`.
* Right after a release, the module can be available before its `dd-sdk-android-core` and
  `dd-sdk-android-internal` dependencies are promoted on Maven Central, so it won't resolve until they are.
* Builds of the `dogfooding` branch are published with the rest of the SDK to
  `https://binaries.ddbuild.io/dd-sdk-android/dogfood/` instead (see `ci/scripts/dogfooding/README.md`).

## Getting started

1. Build the Datadog exporter configuration:

```kotlin
val configuration = DatadogExporterConfiguration.Builder("API_KEY")
    .setApplicationId("YOUR_APPLICATION_ID")
    .setApplicationName("Benchmark Application")
    .setApplicationVersion(BuildConfig.VERSION_NAME)
    .setIntervalInSeconds(30L)
    .build()

```

2. Create `DatadogMeter` with the configuration:

``` kotlin
val meter = DatadogMeter.create(configuration)
```

3. Start all gauges with meter:

```kotlin
meter.startGauges()
```

4. Stop all gauges with meter:

```kotlin
meter.stopGauges()
```

## Observe Metrics

All the metrics are uploaded
through the [Datadog metric submission API](https://docs.datadoghq.com/api/latest/metrics/#submit-metrics).
The metric names for each gauge:

* [android.benchmark.cpu]: CPU ticks
* [android.benchmark.memory]: Memory usage
* [android.benchmark.fps]: Frames per second
