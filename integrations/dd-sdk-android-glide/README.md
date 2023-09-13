# Datadog Integration for Glide

## Getting Started 

To include the Datadog integration for [Glide][1] in your project, simply add the
following to your application's `build.gradle` file.

```groovy
dependencies {
    implementation "com.datadoghq:dd-sdk-android-rum:<latest-version>"
    implementation "com.datadoghq:dd-sdk-android-trace:<latest-version>"
    implementation "com.datadoghq:dd-sdk-android-glide:<latest-version>"
}
```

### Initial Setup

1. Setup RUM monitoring, see the dedicated [Datadog Android RUM Collection documentation][2] to learn how.
2. Setup Trace monitoring, see the dedicated [Datadog Android Trace Collection documentation][3] to learn how.
3. Following Glide's [Generated API documentation][4], you then need to create your own `GlideAppModule` with Datadog integrations by extending the `DatadogGlideModule`, as follow.

Doing so will automatically track Glide's network requests (creating both APM Traces and RUM Resource events), and will also listen for disk cache and image transformation errors (creating RUM Error events).

```kotlin
@GlideModule
class CustomGlideModule : 
    DatadogGlideModule(
        firstPartyHosts = listOf("example.com", "example.eu"), sampleRate = 20f
    )
```

Network requests are sampled with an adjustable sampling rate. A sampling of 20% is applied by default.

## Contributing

Pull requests are welcome, but please open an issue first to discuss what you
would like to change. For more information, read the 
[Contributing Guide](../../CONTRIBUTING.md).

## License

[Apache License, v2.0](../../LICENSE)

[1]: https://bumptech.github.io/glide/
[2]: https://docs.datadoghq.com/real_user_monitoring/android/?tab=kotlin
[3]: https://docs.datadoghq.com/tracing/trace_collection/dd_libraries/android/?tab=kotlin
[4]: https://bumptech.github.io/glide/doc/generatedapi.html