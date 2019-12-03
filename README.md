# Datadog SDK for Android

> A client-side Android library to interact with Datadog.

## Getting Started 

## Gradle Dependency

```groovy
dependencies {
    implementation("com.datadoghq:dd-sdk-android:x.x.x")
}
```

## Usage

### Initial Setup

Before you can use the SDK, you need to setup the library with your application
context and your API token. You can create a token from the Integrations > API
in Datadog. **Make sure you create a key of type `Client Token`.**

```kotlin
class SampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Datadog.initialize(this, BuildConfig.DD_CLIENT_TOKEN)
    }
}
```

### Setup for Europe

If you're targetting our [Europe servers](https://datadoghq.eu), you can
initialize the library like this: 

```kotlin
class SampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Datadog.initialize(this, BuildConfig.DD_CLIENT_TOKEN, Datadog.DATADOG_EU)
    }
}
```

### Initialization

You can create a `Logger` instance using the dedicated builder, as follow:

```kotlin
    logger = Logger.Builder()
            .setNetworkInfoEnabled(true)
            .setServiceName("com.example.app.android")
            .setLogcatLogsEnabled(true)
            .setDatadogLogsEnabled(true)
            .build();
```

### Logging

You can then send logs with the following methods, mimicking the ones available
in the Android Framework: 

```kotlin
    logger.d("A debug message.")
    logger.i("Some relevant information ?")
    logger.w("An important warning…")
    logger.e("An error was met!")
    logger.wtf("What a Terrible Failure!")
```

### Logging Errors

If you caught an exception and want to log it with a message, you can do so as
follow:

```kotlin
    try {
        doSomething()
    } catch (e : IOException) {
        logger.e("Error while doing something", e)
    }
```

> Note: All log level methods can have a throwable attached to them.

## Contributing

Pull requests are welcome, but please open an issue first to discuss what you
would like to change. For more information, read the 
[Contributing Guide](CONTRIBUTING.md).

## License

[Apache License, v2.0](LICENSE)
