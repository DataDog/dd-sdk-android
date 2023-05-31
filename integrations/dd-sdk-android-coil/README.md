# Datadog Integration for Coil

## Getting Started 

To include the Datadog integration for [Coil][1] in your project, add the
following to your application's `build.gradle` file.

```
dependencies {
    implementation "com.datadoghq:dd-sdk-android:<latest-version>"
    implementation "com.datadoghq:dd-sdk-android-coil:<latest-version>"
}
```

### Initial setup

Before using the SDK, set up the library with your application
context, client token, and application ID. 
To generate a client token and an application ID, check **UX Monitoring > RUM Applications > New Application**
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

Follow Coil's [API documentation][2] to:
 
 - Create your own `ImageLoader` by providing your own OkHttpClient (configured with `DatadogInterceptor`).

```kotlin
    val imageLoader = ImageLoader.Builder(context).okHttpClient(okHttpClient).build()
    Coil.setImageLoader(imageLoader)
```

- Decorate the `ImageRequest.Builder` with the `DatadogCoilRequestListener` whenever you perform an image loading request.
 
 ```kotlin
     imageView.load(uri){
        listener(DatadogCoilRequestListener())
     }
 ```

This automatically tracks Coil's network requests (creating both APM Traces and RUM Resource events), and listens for disk cache errors (creating RUM Error events).

## Contributing

For details on contributing, read the 
[Contributing Guide](../../CONTRIBUTING.md).

## License

[Apache License, v2.0](../../LICENSE)

[1]: https://github.com/coil-kt/coil
[2]: https://coil-kt.github.io/coil/getting_started/
