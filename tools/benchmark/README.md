# Datadog benchmark module

## Overview

This is a module for Datadog internal use to benchmark the overhead of SDK features.
It measures gauges that include CPU ticks, memory usage, and FPS of the application.

## Adding the dependency

The module is published as `com.datadoghq:dd-sdk-android-benchmark-internal`. From 3.17.0, it isn't published to
Maven Central (earlier releases stay there): it is published to binaries.ddbuild.io, which is only reachable from
Datadog's network (VPN on laptops).

```kotlin
repositories {
    mavenCentral()
    maven("https://binaries.ddbuild.io/dd-sdk-android/releases/") {
        content { includeModule("com.datadoghq", "dd-sdk-android-benchmark-internal") }
    }
}

dependencies {
    implementation("com.datadoghq:dd-sdk-android-benchmark-internal:<version>")
}
```

* `releases/` has every SDK release from 3.17.0, with the same version as the SDK. Use an exact version: dogfood
  builds share that repository, so a dynamic version could resolve to one of them instead of a release.
* Keep `mavenCentral()`: for releases, the module's dependencies, including `dd-sdk-android-core` and
  `dd-sdk-android-internal`, come from there.
* Builds of the `dogfooding` branch are published to `releases/` too, with the rest of the SDK: to use them, replace
  `includeModule(...)` with `includeGroup("com.datadoghq")` (see `ci/scripts/dogfooding/README.md`).
* Every `develop` build is published as `<version>-SNAPSHOT` to `snapshots/`, not `releases/`. To use one, add both
  repositories below to the `repositories { }` block, since its `dd-sdk-android-core` and `dd-sdk-android-internal`
  snapshots are on Sonatype:

  ```kotlin
  maven("https://binaries.ddbuild.io/dd-sdk-android/snapshots/") {
      content { includeModule("com.datadoghq", "dd-sdk-android-benchmark-internal") }
  }
  maven("https://central.sonatype.com/repository/maven-snapshots/") {
      content { includeGroup("com.datadoghq") }
  }
  ```
* Right after a release, the module can be available before its `dd-sdk-android-core` and
  `dd-sdk-android-internal` dependencies are promoted on Maven Central, so it won't resolve until they are.

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
