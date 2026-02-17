# Datadog RUM Debug Widget for Android

This module can be integrated into your application to help visualize RUM data being collected in **real time**.
It is intended for debugging and development purposes and **should be removed** before shipping to production.
As shown in the screenshot below, the widget provides a floating overlay that displays key metrics such as **memory usage**, **CPU load** and **RUM events**.

<img src="images/screenshot_rumdebugwidget.png" width="400" alt="Datadog RUM Debug Widget"/>

## Getting Started

Add the dependency to your application's `build.gradle.kts` file:

```kotlin
dependencies {
    implementation("com.datadoghq:dd-sdk-android-rum-debug-widget:<latest-version>")
}
```

Then enable the widget in your `RumConfiguration.Builder`:

```kotlin
import com.datadog.android.insights.enableRumDebugWidget

RumConfiguration.Builder(...)
    .enableRumDebugWidget(application = this)
    .build()
```

By default, the widget **only shows in debug builds**. In release builds, the call is a no-op.

### Testing in Release Builds

To test the widget in a release build (e.g., to simulate production environment), pass `allowInRelease = true`:

```kotlin
RumConfiguration.Builder(...)
    .enableRumDebugWidget(application = this, allowInRelease = true)
    .build()
```

### Before Shipping to Production

Remember to remove or comment out the dependency and the `enableRumDebugWidget` call before releasing to production:

```kotlin
dependencies {
    // Remove before release
    // implementation("com.datadoghq:dd-sdk-android-rum-debug-widget:<latest-version>")
}
```

## Contributing

Pull requests are welcome, but please open an issue first to discuss what you
would like to change. For more information, read the
[Contributing Guide](../../CONTRIBUTING.md).

## License

[Apache License, v2.0](../../LICENSE)
