# Datadog benchmark module

## Overview

This is a module for Datadog internal use to benchmark the overhead of SDK features.
It measures gauges that include CPU ticks, memory usage, and FPS of the application.

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
through [Datadog submit metrics API](https://docs.datadoghq.com/api/latest/metrics/#submit-metrics).
The metric names for each gauge:

* [android.benchmark.cpu]: CPU ticks
* [android.benchmark.memory]: Memory usage
* [android.benchmark.fps]: Frames per second
