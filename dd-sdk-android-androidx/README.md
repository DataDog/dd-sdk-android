# Datadog extra tracking features for the Android Jetpack components.

## Getting Started 

To use the Datadog Android Jetpack extra tracking features
simply add the following to your application's `build.gradle` file.

```
repositories {
    maven { url "https://dl.bintray.com/datadog/datadog-maven" }
}

dependencies {
    implementation "com.datadoghq:dd-sdk-android-androidx:<latest-version>"
    implementation "com.datadoghq:dd-sdk-android:<latest-version>"
}
```

## Initial Setup

Before you can use the SDK, you need to setup the library with your application
context, your API token and your application ID. 
You can create a token from the Integrations > API in Datadog. **Make sure you create a key of type `Client Token`.**
You can create an Application ID from UX Monitoring > RUM Applications > New Application

```kotlin
class SampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val config = DatadogConfig.Builder([YOUR_TOKEN], [YOUR_APPLICATION_ID])
                     .build()
        Datadog.initialize(this, config)
    }
}
```

## Datadog Integration to be able to use the TrackFragmentsAsViewsStrategy for AndroidX

You can after initialize the Datadog SDK and set the **TrackFragmentsAaViewStrategy** 
as the current **viewTrackingStrategy** in the config file.

```kotlin
class SampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val config = DatadogConfig.Builder([YOUR_TOKEN], [YOUR_APPLICATION_ID])
                     .setViewTrackingStrategy(TrackFragmentsAsViewsStrategy())   
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
