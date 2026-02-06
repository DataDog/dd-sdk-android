# Datadog Integration for Timber

## Getting Started 

To include the Datadog integration for Timber in your project, simply add the
following to your application's `build.gradle.kts` file.

```kotlin
dependencies {
    implementation("com.datadoghq:dd-sdk-android-logs:<latest-version>")
    implementation("com.datadoghq:dd-sdk-android-timber:<latest-version>")
}
```

### Initial Setup

1. Setup RUM monitoring, see the dedicated [Datadog Android Log Collection documentation][1] to learn how.
2. Add `DatadogTree` to Timber:

   ```kotlin
   val logger = Logger.Builder()
           .setNetworkInfoEnabled(true)
           .setLogcatLogsEnabled(true)
           .setRemoteSampleRate(100f)
           .setBundleWithTraceEnabled(true)
           .setName("<LOGGER_NAME>")
           .build()

   Timber.plant(Timber.DebugTree(), DatadogTree(logger))
   ```

That's it, now all your Timber logs will be sent to Datadog automatically.

You can configure the logger's tags and attributes, as explained in the  [Datadog Android Log Collection documentation][1]

## Contributing

Pull requests are welcome, but please open an issue first to discuss what you
would like to change. For more information, read the 
[Contributing Guide](../../CONTRIBUTING.md).

## License

[Apache License, v2.0](../../LICENSE)

[1]: https://docs.datadoghq.com/logs/log_collection/android/?tab=kotlin
