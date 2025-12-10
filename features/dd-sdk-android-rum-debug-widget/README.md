# Datadog RUM Debug Widget for Android

This module can be integrated into your application to help visualize RUM data being collected in **real time**.
It is intended for debugging and development purposes and **should not** be included in production builds by default.
As shown in the screenshot below, the widget provides a floating overlay that displays key metrics such as **memory usage**, **CPU load** and **RUM events**.

<img src="images/screenshot_rumdebugwidget.png" width="400" alt="Datadog RUM Debug Widget"/>

## Getting Started

To include this module in your project, add the following dependency to your application's `build.gradle` file:

```groovy
dependencies {
    debugImplementation("com.datadoghq:dd-sdk-android-rum-debug-widget:<latest-version>")
}
```

### Initial Setup

To enable the RUM Debug Widget, add the following line to your `RumConfiguration.Builder` setup in the `Application` class:

```kotlin
import com.datadog.android.insights.enableRumDebugWidget

RumConfiguration.Builder(...)
    .enableRumDebugWidget(application = this)
```

### Testing in Release Builds

To test the widget in a release build locally, you can temporarily change the dependency to `implementation` and enable it explicitly:

```groovy
dependencies {
    implementation("com.datadoghq:dd-sdk-android-rum-debug-widget:<latest-version>")
}
```

```kotlin
RumConfiguration.Builder(...)
    .enableRumDebugWidget(application = this, allowInRelease = true)
```

## Contributing

Pull requests are welcome, but please open an issue first to discuss what you
would like to change. For more information, read the 
[Contributing Guide](../../CONTRIBUTING.md).

## License

[Apache License, v2.0](../../LICENSE)
