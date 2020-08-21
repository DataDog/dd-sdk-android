# Android Coil Integration

Enrich your RUM dashboard with [Coil][1] specific information.

**Note**: This package is an extension of the main package, so add both dependencies into your gradle file.

## Setup

```configure
repositories {
    maven { url "https://dl.bintray.com/datadog/datadog-maven" }
}

dependencies {
    implementation "com.datadoghq:dd-sdk-android:x.x.x"
    implementation "com.datadoghq:dd-sdk-android-coil:x.x.x"
}
```

### Coil integration setup

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

[1]: https://github.com/coil-kt/coil
[2]: https://coil-kt.github.io/coil/getting_started/
