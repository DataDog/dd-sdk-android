# Android Glide Integration

Enrich your RUM dashboard with [Glide v4][1] specific information.

**Note**: This package is an extension of the main package, so add both dependencies into your gradle file.

## Setup

```configure
repositories {
    maven { url "https://dl.bintray.com/datadog/datadog-maven" }
}

dependencies {
    implementation "com.datadoghq:dd-sdk-android:x.x.x"
    implementation "com.datadoghq:dd-sdk-android-glide:x.x.x"
}
```

### Glide integration setup

Following Glide's [Generated API documentation][2], you need to create your own `GlideAppModule` with Datadog integrations by extending the `DatadogGlideModule`, as follow.

Doing so will automatically track Glide's network requests (creating both APM Traces and RUM Resource events), and will also listen for disk cache and image transformation errors (creating RUM Error events).

```kotlin
@GlideModule
class CustomGlideModule : 
    DatadogGlideModule(
        listOf("example.com", "example.eu")
    )
```

[1]: https://bumptech.github.io/glide/
[2]: https://bumptech.github.io/glide/doc/generatedapi.html
