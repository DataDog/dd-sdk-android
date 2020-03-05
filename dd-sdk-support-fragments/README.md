# Datadog Integration to be able to use the TrackFragmentsAsViewsStrategy for old Android Support packages

## Getting Started 

To include the Datadog Fragments tracking strategy Android Support implementation in your project, 
simply add the following to your application's `build.gradle` file.

```
repositories {
    maven { url "https://dl.bintray.com/datadog/datadog-maven" }
}

dependencies {
    implementation "com.datadoghq:dd-sdk-android-support-fragments:<latest-version>"
    implementation "com.datadoghq:dd-sdk-android:<latest-version>"
}
```

### Initial Setup

Before you can use the SDK, you need to setup the library with your application
context and your API token. You can create a token from the Integrations > API
in Datadog. **Make sure you create a key of type `Client Token`.**

Once Datadog is initialized, you can then create a `Logger` instance using the
dedicated builder, and integrate it in Timber, as follow: 

```kotlin
class SampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val config = DatadogConfig.Builder(BuildConfig.DD_CLIENT_TOKEN)
                     .setViewTrackingStrategy(TrackFragmentsAsViewsStrategy)   
                     .build()
        Datadog.initialize(this, config)
    }
}
```

## Contributing

Pull requests are welcome, but please open an issue first to discuss what you
would like to change. For more information, read the 
[Contributing Guide](../CONTRIBUTING.md).

## License

[Apache License, v2.0](../LICENSE)
