# Datadog Integration for Fresco

## Getting Started 

To include the Datadog integration for [Fresco][1] in your project, simply add the
following to your application's `build.gradle` file.

```
dependencies {
    implementation "com.datadoghq:dd-sdk-android:<latest-version>"
    implementation "com.datadoghq:dd-sdk-android-fresco:<latest-version>"
}
```

### Initial Setup

Before you can use the SDK, you need to setup the library with your application
context, your Client token and your Application ID. 
To generate a Client token and an Application ID please check **UX Monitoring > RUM Applications > New Application**
in the Datadog dashboard.

```kotlin
class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val configuration = Configuration.Builder(
            rumEnabled = true,
            ...
        )
                        .trackInteractions()
                        .useViewTrackingStrategy(strategy)
                        ...
                        .build()
          val credentials = Credentials(<CLIENT_TOKEN>, <ENV_NAME>, <APP_VARIANT_NAME>, <APPLICATION_ID>)
          Datadog.initialize(this, credentials, configuration, trackingConsent)

          val monitor = RumMonitor.Builder().build()
          GlobalRum.registerIfAbsent(monitor)
   }
}
```

Following Fresco's [Generated API documentation][2], you need to create your own `OkHttpImagePipelineConfigFactory` by providing your own `OkHttpClient` (configured with `DatadogInterceptor`). You can also add an instance of `DatadogFrescoCacheListener` in your `DiskCacheConfig`.

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
[2]: https://frescolib.org/docs/index.html
