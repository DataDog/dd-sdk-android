# Datadog Integration for Glide

## Getting Started 

To include the Datadog integration for [Glide][1] in your project, simply add the
following to your application's `build.gradle` file.

```
repositories {
    maven { url "https://dl.bintray.com/datadog/datadog-maven" }
}

dependencies {
    implementation "com.datadoghq:dd-sdk-android:<latest-version>"
    implementation "com.datadoghq:dd-sdk-android-glide:<latest-version>"
}
```

### Initial Setup

Before you can use the SDK, you need to setup the library with your application
context and your API token. You can create a token from the Integrations > API
in Datadog. **Make sure you create a key of type `Client Token`.**

```kotlin
class SampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val config = DatadogConfig.Builder(BuildConfig.DD_CLIENT_TOKEN).build()
        Datadog.initialize(this, config)

        val  logger = Logger.Builder()
                .setNetworkInfoEnabled(true)
                .setLogcatLogsEnabled(true)
                .setDatadogLogsEnabled(true)
                .build();
    }
}
```

Following Glide's [Generated API documentation][2], you then need to create your own `GlideAppModule` with Datadog integrations by extending the `DatadogGlideModule`, as follow.

Doing so will automatically track Glide's network requests (creating both APM Traces and RUM Resource events), and will also listen for disk cache and image transformation errors (creating RUM Error events).

```kotlin
@GlideModule
class CustomGlideModule : 
    DatadogGlideModule(
        listOf("example.com", "example.eu")
    )
```


That's it, now all your Glide logs will be sent to Datadog automatically.

## Contributing

Pull requests are welcome, but please open an issue first to discuss what you
would like to change. For more information, read the 
[Contributing Guide](../CONTRIBUTING.md).

## License

[Apache License, v2.0](../LICENSE)

[1]: https://bumptech.github.io/glide/
[2]: https://bumptech.github.io/glide/doc/generatedapi.html