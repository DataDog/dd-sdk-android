# Datadog Integration for Coil

## Getting Started 

To include the Datadog integration for [Coil][1] in your project, add the
following to your application's `build.gradle.kts` file.

```kotlin
dependencies {
    implementation("com.datadoghq:dd-sdk-android-rum:<latest-version>")
    implementation("com.datadoghq:dd-sdk-android-okhttp:<latest-version>")
    implementation("com.datadoghq:dd-sdk-android-coil:<latest-version>")
}
```

### Initial Setup

1. Setup RUM monitoring, see the dedicated [Datadog Android RUM Collection documentation][2] to learn how.
2. Setup OkHttp instrumentation with Datadog RUM SDK, see the [dedicated documentation][3] to learn how.

Follow Coil's [API documentation][4] to:
 
 - Create your own `ImageLoader` by providing your own `OkHttpClient` (configured with `DatadogInterceptor`).

```kotlin
    val imageLoader = ImageLoader.Builder(context).okHttpClient(okHttpClient).build()
    Coil.setImageLoader(imageLoader)
```

- Decorate the `ImageRequest.Builder` with the `DatadogCoilRequestListener` whenever you perform an image loading request.
 
```kotlin
    imageView.load(uri) {
       listener(DatadogCoilRequestListener())
    }
```

This automatically tracks Coil's network requests (creating both APM Traces and RUM Resource events), and listens for disk cache errors (creating RUM Error events).

## Contributing

For details on contributing, read the 
[Contributing Guide](../../CONTRIBUTING.md).

## License

[Apache License, v2.0](../../LICENSE)

[1]: https://github.com/coil-kt/coil
[2]: https://docs.datadoghq.com/real_user_monitoring/android/?tab=kotlin
[3]: https://docs.datadoghq.com/real_user_monitoring/android/advanced_configuration/?tab=kotlin#automatically-track-network-requests
[4]: https://coil-kt.github.io/coil/getting_started/
