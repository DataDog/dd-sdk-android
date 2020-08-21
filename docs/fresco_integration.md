# Android Fresco Integration

Enrich your RUM dashboard with [Fresco][1] specific information.

**Note**: This package is an extension of the main package, so add both dependencies into your gradle file.

## Setup

```configure
repositories {
    maven { url "https://dl.bintray.com/datadog/datadog-maven" }
}

dependencies {
    implementation "com.datadoghq:dd-sdk-android:x.x.x"
    implementation "com.datadoghq:dd-sdk-android-fresco:x.x.x"
}
```

### Fresco integration setup

Following Fresco's [Generated API documentation][2], you then need to create your own `OkHttpImagePipelineConfigFactory` by providing your own `OkHttpClient` having the `DatadogInterceptor` attached,
followed by providing the `DatadogFrescoCacheListener` through the `DiskCacheConfig`.

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

[1]: https://github.com/facebook/fresco
[2]: https://frescolib.org/docs/index.html