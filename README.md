# Datadog SDK for Android

> A client-side Android library to interact with Datadog.



## Getting Started 

## Gradle Dependency

```groovy
dependencies {
    testCompile("com.datadoghq:dd-sdk-android:x.x.x")
}
```

## Usage

### Initial Setup

Before you can use the SDK, you need to setup the library with your application context and your API token.
You can create a token from the Integrations > API in Datadog. **Make sure you create a key of type `Client Token`.**

```kotlin
class SampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Datadog.initialize(this, BuildConfig.DD_CLIENT_TOKEN)
    }
}
```

### Logging

You can create a `Logger` instance using the dedicated builder, as follow:

```kotlin
val logger = Logger.Builder().build()
```

> TODO document every feature of the Builder

You can then send logs with the following methods, mimicking the ones available in the Android Framework: 

```kotlin
logger.d("A debug message.")
logger.i("Some relevant information ?")
logger.w("An important warning…")
logger.e("An error was met!")
logger.wtf("What a Terrible Failure!")
```

## Contributing
Pull requests are welcome, but please open an issue first to discuss what you would like to change. For more information, read the [Contributing Guide](CONTRIBUTING.md).

## License
[Apache License, v2.0](LICENSE)
