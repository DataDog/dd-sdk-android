# Datadog Integration for Timber

## Getting Started 

To include the Datadog integration for Timber in your project, simply add the
following to your application's `build.gradle` file.

```
dependencies {
    implementation "com.datadoghq:dd-sdk-android:<latest-version>"
    implementation "com.datadoghq:dd-sdk-android-timber:<latest-version>"
}
```

### Initial Setup

Before you can use the SDK, you need to setup the library with your application
context and your API token. You can create a token from the Integrations > API
in Datadog. **Make sure you create a key of type `Client Token`.**

Once Datadog is initialized, you can then create a `Logger` instance using the
dedicated builder, and integrate it in Timber, as follows: 

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

          val logger = Logger.Builder()
                          .setNetworkInfoEnabled(true)
                          .setLogcatLogsEnabled(true)
                          .setDatadogLogsEnabled(true)
                          .build();
          Timber.plant(DatadogTree(logger))
   }
}
```

That's it, now all your Timber logs will be sent to Datadog automatically.

You can configure the logger's tags and attributes, as explained in the  [Datadog Android log collection documentation](http://docs.datadoghq.com/logs/log_collection/android)

## Contributing

Pull requests are welcome, but please open an issue first to discuss what you
would like to change. For more information, read the 
[Contributing Guide](../../CONTRIBUTING.md).

## License

[Apache License, v2.0](../../LICENSE)
