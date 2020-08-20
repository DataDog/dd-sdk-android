# Datadog Integration for Coil

## Getting Started 

To include the Datadog integration for [Coil][1] in your project, simply add the
following to your application's `build.gradle` file.

```
repositories {
    maven { url "https://dl.bintray.com/datadog/datadog-maven" }
}

dependencies {
    implementation "com.datadoghq:dd-sdk-android:<latest-version>"
    implementation "com.datadoghq:dd-sdk-android-coil:<latest-version>"
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

        val  logger = Logger.Builder()
                .setNetworkInfoEnabled(true)
                .setLogcatLogsEnabled(true)
                .setDatadogLogsEnabled(true)
                .build();
    }
}
```

Following Coil's [API documentation][2], you then need to:
 
 - Create your own `ImageLoader` by providing your own `OkHttpClient` having the `DatadogInterceptor` attached,
and register it.

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

Doing so will automatically track Coil's network requests (creating both APM Traces and RUM Resource events), and will also listen for disk cache errors (creating RUM Error events).

## Contributing

Pull requests are welcome, but please open an issue first to discuss what you
would like to change. For more information, read the 
[Contributing Guide](../CONTRIBUTING.md).

## License

[Apache License, v2.0](../LICENSE)

[1]: https://github.com/coil-kt/coil
[2]: https://coil-kt.github.io/coil/getting_started/
