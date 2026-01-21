# Datadog Integration for Fresco

## Getting Started 

To include the Datadog integration for [Fresco][1] in your project, simply add the
following to your application's `build.gradle.kts` file.

```kotlin
dependencies {
    implementation("com.datadoghq:dd-sdk-android-rum:<latest-version>")
    implementation("com.datadoghq:dd-sdk-android-okhttp:<latest-version>")
    implementation("com.datadoghq:dd-sdk-android-fresco:<latest-version>")
}
```

### Initial Setup

1. Setup RUM monitoring, see the dedicated [Datadog Android RUM Collection documentation][2] to learn how.
2. Setup OkHttp instrumentation with Datadog RUM SDK, see the [dedicated documentation][3] to learn how.
3. Following Fresco's [Generated API documentation][4], you need to create your own `OkHttpImagePipelineConfigFactory` by providing your own `OkHttpClient` (configured with `DatadogInterceptor`). You can also add an instance of `DatadogFrescoCacheListener` in your `DiskCacheConfig`.

Doing so will automatically track Fresco's network requests (creating both APM Traces and RUM Resource events), and will also listen for disk cache errors (creating RUM Error events).

```kotlin
    val config = OkHttpImagePipelineConfigFactory.newBuilder(context, okHttpClient)
        .setMainDiskCacheConfig(
            DiskCacheConfig.newBuilder(context)
                .setCacheEventListener(DatadogFrescoCacheListener())
                .build()
        )
        .build()
    Fresco.initialize(context, config)
```


## Contributing

Pull requests are welcome, but please open an issue first to discuss what you
would like to change. For more information, read the 
[Contributing Guide](../../CONTRIBUTING.md).

## License

[Apache License, v2.0](../../LICENSE)

[1]: https://github.com/facebook/fresco
[2]: https://docs.datadoghq.com/real_user_monitoring/android/?tab=kotlin
[3]: https://docs.datadoghq.com/real_user_monitoring/android/advanced_configuration/?tab=kotlin#automatically-track-network-requests
[4]: https://frescolib.org/docs/index.html
