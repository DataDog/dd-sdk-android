# Datadog Integration for Coil 3

## Getting Started 

To include the Datadog integration for [Coil 3][1] in your project, add the
following to your application's `build.gradle.kts` file.

```kotlin
dependencies {
    implementation("com.datadoghq:dd-sdk-android-rum:<latest-version>")
    implementation("com.datadoghq:dd-sdk-android-okhttp:<latest-version>")
    implementation("com.datadoghq:dd-sdk-android-coil3:<latest-version>")
}
```

### Initial Setup

1. Setup RUM monitoring, see the dedicated [Datadog Android RUM Collection documentation][2] to learn how.
2. Setup OkHttp instrumentation with Datadog RUM SDK, see the [dedicated documentation][3] to learn how.

Follow Coil 3's [documentation][4] to:
 
 - Create your own `ImageLoader` by providing your own `OkHttpClient` (configured with `DatadogInterceptor`) using `OkHttpNetworkFetcherFactory`.

```kotlin
    val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(DatadogInterceptor.Builder(tracedHosts).build())
        .build()

    val imageLoader = ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory(okHttpClient))
        }
        .build()

    SingletonImageLoader.setSafe { imageLoader }
```

- Decorate the `ImageRequest.Builder` with the `DatadogCoilRequestListener` whenever you perform an image loading request.
 
```kotlin
    imageView.load(uri) {
       listener(DatadogCoilRequestListener())
    }
```

With this setup:
- The `DatadogInterceptor` on OkHttpClient automatically tracks Coil's network requests (creating APM Traces and RUM Resource events)
- The `DatadogCoilRequestListener` reports image loading failures (creating RUM Error events)

## Contributing

For details on contributing, read the 
[Contributing Guide](../../CONTRIBUTING.md).

## License

[Apache License, v2.0](../../LICENSE)

[1]: https://github.com/coil-kt/coil
[2]: https://docs.datadoghq.com/real_user_monitoring/android/?tab=kotlin
[3]: https://docs.datadoghq.com/real_user_monitoring/android/advanced_configuration/?tab=kotlin#automatically-track-network-requests
[4]: https://coil-kt.github.io/coil/upgrading_to_coil3/

