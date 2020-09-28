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
context, your Client token and your Application ID. 
To generate a Client token and an Application ID please check **UX Monitoring > RUM Applications > New Application**
in the Datadog dashboard.

```kotlin
class SampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val config = DatadogConfig.Builder("<CLIENT_TOKEN>", "<ENVIRONMENT_NAME>", "<APPLICATION_ID>").build()
        Datadog.initialize(this, config)

        val monitor = RumMonitor.Builder().build()
        GlobalRum.registerIfAbsent(monitor)
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


## Contributing

Pull requests are welcome, but please open an issue first to discuss what you
would like to change. For more information, read the 
[Contributing Guide](../CONTRIBUTING.md).

## License

[Apache License, v2.0](../LICENSE)

[1]: https://bumptech.github.io/glide/
[2]: https://bumptech.github.io/glide/doc/generatedapi.html